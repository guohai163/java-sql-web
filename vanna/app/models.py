from typing import List, Optional
from pydantic import BaseModel, Field, field_validator


class GenerateSqlRequest(BaseModel):
    """前端/JavaSqlWeb 请求 Vanna 生成 SQL 的入参。"""

    serverCode: str | int = Field(...)
    dbName: str = Field(..., min_length=1)
    question: str = Field(..., min_length=1, max_length=4000)

    @field_validator("serverCode")
    @classmethod
    def validate_server_code(cls, value: str | int) -> str:
        """统一将 serverCode 归一化为非空字符串，兼容数字型服务编码。"""

        normalized = str(value).strip()
        if not normalized:
            raise ValueError("serverCode cannot be blank")
        return normalized


class GenerateSqlResponse(BaseModel):
    """Vanna 返回给调用方的 SQL 生成结果。"""

    # True 表示当前问题缺少必要条件，应由前端继续追问用户。
    needsClarification: bool
    clarificationQuestion: Optional[str] = None
    # 只允许返回 SELECT/WITH 只读 SQL；被安全策略拒绝时为空。
    sql: Optional[str] = None
    dialect: str
    summary: str
    # 模型认为本次 SQL 关联到的表，用于前端展示和审计。
    matchedTables: List[str]
    # 生成过程中的风险或假设提示。
    warnings: List[str]


class VannaTable(BaseModel):
    """数据库表元数据。"""

    tableName: str
    tableRows: Optional[int] = None
    tableComment: Optional[str] = ""


class VannaColumn(BaseModel):
    """数据库字段元数据。"""

    tableName: str
    columnName: str
    columnType: Optional[str] = ""
    columnLength: Optional[str] = ""
    columnComment: Optional[str] = ""
    columnIsNull: Optional[str] = ""


class VannaView(BaseModel):
    """数据库视图元数据。"""

    viewName: str
    viewData: Optional[str] = None
    viewComment: Optional[str] = ""


class VannaHistoryExample(BaseModel):
    """历史只读查询样例，用作 SQL 风格和表关联关系参考。"""

    queryLogCode: Optional[int] = None
    queryName: Optional[str] = ""
    queryDatabase: Optional[str] = ""
    sqlTemplate: str
    targetTables: Optional[str] = ""
    queryTime: Optional[str] = ""


class VannaContext(BaseModel):
    """JavaSqlWeb 提供给 Vanna 的完整生成上下文。"""

    serverType: str
    # SQL 方言，例如 mysql、postgresql、clickhouse 等。
    dialect: str
    serverName: str
    dbName: str
    # 上下文版本变化时会触发本地缓存刷新。
    contextVersion: str
    tables: List[VannaTable]
    columns: List[VannaColumn]
    views: List[VannaView] = []
    historyExamples: List[VannaHistoryExample] = []
