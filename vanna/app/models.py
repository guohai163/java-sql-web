from typing import List, Optional
from pydantic import BaseModel, Field, field_validator


class GenerateSqlRequest(BaseModel):
    serverCode: str | int = Field(...)
    dbName: str = Field(..., min_length=1)
    question: str = Field(..., min_length=1, max_length=4000)

    @field_validator("serverCode")
    @classmethod
    def validate_server_code(cls, value: str | int) -> str:
        normalized = str(value).strip()
        if not normalized:
            raise ValueError("serverCode cannot be blank")
        return normalized


class GenerateSqlResponse(BaseModel):
    needsClarification: bool
    clarificationQuestion: Optional[str] = None
    sql: Optional[str] = None
    dialect: str
    summary: str
    matchedTables: List[str]
    warnings: List[str]


class VannaTable(BaseModel):
    tableName: str
    tableRows: Optional[int] = None
    tableComment: Optional[str] = ""


class VannaColumn(BaseModel):
    tableName: str
    columnName: str
    columnType: Optional[str] = ""
    columnLength: Optional[str] = ""
    columnComment: Optional[str] = ""
    columnIsNull: Optional[str] = ""


class VannaView(BaseModel):
    viewName: str
    viewData: Optional[str] = None
    viewComment: Optional[str] = ""


class VannaHistoryExample(BaseModel):
    queryLogCode: Optional[int] = None
    queryName: Optional[str] = ""
    queryDatabase: Optional[str] = ""
    sqlTemplate: str
    targetTables: Optional[str] = ""
    queryTime: Optional[str] = ""


class VannaContext(BaseModel):
    serverType: str
    dialect: str
    serverName: str
    dbName: str
    contextVersion: str
    tables: List[VannaTable]
    columns: List[VannaColumn]
    views: List[VannaView] = []
    historyExamples: List[VannaHistoryExample] = []
