# Kubernetes 模式下从 MySQL 元库迁移到 PostgreSQL

本文档面向已经在 Kubernetes 中运行 JavaSqlWeb，且当前应用元数据库仍是 MySQL/MariaDB 的场景。

目标是把应用自身元数据库迁移到 PostgreSQL 18 / `pgvector/pgvector:pg18`，同时保留 JavaSqlWeb 连接和查询 MySQL/MariaDB 目标库的能力。

## 先说结论

不要直接把现有 `jsw-db` StatefulSet 从 MySQL 镜像改成 PostgreSQL 镜像再执行 [scripts/deploy-k8s.sh](../scripts/deploy-k8s.sh)。

原因有三条：

1. 现有 K8s 模板里的数据库资源名固定是 `jsw-db`，PVC 模板名固定是 `db-data`。
2. [scripts/deploy-k8s.sh](../scripts/deploy-k8s.sh) 只负责渲染和 `kubectl apply`，不负责导数、校验或回滚。
3. PostgreSQL 初始化脚本只会在空数据目录首次启动时执行，不能把已有 MySQL PVC 直接“原地切”成 PostgreSQL。

正确路径是：

- 旧 MySQL 继续跑
- 新建一套独立的 PostgreSQL 数据库资源
- 执行一次停机迁移
- 切换 `jsw-server` 到新 PostgreSQL
- 验证通过后再决定是否下线旧 MySQL

## 适用范围

- 当前运行环境是 Kubernetes。
- 当前 JavaSqlWeb 元数据库是 MySQL/MariaDB。
- 可以接受一次停机迁移。
- 服务端版本已经支持 PostgreSQL 元数据库。

## 当前仓库模板的限制

当前仓库里的 K8s 部署模板默认假设：

- 数据库 Service 固定叫 `jsw-db`
- 数据库 StatefulSet 固定叫 `jsw-db`
- `jsw-server` 固定通过 `DB_HOST=jsw-db` 访问元数据库

对应文件：

- [deploy/k8s/base/service-db.yaml](../deploy/k8s/base/service-db.yaml)
- [deploy/k8s/base/statefulset-db.yaml](../deploy/k8s/base/statefulset-db.yaml)
- [deploy/k8s/base/deployment-server.yaml](../deploy/k8s/base/deployment-server.yaml)

所以迁移期不要直接覆盖现有资源，而是单独创建一个新的 PostgreSQL 数据库实例，例如：

- StatefulSet：`jsw-db-pg`
- Service：`jsw-db-pg`
- PVC：自动生成新的 `db-data-jsw-db-pg-0`

## 总体流程

1. 保持现有 MySQL 版 JavaSqlWeb 在线运行。
2. 在同一 namespace 新建一套 PostgreSQL 数据库资源。
3. 确认 PostgreSQL 空库初始化完成。
4. 停止 `jsw-front` / `jsw-server` 写入。
5. 备份旧 MySQL 元库。
6. 用 `pgloader` 或临时迁移 Pod 把数据导入 PostgreSQL。
7. 确认数据落在正确 schema，并执行结构兼容补丁。
8. 修改 `jsw-server` 连接串，切换到 PostgreSQL。
9. 启动并验收。
10. 保留旧 MySQL 一段时间作为回滚点。

## 迁移前准备

先确认下面信息：

- namespace 名称
- 旧 MySQL Service 名称
- 旧 MySQL root 或应用账号密码
- JavaSqlWeb 当前 Deployment 名称
- PostgreSQL 计划使用的用户名、密码、存储大小、StorageClass

建议先把现有配置导出来留底：

```bash
kubectl get deploy jsw-server -n <namespace> -o yaml > /tmp/jsw-server.before.yaml
kubectl get deploy jsw-front -n <namespace> -o yaml > /tmp/jsw-front.before.yaml
kubectl get sts jsw-db -n <namespace> -o yaml > /tmp/jsw-db.before.yaml
kubectl get svc jsw-db -n <namespace> -o yaml > /tmp/jsw-db-svc.before.yaml
kubectl get secret jsw-app-secret -n <namespace> -o yaml > /tmp/jsw-app-secret.before.yaml
```

## 步骤 1：新建 PostgreSQL 数据库资源

最安全的做法不是改现有模板，而是单独写一份临时 YAML，新建一个 `jsw-db-pg`。

