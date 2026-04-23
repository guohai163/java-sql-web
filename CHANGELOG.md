# Changelog

本文件记录当前 `master` 分支之后到当前 `develop` 工作区的主要变更，按版本和当前未发布改动整理。

## v2.9.4 - 2026-04-23

### Changed
- 工作台 SQL 编辑器从 CodeMirror 5 迁移到 CodeMirror 6，前端依赖切换为 `@uiw/react-codemirror` 与 `@codemirror/lang-sql` 体系。
- SQL 编辑器保持多数据库语法切换和动态对象补全能力，并将快捷补全键统一为 `Ctrl+Space`。
- 工作台样式同步迁移到 CM6 的 `.cm-*` 结构，保持原有布局高度和整体视觉风格。

### Fixed
- 增加中文输入法组合输入保护，避免 composition 期间受控回写干扰，修复中文输入显示异常与双击选中后误删内容的问题。
- 选区快照更新改为基于 CM6 原生 selection 状态，减少双击/拖拽选择在复杂输入时序下的异常行为。

### Tests
- 迁移并扩展 PageContent 编辑器测试 mock，覆盖 CM6 事件链。
- 新增中文输入法 composition 回归用例，验证中文上屏与选区稳定性。
- 回归前端 `PageContent`、`AdminDashboard` 测试与生产构建，确认迁移后功能可用。

## v2.9.3 - 2026-04-22

### Added
- 管理后台“服务器管理”列表新增“会话”操作，可直接打开活动会话明细抽屉查看平台用户、数据库账号、会话状态和正在执行 SQL。
- 服务器管理页新增按服务器名称搜索能力，支持快速定位目标实例。
- 服务器管理页新增按数据库类型筛选能力，支持在混合实例环境中按类型聚焦查看。

### Changed
- 服务器管理列表改为基于“名称搜索 + 类型筛选”组合条件实时过滤，不影响原有运行状态、测试、重置和编辑操作。

### Tests
- 回归前端 AdminDashboard 测试与生产构建，确认服务器管理列表新增交互未影响后台页面打包与基础渲染。

## v2.9.2 - 2026-04-22

### Fixed
- 修复工作台 SQL 编辑器在双击选词或拖动鼠标选择时，偶发把被选内容错误覆盖/删除的问题。
- 将编辑器选区与光标快照从 React state 收敛为 ref 持有，减少 `onCursorActivity` 高频触发时的受控回写干扰。
- 修正表名/字段名插入时的光标切分偏移，避免在光标后方错误吞掉一个字符。

### Tests
- 扩展 PageContent 前端测试，覆盖“只执行选中 SQL”和“光标插入对象名不吞字符”两条交互回归场景。
- 回归前端生产构建，确认编辑器交互修复未影响工作台打包。

## v2.9.1 - 2026-04-22

### Fixed
- 修复已升级到 `v2.9.0` 但主库尚未执行 `db_query_log.db_session_id` 迁移时，工作台 SQL 查询直接报 `Unknown column 'db_session_id' in 'field list'` 的问题。
- 兼容旧表结构场景下的后台活动会话明细：缺少 `db_session_id` 字段时仍返回数据库侧会话信息，只跳过平台用户归因，不再影响管理后台查看动态池明细。

### Changed
- 新增 `deploy/upgrade/2026-04-22-db-session-id.sql` 升级脚本，补齐 `db_session_id` 字段和索引，便于线上环境按需完成热修后的正式迁移。

### Tests
- 增加旧库缺少 `db_session_id` 时的后端兼容测试，覆盖查询日志 legacy 插入回退和后台活动会话明细跳过平台归因两条降级链路。

## v2.9.0 - 2026-04-22

### Added
- 管理后台“动态目标库连接池”新增活动会话明细入口，可查看 MySQL、PostgreSQL、SQL Server 动态池中的会话 ID、数据库账号、状态、运行时长、开始时间和当前 SQL。
- 动态目标库查询日志新增数据库会话 ID 追踪，并在后台会话明细中按 `serverCode + db_session_id` 归因到平台用户，支持管理员定位系统内新发起查询对应的活跃连接。

