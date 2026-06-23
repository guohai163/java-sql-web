# Vanna Service

独立容器服务，负责：

- 拉取 `jsw-server` 暴露的受控上下文
- 基于 schema 备注与历史只读查询模式生成 SQL
- 将上下文缓存和审计记录写入独立的 `jsw_vanna_db`

服务本身不执行 SQL。
