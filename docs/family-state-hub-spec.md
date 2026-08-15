# 家庭状态中枢（Family State Hub）实施规格

- 状态：设计已审计，尚未创建功能、依赖或数据库迁移
- 适用仓库：`/home/vonlohengramm/projects/family-ledger`
- 审计时间：2026-08-15 20:13–20:15（Asia/Shanghai）
- 验收清单：[`state-hub-acceptance.md`](state-hub-acceptance.md)

## 1. 目标与非目标

家庭状态中枢是一个只读聚合视图：让现有家庭账本、GPT/Codex 额度、DeepSeek 按 API Key 用量、汤圆当日状态/趋势/待办以统一的“值 + 来源 + 观测时间 + 新鲜度”呈现。

它不是新的事实库，也不是第二个记账入口。原始交易继续在 `family_ledger.transaction`，用量继续由既有平台/API 与 Hermes 脚本采集，汤圆资料继续在 `~/records/` Markdown 日志中。状态中枢仅保存需要跨刷新呈现的、可重建的快照。

本期明确不做：

- 不修改或迁移既有 `transaction`、`category`、`user`、`auto_transaction` 数据。
- 不在状态页创建、编辑或删除账本交易；记账仍走 `/api/transactions`。
- 不复制凭证、API Key、Basic Auth 哈希、原始日记全文或健康档案全文到状态库。
- 不接入未经实际验证可采集的数据源，也不以零值/示例值掩盖失败。
- 不增加重型中间件、缓存服务、身份系统或外部队列。

## 2. 审计事实（当前基线）

### 2.1 技术与部署

| 项 | 实际结果 | 证据 |
|---|---|---|
| 后端 | Java 17、Spring Boot 3.2.5、MyBatis-Plus 3.5.7、Maven | `backend/pom.xml` |
| 前端 | Vue 3、Vite 8、Element Plus、ECharts、Axios | `frontend/package.json` |
| 数据库 | 本机 MySQL `family_ledger`；应用配置指向 `127.0.0.1:3306` | `backend/src/main/resources/application-template.yml` |
| ORM | 仅 MyBatis-Plus；实体显式 `@TableName` | `backend/src/main/java/com/familyledger/entity/Transaction.java` |
| 容器 | `family-ledger-backend` 与 `family-ledger-frontend` 均运行中；映射 8080、5175 | `docker compose ps` 实测 |
| 前端认证 | nginx 对 5175 返回 `401` 和 `WWW-Authenticate: Basic realm="家庭账本"` | `curl -D - http://127.0.0.1:5175/` 实测 |
| 后端 API | 8080 直接访问未见 Basic Auth；这是本机 API 入口，不应扩大暴露面 | 2026-08-15 curl 实测 |

审计时工作树已有未提交变更：`docker-compose.yml`、`frontend/nginx.conf`、`frontend/src/views/AddPage.vue`，并有未跟踪的 `frontend/.htpasswd`。本规格不触碰这些文件，且不得将 `.htpasswd` 纳入版本控制。

### 2.2 既有 API（兼容基线）

所有已测成功接口统一返回 `ApiResult`：`{code,message,data,timestamp}`；交易列表的 `data` 为分页对象。

| 资源 | 已有路由 | 备注 |
|---|---|---|
| 交易 | `GET/POST /api/transactions`、`GET/PUT/DELETE /api/transactions/{id}`、`POST /api/transactions/batch` | `GET` 支持用户、分类、日期、关键词、金额范围等筛选 |
| 统计 | `GET /api/stats/monthly`、`/by-category`、`/by-user` | 均支持现有日期范围；实测月统计响应含 `month,total,expense,income` |
| 分类 | `GET/POST /api/categories`、`PUT/DELETE /api/categories/{id}` | 两级分类 |
| 用户 | `GET /api/users` | 当前实测两个家庭用户 |
| 专项 | `GET/POST /api/projects`、`GET/PUT/DELETE /api/projects/{id}`、`GET /api/projects/{id}/transactions`、`/stats` | 保持原路径不变 |

实测基线：`GET /api/transactions?page=1&size=1`、`/api/categories`、`/api/users`、`/api/stats/monthly?startDate=2026-08-01&endDate=2026-08-31` 都返回 HTTP 200。

