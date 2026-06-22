# Docker Compose 模式下从 MySQL 元库迁移到 PostgreSQL

本文档面向已经通过 Docker Compose 部署 JavaSqlWeb，且当前应用元数据库仍然是 MySQL/MariaDB 的场景。

目标是把应用自身元数据库迁移到 PostgreSQL 18 / `pgvector/pgvector:pg18`，同时保留 JavaSqlWeb 连接和查询 MySQL/MariaDB 目标库的能力。

## 适用范围

- 当前线上运行方式是 Docker Compose。
- 可以接受一次停机迁移，不做双写。
- 服务端版本已经包含 PostgreSQL 元数据库兼容能力。
- 迁移目标是应用元数据库，不是业务目标库。

## 迁移前先知道的坑

这几个问题都在实际迁移中出现过，建议先看一遍：

1. `mysqldump` 生成的是 MySQL SQL，不要直接拿给 PostgreSQL 的 `psql` 导入。
2. 当前仓库的 `docker-compose.yml` 写死了 `container_name`，不能用同一份 Compose 同时起两套完整栈。
3. PostgreSQL 容器只会在空数据目录首次启动时自动执行 `/docker-entrypoint-initdb.d/init.sql`。
4. `pgloader` 从 MySQL 导入到 PostgreSQL 时，常见情况是把数据放到 `javasqlweb_db` schema，而不是 `public`。
5. 如果应用 JDBC URL 没有显式指定 `currentSchema`，服务端可能会继续读 `public` 下那套空表。
6. 迁移旧结构后，必须再执行一次 [deploy/init.postgresql.sql](../deploy/init.postgresql.sql) 里的兼容补丁，补齐 `db_session_id`、OIDC、WebAuthn 等后续新增字段。

## 目录和命名约定

下文使用下面这些名字举例：

- 旧 MySQL 容器：`jsw-db`
- 旧前端容器：`jsw-front`
- 旧服务端容器：`jsw-server`
- 新 PostgreSQL 容器：仍使用当前 Compose 里的 `jsw-db`
- 临时 MySQL 恢复容器：`jsw-mysql-restore`

如果你的容器名不同，把命令里的名字替换掉即可。

## 准备新的 `.env`

如果当前 tag 还没有仓库根目录 `.env.example`，直接手工创建 `.env` 即可。

迁移到 PostgreSQL 后，建议至少包含：

```env
TAG=v3.0.0
DB_NAME=javasqlweb_db
DB_USERNAME=jsw
DB_PASSWORD=change-me
DB_DIALECT=postgresql
APP_DB_DIALECT=postgresql
DB_PORT=5432
DB_URL=jdbc:postgresql://jsw-db:5432/javasqlweb_db?currentSchema=javasqlweb_db
PUBLIC_DOMAIN=jsw.example.com
PUBLIC_HOST=https://jsw.example.com
FRONT_PORT=80
```

这里的 `currentSchema=javasqlweb_db` 很重要。它对应 `pgloader` 导入后的默认 schema 落点，能避免应用误连到 `public` 下的空表。

## 步骤 1：停前后端并备份旧 MySQL

先停应用写入，避免迁移期间继续写入登录态、权限、查询日志、OIDC/WebAuthn 请求状态。

```bash
docker stop jsw-front jsw-server
```

备份旧 MySQL 元库。推荐在宿主机执行 `docker exec`，让导出文件直接落到宿主机当前目录：

```bash
mkdir -p backup

docker exec -e MYSQL_PWD='旧库密码' jsw-db \
  mysqldump --single-transaction --routines --triggers \
  -u root javasqlweb_db \
  > ./backup/javasqlweb_db.sql
```

确认备份文件已经生成：

```bash
ls -lh ./backup/javasqlweb_db.sql
```

## 步骤 2：释放 `jsw-db` 容器名

当前 Compose 文件把数据库容器固定命名为 `jsw-db`。如果旧 MySQL 容器也叫 `jsw-db`，需要先把旧容器改名，否则新 PostgreSQL 起不来。

```bash
docker stop jsw-db
docker rename jsw-db jsw-db-mysql-legacy
```

这样做不会删除旧容器和旧数据卷，回滚时还能用。