下面这个示例仿照“挂已有 PVC，并通过 `subPath` 使用卷内子目录”的写法。如果你已经有一块公共 PVC，想在同一个卷里单独给 PostgreSQL 划一个目录，这种方式比较合适。

前提：

- PVC `pgsql-pv-claim` 已经存在。
- 该卷支持当前节点正常挂载。
- 卷内的 `database/jsw-pg` 子目录可用；如果存储后端不会自动创建子目录，需要先手工准备。

示例：

```yaml
apiVersion: v1
kind: Service
metadata:
  name: jsw-db-pg
  namespace: <namespace>
spec:
  selector:
    app: jsw-db-pg
  ports:
    - name: postgres
      port: 5432
      targetPort: 5432
---
apiVersion: v1
kind: ConfigMap
metadata:
  name: jsw-init-sql-pg
  namespace: <namespace>
data:
  init.sql: |
    -- 这里建议直接使用 deploy/init.postgresql.sql 的完整内容，
    -- 或用 kubectl create configmap --from-file 方式创建。
---
apiVersion: apps/v1
kind: StatefulSet
metadata:
  name: jsw-db-pg
  namespace: <namespace>
spec:
  serviceName: jsw-db-pg
  replicas: 1
  selector:
    matchLabels:
      app: jsw-db-pg
  template:
    metadata:
      labels:
        app: jsw-db-pg
        tier: postgres
    spec:
      securityContext:
        fsGroup: 999
        runAsUser: 999
      containers:
        - name: postgres
          image: pgvector/pgvector:pg18
          ports:
            - containerPort: 5432
              name: postgres
          env:
            - name: POSTGRES_DB
              value: javasqlweb_db
            - name: POSTGRES_USER
              value: jsw
            - name: POSTGRES_PASSWORD
              value: change-me
            - name: PGDATA
              value: /var/lib/postgresql/18/docker
          readinessProbe:
            exec:
              command:
                - sh
                - -c
                - pg_isready -h 127.0.0.1 -U "$POSTGRES_USER" -d "$POSTGRES_DB"
          volumeMounts:
            - name: pgsql-persistent-storage
              mountPath: /var/lib/postgresql
              subPath: database/jsw-pg
            - name: init-sql
              mountPath: /docker-entrypoint-initdb.d/init.sql
              subPath: init.sql
      volumes:
        - name: pgsql-persistent-storage
          persistentVolumeClaim:
            claimName: pgsql-pv-claim
        - name: init-sql
          configMap:
            name: jsw-init-sql-pg
```

应用：

```bash
kubectl apply -f jsw-db-pg.yaml
```

确认状态：

```bash
kubectl get pods -n <namespace>
kubectl get pvc -n <namespace>
kubectl logs sts/jsw-db-pg -n <namespace>
```

## 步骤 2：确认 PostgreSQL 已完成首次初始化

确认 PG Pod Ready 后，检查表是否已经由 [deploy/init.postgresql.sql](../deploy/init.postgresql.sql) 初始化出来：

```bash
kubectl exec -it sts/jsw-db-pg -n <namespace> -- \
  psql -U jsw -d javasqlweb_db -c '\dt'
```

如果这里查不到表，先不要迁移，先处理初始化问题。

## 步骤 3：停止前后端写入

迁移窗口开始后，先停 `jsw-server` 和 `jsw-front`，避免迁移期继续写入登录态、权限、日志、OIDC/WebAuthn 状态。

```bash
kubectl scale deploy/jsw-server -n <namespace> --replicas=0
kubectl scale deploy/jsw-front -n <namespace> --replicas=0
```

确认副本数已归零：

```bash
kubectl get deploy -n <namespace>
```

## 步骤 4：备份旧 MySQL 元库

可以直接在旧 MySQL Pod 里执行 `mysqldump`，再把文件拷回本机。

先找旧 MySQL Pod：

```bash
kubectl get pods -n <namespace> -l app=jsw-db
```

执行导出：

```bash
kubectl exec -n <namespace> <old-mysql-pod> -- \
  sh -c 'exec mysqldump --single-transaction --routines --triggers -uroot -p"$MARIADB_ROOT_PASSWORD" javasqlweb_db > /tmp/javasqlweb_db.sql'
```

拷贝到本地：

```bash
kubectl cp <namespace>/<old-mysql-pod>:/tmp/javasqlweb_db.sql ./javasqlweb_db.sql
```

确认文件大小：

```bash
ls -lh ./javasqlweb_db.sql
```

