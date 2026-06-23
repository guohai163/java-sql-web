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
    """负责上下文缓存、相关片段召回、LLM 生成和审计落库的应用服务。"""

    def __init__(self) -> None:
        """初始化 OpenAI 兼容客户端和本地中文向量模型。"""

        self.client = AsyncOpenAI(
            api_key=settings.llm_api_key,
            base_url=settings.llm_base_url,
            timeout=settings.generation_timeout_seconds,
        )
        self.embedder = SentenceTransformer(settings.embedding_model, device="cpu")

    def _cache_key(self, server_code: str, db_name: str) -> str:
        """按数据源和数据库生成唯一缓存键。"""

        return f"{server_code}::{db_name}"

    def _build_chunks(self, context: VannaContext) -> list[tuple[str, str, str]]:
        """把完整上下文拆成可检索片段，供向量召回和缓存明细使用。"""

        chunks: list[tuple[str, str, str]] = []
        for table in context.tables:
            # 表名和表注释通常能快速定位业务主题。
            chunks.append(("table", table.tableName, f"{table.tableName}: {table.tableComment or ''}"))
        for column in context.columns:
            # 字段粒度片段用于提升问题中指标、状态、时间字段的命中率。
            chunks.append(
                (
                    "column",
                    f"{column.tableName}.{column.columnName}",
                    f"{column.tableName}.{column.columnName} {column.columnType or ''} {column.columnComment or ''}",
                )
            )
        for example in context.historyExamples:
            # 历史 SQL 片段帮助模型学习当前项目里常见的 join 和过滤写法。
            chunks.append(
                (
                    "history",
                    str(example.queryLogCode or example.sqlTemplate[:120]),
                    example.sqlTemplate,
                )
            )
        return chunks

    def _persist_context(self, cache_key: str, context: VannaContext) -> None:
        """持久化最新上下文文本和检索片段。"""

        schema_text = build_schema_text(context)
        matched_tables = [table.tableName for table in context.tables]
        with get_conn() as conn:
            with conn.cursor() as cur:
                # cache 表保存完整 schema 文本，contextVersion 不变时无需重复刷新。
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
                # 上下文版本变化后重建片段，避免旧字段/旧历史 SQL 干扰生成。
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
        """确保本地缓存与 JavaSqlWeb 返回的上下文版本一致。"""

        cache_key = self._cache_key(server_code, db_name)
        with get_conn() as conn:
            with conn.cursor() as cur:
                cur.execute("SELECT context_version FROM vanna_context_cache WHERE cache_key = %s", (cache_key,))
                row = cur.fetchone()
        if row is None or row[0] != context.contextVersion:
            self._persist_context(cache_key, context)

    def _retrieve_relevant_chunks(self, context: VannaContext, question: str) -> list[str]:
        """用本地向量模型从 schema、字段和历史 SQL 中召回高相关片段。"""

        chunks = [chunk_text for _, _, chunk_text in self._build_chunks(context)]
        if not chunks:
            return []
        query_text = f"{settings.embedding_query_prefix}{question}".strip()
        query_vector = self.embedder.encode([query_text], normalize_embeddings=True)[0]
        chunk_vectors = self.embedder.encode(chunks, normalize_embeddings=True)
        ranked: list[tuple[float, str]] = []
        for vector, chunk in zip(chunk_vectors, chunks):
            # 向量已归一化，点积即可表示余弦相似度。
            ranked.append((float((query_vector * vector).sum()), chunk))
        ranked.sort(key=lambda item: item[0], reverse=True)
        return [chunk for _, chunk in ranked[: settings.embedding_top_k]]

    def _extract_chat_content(self, response: Any) -> str:
        """兼容不同 OpenAI SDK/代理返回形态，抽取最终 message.content。"""

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
            # 官方 SDK 对象通常是 response.choices[0].message.content。
            message = getattr(choices[0], "message", None)
            content = getattr(message, "content", None)
            return self._normalize_chat_content(content)

        if hasattr(response, "model_dump"):
            # 某些响应对象不能直接取 choices，但可以转为 dict 后继续解析。
            return self._extract_chat_content_from_mapping(response.model_dump())

        return str(response)

    def _extract_chat_content_from_mapping(self, response: dict[str, Any]) -> str:
        """从 dict 形态的 chat completion 响应中抽取内容。"""

        choices = response.get("choices")
        if choices:
            message = choices[0].get("message", {}) if isinstance(choices[0], dict) else {}
            return self._normalize_chat_content(message.get("content"))
        return json.dumps(response)

    def _normalize_chat_content(self, content: Any) -> str:
        """把 message.content 归一化为字符串，兼容多段 content 列表。"""

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
        """解析模型返回内容，兼容 JSON、包裹 JSON、纯 SQL 和不可解析文本。"""

        content = self._strip_json_fence(self._extract_chat_content(response).strip())
        if not content:
            return self._clarification_payload("模型返回为空，请稍后重试或换一种问法。", "模型返回为空")
        payload = self._loads_payload_json(content)
        if payload is None:
            extracted_json = self._extract_first_json_object(content)
            if extracted_json:
                payload = self._loads_payload_json(extracted_json)
        if payload is None:
            sql = self._extract_read_only_sql(content)
            if sql:
                return {
                    "needsClarification": False,
                    "clarificationQuestion": None,
                    "sql": sql,
                    "summary": "已根据当前上下文生成 SQL 建议",
                    "matchedTables": [],
                    "warnings": ["模型未按 JSON 格式返回，已从文本中提取只读 SQL"],
                }
            LOG.warning("Chat completion content is not valid JSON: %r", content[:1000])
            return self._clarification_payload(
                "AI 返回内容格式异常，请稍后重试或换一种问法。",
                "模型返回内容不是有效 JSON，且未提取到只读 SQL",
            )
        if isinstance(payload, dict) and payload.get("choices"):
            return self._parse_chat_payload(payload)
        if not isinstance(payload, dict):
            return self._clarification_payload("AI 返回内容格式异常，请稍后重试或换一种问法。", "模型返回内容不是 JSON 对象")
        return payload

    def _loads_payload_json(self, content: str) -> Any | None:
        """尝试解析 JSON，失败时返回 None 让上层继续走其它兼容路径。"""

        try:
            return json.loads(content)
        except json.JSONDecodeError:
            return None

    def _extract_first_json_object(self, content: str) -> str | None:
        """从包含说明文字的响应里提取第一个完整 JSON object。"""

        start = content.find("{")
        if start < 0:
            return None
        depth = 0
        in_string = False
        escaped = False
        for index in range(start, len(content)):
            char = content[index]
            if in_string:
                if escaped:
                    escaped = False
                elif char == "\\":
                    escaped = True
                elif char == '"':
                    in_string = False
                continue
            if char == '"':
                in_string = True
            elif char == "{":
                depth += 1
            elif char == "}":
                depth -= 1
                if depth == 0:
                    return content[start : index + 1]
        return None

    def _extract_read_only_sql(self, content: str) -> str | None:
        """当兼容网关返回纯 SQL 时，提取只读语句作为生成结果。"""

        candidate = (self._extract_first_fenced_block(content) or self._strip_sql_fence(content)).strip()
        if not candidate:
            return None
        lower = candidate.lower()
        if lower.startswith("select") or lower.startswith("with"):
            return candidate
        for marker in ("select ", "with "):
            index = lower.find(marker)
            if index >= 0:
                sql = self._strip_trailing_fence(candidate[index:].strip())
                sql_lower = sql.lower()
                if sql_lower.startswith("select") or sql_lower.startswith("with"):
                    return sql
        return None

    def _extract_first_fenced_block(self, content: str) -> str | None:
        """提取响应中任意位置的第一个 Markdown 代码块内容。"""

        start = content.find("```")
        if start < 0:
            return None
        body_start = content.find("\n", start + 3)
        if body_start < 0:
            return None
        end = content.find("```", body_start + 1)
        if end < 0:
            return None
        return content[body_start + 1 : end].strip()

    def _strip_trailing_fence(self, content: str) -> str:
        """去掉从说明文本中截取 SQL 时可能带上的尾部代码块标记。"""

        fence_index = content.find("```")
        if fence_index >= 0:
            return content[:fence_index].strip()
        return content

    def _strip_sql_fence(self, content: str) -> str:
        """去掉模型偶尔返回的 Markdown SQL 代码块包裹。"""

        if content.startswith("```") and content.endswith("```"):
            lines = content.splitlines()
            if lines:
                lines = lines[1:]
            if lines and lines[-1].strip() == "```":
                lines = lines[:-1]
            return "\n".join(lines).strip()
        return content

    def _clarification_payload(self, question: str, warning: str) -> dict[str, Any]:
        """生成不会导致接口 500 的保守失败响应。"""

        return {
            "needsClarification": True,
            "clarificationQuestion": question,
            "sql": None,
            "summary": "AI 返回内容格式异常，未生成 SQL",
            "matchedTables": [],
            "warnings": [warning],
        }

    def _strip_json_fence(self, content: str) -> str:
        """去掉模型偶尔返回的 Markdown JSON 代码块包裹。"""

        if content.startswith("```") and content.endswith("```"):
            lines = content.splitlines()
            if lines:
                lines = lines[1:]
            if lines and lines[-1].strip() == "```":
                lines = lines[:-1]
            return "\n".join(lines).strip()
        return content

    async def generate_sql(self, server_code: str, db_name: str, question: str, context: VannaContext) -> GenerateSqlResponse:
        """根据用户问题和受权限控制的上下文生成只读 SQL。"""

        started_at = time.time()
        self.ensure_context(server_code, db_name, context)
        relevant_chunks = self._retrieve_relevant_chunks(context, question)
        # response_format 要求模型尽量返回 JSON，后续仍会做兼容解析和安全校验。
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
        # 先按标准返回结构建模，缺失字段使用保守默认值。
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
            # 双重保护：即使 prompt 要求只读，也在服务端拒绝非 SELECT/WITH 语句。
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
        """保存本次生成的审计记录，包含 SQL、告警、状态和耗时。"""

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
