import json
import sys
import types
from pathlib import Path
from types import SimpleNamespace

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


def _service() -> VannaService:
    return VannaService.__new__(VannaService)


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