### Changed
- 动态目标库连接池观测扩展到 PostgreSQL、SQL Server 与 ClickHouse，后台统一按运行时连接快照判断 `正常 / 警告 / 冷却中` 状态。
- 工作台 SQL 查询失败时增加“弹窗 + 页内错误态”双重反馈，避免后端返回错误消息后界面无感知。
- 初始化 SQL 与查询日志 DAO 同步补齐 `db_session_id` 字段和索引，为活动会话归因提供持久化关联。

### Tests
- 增加动态会话明细与平台用户归因的后端测试，覆盖 MySQL、PostgreSQL、SQL Server 会话映射、后台接口返回和查询日志会话 ID 写入。
- 扩展 AdminDashboard 与 PageContent 前端测试，并回归前端生产构建，验证会话明细入口与 SQL 失败提示渲染正常。

## v2.8.2 - 2026-04-21

### Changed
- 动态 MySQL 查询新增 `project.target-query-timeout-seconds` 配置，默认 30 秒，并在执行用户查询前统一下发 statement timeout，避免慢查询长时间占满动态连接池。
- 管理后台服务器列表新增目标库运行状态展示与“重置”操作，可直接查看动态池活跃/空闲/等待情况、冷却剩余时间和最近错误，并手动清理单库动态池与冷却状态。
- 管理后台 dashboard 新增动态目标库连接池 KPI 与异常列表，统一展示活跃池、冷却池、动态池连接数和等待线程数。

### Fixed
- SQL 执行超时不再被误判为连接失败，因此不会把目标 MySQL 实例错误地打入冷却期。
- 新增动态目标库运行时快照接口，补齐管理员对目标库动态连接池状态的可观测性。

### Tests
- 增加 MySQL 查询超时与动态池状态的后端测试，覆盖超时不进冷却、运行时状态快照、后台 reset 接口和 dashboard 汇总。
- 扩展 AdminDashboard 测试并回归前端构建，验证动态目标库连接池卡片和列表渲染正常。

## v2.8.1 - 2026-04-21

### Changed
- ClickHouse 查询执行改为按所选库复用默认 database 连接，不再依赖查询前显式执行 `USE <db>`，继续保持无状态只读查询模型。
- 为 ClickHouse 的按库查询连接新增空闲回收和缓存上限控制，减少单实例访问多个库时连接池长期常驻。

### Fixed
- 修复查询 ClickHouse 时因内部执行 `USE` 触发 `There is no session or session context has expired` 的问题。
- 让 ClickHouse dashboard 中依赖 `currentDatabase()` 的指标跟随当前选中库执行，不再受 session 上下文影响。

### Tests
- 更新 ClickHouse 查询测试，覆盖默认库连接、不再执行 `USE`、同库连接复用、空闲回收和超量淘汰。
- 新增 ClickHouse dashboard 定向测试，验证 `currentDatabase()` 类查询会透传当前选中库。

## v2.8.0 - 2026-04-20

### Changed
- 将仓库内置查询 skill 的安装与发布流程统一到 SkillHub / ClawHub，README、安装脚本和 skill 名称同步切换为 `jsw-db-query`。
- 新增 `scripts/publish-skillhub-skill.sh`，便于直接发布仓库内的 SkillHub 查询 skill 包。

### Fixed
- 修复 MSSQL 只读查询在批处理首句包含 `USE` 时被 guard 拦截的问题，现支持配合标量 `DECLARE/SET`、CTE 和子查询执行，并按实际生效库记录查询审计。

### Tests
- 增加 MSSQL 批处理解析与只读校验测试，覆盖 `USE`、CTE、子查询放行以及表变量、`SELECT INTO` 拒绝场景。
- 补充服务层测试，验证 MSSQL 查询执行时会把实际生效库同步到执行参数、查询日志和目标表审计。

## v2.7.8 - 2026-04-20

