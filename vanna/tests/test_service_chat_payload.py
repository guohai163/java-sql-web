import json
import asyncio
import sys
import types
from pathlib import Path
from types import SimpleNamespace
from unittest.mock import patch

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))


class _AsyncOpenAI:
    def __init__(self, *args, **kwargs):
        pass


class _SentenceTransformer:
    def __init__(self, *args, **kwargs):
        pass


sys.modules.setdefault("openai", types.SimpleNamespace(AsyncOpenAI=_AsyncOpenAI))
sys.modules.setdefault(
    "sentence_transformers",
    types.SimpleNamespace(SentenceTransformer=_SentenceTransformer),
)
sys.modules.setdefault("psycopg", types.SimpleNamespace(Connection=object, connect=lambda *args, **kwargs: None))

try:
    import pydantic  # noqa: F401
except ModuleNotFoundError:
    class _BaseModel:
        def __init__(self, **kwargs):
            for key, value in kwargs.items():
                setattr(self, key, value)

        @classmethod
        def model_validate(cls, value):
            return value

    def _field(*args, **kwargs):
        return None

    def _field_validator(*args, **kwargs):
        def decorator(func):
            return func

        return decorator

    sys.modules["pydantic"] = types.SimpleNamespace(
        BaseModel=_BaseModel,
        Field=_field,
        field_validator=_field_validator,
    )

from app.service import VannaService  # noqa: E402
from app.models import DatabaseNameBean, VannaContext, VannaServerWarmupItem, VannaTable, VannaColumn  # noqa: E402


def _service() -> VannaService:
    return VannaService.__new__(VannaService)


def _context() -> VannaContext:
    return VannaContext(
        serverType="postgresql",
        dialect="pgsql",
        serverName="demo",
        dbName="demo",
        contextVersion="1",
        tables=[VannaTable(tableName="users")],
        columns=[VannaColumn(tableName="users", columnName="id", columnType="bigint")],
    )


def _response(content: str):
    return SimpleNamespace(
        choices=[
            SimpleNamespace(
                message=SimpleNamespace(content=content)
            )
        ]
    )


def _run_generate_sql(content: str):
    async def run():
        async def create(*args, **kwargs):
            return _response(content)

        service = _service()
        service.ensure_context = lambda *args, **kwargs: None
        service._retrieve_relevant_chunks = lambda *args, **kwargs: ["users: user table"]
        service._save_audit = lambda *args, **kwargs: None
        service.client = SimpleNamespace(
            chat=SimpleNamespace(
                completions=SimpleNamespace(
                    create=create
                )
            )
        )
        return await service.generate_sql("1", "demo", "list users", _context())

    return asyncio.run(run())


def test_parse_chat_payload_accepts_openai_sdk_response_shape():
    response = SimpleNamespace(
        choices=[
            SimpleNamespace(
                message=SimpleNamespace(
                    content=json.dumps(
                        {
                            "needsClarification": False,
                            "sql": "select id from users",
                            "summary": "List users",
                            "matchedTables": ["users"],
                            "warnings": [],
                        }
                    )
                )
            )
        ]
    )

    payload = _service()._parse_chat_payload(response)

    assert payload["sql"] == "select id from users"
    assert payload["matchedTables"] == ["users"]


def test_parse_chat_payload_accepts_raw_json_string_response():
    payload = _service()._parse_chat_payload(
        json.dumps(
            {
                "needsClarification": True,
                "clarificationQuestion": "Which date range?",
                "sql": None,
                "summary": "Need more context",
                "matchedTables": [],
                "warnings": ["missing date range"],
            }
        )
    )

    assert payload["needsClarification"] is True
    assert payload["clarificationQuestion"] == "Which date range?"


