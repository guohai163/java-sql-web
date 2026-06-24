# Kubernetes 下在 `v3.0.0` 基线尽量少停机接入 Vanna

本文档面向这样一种场景：

- 当前线上已经在 Kubernetes 中稳定运行 `JavaSqlWeb v3.0.0`
- 现有 `jsw-front`、`jsw-server`、`jsw-db` 已正常提供服务
- 现在要把 `Vanna` 问数能力接进去
- 希望尽量减少停机时间，最好把停机范围压缩到“前端切流量”或“服务滚动发布”级别

## 先说结论

在 Kubernetes 环境里，**接入 Vanna 本身不应该需要长时间停机**。

最稳妥的路径不是直接替换现有资源，而是按下面的顺序做：

1. 先准备数据库新增对象
2. 再把 `jsw-vanna` 独立部署起来，但先不让前端流量打过去
3. 再发布带 `/api/vanna/` 代理和内部上下文接口的新 `jsw-server/jsw-front`
4. 最后灰度验证 `Vanna` 请求链路

这样做的结果是：

- 旧查询能力持续可用
- 现有 `jsw-front/jsw-server` 仅经历正常滚动发布
- 真正有风险的只有新 `Vanna` 能力本身，而不是整个系统

## 适用前提

- 现网版本基线是 `v3.0.0`
- Kubernetes Deployment 支持滚动更新
- PostgreSQL 已经在跑，并且允许你新增数据库/表/扩展
- 允许先升级应用版本，再逐步开放 Vanna 入口

## 为什么不建议“先停机再整体切换”

如果当前系统已经能跑，Vanna 是一个“新增能力”，不是替换核心查询链路。

从依赖关系看：

- 老功能链路：`jsw-front -> jsw-server -> jsw-db / 目标库`
- 新功能链路：`jsw-front -> jsw-vanna -> jsw-server(/internal/vanna/context) -> jsw-db`

也就是说：

- `jsw-vanna` 是新增旁路
- 原本 SQL 查询主链路没有被替换
- 因此完全可以先把新增依赖准备好，再做滚动接入

## 总体流程

1. 备份当前 Deployment/Secret/Config
2. 在 PG 中新增 `jsw_vanna_db` 与相关表
3. 先部署 `jsw-vanna`
4. 验证 `jsw-vanna` 容器健康和启动日志
5. 再滚动升级 `jsw-server`
6. 再滚动升级 `jsw-front`
7. 验证 `/api/vanna/sql/generate` 整体链路
8. 如有问题，仅回滚 `jsw-vanna` 或前端代理，不影响原始查询功能

## 步骤 1：先备份当前线上配置

建议先导出现网资源：

```bash
kubectl get deploy jsw-server -n <namespace> -o yaml > /tmp/jsw-server.before-vanna.yaml
kubectl get deploy jsw-front -n <namespace> -o yaml > /tmp/jsw-front.before-vanna.yaml
kubectl get secret jsw-app-secret -n <namespace> -o yaml > /tmp/jsw-app-secret.before-vanna.yaml
kubectl get ingress -n <namespace> -o yaml > /tmp/jsw-ingress.before-vanna.yaml
```

## 步骤 2：先准备 PostgreSQL 的新增对象

Vanna 接入时真正新增的数据库对象主要是：

- 独立数据库 `jsw_vanna_db`
- 独立角色 `jsw_vanna`
- `vector` 扩展
- `vanna_context_cache`
- `vanna_context_embedding`
- `vanna_audit_log`

如果现有 PG 已经在线，不建议通过“删库重建”方式接入，而是直接执行增量 SQL：

- [deploy/init.vanna.postgresql.sql](../deploy/init.vanna.postgresql.sql)

在 Kubernetes 里，可以直接进现有 PG Pod 执行，或在运维跳板机上用 `psql` 执行。

建议先手工创建数据库：

```sql
CREATE ROLE jsw_vanna LOGIN PASSWORD 'change-me';
CREATE DATABASE jsw_vanna_db OWNER jsw_vanna;
```

然后再执行增量脚本：

```bash
psql -U <pg-user> -d jsw_vanna_db -f deploy/init.vanna.postgresql.sql
```

### 这一步是否需要停机

通常不需要。

原因：

- 新建的是独立数据库和独立表
- 不会改动现有 `javasqlweb_db` 业务表结构
- 不会阻塞现有 `jsw-server` 查询链路

## 步骤 3：先部署 `jsw-vanna`，但不要急着对外放流量

仓库已经有 Vanna 的 K8s 清单：

- [deploy/k8s/base/deployment-vanna.yaml](../deploy/k8s/base/deployment-vanna.yaml)
- [deploy/k8s/base/service-vanna.yaml](../deploy/k8s/base/service-vanna.yaml)

先在 `Secret` 中补齐这些变量：

- `VANNA_INTERNAL_TOKEN`
- `VANNA_DB_URL`
- `VANNA_CHAT_MODEL`
- `VANNA_EMBEDDING_MODEL`
- `VANNA_LLM_BASE_URL`
- `VANNA_LLM_API_KEY`

然后部署 `jsw-vanna`：

```bash
kubectl apply -f deploy/k8s/base/service-vanna.yaml
kubectl apply -f deploy/k8s/base/deployment-vanna.yaml
```

或者继续使用：

```bash
bash scripts/deploy-k8s.sh
```

但前提是你的 `deploy/k8s/env/prod.env` 已经填好 Vanna 相关配置。

### 这一步是否需要停机

不需要。

因为：

- 此时前端还没把 `/api/vanna/` 流量代理到它
- `jsw-vanna` 只是一个新的内部服务
- 老功能完全不受影响

## 步骤 4：验证 `jsw-vanna` 启动是否正常

