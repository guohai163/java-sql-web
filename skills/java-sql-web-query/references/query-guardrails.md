# Query Guardrails

This skill is strictly read-only.

## Allowed SQL

Use only read-only statements such as:

- `SELECT`
- `SHOW`
- `DESCRIBE`
- `EXPLAIN`
- Metadata inspection queries that do not modify data

## Disallowed SQL

Reject any request that attempts or implies:

- `INSERT`
- `UPDATE`
- `DELETE`
- `TRUNCATE`
- `DROP`
- `ALTER`
- `CREATE`
- `REPLACE`
- `GRANT`
- `REVOKE`
- `CALL`
- Multi-step write workflows or schema changes

## Permission Rules

- Only use servers returned by `GET /database/serverlist`.
- If a user asks for a server, database, or table that is outside the accessible scope, stop and report permission denial.
- Do not try to bypass permission checks by guessing codes, changing headers, or switching authentication mode.

## Token Rules

- If the API returns `access token invalid`, report that the token is invalid and stop.
- If the API returns `access token expired`, tell the user to renew or reset the token in the JavaSqlWeb frontend and stop.
- Do not fabricate a `User-Token` or simulate a frontend login flow from this skill.

## Result Handling

- Prefer narrow queries over broad scans.
- If the result is large, mention that the platform may have truncated the returned rows.
- If the result is empty, say so clearly instead of implying failure.
- If a request is unsafe or out of scope, refuse it directly in Chinese and explain that this skill is for read-only querying.
