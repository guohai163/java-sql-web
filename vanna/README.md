# Vanna Service

独立容器服务，负责：

- 拉取 `jsw-server` 暴露的受控上下文
- 基于 schema 备注与历史只读查询模式生成 SQL
- 将上下文缓存和审计记录写入独立的 `jsw_vanna_db`

服务本身不执行 SQL。

默认 embedding 方案：

- 本地 CPU 运行 `BAAI/bge-small-zh-v1.5`
- 不再依赖远程 `text-embedding-3-small`
- 仅聊天模型仍通过 OpenAI 兼容接口调用