### 2.3 数据库边界

现有库包含 `transaction`、`category`、`user`、`auto_transaction`、`project`，另有独立的 `energy_log`、`vehicle_expense`。`transaction` 使用 `DECIMAL(12,2)`、有符号金额、北京时间日期/时间和 `cash_settled`，并已有 `idx_user_date`、`idx_category`、`idx_project`、`idx_date` 索引。状态中枢不得把 `energy_log` 或 `transaction` 误当成彼此的替代事实源。

仓库未发现现有 SQL 迁移目录或 `V*__*.sql`。如进入实现，先在项目约定的位置补充迁移机制，并使用新的增量脚本；本任务不执行 DDL。

## 3. 四域来源清单与新鲜度合同

所有域返回都必须使用下列统一状态枚举：

- `fresh`：最近一次成功采集未超过该域 TTL。
- `stale`：最近一次成功值仍可展示，但超过 TTL 或本轮采集失败。
- `unavailable`：没有成功快照、来源未配置、权限不足、格式无法解析或采集明确失败。
- `loading`：前端初次请求未完成；不可替代任何最终状态。

| 域/信息 | 权威源与采集路径 | 实际可采集性 | 刷新建议 | 失败/过期规则 |
|---|---|---|---|---|
| 财务概览、近期开支、预算进度 | 本机账本 API：`/api/transactions`、`/api/stats/*`、`/api/categories`、`/api/users`；只读数据库可作为同口径核验，不是常规 UI 依赖 | **available**：2026-08-15 已成功 curl | 页面进入时拉取；手动刷新；聚合快照 15 分钟 TTL | API 失败则展示最后快照为 `stale`；无快照为 `unavailable`，不得显示 0 |
| GPT/Codex 窗口额度/重置/计划 | `~/.hermes/scripts/gpt-plus-usage-monitor.py` 调 Hermes `agent.account_usage` 的 Codex usage URL；状态文件 `~/.hermes/data/gpt-plus-usage-state.json` 仅为采集器自身状态 | **conditionally available**：采集代码与每小时 cron（`6d9afacaa5ce`）存在；本次不直接执行，避免其写入状态文件并触发 VPN 路由切换 | 已有每小时第 7 分钟采集；中枢读取最近一次成功快照，或按用户手动刷新触发受控采集 | 凭证/网络/geo 限制失败时保留最后成功值为 `stale`；无成功值为 `unavailable`，响应不含 token |
| DeepSeek 平台总览与按 API Key 明细 | `~/.hermes/scripts/deepseek-usage-query.py --by-key`：平台 cost API + export API，SQLite 仅用于其他统计回退 | **available**：2026-08-15 实测 exit 0，输出 `source=api`、按 API Key 导出 metadata；已有每天 09:00 no-agent cron（`4ced8a34d2fa`） | 每天 09:00 后读取快照；手动刷新可即时执行一次受控采集 | 平台总览或按 Key export 分别失败则对应字段 `unavailable`；不得以 SQLite 总 token 伪造按 Key、模型、请求数或费用 |
| 汤圆今日 | `~/records/baby-daily-reports/daily-logs/YYYY-MM-DD.md`，当天文件优先 | **available**：2026-08-15 文件存在并于当天 12:28 修改 | 页面进入读取；每日首次访问刷新；手动刷新 | 当天缺文件，回退最近日报并标 `stale`、明确最后文件日期；不能凭旧报告写“今天” |
| 汤圆趋势/里程碑 | `~/records/汤圆/health/health-record.md`、`~/records/汤圆/health/weight-timeline.md` | **available**：健康总览 2026-08-15 修改，体重时间线 2026-08-11 修改 | 每日一次或源文件 mtime 变更时 | 读取/解析失败为 `unavailable`；单一来源过期不阻断其他汤圆卡片 |
| 汤圆待办 | 健康总览中明确的计划项目与当天日报中的待办语义；首期采用受控解析的显式条目 | **available with parser scope**：源文件存在；需先定义可识别 heading/checkbox/日期格式 | 每日一次或源文件变更时 | 解析不到显式待办时返回空列表 + `fresh`（不是错误）；格式不可识别则 `unavailable`，不根据模型猜测待办 |