先确认 Pod Ready：

```bash
kubectl get pods -n <namespace> -l app=jsw-vanna
```

查看日志：

```bash
kubectl logs deployment/jsw-vanna -n <namespace> -f
```

健康检查：

```bash
kubectl exec -it deploy/jsw-vanna -n <namespace> -- wget -qO- http://127.0.0.1:8003/health
```

正常时应至少看到：

- 启动版本号
- chat model / embedding model / llm_base_url
- `Vanna startup completed`

## 步骤 5：滚动升级 `jsw-server`

接入 Vanna 后，`jsw-server` 需要提供：

- `/internal/vanna/context/{serverCode}/{dbName}`
- `Authorization` / `User-Token` 透传校验
- `VANNA_INTERNAL_TOKEN` 校验

因此旧的 `v3.0.0` 版 `jsw-server` 不够，需要升级到包含这些功能的新版本。

推荐方式是正常滚动升级 Deployment：

```bash
kubectl set image deployment/jsw-server jsw-server=<your-server-image>:<new-tag> -n <namespace>
kubectl rollout status deployment/jsw-server -n <namespace>
```

### 这一步是否需要停机

通常不需要，只要满足：

- `replicas >= 2`
- `readinessProbe` 正常
- 滚动更新参数没有设成全停再起

如果当前只有 `1` 个副本，也通常只会有一个很短的切换窗口，不属于长时间停机。

### 建议

如果你现在只有 1 个副本，建议先临时扩成 2 个：

```bash
kubectl scale deploy/jsw-server -n <namespace> --replicas=2
```

等 Vanna 验证通过后，再决定是否缩回去。

## 步骤 6：滚动升级 `jsw-front`

`jsw-front` 需要新增：

- `/api/vanna/` 代理到 `jsw-vanna`
- 前端工作台里的 AI 问数入口

同样建议滚动升级：

```bash
kubectl set image deployment/jsw-front jsw-front=<your-front-image>:<new-tag> -n <namespace>
kubectl rollout status deployment/jsw-front -n <namespace>
```

### 这一步是否需要停机

通常也不需要。

因为：

- 只是 Nginx 静态资源和代理配置滚动更新
- 原有 `/api/`、`/database/`、`/user/` 路径不变
- 即使 Vanna 功能异常，老查询能力仍应保留

## 步骤 7：最小影响验证顺序

建议不要一上来就让业务用户点 AI 问数，而是按下面顺序逐层验证。

### 7.1 验证 `jsw-vanna -> jsw-server` 内部上下文链路

在集群内执行：

```bash
kubectl exec -it deploy/jsw-vanna -n <namespace> -- sh
```

然后用内部网络调用：

```bash
curl -i \
  -H "X-Vanna-Internal-Token: <token>" \
  -H "User-Token: <user-token>" \
  "http://jsw-server:8002/internal/vanna/context/<serverCode>/<dbName>"
```

确认返回：

- 不是 401
- 不是 500
- 有 tables / columns / historyExamples

### 7.2 验证 `jsw-vanna` 生成接口

```bash
curl -i \
  -H "Content-Type: application/json" \
  -H "User-Token: <user-token>" \
  -d '{"serverCode":2,"dbName":"sub2api","question":"这个库是做什么用的"}' \
  http://<front-domain>/api/vanna/sql/generate
```

确认返回：

- 200
- 有 `needsClarification` / `sql` / `warnings`

### 7.3 最后再让前端入口对用户可见

如果你担心新入口对用户影响太快，可以先只升级后端和代理配置，再通过：

- 特定环境
- 指定域名
- 或临时测试分支镜像

验证完成后再推正式前端镜像。

## 如何减少停机时间

核心原则只有三条：

### 原则 1：数据库扩展与表结构先做，且做在独立库里

这样不会影响现有 `javasqlweb_db` 的在线读写。

### 原则 2：先部署 `jsw-vanna`，后切 `jsw-server/jsw-front`

先把新旁路服务准备好，再让现有流量引用它，而不是边起边切。

### 原则 3：用滚动发布，不做“整体停服”

只要：

- Deployment 副本数大于 1
- Readiness 探针正常
- 镜像本身启动没问题

那么 Vanna 接入本身就不需要长时间停机。

## 如果当前就是单副本，怎么把停机压到最短

如果你当前 `jsw-server` / `jsw-front` 都是单副本，推荐这样做：

1. 先单独部署 `jsw-vanna`
2. 先把 `jsw-server` 扩到 2 副本
3. 再升级 `jsw-server`
4. 再把 `jsw-front` 扩到 2 副本
5. 再升级 `jsw-front`

这样停机时间通常可以接近 0，至少不会有“整站不可用数分钟”的情况。

## 回滚策略

如果 Vanna 功能上线后有问题，优先按下面顺序回滚：

1. 回滚 `jsw-front`
   - 去掉 `/api/vanna/` 入口
   - 老功能继续可用

2. 回滚 `jsw-server`
   - 回到不含 `/internal/vanna/context` 的旧版本

3. 保留 `jsw-vanna` 和 `jsw_vanna_db`
   - 因为它们是新增旁路，不影响老功能

这比“回滚整套系统”成本低很多。

## 推荐的最小停机方案

如果你要一句话版执行策略：

1. 先在 PG 中创建 `jsw_vanna_db` 和 Vanna 表
2. 先部署 `jsw-vanna`
3. 把 `jsw-server` 扩到 2 副本并滚动升级
4. 把 `jsw-front` 扩到 2 副本并滚动升级
5. 逐层验证 `/internal/vanna/context` 和 `/api/vanna/sql/generate`

这样接入 Vanna 的过程，通常不需要“业务停机窗口”，最多只需要非常短的滚动切换时间。
