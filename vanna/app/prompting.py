from .models import VannaContext


def build_schema_text(context: VannaContext) -> str:
    lines: list[str] = [
        f"Server: {context.serverName}",
        f"Database: {context.dbName}",
        f"Dialect: {context.dialect}",
        "",
        "Tables:",
    ]
    for table in context.tables:
        comment = (table.tableComment or "").strip()
        lines.append(f"- {table.tableName}: {comment or 'no table comment'}")
        for column in [col for col in context.columns if col.tableName == table.tableName]:
            column_comment = (column.columnComment or "").strip()
            lines.append(
                f"  - {column.columnName} [{column.columnType or ''} {column.columnLength or ''}] "
                f"{column.columnIsNull or ''} comment={column_comment or 'none'}"
            )
    if context.historyExamples:
        lines.extend(["", "Historical read-only query patterns:"])
        for example in context.historyExamples:
            lines.append(f"- targets={example.targetTables or '-'} sql={example.sqlTemplate}")
    return "\n".join(lines)


SYSTEM_PROMPT = """You generate read-only SQL for business users.
Rules:
- Return only SELECT-style read-only SQL.
- Never generate INSERT, UPDATE, DELETE, ALTER, DROP, TRUNCATE, CREATE, MERGE, CALL, EXEC, GRANT, REVOKE.
- If the question is ambiguous or misses a key filter/table choice, ask one concise clarification question instead of guessing.
- Use only the schema and history patterns provided.
- Prefer explicit column lists when practical, but SELECT * is acceptable for first-pass troubleshooting queries.
- Do not execute SQL.
"""


def build_user_prompt(context: VannaContext, question: str) -> str:
    return (
        f"{build_schema_text(context)}\n\n"
        f"User question:\n{question}\n\n"
        "Respond in JSON with fields: "
        "needsClarification(boolean), clarificationQuestion(string|null), sql(string|null), "
        "summary(string), matchedTables(string array), warnings(string array)."
    )