def test_parse_chat_payload_accepts_serialized_chat_completion_response():
    response = json.dumps(
        {
            "choices": [
                {
                    "message": {
                        "content": json.dumps(
                            {
                                "needsClarification": False,
                                "sql": "select count(*) from orders",
                                "summary": "Count orders",
                                "matchedTables": ["orders"],
                                "warnings": [],
                            }
                        )
                    }
                }
            ]
        }
    )

    payload = _service()._parse_chat_payload(response)

    assert payload["sql"] == "select count(*) from orders"


def test_parse_chat_payload_accepts_json_fenced_content():
    response = "```json\n{\"needsClarification\": false, \"sql\": \"select 1\"}\n```"

    payload = _service()._parse_chat_payload(response)

    assert payload["sql"] == "select 1"


def test_parse_chat_payload_extracts_json_object_from_text():
    response = (
        "Here is the result:\n"
        "{\"needsClarification\": false, \"sql\": \"select name from users\", "
        "\"matchedTables\": [\"users\"], \"warnings\": []}"
    )

    payload = _service()._parse_chat_payload(response)

    assert payload["sql"] == "select name from users"
    assert payload["matchedTables"] == ["users"]


def test_parse_chat_payload_accepts_plain_read_only_sql():
    payload = _service()._parse_chat_payload("select id, name from users order by id desc")

    assert payload["needsClarification"] is False
    assert payload["sql"] == "select id, name from users order by id desc"
    assert "模型未按 JSON 格式返回" in payload["warnings"][0]


def test_parse_chat_payload_accepts_sql_fenced_content():
    response = "```sql\nwith recent_users as (select id from users) select * from recent_users\n```"

    payload = _service()._parse_chat_payload(response)

    assert payload["sql"].startswith("with recent_users")


def test_parse_chat_payload_extracts_sql_fence_from_text():
    response = "Sure, here is the SQL:\n```sql\nselect id from users\n```"

    payload = _service()._parse_chat_payload(response)

    assert payload["sql"] == "select id from users"


def test_parse_chat_payload_returns_clarification_for_unparseable_text():
    payload = _service()._parse_chat_payload("I cannot answer that yet.")

    assert payload["needsClarification"] is True
    assert payload["sql"] is None
    assert payload["warnings"] == ["模型返回内容不是有效 JSON，且未提取到只读 SQL"]


def test_generate_sql_converts_empty_payload_to_clarification():
    response = _run_generate_sql("{}")

    assert response.needsClarification is True
    assert response.sql is None
    assert response.summary == "AI 返回内容缺少有效 SQL，未生成 SQL"
    assert response.warnings == ["模型返回 JSON 中缺少有效 SQL"]


def test_generate_sql_converts_null_sql_payload_to_clarification():
    response = _run_generate_sql(
        json.dumps(
            {
                "needsClarification": False,
                "sql": None,
                "summary": "已根据当前上下文生成 SQL 建议",
                "matchedTables": [],
                "warnings": [],
            }
        )
    )

    assert response.needsClarification is True
    assert response.sql is None
    assert response.warnings == ["模型返回 JSON 中缺少有效 SQL"]


def test_generate_sql_converts_blank_sql_payload_to_clarification():
    response = _run_generate_sql(json.dumps({"sql": "   "}))

    assert response.needsClarification is True
    assert response.sql is None
    assert response.warnings == ["模型返回 JSON 中缺少有效 SQL"]


def test_generate_sql_preserves_clarification_and_fills_missing_question():
    response = _run_generate_sql(json.dumps({"needsClarification": True, "sql": None}))

    assert response.needsClarification is True
    assert response.sql is None
    assert response.clarificationQuestion == "AI 未能生成有效 SQL，请补充查询目标或换一种问法。"


def test_generate_sql_preserves_valid_read_only_sql():
    response = _run_generate_sql(json.dumps({"sql": " select id from users "}))

    assert response.needsClarification is False
    assert response.sql == "select id from users"
    assert response.warnings == []


def test_generate_sql_rejects_non_read_only_sql():
    response = _run_generate_sql(json.dumps({"sql": "delete from users"}))

    assert response.needsClarification is True
    assert response.sql is None
    assert "只允许生成只读 SQL" in response.warnings


