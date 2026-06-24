import json
import logging
import time
from pathlib import Path
from typing import Any

from openai import AsyncOpenAI
from sentence_transformers import SentenceTransformer

from .config import settings
from .db import get_conn
from .models import GenerateSqlResponse, VannaContext
from .prompting import SYSTEM_PROMPT, build_schema_text, build_user_prompt

LOG = logging.getLogger(__name__)
DEFAULT_CLARIFICATION_QUESTION = "AI 未能生成有效 SQL，请补充查询目标或换一种问法。"
MISSING_SQL_WARNING = "模型返回 JSON 中缺少有效 SQL"


class VannaService:
    """负责上下文缓存、相关片段召回、LLM 生成和审计落库的应用服务。"""

    def __init__(self) -> None:
        """初始化 OpenAI 兼容客户端和本地中文向量模型。"""

        self.client = AsyncOpenAI(
            api_key=settings.llm_api_key,
            base_url=settings.llm_base_url,
            timeout=settings.generation_timeout_seconds,
        )
        source = settings.embedding_model_source.strip().lower() or "huggingface"
        if source == "huggingface":
            kwargs: dict[str, Any] = {"device": "cpu"}
            if settings.embedding_model_cache_dir.strip():
                kwargs["cache_folder"] = settings.embedding_model_cache_dir.strip()
            if settings.embedding_model_revision.strip():
                kwargs["revision"] = settings.embedding_model_revision.strip()
            self.embedder = SentenceTransformer(settings.embedding_model, **kwargs)
        elif source == "modelscope":
            self.embedder = SentenceTransformer(
                self._download_model_from_modelscope(),
                device="cpu",
            )
        else:
            raise ValueError(
                f"Unsupported VANNA_EMBEDDING_MODEL_SOURCE={settings.embedding_model_source!r}, "
                "expected 'huggingface' or 'modelscope'"
            )

    def _download_model_from_modelscope(self) -> str:
        """从 ModelScope 下载模型到本地缓存，再交给 SentenceTransformer 加载。"""

        try:
            from modelscope import snapshot_download
        except ModuleNotFoundError as exc:
            raise RuntimeError(
                "VANNA_EMBEDDING_MODEL_SOURCE=modelscope requires the 'modelscope' package"
            ) from exc

        model_id = settings.embedding_modelscope_model_id.strip() or settings.embedding_model
        revision = settings.embedding_model_revision.strip() or None
        cache_dir = settings.embedding_model_cache_dir.strip() or None
        LOG.info(
            "Downloading embedding model from ModelScope model_id=%s revision=%s cache_dir=%s",
            model_id,
            revision or "default",
            cache_dir or "default",
        )
        local_path = snapshot_download(
            model_id=model_id,
            revision=revision,
            cache_dir=cache_dir,
        )
        resolved = str(Path(local_path).resolve())
        LOG.info("ModelScope embedding model downloaded to %s", resolved)
        return resolved

    def _cache_key(self, server_code: str, db_name: str) -> str:
        """按数据源和数据库生成唯一缓存键。"""

        return f"{server_code}::{db_name}"

    def _embedding_model_key(self) -> str:
        """生成当前 chunk 向量缓存对应的模型标识。"""

        return (
            f"source={settings.embedding_model_source.strip().lower() or 'huggingface'}"
            f"|model={settings.embedding_model.strip()}"
            f"|revision={settings.embedding_model_revision.strip() or 'default'}"
            "|normalize_embeddings=true"
        )

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

    def _normalize_embedding_vector(self, vector: Any) -> list[float]:
        """把 embedding 输出统一转换成 float 列表，兼容 numpy/list 等返回形态。"""

        if hasattr(vector, "tolist"):
            vector = vector.tolist()
        if not isinstance(vector, (list, tuple)):
            raise TypeError(f"Unsupported embedding vector type: {type(vector)!r}")
        return [float(value) for value in vector]

    def _encode_texts(self, texts: list[str]) -> list[list[float]]:
        """对文本列表做归一化向量编码，并统一成可持久化的 float 数组。"""

        if not texts:
            return []
        encoded = self.embedder.encode(texts, normalize_embeddings=True)
        return [self._normalize_embedding_vector(vector) for vector in encoded]

    def _dot_product(self, left: list[float], right: list[float]) -> float:
        """对两个已归一化的向量做点积，用于近似余弦相似度。"""

        return sum(left_value * right_value for left_value, right_value in zip(left, right))

    def _load_cache_state(self, cache_key: str) -> tuple[str, str] | None:
        """读取当前 cache 的上下文版本与向量模型标识。"""

        with get_conn() as conn:
            with conn.cursor() as cur:
                cur.execute(
                    "SELECT context_version, COALESCE(embedding_model_key, '') FROM vanna_context_cache WHERE cache_key = %s",
                    (cache_key,),
                )
                row = cur.fetchone()
        if row is None:
            return None
        return str(row[0]), str(row[1] or "")

    def _has_complete_embeddings(self, cache_key: str, expected_count: int) -> bool:
        """检查当前 cache 下是否存在完整、可复用的 chunk 向量。"""

        with get_conn() as conn:
            with conn.cursor() as cur:
                cur.execute(
                    """
                    SELECT
                        COUNT(*),
                        COALESCE(
                            SUM(
                                CASE
                                    WHEN embedding_values IS NULL
                                        OR embedding_dims <= 0
                                        OR COALESCE(cardinality(embedding_values), 0) = 0
                                        OR COALESCE(cardinality(embedding_values), 0) <> embedding_dims
                                    THEN 1
                                    ELSE 0
                                END
                            ),
                            0
                        )
                    FROM vanna_context_embedding
                    WHERE cache_key = %s
                    """,
                    (cache_key,),
                )
                row = cur.fetchone()
        total = int((row or (0, 0))[0] or 0)
        invalid = int((row or (0, 0))[1] or 0)
        return total == expected_count and invalid == 0

    def _load_persisted_chunk_vectors(self, cache_key: str) -> list[tuple[str, list[float], int]]:
        """从数据库加载已持久化的 chunk 文本与向量。"""

        with get_conn() as conn:
            with conn.cursor() as cur:
                cur.execute(
                    """
                    SELECT chunk_text, embedding_values, embedding_dims
                    FROM vanna_context_embedding
                    WHERE cache_key = %s
                    ORDER BY id ASC
                    """,
                    (cache_key,),
                )
                rows = cur.fetchall() or []

        persisted: list[tuple[str, list[float], int]] = []
        for chunk_text, embedding_values, embedding_dims in rows:
            vector = self._normalize_embedding_vector(embedding_values) if embedding_values is not None else []
            persisted.append((str(chunk_text), vector, int(embedding_dims or 0)))
        return persisted

    def _rank_chunks_from_vectors(
        self,
        query_vector: list[float],
        chunk_vectors: list[tuple[str, list[float], int]],
    ) -> list[str]:
        """基于已持久化的向量结果做相似度排序。"""

        ranked: list[tuple[float, str]] = []
        query_dims = len(query_vector)
        for chunk_text, vector, dims in chunk_vectors:
            if dims <= 0 or not vector:
                continue
            if dims != len(vector):
                LOG.warning(
                    "Skip persisted chunk due to inconsistent dims cache dims=%s vector_len=%s chunk=%r",
                    dims,
                    len(vector),
                    chunk_text[:120],
                )
                continue
            if dims != query_dims:
                LOG.warning(
                    "Skip persisted chunk due to dims mismatch query_dims=%s chunk_dims=%s chunk=%r",
                    query_dims,
                    dims,
                    chunk_text[:120],
                )
                continue
            ranked.append((self._dot_product(query_vector, vector), chunk_text))
        ranked.sort(key=lambda item: item[0], reverse=True)
        return [chunk for _, chunk in ranked[: settings.embedding_top_k]]

    def _rank_chunks_in_memory(self, query_vector: list[float], chunks: list[str]) -> list[str]:
        """当持久化向量不可用时，回退到旧的内存内全量编码逻辑。"""

        if not chunks:
            return []
        chunk_vectors = self._encode_texts(chunks)
        ranked: list[tuple[float, str]] = []
        query_dims = len(query_vector)
        for vector, chunk in zip(chunk_vectors, chunks):
            if len(vector) != query_dims:
                LOG.warning(
                    "Skip in-memory chunk due to dims mismatch query_dims=%s chunk_dims=%s chunk=%r",
                    query_dims,
                    len(vector),
                    chunk[:120],
                )
                continue
            ranked.append((self._dot_product(query_vector, vector), chunk))
        ranked.sort(key=lambda item: item[0], reverse=True)
        return [chunk for _, chunk in ranked[: settings.embedding_top_k]]

    def _persist_context(self, cache_key: str, context: VannaContext) -> None:
        """持久化最新上下文文本和检索片段。"""

        schema_text = build_schema_text(context)
        matched_tables = [table.tableName for table in context.tables]
        chunks = self._build_chunks(context)
        chunk_texts = [chunk_text for _, _, chunk_text in chunks]
        chunk_embeddings = self._encode_texts(chunk_texts)
        embedding_model_key = self._embedding_model_key()

        with get_conn() as conn:
            with conn.cursor() as cur:
                # cache 表保存完整 schema 文本，contextVersion 不变时无需重复刷新。
                cur.execute(
                    """
                    INSERT INTO vanna_context_cache (
                        cache_key, context_version, dialect, server_name, db_name,
                        schema_text, matched_tables, embedding_model_key, updated_at
                    )
                    VALUES (%s, %s, %s, %s, %s, %s, %s, %s, now())
                    ON CONFLICT (cache_key) DO UPDATE SET
                        context_version = excluded.context_version,
                        dialect = excluded.dialect,
                        server_name = excluded.server_name,
                        db_name = excluded.db_name,
                        schema_text = excluded.schema_text,
                        matched_tables = excluded.matched_tables,
                        embedding_model_key = excluded.embedding_model_key,
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
                        embedding_model_key,
                    ),
                )
                # 上下文版本变化后重建片段，避免旧字段/旧历史 SQL 干扰生成。
                cur.execute("DELETE FROM vanna_context_embedding WHERE cache_key = %s", (cache_key,))
                for (chunk_type, chunk_key, chunk_text), chunk_vector in zip(chunks, chunk_embeddings):
                    cur.execute(
                        """
                        INSERT INTO vanna_context_embedding (
                            cache_key, chunk_type, chunk_key, chunk_text,
                            embedding_values, embedding_dims, updated_at
                        )
                        VALUES (%s, %s, %s, %s, %s, %s, now())
                        ON CONFLICT (cache_key, chunk_type, chunk_key) DO UPDATE SET
                            chunk_text = excluded.chunk_text,
                            embedding_values = excluded.embedding_values,
                            embedding_dims = excluded.embedding_dims,
                            updated_at = now()
                        """,
                        (cache_key, chunk_type, chunk_key, chunk_text, chunk_vector, len(chunk_vector)),
                    )
            conn.commit()

    def ensure_context(self, server_code: str, db_name: str, context: VannaContext) -> None:
        """确保本地缓存与 JavaSqlWeb 返回的上下文版本一致。"""

        cache_key = self._cache_key(server_code, db_name)
        chunks = self._build_chunks(context)
        cache_state = self._load_cache_state(cache_key)
        if cache_state is None:
            self._persist_context(cache_key, context)
            return

        cached_version, cached_embedding_model_key = cache_state
        if (
            cached_version != context.contextVersion
            or cached_embedding_model_key != self._embedding_model_key()
            or not self._has_complete_embeddings(cache_key, len(chunks))
        ):
            self._persist_context(cache_key, context)

    def _retrieve_relevant_chunks(self, server_code: str, db_name: str, context: VannaContext, question: str) -> list[str]:
        """用本地向量模型从 schema、字段和历史 SQL 中召回高相关片段。"""

        chunks = [chunk_text for _, _, chunk_text in self._build_chunks(context)]
        if not chunks:
            return []
        query_text = f"{settings.embedding_query_prefix}{question}".strip()
        query_vector = self._encode_texts([query_text])[0]
        cache_key = self._cache_key(server_code, db_name)

        persisted_vectors = self._load_persisted_chunk_vectors(cache_key)
        ranked_chunks = self._rank_chunks_from_vectors(query_vector, persisted_vectors)
        if ranked_chunks:
            return ranked_chunks

        LOG.info("No usable persisted chunk embeddings for cache_key=%s, rebuilding now", cache_key)
        self._persist_context(cache_key, context)
        persisted_vectors = self._load_persisted_chunk_vectors(cache_key)
        ranked_chunks = self._rank_chunks_from_vectors(query_vector, persisted_vectors)
        if ranked_chunks:
            return ranked_chunks

        LOG.warning("Falling back to in-memory chunk embedding retrieval for cache_key=%s", cache_key)
        return self._rank_chunks_in_memory(query_vector, chunks)

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

        output = getattr(response, "output", None)
        if output:
            text = self._extract_text_from_output_items(output)
            if text:
                return text

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

        output = response.get("output")
        if output:
            text = self._extract_text_from_output_items(output)
            if text:
                return text
        choices = response.get("choices")
        if choices:
            message = choices[0].get("message", {}) if isinstance(choices[0], dict) else {}
            return self._normalize_chat_content(message.get("content"))
        if "content" in response:
            return self._normalize_chat_content(response.get("content"))
        if "text" in response:
            return self._normalize_chat_content(response.get("text"))
        return json.dumps(response)

    def _extract_text_from_output_items(self, output: Any) -> str:
        """从 responses 风格 output 数组中提取文本内容。"""

        if not isinstance(output, list):
            return ""
        parts: list[str] = []
        for item in output:
            if not isinstance(item, dict):
                parts.append(str(item))
                continue
            content_items = item.get("content")
            if isinstance(content_items, list):
                for content_item in content_items:
                    if isinstance(content_item, dict):
                        text = (
                            content_item.get("text")
                            or content_item.get("output_text")
                            or content_item.get("content")
                        )
                        if text:
                            parts.append(str(text))
                    else:
                        parts.append(str(content_item))
            else:
                text = item.get("text") or item.get("output_text")
                if text:
                    parts.append(str(text))
        return "".join(parts)

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

    def _normalize_chat_payload(self, payload: dict[str, Any]) -> dict[str, Any]:
        """把模型 JSON 归一化为不会出现“成功但无 SQL”的响应负载。"""

        matched_tables = self._string_list(payload.get("matchedTables"))
        warnings = self._string_list(payload.get("warnings"))
        summary = payload.get("summary")
        summary = summary.strip() if isinstance(summary, str) else ""
        clarification_question = payload.get("clarificationQuestion")
        clarification_question = clarification_question.strip() if isinstance(clarification_question, str) else ""
        sql = payload.get("sql")
        sql = sql.strip() if isinstance(sql, str) else ""

        if bool(payload.get("needsClarification")):
            return {
                "needsClarification": True,
                "clarificationQuestion": clarification_question or DEFAULT_CLARIFICATION_QUESTION,
                "sql": None,
                "summary": summary or "AI 需要补充信息后才能生成 SQL",
                "matchedTables": matched_tables,
                "warnings": warnings,
            }

        if not sql:
            return {
                "needsClarification": True,
                "clarificationQuestion": DEFAULT_CLARIFICATION_QUESTION,
                "sql": None,
                "summary": summary or "AI 返回内容缺少有效 SQL，未生成 SQL",
                "matchedTables": matched_tables,
                "warnings": warnings + [MISSING_SQL_WARNING],
            }

        return {
            "needsClarification": False,
            "clarificationQuestion": clarification_question or None,
            "sql": sql,
            "summary": summary or "已根据当前上下文生成 SQL 建议",
            "matchedTables": matched_tables,
            "warnings": warnings,
        }

    def _string_list(self, value: Any) -> list[str]:
        """把模型可能返回的列表字段安全转换成字符串列表。"""

        if not isinstance(value, list):
            return []
        return [str(item) for item in value]

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
        relevant_chunks = self._retrieve_relevant_chunks(server_code, db_name, context, question)
        # response_format 要求模型尽量返回 JSON，后续仍会做兼容解析和安全校验。
        request_payload = {
            "model": settings.chat_model,
            "temperature": 0.1,
            "response_format": {"type": "json_object"},
            "messages": [
                {"role": "system", "content": SYSTEM_PROMPT},
                {"role": "user", "content": build_user_prompt(context, question, relevant_chunks)},
            ],
        }
        LOG.info("Chat completion request payload=%s", json.dumps(request_payload, ensure_ascii=False))
        response = await self.client.chat.completions.create(**request_payload)
        LOG.info("Raw chat completion response type=%s value=%r", type(response).__name__, response)
        response_content = self._extract_chat_content(response)
        LOG.info("Chat completion response content=%r", response_content)
        if hasattr(response, "model_dump"):
            try:
                LOG.info("Raw chat completion response model_dump=%s", response.model_dump())
            except Exception as exception:
                LOG.warning("Failed to dump chat completion response: %s", exception)
        payload = self._parse_chat_payload(response)
        # 先归一化模型 JSON 语义，避免出现“成功但没有 SQL”的假阳性。
        normalized_payload = self._normalize_chat_payload(payload)
        parsed = GenerateSqlResponse(
            needsClarification=normalized_payload["needsClarification"],
            clarificationQuestion=normalized_payload["clarificationQuestion"],
            sql=normalized_payload["sql"],
            dialect=context.dialect,
            summary=normalized_payload["summary"],
            matchedTables=normalized_payload["matchedTables"],
            warnings=normalized_payload["warnings"],
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
