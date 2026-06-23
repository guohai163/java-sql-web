import httpx

from .config import settings
from .models import VannaContext


class JswContextAuthError(Exception):
    pass


class JswContextRequestError(Exception):
    pass


class JswServerClient:
    async def get_context(self, server_code: str, db_name: str, headers: dict[str, str]) -> VannaContext:
        request_headers = {
            "X-Vanna-Internal-Token": settings.vanna_internal_token,
        }
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
            if response.status_code == 401:
                raise JswContextAuthError(payload.get("message") or "not logged in")
            if response.status_code >= 400:
                raise JswContextRequestError(payload.get("message") or "failed to load Vanna context")
            if not payload.get("status"):
                raise JswContextRequestError(payload.get("message") or "failed to load Vanna context")
            return VannaContext.model_validate(payload["data"])