def test_extract_chat_content_accepts_output_shape():
    service = _service()
    content = service._extract_chat_content(
        {
            "output": [
                {
                    "content": [
                        {
                            "text": "{\"sql\": \"select 1\"}"
                        }
                    ]
                }
            ]
        }
    )

    assert content == "{\"sql\": \"select 1\"}"


def test_generate_sql_returns_warmup_hint_when_cached_embeddings_missing():
    async def run():
        service = _service()
        service.ensure_context = lambda *args, **kwargs: None
        service._retrieve_relevant_chunks = lambda *args, **kwargs: []
        service._save_audit = lambda *args, **kwargs: None
        return await service.generate_sql("1", "demo", "list users", _context())

    response = asyncio.run(run())

    assert response.needsClarification is True
    assert response.sql is None
    assert response.clarificationQuestion == "系统正在预热该库，请稍后重试。"


def test_generate_sql_runs_embedding_work_off_the_event_loop():
    async def run():
        service = _service()
        worker_functions = []

        def ensure_context(*args, **kwargs):
            return None

        def retrieve_relevant_chunks(*args, **kwargs):
            return ["users: user table"]

        async def create(*args, **kwargs):
            return _response(json.dumps({"sql": "select id from users"}))

        async def immediate_to_thread(function, *args, **kwargs):
            worker_functions.append(function)
            return function(*args, **kwargs)

        service.ensure_context = ensure_context
        service._retrieve_relevant_chunks = retrieve_relevant_chunks
        service._save_audit = lambda *args, **kwargs: None
        service.client = SimpleNamespace(
            chat=SimpleNamespace(
                completions=SimpleNamespace(
                    create=create,
                )
            )
        )

        import app.service as service_module

        with patch.object(service_module.asyncio, "to_thread", side_effect=immediate_to_thread):
            response = await service.generate_sql("1", "demo", "list users", _context())

        assert response.sql == "select id from users"
        assert worker_functions == [ensure_context, retrieve_relevant_chunks]

    asyncio.run(run())


def test_run_nightly_warmup_if_due_only_triggers_at_target_hour():
    async def run():
        service = _service()
        called = {"value": False}

        async def warmup_all_contexts(_):
            called["value"] = True

        service.warmup_all_contexts = warmup_all_contexts

        from datetime import datetime

        triggered = await service.run_nightly_warmup_if_due(SimpleNamespace(), datetime(2026, 6, 25, 1, 0, 0))
        assert triggered is True
        assert called["value"] is True

        called["value"] = False
        triggered = await service.run_nightly_warmup_if_due(SimpleNamespace(), datetime(2026, 6, 25, 2, 0, 0))
        assert triggered is False
        assert called["value"] is False

    asyncio.run(run())


def test_warmup_all_contexts_walks_servers_and_databases():
    async def run():
        service = _service()
        warmed = []

        async def warmup_single_database(_, server, database):
            warmed.append((server.serverCode, database.dbName))

        service._warmup_single_database = warmup_single_database

        jsw_client = SimpleNamespace(
            get_warmup_servers=lambda: asyncio.sleep(0, result=[VannaServerWarmupItem(serverCode=1, serverName="s1", serverType="postgresql")]),
            get_warmup_databases=lambda server_code: asyncio.sleep(0, result=[DatabaseNameBean(dbName="db1"), DatabaseNameBean(dbName="db2")]),
        )

        await service.warmup_all_contexts(jsw_client)
        assert warmed == [(1, "db1"), (1, "db2")]

    asyncio.run(run())


def test_retrieve_relevant_chunks_returns_empty_when_cache_missing():
    service = _service()
    from app import service as service_module

    original_loader = service_module.load_chunk_embeddings
    try:
        service_module.load_chunk_embeddings = lambda cache_key: []
        assert service._retrieve_relevant_chunks("1::demo", "find users") == []
    finally:
        service_module.load_chunk_embeddings = original_loader
