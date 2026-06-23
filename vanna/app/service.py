import json
import time
from typing import Any

from openai import AsyncOpenAI

from .config import settings
from .db import get_conn
from .models import GenerateSqlResponse, VannaContext
from .prompting import SYSTEM_PROMPT, build_schema_text, build_user_prompt


class VannaService:
    def __init__(self) -> None:
        self.client = AsyncOpenAI(
            api_key=settings.llm_api_key,
            base_url=settings.llm_base_url,
            timeout=settings.generation_timeout_seconds,
        )

    def _cache_key(self, server_code: str, db_name: str) -> str:
        return f"{server_code}::{db_name}"

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

    async def generate_sql(self, server_code: str, db_name: str, question: str, context: VannaContext) -> GenerateSqlResponse:
        started_at = time.time()
        self.ensure_context(server_code, db_name, context)
        response = await self.client.chat.completions.create(
            model=settings.chat_model,
            temperature=0.1,
            response_format={"type": "json_object"},
            messages=[
                {"role": "system", "content": SYSTEM_PROMPT},
                {"role": "user", "content": build_user_prompt(context, question)},
            ],
        )
        payload = json.loads(response.choices[0].message.content or "{}")
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