## 4. 数据模型与最小迁移方案

### 4.1 设计原则

状态库可放在现有 `family_ledger` 内，但只能存“导入后可再生”的归一化快照。首期不引入新依赖：Spring Boot + MyBatis-Plus + MySQL 已足够。

推荐单表 `state_snapshot`，避免为四个域提前拆出空壳表：

| 字段 | 类型 | 含义 |
|---|---|---|
| `id` | bigint PK | 自增 ID |
| `domain` | varchar(32) | `finance`、`codex`、`deepseek`、`baby` |
| `snapshot_key` | varchar(128) | 域内稳定键，例如 `overview`、`by_api_key:<opaque-key-id>` |
| `payload_json` | json 或 longtext | 已脱敏的显示数据，不保存 secret/全文原始资料 |
| `source` | varchar(256) | 具体权威源 ID/路径/API 名称 |
| `observed_at` | datetime | 来源被成功观测的北京时间 |
| `freshness` | varchar(16) | `fresh`/`stale`/`unavailable` |
| `expires_at` | datetime | 根据域 TTL 计算 |
| `error_code` | varchar(64) nullable | 稳定错误码，不写凭证/原始异常 |
| `error_message` | varchar(500) nullable | 面向用户的脱敏错误摘要 |
| `created_at` | datetime | 写入时间 |
| `updated_at` | datetime | 最近更新 |

唯一键：`uk_state_snapshot_domain_key (domain, snapshot_key)`。采集过程按该唯一键 upsert，既避免重复可见快照，也允许在失败时只更新状态/错误而保留最后成功 `payload_json` 与 `observed_at`。

### 4.2 迁移最小方案（待实现阶段）

1. 确认项目采用的迁移框架或先以受控 SQL runner 落地；不得直接在生产库手工创建。
2. 新增一份增量 DDL，例如 `V3__create_state_snapshot.sql`（实际版本按现有迁移基线确定）。
3. 仅创建 `state_snapshot` 和唯一索引；不对账本表加列、不回填历史交易、不执行数据迁移。
4. 为 `domain, expires_at` 建索引，支持过期扫描。若 JSON 类型在目标 MySQL 环境的迁移工具兼容性存在疑虑，使用 `LONGTEXT` 并在服务层 JSON 校验。
5. 在迁移说明中给出回滚：删除新表/索引即可；不影响任何原始事实表。

## 5. API 合同

新增路径命名空间为 `/api/state`，全数沿用 `ApiResult` 包装；不得改变第 2.2 节的路由或响应。

| 方法 | 路径 | 用途 | 写入边界 |
|---|---|---|---|
| GET | `/api/state/overview` | 一次返回四域摘要，适合首页 | 只读快照；不触发账本写入 |
| GET | `/api/state/finance` | 财务域详细卡片/趋势 | 只读快照或现有账本只读 API 聚合 |
| GET | `/api/state/codex` | GPT/Codex 当前额度卡片 | 只读快照 |
| GET | `/api/state/deepseek` | 平台总览与按 API Key 维度 | 只读快照 |
| GET | `/api/state/baby` | 今日、趋势、待办 | 只读快照 |
| POST | `/api/state/refresh/{domain}` | 用户显式请求的受控刷新 | 仅 upsert `state_snapshot`；禁止调用交易创建/更新 API |

状态对象最小形状：

```json
{
  "status": "fresh",
  "source": "ledger-api:/api/stats/monthly",
  "observedAt": "2026-08-15T20:15:00+08:00",
  "freshness": {"ttlSeconds": 900, "expiresAt": "2026-08-15T20:30:00+08:00"},
  "data": {},
  "errorCode": null,
  "nextRefreshAt": "2026-08-15T20:30:00+08:00"
}
```

`source` 是面向用户可理解的来源 ID，不含本机密钥路径、凭证、查询参数中的 secret 或 API Key 明文。DeepSeek 若要识别 Key，仅返回已有平台名称或经稳定不可逆映射的显示标签；不可回传 key prefix。

## 6. UI 信息架构（手机优先）

