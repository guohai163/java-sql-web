import logging
import asyncio
from datetime import datetime, date

from fastapi import FastAPI, Header, HTTPException

from .config import settings
from .db import init_db
from .jsw_client import JswContextAuthError, JswContextRequestError, JswServerClient
from .models import GenerateSqlRequest, GenerateSqlResponse
from .service import VannaService

logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s %(levelname)s [%(name)s] %(message)s",
    force=True,
)
LOG = logging.getLogger(__name__)

app = FastAPI(title="JavaSqlWeb Vanna Service", version="0.1.0")
jsw_client = JswServerClient()
vanna_service = VannaService()
last_nightly_run_date: date | None = None


async def _startup_warmup_task() -> None:
    """启动后后台执行一次全量预热。"""

    await asyncio.sleep(1)
    try:
        await vanna_service.warmup_all_contexts(jsw_client)
    except Exception as exception:
        LOG.exception("Startup warmup failed: %s", exception)


async def _nightly_scheduler_task() -> None:
    """常驻夜间调度器，每分钟检查一次是否需要触发增量重跑。"""

    global last_nightly_run_date
    while True:
        await asyncio.sleep(60)
        now = datetime.now()
        if last_nightly_run_date == now.date():
            continue
        try:
            triggered = await vanna_service.run_nightly_warmup_if_due(jsw_client, now)
            if triggered:
                last_nightly_run_date = now.date()
                LOG.info("Nightly warmup finished for date=%s", last_nightly_run_date)
        except Exception as exception:
            LOG.exception("Nightly warmup failed: %s", exception)


@app.on_event("startup")
async def startup() -> None:
    """应用启动时初始化 Vanna 本地缓存和审计表。"""

    LOG.info(
        "Starting JavaSqlWeb Vanna version=%s chat_model=%s embedding_model=%s llm_base_url=%s",
        settings.version,
        settings.chat_model,
        settings.embedding_model,
        settings.llm_base_url,
    )
    init_db()
    LOG.info("Vanna startup completed version=%s", settings.version)
    if settings.warmup_enabled and settings.warmup_on_startup:
        asyncio.create_task(_startup_warmup_task())
    if settings.warmup_enabled:
        asyncio.create_task(_nightly_scheduler_task())


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
