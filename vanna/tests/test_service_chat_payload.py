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
