# 将 Vanna 迁移到独立 GPU 主机

此方案保留 JavaSqlWeb、`jsw-server` 和 PostgreSQL 在 Kubernetes 中，仅把 Vanna 移到一台内网独立 GPU 主机上运行 Docker。浏览器路径保持不变：浏览器 -> Ingress -> `jsw-front` -> 独立 Vanna。

独立 Vanna 不直接查询受管业务数据库。它仍通过 `jsw-server` 的 `/internal/vanna/*` 接口获取受权限控制的上下文，随后调用配置的 OpenAI 兼容 LLM 网关生成 SQL。

## 前置条件

1. GPU 主机、Kubernetes 节点和数据库 LoadBalancer IP 位于可信网络。
2. GPU 主机已安装 NVIDIA 驱动、Docker 和 NVIDIA Container Toolkit；执行 `docker run --rm --gpus all nvidia/cuda:12.1.1-base-ubuntu22.04 nvidia-smi` 必须能列出两张 GTX 1660。
3. `10.12.54.239/32` 必须已由 Calico 宣告并被 RouterOS 学到 BGP 路由。迁移机上执行 `nc -vz 10.12.54.239 5432` 必须成功。
4. 选择一个尚未被使用的独立 GPU 主机 IP，例如下文的 `10.12.54.240`，并在防火墙中仅允许 K8s 节点到该 IP 的 TCP/8003。
5. 先发布包含本次变更的新前端镜像，例如 Git tag `v3.3.1`。当前线上 `v3.3.0` 镜像把 Vanna upstream 写死为 `jsw-vanna`，即使注入 `VANNA_BASE_URL` 也不会生效。

## 1. 提供 jsw-server 内网入口

`jsw-server` 原来的 `ClusterIP` 不变。为独立 Vanna 创建一个只在内网使用的 NodePort：

```shell
kubectl apply -f deploy/k8s/optional/service-server-vanna-nodeport.yaml
kubectl -n app-local get svc jsw-server-vanna
```

独立机用任一节点 IP 加端口 32002 访问，例如 `http://10.12.54.103:32002`。不要经 Ingress 或公网暴露 `/internal/vanna/*`。

### 数据库 VIP 未就绪时的临时回退

当前环境的 `10.12.54.239` 必须先在 RouterOS 中出现 BGP 路由后才能使用。若尚未出现，可暂时通过已有 `pgsql-db` Service 的 NodePort 访问 Vanna 缓存库：

```shell
kubectl -n database get svc pgsql-db \
  -o jsonpath='{.spec.ports[?(@.port==5432)].nodePort}{"\n"}'
```

将 `.env` 中的 `VANNA_DB_URL` 主机和端口改为一个 K8s 节点 IP 和该端口，例如 `postgresql://jsw_vanna:<password>@10.12.54.103:<nodePort>/jsw_vanna_db`。只允许 GPU 主机访问该端口；VIP 路由恢复后再切回 `10.12.54.239:5432`。

## 2. 启动独立 GPU 容器

在独立 GPU 主机上克隆同一版本的仓库：

```shell
cp deploy/vanna-external/.env.example deploy/vanna-external/.env
```

编辑 `.env`：

- `VANNA_BIND_IP`：独立机的内网 IP。
- `JSW_SERVER_BASE_URL`：K8s 节点 IP 加 `32002`。
- `VANNA_INTERNAL_TOKEN`、`VANNA_DB_URL`、LLM 配置：使用现有 Vanna 的相同值。
- `VANNA_EMBEDDING_DEVICE=cuda:0`：单实例只使用第一张 1660。BGE-small 的负载很小，第二张卡不降低单次问答时延；除非先解决多实例下的缓存写入竞态，否则不要启动两个 Vanna 副本。

首次预热会下载模型并重新检查缓存，运行：

```shell
docker compose --env-file deploy/vanna-external/.env -f deploy/vanna-external/docker-compose.yml up -d --build
docker compose --env-file deploy/vanna-external/.env -f deploy/vanna-external/docker-compose.yml logs -f jsw-vanna
```

验证 CUDA 和服务：

```shell
docker exec jsw-vanna python -c "import torch; print(torch.cuda.is_available(), torch.cuda.get_device_name(0))"
curl http://10.12.54.240:8003/health
```

## 3. 切换前端到独立 Vanna

将前端切换到新镜像并注入独立机地址：

```shell
kubectl -n app-local set image deployment/jsw-front \
  jsw-front=ghcr.io/guohai163/java-sql-web-front:v3.3.1
kubectl -n app-local set env deployment/jsw-front \
  VANNA_BASE_URL=http://10.12.54.240:8003
kubectl -n app-local rollout status deployment/jsw-front
```

前端 Nginx 会把 `/api/vanna/*` 代理到独立机 IP。`jsw-server` 不会、也不需要直接访问 Vanna。

不要直接 `apply deploy/k8s/base/deployment-front.yaml` 到当前线上 Deployment：线上资源使用的 selector 与仓库模板不同，而 Deployment selector 不可修改。

确认浏览器 AI 问答成功后，再停掉 K8s Vanna：

```shell
kubectl -n app-local scale deployment/jsw-vanna --replicas=0
```

## 回滚

将 `VANNA_BASE_URL` 改回 `http://jsw-vanna:8003`，等待前端 rollout 完成，然后恢复 Vanna：

```shell
kubectl -n app-local set env deployment/jsw-front \
  VANNA_BASE_URL=http://jsw-vanna:8003
kubectl -n app-local rollout status deployment/jsw-front
kubectl -n app-local scale deployment/jsw-vanna --replicas=1
```

独立 Docker 容器和数据表无需删除。