### Fixed
- 修复 ClickHouse 查看表结构时错误按 MySQL 风格列名读取 `system.columns` 结果，导致 `/database/columnslist/...` 返回 `Result has no column with name 'Field'` 的问题。

### Changed
- 将 ClickHouse 表结构查询结果显式映射为 `column_name`、`column_type`、`column_comment` 字段，并按 `Nullable(...)` 类型推导列是否可空。

### Tests
- 为 ClickHouse 增加表结构查询定向测试，覆盖别名映射、Nullable 判定、空备注兜底和资源释放。

## v2.7.7 - 2026-04-20

### Fixed
- 修复 ClickHouse 查询因 JDBC 仅支持 `TYPE_FORWARD_ONLY` 而报错的问题，恢复工作台 SQL 执行能力。
- 修复动态数据库访问中的内存与资源滞留问题，避免限流窗口、动态连接池和 dashboard 缓存长期累积。

### Changed
- 将 ClickHouse 查询结果读取改为 forward-only 流式遍历，并保留现有“结果被截断”提示语义。
- 为动态数据库操作增加显式资源失效机制，后台更新或删除服务器配置后会主动清理对应缓存与连接资源。
- 为 PostgreSQL 按库连接池增加空闲回收和缓存上限，降低长时间运行后的连接池驻留规模。
- 为内存限流窗口和 dashboard 缓存增加过期清理，减少历史 key 和过期数据常驻内存。

### Tests
- 增加 ClickHouse 查询兼容测试、限流清理测试，以及动态资源失效与 PostgreSQL 空闲池回收测试。

## v2.7.6 - 2026-04-19

### Fixed
- 修复 Passkey / WebAuthn 注册在公网 HTTPS 站点下因 `PUBLIC_HOST` 示例仍为 `http://...` 导致的 origin 校验失败问题。
- 修复 WebAuthn 凭证仓储在查不到凭证或用户名时抛出空指针的问题，改为返回空结果参与认证失败分支处理。

### Changed
- 增强 Passkey 注册与登录失败日志，输出配置的 relying party domain、host、allowed origins 和原始异常信息，便于快速定位域名配置错误。
- 优化 Passkey 注册失败返回文案，origin mismatch 时直接提示检查 `PROJECT_HOST` 是否与浏览器访问地址完全一致。
- 更新本地与 Kubernetes 部署示例中的 `PUBLIC_HOST` 为 `https://...`，并在部署文档中补充推荐配置示例。

### Tests
- 增加 `MyCredentialRepository` 单测，覆盖空凭证、空用户名和正常凭证构建场景。
- 增加 `WebAuthService.register` 的 origin mismatch 失败测试，验证返回消息可直接指导修复配置。

## v2.7.5 - 2026-04-17

### Fixed
- 修复 WebAuthn / Passkey 在 Kubernetes 多 Pod 部署下因请求落到不同实例而导致的登录失败问题。
- 修复 Passkey 绑定流程对单实例内存状态的依赖，避免注册请求跨 Pod 时失效。

### Changed
- 将 WebAuthn 登录与注册流程的 pending request 从服务进程内存改为 MySQL 共享一次性存储，并统一增加过期和消费校验。
- 补充初始化 SQL 与升级脚本，新增 `webauthn_request_tb` 用于共享保存临时 challenge 请求。

### Tests
- 增加 WebAuthn 服务层测试，覆盖共享存储、跨实例消费、一次性失效和空 key 校验场景。

## v2.7.3

### 后台服务器管理
- 修复服务器管理页点击 `测试` 后前端无任何反馈的问题，补齐请求失败、接口失败和网络异常场景下的提示链路。
- 服务器列表新增“最近测试”状态展示，支持查看最近一次测试结果、测试时间以及失败详情。
- 新增/编辑服务器弹窗中的 `连接...` 按钮改为成功轻提示、失败详情弹窗，并增加独立 loading 状态避免和行内测试互相干扰。

### 前端体验
- 优化管理台通用提示弹窗的长文本换行展示，便于直接查看数据库连通性错误详情。

