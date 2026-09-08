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
from app.models import VannaColumn, VannaContext, VannaHistoryExample, VannaTable  # noqa: E402


def _service() -> VannaService:
    service = VannaService.__new__(VannaService)
    service.embedder = SimpleNamespace()
    return service


def _context() -> VannaContext:
    return VannaContext(
        serverType="postgresql",
        dialect="pgsql",
        serverName="demo",
        dbName="demo",
        contextVersion="ctx-v1",
        tables=[VannaTable(tableName="users", tableComment="用户表")],
        columns=[
            VannaColumn(tableName="users", columnName="id", columnType="bigint"),
            VannaColumn(tableName="users", columnName="name", columnType="varchar"),
        ],
        historyExamples=[
            VannaHistoryExample(sqlTemplate="select id, name from users order by id desc")
        ],
    )


def test_ensure_context_skips_rebuild_when_cache_is_complete():
    service = _service()
    context = _context()
    persist_calls: list[tuple[str, str]] = []
    service._load_cache_state = lambda cache_key: (context.contextVersion, service._embedding_model_key())
    service._has_complete_embeddings = lambda cache_key, expected_count: True
    service._persist_context = lambda cache_key, ctx: persist_calls.append((cache_key, ctx.contextVersion))

    service.ensure_context("1", "demo", context)

    assert persist_calls == []


def test_ensure_context_rebuilds_when_embedding_model_key_changes():
    service = _service()
    context = _context()
    persist_calls: list[tuple[str, str]] = []
    service._load_cache_state = lambda cache_key: (context.contextVersion, "old-model-key")
    service._has_complete_embeddings = lambda cache_key, expected_count: True
    service._persist_context = lambda cache_key, ctx: persist_calls.append((cache_key, ctx.contextVersion))

    service.ensure_context("1", "demo", context)

    assert persist_calls == [("1::demo", "ctx-v1")]


def test_persist_context_saves_embedding_values_and_dims():
    service = _service()
    context = _context()
    executed: list[tuple[str, tuple]] = []

    class FakeCursor:
        def execute(self, sql, params=None):
            executed.append((" ".join(sql.split()), params))

        def __enter__(self):
            return self

        def __exit__(self, exc_type, exc, tb):
            return False

    class FakeConn:
        def cursor(self):
            return FakeCursor()

        def commit(self):
            executed.append(("COMMIT", ()))

        def __enter__(self):
            return self

        def __exit__(self, exc_type, exc, tb):
            return False

    def fake_get_conn():
        return FakeConn()

    service._encode_texts = lambda texts: [[0.1, 0.2], [0.3, 0.4], [0.5, 0.6], [0.7, 0.8]]

    import app.service as service_module

    original_get_conn = service_module.get_conn
    try:
        service_module.get_conn = fake_get_conn
        service._persist_context("1::demo", context)
    finally:
        service_module.get_conn = original_get_conn

    insert_rows = [
        params for sql, params in executed if "INSERT INTO vanna_context_embedding" in sql
    ]
    assert len(insert_rows) == 4
    assert insert_rows[0][4] == [0.1, 0.2]
    assert insert_rows[0][5] == 2


def test_retrieve_relevant_chunks_uses_persisted_vectors_without_reencoding_chunks():
    service = _service()
    context = _context()
    calls: list[list[str]] = []

    def fake_encode_texts(texts):
        calls.append(list(texts))
        return [[1.0, 0.0]]

    service._encode_texts = fake_encode_texts
    service._load_persisted_chunk_vectors = lambda cache_key: [
        ("users.id bigint", [0.9, 0.0], 2),
        ("users.name varchar", [0.1, 0.0], 2),
    ]
    service._persist_context = lambda *args, **kwargs: None
    service._rank_chunks_in_memory = lambda *args, **kwargs: ["unexpected fallback"]

    result = service._retrieve_relevant_chunks("1", "demo", context, "查用户")

    assert result == ["users.id bigint", "users.name varchar"]
    assert calls == [["为这个句子生成表示以用于检索相关文章：查用户"]]


def test_retrieve_relevant_chunks_rebuilds_and_falls_back_when_persisted_vectors_missing():
    service = _service()
    context = _context()
    calls: list[list[str]] = []
    persist_calls: list[str] = []

    def fake_encode_texts(texts):
        calls.append(list(texts))
        if len(texts) == 1:
            return [[1.0, 0.0]]
        return [[0.0, 1.0] for _ in texts]

    persisted_calls = {"count": 0}

    def fake_load(cache_key):
        persisted_calls["count"] += 1
        return []

    service._encode_texts = fake_encode_texts
    service._load_persisted_chunk_vectors = fake_load
    service._persist_context = lambda cache_key, ctx: persist_calls.append(cache_key)
    service._rank_chunks_in_memory = lambda query_vector, chunks: chunks[:2]

    result = service._retrieve_relevant_chunks("1", "demo", context, "查用户")

    assert result == [
        "users: 用户表",
        "users.id bigint ",
    ]
    assert persist_calls == ["1::demo"]
    assert persisted_calls["count"] == 2
    assert calls == [["为这个句子生成表示以用于检索相关文章：查用户"]]


def test_rank_chunks_from_vectors_skips_invalid_and_mismatched_dims():
    service = _service()

    result = service._rank_chunks_from_vectors(
        [1.0, 0.0],
        [
            ("bad-empty", [], 0),
            ("bad-mismatch", [1.0], 1),
            ("good", [0.5, 0.0], 2),
            ("bad-inconsistent", [0.5], 2),
        ],
    )

    assert result == ["good"]
