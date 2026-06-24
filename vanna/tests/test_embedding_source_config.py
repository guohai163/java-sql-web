import sys
import types
from pathlib import Path
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

from app import service as service_module  # noqa: E402
from app.config import settings  # noqa: E402


def test_init_uses_huggingface_name_by_default():
    original_source = settings.embedding_model_source
    original_model = settings.embedding_model
    original_revision = settings.embedding_model_revision
    original_cache_dir = settings.embedding_model_cache_dir
    try:
        object.__setattr__(settings, "embedding_model_source", "huggingface")
        object.__setattr__(settings, "embedding_model", "BAAI/bge-small-zh-v1.5")
        object.__setattr__(settings, "embedding_model_revision", "")
        object.__setattr__(settings, "embedding_model_cache_dir", "")

        with patch.object(service_module, "SentenceTransformer") as sentence_transformer:
            service_module.VannaService()

        sentence_transformer.assert_called_once_with("BAAI/bge-small-zh-v1.5", device="cpu")
    finally:
        object.__setattr__(settings, "embedding_model_source", original_source)
        object.__setattr__(settings, "embedding_model", original_model)
        object.__setattr__(settings, "embedding_model_revision", original_revision)
        object.__setattr__(settings, "embedding_model_cache_dir", original_cache_dir)


def test_init_uses_modelscope_download_when_configured():
    original_source = settings.embedding_model_source
    try:
        object.__setattr__(settings, "embedding_model_source", "modelscope")
        with patch.object(service_module.VannaService, "_download_model_from_modelscope", return_value="/tmp/modelscope-cache/model"), patch.object(
            service_module, "SentenceTransformer"
        ) as sentence_transformer:
            service_module.VannaService()

        sentence_transformer.assert_called_once_with("/tmp/modelscope-cache/model", device="cpu")
    finally:
        object.__setattr__(settings, "embedding_model_source", original_source)