## v2.7.2

### k8s Probe
- 新增 `GET /livez` 轻量存活探针接口，仅用于确认应用进程仍可响应 HTTP 请求。
- 新增 `GET /readyz` 就绪探针接口，仅检查主数据库连通性，不再受动态目标库状态影响。
- 保留现有 `GET /health` 作为业务巡检接口，继续用于人工排障和全量动态数据库健康检查。

### 部署
- 调整 `deploy/k8s/base/deployment-server.yaml`，将 `livenessProbe` 切换到 `/livez`、`readinessProbe` 切换到 `/readyz`。
- 新增 `startupProbe`，降低启动阶段因探针过早介入导致的误重启风险。

### 测试
- 增加 probe 服务与 HomeController 的定向测试，验证 `/livez`、`/readyz` 和 `/health` 的 HTTP 语义分离。

## v2.7.1

### 版本同步
- 统一左上角版本号的展示规则，前端展示时只补一个 `v` 前缀，避免出现 `vv2.7.0` 之类的重复前缀。
- `/version` 接口返回前先做版本归一化，无论部署传入 `2.7.1` 还是 `v2.7.1`，页面都会稳定显示为 `v2.7.1`。
- 将后端默认版本来源改为 `server/pom.xml` 的项目版本，避免 `application.yml` 中的旧默认值与发布 tag 脱节。

### 测试
- 增加版本归一化单元测试，并回归前端构建，确认版本展示改动不会影响现有页面打包。

## v2.7.0

### 工作台 Dashboard
- 在工作台右下区域新增 `Dashboard / 查询结果` 双页签，选库后自动展示数据库 dashboard，执行 SQL 后自动切回查询结果。
- 新增工作台专用 dashboard 接口，按实例和数据库分层缓存 10 分钟，并支持手动刷新。
- dashboard 统一采用只读 SQL 拉取实例信息、数据库信息和其他可观测指标，失败时按卡片局部降级显示。

### 数据库兼容
- 补齐工作台 dashboard 对 `mysql`、`mariadb`、`postgresql(pgsql)`、`mssql`、`clickhouse` 的分库型实现。
- 新增数据库类型归一化逻辑，兼容历史 `pgsql`、`mssql_druid`、`clickhouce` 等旧值。
- 后台服务器配置页增加 `mariadb`、`clickhouse` 选项，并统一服务器类型展示文案。

### 测试
- 增加工作台 dashboard 组件测试与数据库类型归一化测试。
- 回归后端基础服务测试，验证 dashboard 缓存行为未破坏现有连接冷却逻辑。

## v2.6.3

### 前端
- 收紧工作台左侧树中表名、视图名、存储过程名的字号，降低长对象名带来的视觉拥挤。
- 调整左侧树对象名为单行展示，超长时不再换行折叠，改为继续向右延伸。

## v2.6.2

### 后端
- 补齐 PostgreSQL 常用元数据接口实现，支持表字段、索引、表列映射、视图与存储过程信息读取。

### 前端
- 修复 PostgreSQL 工作台点表展开时因 `null` 数据触发 `.map()` 导致的白屏问题。
- 为 PostgreSQL 增加默认查表 SQL 生成功能，双击表后自动填入 `SELECT * FROM public.\"table\" LIMIT 100`。
- 为视图、存储过程、字段索引等树节点加载增加空数组/空对象兜底，避免异常响应直接拖垮页面。

### 测试
- 验证前端构建通过，并回归后端定向测试，确认 PostgreSQL 修复未影响现有管理后台与连接冷却逻辑。

## v2.6.1

### 前端
- 修复安全任务页面（重置密码 / OTP 重绑）样式丢失的问题，恢复为与登录页一致的独立卡片布局。
- 重构安全任务页的密码重置与 OTP 绑定界面，避免继续依赖旧的全局 `fieldset` 样式。
- 将安全任务页中的 OTP 输入统一为 6 位独立输入框，支持自动跳转、退格回退和整段粘贴。

