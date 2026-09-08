import sys
import types
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

sys.modules.setdefault("psycopg", types.SimpleNamespace(Connection=object, connect=lambda *args, **kwargs: None))

from app import db as db_module  # noqa: E402


def test_init_db_adds_embedding_compat_columns():
    executed: list[str] = []

    class FakeCursor:
        def execute(self, sql, params=None):
            executed.append(" ".join(sql.split()))

        def __enter__(self):
            return self

        def __exit__(self, exc_type, exc, tb):
            return False

    class FakeConn:
        def cursor(self):
            return FakeCursor()

        def commit(self):
            executed.append("COMMIT")

        def __enter__(self):
            return self

        def __exit__(self, exc_type, exc, tb):
            return False

    original_get_conn = db_module.get_conn
    try:
        db_module.get_conn = lambda: FakeConn()
        db_module.init_db()
    finally:
        db_module.get_conn = original_get_conn

    assert any("ALTER TABLE vanna_context_cache ADD COLUMN IF NOT EXISTS embedding_model_key" in sql for sql in executed)
    assert any("ALTER TABLE vanna_context_embedding ADD COLUMN IF NOT EXISTS embedding_values real[] NULL" in sql for sql in executed)
    assert any("ALTER TABLE vanna_context_embedding ADD COLUMN IF NOT EXISTS embedding_dims integer NOT NULL DEFAULT 0" in sql for sql in executed)