新增“家庭状态”路由，不替换现有仪表盘、记一笔、交易、统计、管理路由。桌面可作为首页入口卡；手机（`<=767px`）为单列、卡片顺序固定：

1. **总览条**：最后总体观测时间；四域的 `fresh/stale/unavailable` 小标识；“刷新”只针对明确选择的域。
2. **财务**：本月收入、支出、结余、预算进度、最近异常/待结算提示；点击“账本明细”仅导航到既有交易列表，不直接编辑。
3. **GPT/Codex**：计划、当前窗口剩余/已用、重置时间、数据采集状态。`unavailable` 时显示原因与最近成功时间。
4. **DeepSeek**：当天/周期费用、token、请求数、按 API Key 卡片；按 Key 不能采集时只显示该分区 unavailable，不掩盖全局状态。
5. **汤圆**：今日摘要、体重/奶量/里程碑趋势、待办。每一段均显示对应源文件和日期；日报缺失时不可把旧条目冠以“今日”。

交互约束：不使用需横向滚动的宽表；数值、状态、源和时间在同一卡片；屏幕阅读器有状态文本，颜色不是唯一信号；加载骨架仅在请求中展示。

## 7. 分阶段实施任务

### Phase 0：契约与测试（先行）

- 将 `state-hub-acceptance.md` 转为自动化 API/服务测试清单：新状态对象必须含 `source/observedAt/freshness`。
- 为每个解析器编写 fixture：当天日报、缺当天日报、损坏文件、DeepSeek 按 Key export 失败、Codex 无凭证、账本 API 失败。
- 每个测试先红后绿；不写页面或生产采集逻辑直到对应测试失败已确认。

### Phase 1：快照基础设施

- 审核迁移框架并提交唯一的新表迁移。
- 实现 `StateSnapshot` entity/mapper/service；测试 upsert、过期转换、失败保留最后成功值及 secret 脱敏。
- 只读核验账本 API 数据映射；不操作 `transaction`。

### Phase 2：四个采集器

- `FinanceStateCollector` 使用现有 API 或同口径只读服务，不复制账本事实。
- `CodexStateCollector` 调用已存在受控脚本/模块接口并安全捕捉 geo、VPN、token 问题。
- `DeepSeekStateCollector` 使用现有 `--by-key` 输出的稳定 metadata/结构化输出，保留平台 API 与 export API 的独立失败状态。
- `BabyStateCollector` 只解析已约定的日报/健康标题，保存摘要、源文件标识和 mtime；解析范围外明确 unavailable。

### Phase 3：API 与前端

- 实现 `/api/state/*` 的只读查询和显式刷新；刷新仅更新快照。
- 实现手机优先的单列状态页，复用现有 Vue/Element Plus；每张卡都显示来源、观测时间、新鲜度。
- 将现有路由保留原样；从状态页跳转账本使用 query 参数，不实现写操作。

### Phase 4：验收与运营

- 执行单元/集成测试、后端构建、前端构建、现有 API 回归和 767px 浏览器检查。
- 在真实源不可达、过期、源格式改变时逐项检查 UI 不会显示伪造数据。
- 确认日志、响应、快照 JSON 和 git diff 均不含 `.htpasswd`、数据库密码、Bearer token 或 DeepSeek API Key。
- 记录各采集器最后成功/失败时间，后续才评估是否需要 cron；首期优先复用现有 09:00 DeepSeek 与每小时 Codex 调度。

## 8. 完成定义

只有以下条件全部满足，功能才算完成：

1. `state-hub-acceptance.md` 全部勾选，且每条有可执行证据。
2. 四域都返回独立状态；任一域失败不拖垮总览，也不被补零。
3. 每一项可见数据都带 `source`、`observedAt`、`freshness`；DeepSeek 仍保留按 API Key 的模型/token/请求/费用维度或明确 unavailable。
4. 状态页对账本交易零写入，原始事实源没有迁移，状态表仅有快照。
5. 现有 `/api/transactions`、`/api/stats/*`、`/api/categories`、`/api/users` 回归成功；手机 767px 可完整阅读。
6. 只提交必要代码、迁移、测试与文档；不包含秘密或本任务开始前的未提交改动。