## 步骤 5：准备迁移通道

推荐两种方式：

1. 本地端口转发后，在运维机或本机直接跑 `pgloader`
2. 在集群里起一个临时 `pgloader` Pod

第一种通常更直观，也更方便排查错误。

### 方式 A：端口转发后本地执行 `pgloader`

先分别转发旧 MySQL 和新 PostgreSQL：

```bash
kubectl port-forward svc/jsw-db 3306:3306 -n <namespace>
kubectl port-forward svc/jsw-db-pg 5432:5432 -n <namespace>
```

然后在本机执行：

注意：如果用户名或密码里包含 `@`、`:`、`/`、`#` 这类 URI 特殊字符，必须先做 URL 编码。

例如：

- `P@ssw0rd` 需要写成 `P%40ssw0rd`
- `user:name` 需要写成 `user%3Aname`

```bash
docker run --rm dimitri/pgloader:latest \
  pgloader \
  mysql://root:旧库密码@host.docker.internal:3306/javasqlweb_db \
  postgresql://jsw:change-me@host.docker.internal:5432/javasqlweb_db
```

如果你是在 Linux 宿主机上跑 Docker，而不是 Docker Desktop，`host.docker.internal` 可能不可用，这时可以直接在宿主机安装 `pgloader` 或改用 `--network host`。

例如你的密码是 `P@ssw0rd`，实际应写成：

```bash
docker run --rm dimitri/pgloader:latest \
  pgloader \
  mysql://root:P%40ssw0rd@host.docker.internal:3306/javasqlweb_db \
  postgresql://jsw:P%40ssw0rd@host.docker.internal:5432/javasqlweb_db
```

### 方式 B：在集群里起临时迁移 Pod

也可以起一个临时 Pod，让它同时访问 `jsw-db` 和 `jsw-db-pg`：

```bash
kubectl run pgloader --rm -it -n <namespace> \
  --image=dimitri/pgloader:latest \
  --restart=Never -- \
  pgloader \
  mysql://root:旧库密码@jsw-db:3306/javasqlweb_db \
  postgresql://jsw:change-me@jsw-db-pg:5432/javasqlweb_db
```

如果 `kubectl run ... --rm -it` 一直卡在 `timed out waiting for the condition`，优先排查：

- Pod 是否真的启动成功：`kubectl get pod pgloader -n <namespace>`
- 日志里是否已经报错：`kubectl logs pgloader -n <namespace>`
- 数据库连接串里的密码是否包含未编码的特殊字符
- 集群里是否限制了临时交互 Pod 的创建

出现这类情况时，优先改走“方式 A：本机端口转发后执行”，通常更省心。

## 步骤 6：确认数据落在哪个 schema

`pgloader` 常见行为是把 MySQL 库名 `javasqlweb_db` 映射成 PostgreSQL schema `javasqlweb_db`。

所以迁移完成后不要直接查 `public.user_tb`，要先确认 schema：

```bash
kubectl exec -it sts/jsw-db-pg -n <namespace> -- \
  psql -U jsw -d javasqlweb_db -c '\dn'

kubectl exec -it sts/jsw-db-pg -n <namespace> -- \
  psql -U jsw -d javasqlweb_db -c '\dt javasqlweb_db.*'

kubectl exec -it sts/jsw-db-pg -n <namespace> -- \
  psql -U jsw -d javasqlweb_db -c 'select count(*) from javasqlweb_db.user_tb;'
```

如果带 schema 的查询有数据，而不带 schema 的查询没有数据，不是迁移失败，是默认 schema 还没切对。

## 步骤 7：设置默认 schema 并补齐结构

先设置数据库默认 `search_path`：

```bash
kubectl exec -it sts/jsw-db-pg -n <namespace> -- \
  psql -U jsw -d javasqlweb_db -c "ALTER DATABASE javasqlweb_db SET search_path TO javasqlweb_db, public;"
```

然后进入 `psql`，手工执行一次初始化脚本，让兼容补丁打到真正有数据的 schema：

```bash
kubectl exec -it sts/jsw-db-pg -n <namespace> -- psql -U jsw -d javasqlweb_db
```

在 `psql` 中执行：

```sql
SET search_path TO javasqlweb_db, public;
\i /docker-entrypoint-initdb.d/init.sql
\q
```

这一步很关键。它会补齐迁移后容易缺失的字段，例如：