## 步骤 3：启动最终目标 PostgreSQL 容器

确保 `.env` 已经改成 PostgreSQL 版本，然后只启动数据库：

```bash
docker compose up -d jsw-db
```

确认 PostgreSQL 就绪：

```bash
docker exec -it jsw-db pg_isready -h 127.0.0.1 -U jsw -d javasqlweb_db
docker exec -it jsw-db psql -U jsw -d javasqlweb_db -c '\dt'
```

注意：这一步会触发 [deploy/init.postgresql.sql](../deploy/init.postgresql.sql) 自动初始化，但只作用于空数据卷首次启动。

## 步骤 4：起一个临时 MySQL 恢复容器

由于 PostgreSQL 不能直接消费 `mysqldump`，推荐把 dump 恢复到一个临时 MySQL 容器，再让 `pgloader` 从它迁到 PostgreSQL。

```bash
docker run -d \
  --name jsw-mysql-restore \
  -e MYSQL_ROOT_PASSWORD=restore-pass \
  -e MYSQL_DATABASE=javasqlweb_db \
  -p 3307:3306 \
  mysql:8.4
```

等待 MySQL 就绪后恢复备份：

```bash
docker exec -i jsw-mysql-restore \
  mysql -uroot -prestore-pass javasqlweb_db \
  < ./backup/javasqlweb_db.sql
```

简单验证恢复结果：

```bash
docker exec -it jsw-mysql-restore \
  mysql -uroot -prestore-pass -e "SELECT COUNT(*) AS user_count FROM javasqlweb_db.user_tb;"
```

## 步骤 5：用 `pgloader` 导入到 PostgreSQL

推荐让 `pgloader` 复用 PostgreSQL 容器网络，这样目标地址固定写 `127.0.0.1:5432` 即可：

```bash
docker run --rm --network container:jsw-db dimitri/pgloader:latest \
  pgloader \
  mysql://root:restore-pass@host.docker.internal:3307/javasqlweb_db \
  postgresql://jsw:change-me@127.0.0.1:5432/javasqlweb_db
```

预期结果里应该看到：

- `errors` 为 `0`
- `Reset Sequences` 成功
- `Total import time` 行以 `✓` 结束

## 步骤 6：确认数据实际落在哪个 schema

迁移完成后，先不要急着启动应用。先检查 schema：

```bash
docker exec -it jsw-db psql -U jsw -d javasqlweb_db -c '\dn'
docker exec -it jsw-db psql -U jsw -d javasqlweb_db -c '\dt javasqlweb_db.*'
docker exec -it jsw-db psql -U jsw -d javasqlweb_db -c "SELECT COUNT(*) FROM javasqlweb_db.user_tb;"
```

如果这里能查到数据，而 `SELECT COUNT(*) FROM user_tb;` 是 0，说明数据在 `javasqlweb_db` schema，`public` 下只是自动初始化出来的空表。这是正常现象，不是导入失败。

## 步骤 7：设置默认 schema，并手工重跑初始化补丁

先把数据库默认 `search_path` 指向 `javasqlweb_db, public`：

```bash
docker exec -it jsw-db psql -U jsw -d javasqlweb_db -c \
  "ALTER DATABASE javasqlweb_db SET search_path TO javasqlweb_db, public;"
```

然后进入 `psql`，在 `javasqlweb_db` schema 上手工执行一次初始化脚本：

```bash
docker exec -it jsw-db psql -U jsw -d javasqlweb_db
```

在 `psql` 里执行：

```sql
SET search_path TO javasqlweb_db, public;
\i /docker-entrypoint-initdb.d/init.sql
\q
```

这一步非常重要。它会把 `deploy/init.postgresql.sql` 末尾的兼容补丁应用到当前 schema，补齐这些迁移后经常缺失的字段：

- `db_query_log.db_session_id`
- `db_query_log.query_consuming`
- `db_query_log.result_row_count`
- `user_tb.oidc_sub`
- `user_tb.access_token_hash`
- `user_tb.access_token_expire_time`
- `oidc_config_tb.ssf_configuration_url`
- `webauthn_request_tb.request_json`
- `oidc_login_state_tb.state_key`

执行后建议确认关键列已经存在：

