import json
import logging
import time
from typing import Any

from openai import AsyncOpenAI
from sentence_transformers import SentenceTransformer

from .config import settings
from .db import get_conn
from .models import GenerateSqlResponse, VannaContext
from .prompting import SYSTEM_PROMPT, build_schema_text, build_user_prompt

LOG = logging.getLogger(__name__)


class VannaService:
    def __init__(self) -> None:
        self.client = AsyncOpenAI(
            api_key=settings.llm_api_key,
            base_url=settings.llm_base_url,
            timeout=settings.generation_timeout_seconds,
        )
        self.embedder = SentenceTransformer(settings.embedding_model, device="cpu")

    def _cache_key(self, server_code: str, db_name: str) -> str:
        return f"{server_code}::{db_name}"

    def _build_chunks(self, context: VannaContext) -> list[tuple[str, str, str]]:
        chunks: list[tuple[str, str, str]] = []
        for table in context.tables:
            chunks.append(("table", table.tableName, f"{table.tableName}: {table.tableComment or ''}"))
        for column in context.columns:
            chunks.append(
                (
                    "column",
                    f"{column.tableName}.{column.columnName}",
                    f"{column.tableName}.{column.columnName} {column.columnType or ''} {column.columnComment or ''}",
                )
            )
        for example in context.historyExamples:
            chunks.append(
                (
                    "history",
                    str(example.queryLogCode or example.sqlTemplate[:120]),
                    example.sqlTemplate,
                )
            )
        return chunks

    def _persist_context(self, cache_key: str, context: VannaContext) -> None:
        schema_text = build_schema_text(context)
        matched_tables = [table.tableName for table in context.tables]
        with get_conn() as conn:
            with conn.cursor() as cur:
                cur.execute(
                    """
                    INSERT INTO vanna_context_cache (cache_key, context_version, dialect, server_name, db_name, schema_text, matched_tables, updated_at)
                    VALUES (%s, %s, %s, %s, %s, %s, %s, now())
                    ON CONFLICT (cache_key) DO UPDATE SET
                        context_version = excluded.context_version,
                        dialect = excluded.dialect,
                        server_name = excluded.server_name,
                        db_name = excluded.db_name,
                        schema_text = excluded.schema_text,
                        matched_tables = excluded.matched_tables,
                        updated_at = now()
                    """,
                    (
                        cache_key,
                        context.contextVersion,
                        context.dialect,
                        context.serverName,
                        context.dbName,
                        schema_text,
                        matched_tables,
                    ),
                )
                cur.execute("DELETE FROM vanna_context_embedding WHERE cache_key = %s", (cache_key,))
                chunks = self._build_chunks(context)
                for chunk_type, chunk_key, chunk_text in chunks:
                    cur.execute(
                        """
                        INSERT INTO vanna_context_embedding (cache_key, chunk_type, chunk_key, chunk_text, embedding, updated_at)
                        VALUES (%s, %s, %s, %s, NULL, now())
                        ON CONFLICT (cache_key, chunk_type, chunk_key) DO UPDATE SET
                            chunk_text = excluded.chunk_text,
                            embedding = excluded.embedding,
                            updated_at = now()
                        """,
                        (cache_key, chunk_type, chunk_key, chunk_text),
                    )
            conn.commit()

    def ensure_context(self, server_code: str, db_name: str, context: VannaContext) -> None:
        cache_key = self._cache_key(server_code, db_name)
        with get_conn() as conn:
            with conn.cursor() as cur:
                cur.execute("SELECT context_version FROM vanna_context_cache WHERE cache_key = %s", (cache_key,))
                row = cur.fetchone()
        if row is None or row[0] != context.contextVersion:
            self._persist_context(cache_key, context)

    def _retrieve_relevant_chunks(self, context: VannaContext, question: str) -> list[str]:
        chunks = [chunk_text for _, _, chunk_text in self._build_chunks(context)]
        if not chunks:
            return []
        query_text = f"{settings.embedding_query_prefix}{question}".strip()
        query_vector = self.embedder.encode([query_text], normalize_embeddings=True)[0]
        chunk_vectors = self.embedder.encode(chunks, normalize_embeddings=True)
        ranked: list[tuple[float, str]] = []
        for vector, chunk in zip(chunk_vectors, chunks):
            ranked.append((float((query_vector * vector).sum()), chunk))
        ranked.sort(key=lambda item: item[0], reverse=True)
        return [chunk for _, chunk in ranked[: settings.embedding_top_k]]

    def _extract_chat_content(self, response: Any) -> str:
        if response is None:
            return ""
        if isinstance(response, (bytes, bytearray)):
            return response.decode("utf-8")
        if isinstance(response, str):
            return response
        if isinstance(response, dict):
            return self._extract_chat_content_from_mapping(response)

        choices = getattr(response, "choices", None)
        if choices:
            message = getattr(choices[0], "message", None)
            content = getattr(message, "content", None)
            return self._normalize_chat_content(content)

        if hasattr(response, "model_dump"):
            return self._extract_chat_content_from_mapping(response.model_dump())

        return str(response)

    def _extract_chat_content_from_mapping(self, response: dict[str, Any]) -> str:
        choices = response.get("choices")
        if choices:
            message = choices[0].get("message", {}) if isinstance(choices[0], dict) else {}
            return self._normalize_chat_content(message.get("content"))
        return json.dumps(response)

    def _normalize_chat_content(self, content: Any) -> str:
        if content is None:
            return ""
        if isinstance(content, str):
            return content
        if isinstance(content, list):
            parts: list[str] = []
            for item in content:
                if isinstance(item, dict):
                    text = item.get("text") or item.get("content")
                    if text:
                        parts.append(str(text))
                else:
                    parts.append(str(item))
            return "".join(parts)
        return str(content)

    def _parse_chat_payload(self, response: Any) -> dict[str, Any]:
        content = self._strip_json_fence(self._extract_chat_content(response).strip())
        if not content:
            return {}
        payload = json.loads(content)
        if isinstance(payload, dict) and payload.get("choices"):
            return self._parse_chat_payload(payload)
        if not isinstance(payload, dict):
            raise ValueError("chat completion content must be a JSON object")
        return payload

    def _strip_json_fence(self, content: str) -> str:
        if content.startswith("```") and content.endswith("```"):
            lines = content.splitlines()
            if lines:
                lines = lines[1:]
            if lines and lines[-1].strip() == "```":
                lines = lines[:-1]
            return "\n".join(lines).strip()
        return content

    async def generate_sql(self, server_code: str, db_name: str, question: str, context: VannaContext) -> GenerateSqlResponse:
        started_at = time.time()
        self.ensure_context(server_code, db_name, context)
        relevant_chunks = self._retrieve_relevant_chunks(context, question)
        response = await self.client.chat.completions.create(
            model=settings.chat_model,
            temperature=0.1,
            response_format={"type": "json_object"},
            messages=[
                {"role": "system", "content": SYSTEM_PROMPT},
                {"role": "user", "content": build_user_prompt(context, question, relevant_chunks)},
            ],
        )
        LOG.info("Raw chat completion response type=%s value=%r", type(response).__name__, response)
        if hasattr(response, "model_dump"):
            try:
                LOG.info("Raw chat completion response model_dump=%s", response.model_dump())
            except Exception as exception:
                LOG.warning("Failed to dump chat completion response: %s", exception)
        payload = self._parse_chat_payload(response)
        parsed = GenerateSqlResponse(
            needsClarification=bool(payload.get("needsClarification")),
            clarificationQuestion=payload.get("clarificationQuestion"),
            sql=payload.get("sql"),
            dialect=context.dialect,
            summary=payload.get("summary") or "已根据当前上下文生成 SQL 建议",
            matchedTables=[str(item) for item in payload.get("matchedTables", [])],
            warnings=[str(item) for item in payload.get("warnings", [])],
        )
        if parsed.sql:
            normalized = parsed.sql.strip().lower()
            if not (normalized.startswith("select") or normalized.startswith("with")):
                parsed = GenerateSqlResponse(
                    needsClarification=True,
                    clarificationQuestion="当前问题可能会导向非只读语句，请再明确需要查询的指标和过滤条件。",
                    sql=None,
                    dialect=context.dialect,
                    summary="已拒绝非只读 SQL 生成",
                    matchedTables=parsed.matchedTables,
                    warnings=parsed.warnings + ["只允许生成只读 SQL"],
                )
        cost_millis = int((time.time() - started_at) * 1000)
        self._save_audit(server_code, db_name, question, parsed, cost_millis)
        return parsed

    def _save_audit(self, server_code: str, db_name: str, question: str, response: GenerateSqlResponse, cost_millis: int) -> None:
        with get_conn() as conn:
            with conn.cursor() as cur:
                cur.execute(
                    """
                    INSERT INTO vanna_audit_log (
                        server_code, db_name, question, generated_sql, matched_tables,
                        needs_clarification, clarification_question, warnings, model_name,
                        response_status, cost_millis, created_at
                    ) VALUES (%s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, now())
                    """,
                    (
                        server_code,
                        db_name,
                        question,
                        response.sql,
                        response.matchedTables,
                        response.needsClarification,
                        response.clarificationQuestion,
                        response.warnings,
                        settings.chat_model,
                        "clarification" if response.needsClarification else "generated",
                        cost_millis,
                    ),
                )
            conn.commit()