- `db_query_log.db_session_id`
- `db_query_log.query_consuming`
- `db_query_log.result_row_count`
- `user_tb.oidc_sub`
- `user_tb.access_token_hash`
- `user_tb.access_token_expire_time`
- `oidc_config_tb.ssf_configuration_url`
- `webauthn_request_tb.request_json`
- `oidc_login_state_tb.state_key`

## 步骤 8：核对核心表行数

建议逐表核对：

```bash
kubectl exec -it sts/jsw-db-pg -n <namespace> -- \
  psql -U jsw -d javasqlweb_db -c "
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

重点检查：

- 管理员用户是否存在
- 数据库连接配置是否存在
- 用户组和权限关系是否完整
- 查询日志是否有历史数据

## 步骤 9：切换 `jsw-server` 到 PostgreSQL

不要马上动 [scripts/deploy-k8s.sh](../scripts/deploy-k8s.sh)。先把 `jsw-server` 手工 patch 到新 PG，验证通过后再决定是否回收旧库。

最小切换项包括：

- `DB_HOST=jsw-db-pg`
- `DB_PORT=5432`
- `DB_DIALECT=postgresql`
- `APP_DB_DIALECT=postgresql`
- `DB_URL=jdbc:postgresql://jsw-db-pg:5432/javasqlweb_db?currentSchema=javasqlweb_db`

示例：

```bash
kubectl set env deployment/jsw-server -n <namespace> \
  DB_HOST=jsw-db-pg \
  DB_PORT=5432 \
  DB_DIALECT=postgresql \
  APP_DB_DIALECT=postgresql \
  DB_URL='jdbc:postgresql://jsw-db-pg:5432/javasqlweb_db?currentSchema=javasqlweb_db'
```

如果 `DB_USERNAME` 和 `DB_PASSWORD` 也改了，需要同步更新 `jsw-app-secret`。

## 步骤 10：恢复前后端并观察日志

启动服务：

```bash
kubectl scale deploy/jsw-server -n <namespace> --replicas=1
kubectl scale deploy/jsw-front -n <namespace> --replicas=1
```

观察：

```bash
kubectl rollout status deploy/jsw-server -n <namespace>
kubectl logs deployment/jsw-server -n <namespace> -f
```

如果日志里出现：

```text
column "db_session_id" of relation "db_query_log" does not exist
```

说明兼容补丁没有真正打到数据所在 schema。重新执行第 7 步。

## 步骤 11：业务验收

至少验证：

- 管理员登录
- 普通用户登录
- 已有连接配置可见
- 权限组和数据库授权正常
- 最近查询日志可读
- Dashboard 可读
- OIDC 登录回调
- WebAuthn 注册/登录回调

## 回滚

如果 PostgreSQL 验证失败，直接回滚到旧 MySQL：

1. 把 `jsw-server` 环境变量改回旧 MySQL
2. 保持 `jsw-db-pg` 不动，先不要删
3. 重新启动 `jsw-server` / `jsw-front`

示例：

```bash
kubectl set env deployment/jsw-server -n <namespace> \
  DB_HOST=jsw-db \
  DB_PORT=3306 \
  DB_DIALECT=mysql \
  APP_DB_DIALECT=mysql \
  DB_URL='jdbc:mysql://jsw-db:3306/javasqlweb_db?useUnicode=true&characterEncoding=UTF-8&serverTimezone=Hongkong'
```

然后：

```bash
kubectl rollout restart deployment/jsw-server -n <namespace>
kubectl rollout restart deployment/jsw-front -n <namespace>
```

## `deploy-k8s.sh` 在迁移中的正确用法

[scripts/deploy-k8s.sh](../scripts/deploy-k8s.sh) 在这个迁移场景里更适合做两件事：

1. 迁移完成后，为“全新环境”部署 PostgreSQL 版 JavaSqlWeb。
2. 迁移后整理目标环境配置时，统一渲染 PostgreSQL 参数。

它**不适合**直接承担以下职责：

- 复用旧 `jsw-db` StatefulSet 原地切换数据库类型
- 在已有 MySQL PVC 上直接切 PostgreSQL
- 自动导数
- 自动校验迁移结果
- 自动回滚

## 迁移后建议

迁移稳定后，建议再做两件事：

1. 把仓库里的 K8s 模板改成支持可配置的数据库资源名，而不是写死 `jsw-db`。
2. 为 K8s 增加一个官方迁移 Job 模板，把 `pgloader` 和补结构步骤固化下来。