```bash
docker exec -it jsw-db psql -U jsw -d javasqlweb_db -c '\d javasqlweb_db.db_query_log'
docker exec -it jsw-db psql -U jsw -d javasqlweb_db -c '\d javasqlweb_db.user_tb'
```

## 步骤 8：核对核心表行数

建议用 schema 限定名核对一次核心表：

```bash
docker exec -it jsw-db psql -U jsw -d javasqlweb_db -c "
select 'user_tb' as table_name, count(*) as row_count from javasqlweb_db.user_tb
union all select 'usergroup', count(*) from javasqlweb_db.usergroup
union all select 'user_permissions', count(*) from javasqlweb_db.user_permissions
union all select 'db_permissions', count(*) from javasqlweb_db.db_permissions
union all select 'db_connect_config_tb', count(*) from javasqlweb_db.db_connect_config_tb
union all select 'db_query_log', count(*) from javasqlweb_db.db_query_log
union all select 'db_query_log_target_tb', count(*) from javasqlweb_db.db_query_log_target_tb
union all select 'db_server_database_snapshot_tb', count(*) from javasqlweb_db.db_server_database_snapshot_tb
union all select 'guid_sql_tb', count(*) from javasqlweb_db.guid_sql_tb
union all select 'oidc_config_tb', count(*) from javasqlweb_db.oidc_config_tb
union all select 'oidc_login_state_tb', count(*) from javasqlweb_db.oidc_login_state_tb
union all select 'passkey_auths_tb', count(*) from javasqlweb_db.passkey_auths_tb
union all select 'webauthn_request_tb', count(*) from javasqlweb_db.webauthn_request_tb
union all select 'user_security_task_tb', count(*) from javasqlweb_db.user_security_task_tb
order by 1;"
```

如果 `pgloader` 输出里某些表有数据，而这里还是 0，优先检查：

- 是不是还在查 `public.*`
- `search_path` 是否已经设置
- JDBC URL 是否带了 `currentSchema=javasqlweb_db`

## 步骤 9：启动服务端和前端

确认 `.env` 至少包含以下配置：

```env
DB_DIALECT=postgresql
APP_DB_DIALECT=postgresql
DB_PORT=5432
DB_URL=jdbc:postgresql://jsw-db:5432/javasqlweb_db?currentSchema=javasqlweb_db
```

然后启动应用：

```bash
docker compose up -d jsw-server jsw-front
docker compose logs -f jsw-server
```

如果日志里出现类似：

```text
column "db_session_id" of relation "db_query_log" does not exist
```

说明第 7 步的结构补丁没有真正打到数据所在 schema。回到 `psql` 里重新确认：

```sql
SET search_path TO javasqlweb_db, public;
\i /docker-entrypoint-initdb.d/init.sql
```

## 步骤 10：业务验收

至少验证下面这些功能：

- 管理员登录
- 普通用户登录
- 已有连接配置可见
- 权限组和数据库授权正常
- 最近查询日志可读
- Dashboard 可读
- OIDC 登录回调
- WebAuthn 注册/登录回调

## 回滚

如果 PostgreSQL 验证失败，按下面顺序回滚：

1. 停掉新 `jsw-front`、`jsw-server`、`jsw-db`
2. 把 `.env` 改回 MySQL 版本
3. 停掉临时容器 `jsw-mysql-restore`
4. 把旧 MySQL 容器改回原名或直接按旧配置重新启动
5. 恢复旧前后端容器

示例：

```bash
docker stop jsw-front jsw-server jsw-db
docker stop jsw-mysql-restore
docker rename jsw-db-mysql-legacy jsw-db
docker start jsw-db jsw-server jsw-front
```

在 PostgreSQL 验收完成前，不要删除：

- 旧 MySQL 容器 / 数据卷
- `./backup/javasqlweb_db.sql`
- 临时恢复容器对应的导入记录

## 迁移后建议

迁移完成并稳定运行后，建议额外做两件事：

1. 清理 `public` schema 下那套空表，避免后续误用。
2. 把 PostgreSQL 兼容补丁执行记录纳入发布手册，避免后续环境遗漏 `db_session_id`、OIDC/WebAuthn 新字段。
