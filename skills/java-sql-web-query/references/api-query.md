# API Query Flow

This project exposes the query workflow through the backend API under `/database/**`.

## Authentication

- Build request URLs from `JSW_BASE_URL`.

- Send the long-lived access token from `JSW_ACCESS_TOKEN` as:

```http
Authorization: Bearer <access-token>
```

- The access token is managed through:
  - `GET /user/access-token`
  - `POST /user/access-token`
  - `PUT /user/access-token/renew`
  - `PUT /user/access-token/reset`

Those token-management endpoints require the short-lived `User-Token` from a normal frontend login and are not part of the query flow itself.

## Query Sequence

Use the following order:

1. `GET /database/serverlist`
   Purpose: discover which database servers are accessible to the current token.

2. `GET /database/dblist/{serverCode}`
   Purpose: list databases under a permitted server.

3. Optional metadata endpoints before querying:
   - `GET /database/tablelist/{serverCode}/{dbName}`
   - `GET /database/tablecolumn/{serverCode}/{dbName}`
   - `GET /database/columnslist/{serverCode}/{dbName}/{tableName}`
   - `GET /database/indexeslist/{serverCode}/{dbName}/{tableName}`
   - `GET /database/views/{serverCode}/{dbName}`
   - `GET /database/views/{serverCode}/{dbName}/{viewName}`
   - `GET /database/storedprocedures/{serverCode}/{dbName}`
   - `GET /database/storedprocedures/{serverCode}/{dbName}/{spName}`

4. Execute the read-only SQL:

```http
POST ${JSW_BASE_URL}/database/query/{serverCode}/{dbName}
Content-Type: text/plain
Authorization: Bearer ${JSW_ACCESS_TOKEN}
```

Request body is the SQL string itself.

## Important Notes

- `/database/**` enforces server-level permission checks on the backend. If a server is not assigned to the user, the request must be treated as forbidden.
- Query results may be truncated by the service-side row limit. If the response message indicates a limit, surface that fact in the final answer.
- `GET /version`, `GET /health`, and `GET /sql/guid` exist, but they are auxiliary and not the primary workflow for data querying.

## Recommended Query Pattern

1. Discover accessible servers.
2. Confirm the target server is actually visible.
3. List databases.
4. If needed, inspect tables/columns first.
5. Build a minimal read-only SQL query.
6. Execute it once.
7. Explain the result, including any limits or permission boundaries.