## v2.6.0

### 后端
- 将 `/api/backstage/querylog` 调整为游标翻页方案，新增窗口查询和目标表摘要批量聚合，减少一次性返回的大批量日志数据。
- 停止 dashboard 对目标库做实时探活，避免首页统计被不可用数据库拖慢。
- 为动态目标库连接增加失败冷却机制，限制持续重试导致的连接池线程风暴。
- 调整动态 Hikari 数据源配置，关闭不必要的保底空闲连接，并补齐资源释放逻辑。
- 修复 dashboard 热点 SQL 在 `ONLY_FULL_GROUP_BY` 模式下的兼容问题。
- 新增按服务编号回查已保存配置的后台测试接口，避免服务器管理列表因密码脱敏而无法完成连通性校验。
- 首次启动时如果库里没有 `admin` 用户，自动创建管理员账号并将随机密码打印到 `server` 控制台日志中。
- 修复 MySQL `DATETIME` / `DATETIME(3)` 查询结果少 8 小时的问题，恢复为与数据库一致的本地时间展示。

### 前端
- 管理台品牌区改为 `jsw_logo.png`，并统一品牌字样与版本展示。
- 管理台账号管理表格重做为更轻量的卡片表格风格。
- 用户工作台整体按后台视觉语言做轻量化改造，压缩装饰和留白，突出 SQL 编辑与结果查看。
- 登录页重做为独立卡片布局，避免被其它页面样式串改。
- OTP 绑定与验证页面改为 6 位独立输入框，支持自动跳转、退格回退和整段粘贴。
- 当前端接口返回 `not logged in` 或 `401` 时，统一自动跳转到登录页。
- 登录页补充内联错误提示，并增强 Passkey 域名不匹配时的错误说明。
- 用户注销后会自动返回登录页。
- 工作台左侧服务器选择器支持输入筛选，按 `dbServerHost` 或 `dbServerName` 即时匹配待选项。
- 后台服务器管理列表新增行内 `测试` 按钮，直接校验已保存服务的连通性与账号可用性。

### 测试与文档
- 增加后台服务关于连接冷却、临时连接释放和 dashboard 行为的测试覆盖。
- 更新前端 dashboard 测试，覆盖新的实例文案展示。
- 更新初始化说明，改为通过 `docker logs` 获取首次自动创建的管理员随机密码。

## v2.2.4

### 管理台驾驶舱
- 将后台重构为新的 dashboard cockpit 结构。
- 新增首页趋势图、用户排行、热点对象、最近查询等聚合视图。
- 优化管理台整体视觉风格，统一为更现代的卡片化后台界面。

## v2.2.3

### 账号安全
- 新增用户激活链接、安全任务链接、密码重置链接与 OTP 重绑链接能力。
- 管理台开始支持围绕账号激活与安全任务的管理员操作。

### 数据库兼容
- 增强老旧 TLS 场景下的兼容处理，补充相关配置路径。

## v2.2.2

### 运行诊断
- 服务启动时输出 TLS 运行时参数，便于排查数据库连接安全问题。

## v2.2.1

### 发布整理
- 整理 2.2.0 版本后的发布项与依赖状态。

## v2.2.0

### 部署能力
- 增加 Kubernetes 部署脚本和配套清单。
- 补充容器化部署相关文档和环境变量模板。

## v2.1.1

### 技能支持
- 增加 OpenClaw 技能安装脚本与相关引用。

## v2.1.0

### 前端工程
- 刷新前端 lockfile，提升 CI 和依赖安装稳定性。

## v2.0.1

### 前端构建
- 前端迁移到 Vite，重构构建、测试与资源组织方式。

## v2.0.0

### 工程结构
- 将项目拆分为 `front/` 与 `server/` 的前后端结构。
- 增加 Docker / Nginx / Compose 配置，形成完整前后端容器化方案。

### 平台能力
- 新增 Passkey / WebAuthn 登录支持。
- 扩展用户管理、安全状态、权限和数据库审计基础能力。
