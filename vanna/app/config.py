from dataclasses import dataclass
import os


@dataclass(frozen=True)
class Settings:
    """Vanna 服务运行配置，统一从环境变量读取并提供默认值。"""

    # 当前服务版本号，用于启动日志和镜像排障。
    version: str = os.getenv("PROJECT_VERSION", "dev")
    # FastAPI 服务监听端口。
    vanna_port: int = int(os.getenv("VANNA_PORT", "8003"))
    # JavaSqlWeb 主服务地址，用于拉取数据库结构与历史查询上下文。
    jsw_server_base_url: str = os.getenv("JSW_SERVER_BASE_URL", "http://jsw-server:8002")
    # 服务间调用的内部令牌，会随上下文请求一起透传给 JavaSqlWeb。
    vanna_internal_token: str = os.getenv("VANNA_INTERNAL_TOKEN", "")
    # Vanna 自身的 PostgreSQL 连接串，用于缓存上下文和记录审计日志。
    vanna_db_url: str = os.getenv("VANNA_DB_URL", "postgresql://jsw_vanna:change-me@jsw-db:5432/jsw_vanna_db")
    # 生成 SQL 使用的聊天模型名称。
    chat_model: str = os.getenv("VANNA_CHAT_MODEL", "gpt-5.4-mini")
    # 本地向量召回模型，用于从 schema/history 中找出与问题最相关的片段。
    embedding_model: str = os.getenv("VANNA_EMBEDDING_MODEL", "BAAI/bge-small-zh-v1.5")
    # bge 系列中文检索模型推荐的查询前缀。
    embedding_query_prefix: str = os.getenv("VANNA_EMBEDDING_QUERY_PREFIX", "为这个句子生成表示以用于检索相关文章：")
    # 送入大模型前保留的上下文片段数量。
    embedding_top_k: int = int(os.getenv("VANNA_EMBEDDING_TOP_K", "12"))
    # OpenAI 兼容接口地址，支持接入代理或私有模型网关。
    llm_base_url: str = os.getenv("VANNA_LLM_BASE_URL", "https://api.openai.com/v1")
    # OpenAI 兼容接口密钥。
    llm_api_key: str = os.getenv("VANNA_LLM_API_KEY", "")
    # 拉取 JavaSqlWeb 上下文的超时时间。
    context_timeout_seconds: float = float(os.getenv("VANNA_CONTEXT_TIMEOUT_SECONDS", "15"))
    # 调用大模型生成 SQL 的超时时间。
    generation_timeout_seconds: float = float(os.getenv("VANNA_GENERATION_TIMEOUT_SECONDS", "60"))


settings = Settings()
