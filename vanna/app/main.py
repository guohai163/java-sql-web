from fastapi import FastAPI, Header, HTTPException

from .db import init_db
from .jsw_client import JswContextAuthError, JswContextRequestError, JswServerClient
from .models import GenerateSqlRequest, GenerateSqlResponse
from .service import VannaService


app = FastAPI(title="JavaSqlWeb Vanna Service", version="0.1.0")
jsw_client = JswServerClient()
vanna_service = VannaService()


@app.on_event("startup")
async def startup() -> None:
    """应用启动时初始化 Vanna 本地缓存和审计表。"""

    init_db()


@app.get("/health")
async def health() -> dict[str, str]:
    """容器和网关使用的轻量健康检查接口。"""

    return {"status": "ok"}


@app.post("/api/vanna/sql/generate", response_model=GenerateSqlResponse)
async def generate_sql(
    body: GenerateSqlRequest,
    user_token: str | None = Header(default=None, alias="User-Token"),
    authorization: str | None = Header(default=None, alias="Authorization"),
) -> GenerateSqlResponse:
    """生成只读 SQL 的 HTTP 入口。"""

    if not user_token and not authorization:
        raise HTTPException(status_code=401, detail="missing credentials")
    headers: dict[str, str] = {}
    # 保留调用方的认证头，让 JavaSqlWeb 按当前用户权限返回可见上下文。
    if user_token:
        headers["User-Token"] = user_token
    if authorization:
        headers["Authorization"] = authorization
    server_code = str(body.serverCode).strip()
    try:
        # 先从 JavaSqlWeb 获取受权限控制的数据库上下文，再交给 Vanna 生成 SQL。
        context = await jsw_client.get_context(server_code, body.dbName, headers)
    except JswContextAuthError as exc:
        raise HTTPException(status_code=401, detail=str(exc)) from exc
    except JswContextRequestError as exc:
        raise HTTPException(status_code=502, detail=str(exc)) from exc
    return await vanna_service.generate_sql(server_code, body.dbName, body.question, context)
