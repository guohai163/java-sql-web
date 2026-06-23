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
    init_db()


@app.get("/health")
async def health() -> dict[str, str]:
    return {"status": "ok"}


@app.post("/api/vanna/sql/generate", response_model=GenerateSqlResponse)
async def generate_sql(
    body: GenerateSqlRequest,
    user_token: str | None = Header(default=None, alias="User-Token"),
    authorization: str | None = Header(default=None, alias="Authorization"),
) -> GenerateSqlResponse:
    if not user_token and not authorization:
        raise HTTPException(status_code=401, detail="missing credentials")
    headers: dict[str, str] = {}
    if user_token:
        headers["User-Token"] = user_token
    if authorization:
        headers["Authorization"] = authorization
    server_code = str(body.serverCode).strip()
    try:
        context = await jsw_client.get_context(server_code, body.dbName, headers)
    except JswContextAuthError as exc:
        raise HTTPException(status_code=401, detail=str(exc)) from exc
    except JswContextRequestError as exc:
        raise HTTPException(status_code=502, detail=str(exc)) from exc
    return await vanna_service.generate_sql(server_code, body.dbName, body.question, context)
