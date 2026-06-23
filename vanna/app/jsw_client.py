import httpx

from .config import settings
from .models import VannaContext


class JswContextAuthError(Exception):
    """JavaSqlWeb 返回认证失败时抛出的上下文拉取异常。"""

    pass


class JswContextRequestError(Exception):
    """JavaSqlWeb 上下文接口请求失败或业务失败时抛出的异常。"""

    pass


class JswServerClient:
    """封装对 JavaSqlWeb 内部 Vanna 上下文接口的调用。"""

    async def get_context(self, server_code: str, db_name: str, headers: dict[str, str]) -> VannaContext:
        """拉取指定数据源/数据库的 schema、字段和历史查询上下文。"""

        request_headers = {
            "X-Vanna-Internal-Token": settings.vanna_internal_token,
        }
        # 调用方可能使用 User-Token 或标准 Authorization，服务端需要原样校验用户身份。
        if "User-Token" in headers:
            request_headers["User-Token"] = headers["User-Token"]
        if "Authorization" in headers:
            request_headers["Authorization"] = headers["Authorization"]

        async with httpx.AsyncClient(timeout=settings.context_timeout_seconds) as client:
            response = await client.get(
                f"{settings.jsw_server_base_url}/internal/vanna/context/{server_code}/{db_name}",
                headers=request_headers,
            )
            payload = response.json()
            # 认证错误要映射为 401，让前端保持原有登录态处理逻辑。
            if response.status_code == 401:
                raise JswContextAuthError(payload.get("message") or "not logged in")
            if response.status_code >= 400:
                raise JswContextRequestError(payload.get("message") or "failed to load Vanna context")
            if not payload.get("status"):
                raise JswContextRequestError(payload.get("message") or "failed to load Vanna context")
            # 所有上下文字段在模型层统一校验，避免脏数据进入 prompt。
            return VannaContext.model_validate(payload["data"])
