from dataclasses import dataclass
import os


@dataclass(frozen=True)
class Settings:
    vanna_port: int = int(os.getenv("VANNA_PORT", "8003"))
    jsw_server_base_url: str = os.getenv("JSW_SERVER_BASE_URL", "http://jsw-server:8002")
    vanna_internal_token: str = os.getenv("VANNA_INTERNAL_TOKEN", "")
    vanna_db_url: str = os.getenv("VANNA_DB_URL", "postgresql://jsw_vanna:change-me@jsw-db:5432/jsw_vanna_db")
    chat_model: str = os.getenv("VANNA_CHAT_MODEL", "gpt-4.1-mini")
    embedding_model: str = os.getenv("VANNA_EMBEDDING_MODEL", "text-embedding-3-small")
    llm_base_url: str = os.getenv("VANNA_LLM_BASE_URL", "https://api.openai.com/v1")
    llm_api_key: str = os.getenv("VANNA_LLM_API_KEY", "")
    context_timeout_seconds: float = float(os.getenv("VANNA_CONTEXT_TIMEOUT_SECONDS", "15"))
    generation_timeout_seconds: float = float(os.getenv("VANNA_GENERATION_TIMEOUT_SECONDS", "60"))


settings = Settings()
