---
name: java-sql-web-query
description: Use this skill when OpenClaw needs to query data, inspect databases, list tables, or explain read-only SQL results through this JavaSqlWeb project's backend API with Bearer authentication and permission-aware guardrails.
---

# JavaSqlWeb Query

Use this skill when a user wants to query data through JavaSqlWeb by API instead of clicking through the UI.

This skill is API-first and read-only by default. It assumes the caller already has a valid access token and must stay within the user's assigned database-server permissions.

## Runtime Configuration

This skill expects these runtime variables to be provided by the caller environment:

- `JSW_BASE_URL`: JavaSqlWeb backend base URL, for example `https://jsw.example.com`
- `JSW_ACCESS_TOKEN`: long-lived access token in `jsw_xxx` format

If either value is missing, stop and tell the user how to configure it before attempting any query.

For a concrete OpenClaw configuration example, see [references/openclaw-config.md](references/openclaw-config.md).

## Workflow

1. Read [references/query-guardrails.md](references/query-guardrails.md) before forming any SQL.
2. Read [references/api-query.md](references/api-query.md) to choose the right endpoints and request order.
3. Confirm `JSW_BASE_URL` and `JSW_ACCESS_TOKEN` are available before making any API request.
4. Start from `/database/serverlist` and only continue with a server that appears in that response.
5. Narrow down to the target database and table before executing SQL.
6. Use only read-only SQL. If the request implies a write or schema change, refuse and explain why.
7. When returning results, say which server, database, and SQL were used, and whether the result may have been truncated by the service limit.

## Rules

- Prefer backend API calls, not browser UI automation.
- Do not guess `serverCode`, `dbName`, or table names.
- Do not query servers or databases that are not present in the user's accessible list.
- Build every request from `JSW_BASE_URL`.
- Treat `Authorization: Bearer ${JSW_ACCESS_TOKEN}` as the primary authentication method for `/database/**`.
- If the token is expired or invalid, stop and tell the user to renew or reset it from the JavaSqlWeb frontend.
- If the API reports permission denial for a server, report it plainly and do not try to bypass it.

## Output Expectations

- State the selected server and database explicitly.
- Summarize the SQL in plain Chinese when helpful.
- Note if the result is empty, truncated, or blocked by permission/token issues.
- Keep result explanations in Chinese unless the user asks otherwise.
