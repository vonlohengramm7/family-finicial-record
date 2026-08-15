# 家庭状态中枢 · K2 快照契约（冻结版）

> 状态：**已冻结（FROZEN）** — K1 范围验收后，本契约对 K2 及其所有子任务生效。
> 冻结日期：2026-08-15（Asia/Shanghai）
> 上游依据：`docs/family-state-hub-spec.md`、`docs/state-hub-acceptance.md`、K1 实现（`3f769cb` + 工作树中 t_19fc0b96 / t_803b362f 的后续修订与测试证据）。
> 下游任务（t_1653a6d3、t_5d7706ee、t_7a57eae3、t_bb50400b、t_496214b4）必须遵守本契约，不得偏离。

---

## 0. K1 范围冻结声明

K1 已交付并通过验收（证据见第 5 节）：

- 只读 `/api/family-status` 与 `/api/family-status/{domain}`（GET 双端点，ApiResult 包装）
- 可重复迁移 `V3__create_state_snapshot.sql` + 受控运行器 `scripts/migrate_state.py`（--check/--apply，安全门禁拒绝破坏性 SQL 与财务表引用）
- 快照存储层 `StateSnapshotService`（upsert 幂等、过期→stale、失败保留最后成功值）
- 四域采集器：Finance / Codex / DeepSeek / Baby
- 出口兜底脱敏 `SecretSanitizer`（sk-/ghp_/Bearer/token 等一律 `***`）

**冻结含义**：以上行为、字段、路由与状态语义为唯一事实源；K2 不得新增第二套字段命名、状态枚举或路由，不得修改既有 `/api/transactions`、`/api/stats/*`、`/api/categories`、`/api/users`。

---

## 1. 已确认可读数据源清单（唯一允许接入）

只允许接入下表已验证可读的真实源；未列入者一律不接入，也不得以零值/示例值伪造。

| 域 domain | source 标识 | 真实采集路径 | 可读性证据（2026-08-15） | TTL |
|---|---|---|---|---|
| `finance` | `ledger-service:monthlyStats` | `TransactionService.monthlyStats`（只读服务聚合，不复制交易） | 账本 API HTTP 200（transactions total=4791、月统计 rows=1）；FinanceStateCollector FRESH | 900s |
| `codex` | `codex-usage-monitor` | 读取 `~/.hermes/data/gpt-plus-usage-state.json`（gpt-plus-usage-monitor.py 每小时 cron 6d9afacaa5ce 写入的快照文件，非直连 API） | 文件存在：`weekly_used_pct=24`、`last_check=2026-08-15 21:07`；session 字段允许为 null | 3600s |
| `deepseek` | `deepseek-platform-api-export` | `deepseek-usage-query.py --date <当日> --by-key`（平台 cost API + export API） | 本机实测 exit 0：`source=api`、cost=3.12、按 Key 明细含模型/输入/输出/请求/费用；METADATA 块含 `gpt_plus_usage` 的须显式忽略（属 codex 域） | 86400s |
| `baby` | `baby-daily-report:<文件名>` | `~/records/baby-daily-reports/daily-logs/YYYY-MM-DD.md`（当天优先，缺则回退最近日报标 stale） | 2026-08-15.md 存在（当日 12:28 修改）；健康总览 health-record.md、体重时间线 weight-timeline.md 均在 | 86400s |
| `baby`（趋势） | `health-record.md` / `weight-timeline.md` | 同目录只读解析，失败仅置该子项 unavailable，不阻断今日卡片 | 文件存在；解析器有 fixture 测试 | 86400s |

**明确不可用（必须返回 unavailable，不得伪造）**：

- DeepSeek **在 Docker 容器内**：不挂载凭证/脚本目录（安全边界，见协调人指示），容器内无凭据可读快照源时保持 `unavailable`；本机宿主机可读脚本输出，不得为了 fresh 而把密钥写入 compose/env/日志。
- 任何未经验证的源、以及需要暴露凭证才能读取的源。
- Codex 直连账号 API（未验证）；仅状态文件是已验证源。

**失败隔离**：单一源失败只影响该域，绝不拖垮 overview 或首页；HTTP 响应、日志、快照与前端不得泄露凭证或 API Key 原文。

---

## 2. (a) 快照字段定义

存储层 `state_snapshot`（迁移已建）与 API 字段一一对应：

| 契约字段 | 存储列 | 类型 | 必填 | 含义 |
|---|---|---|---|---|
| `domain` | `domain` | VARCHAR(32) | ✅ | 域标识：`finance` / `codex` / `deepseek` / `baby` |
| `key` | `snapshot_key` | VARCHAR(128) | ✅ | 域内稳定键；首期统一 `overview`；DeepSeek 按 Key 维度用 `by_api_key:<stable-id>`（绝不含 key 原文） |
| `value` | `payload_json` | LONGTEXT | 条件 | 已脱敏展示数据（JSON）；不得保存 secret/原始全文；失败时可保留最后成功值 |
| `source` | `source` | VARCHAR(256) | ✅ | 面向用户可理解的来源标识（第 1 节），不含本机密钥路径、凭证明文、API Key 前缀 |
| `observedAt` | `observed_at` | DATETIME | 条件 | 来源被成功观测的北京时间（Asia/Shanghai）；无成功观测为 null |
| `freshUntil` | `fresh_until` | DATETIME | 条件 | `observedAt + TTL`；无观测为 null |
| `status` | `status` | VARCHAR(16) | ✅ | 三值枚举，见第 3 节 |
| `error` | `error_code` + `error_message` | VARCHAR(64)+VARCHAR(500) | 条件 | 失败时携带；成功为 null。稳定错误码 + 脱敏的面向用户摘要 |

唯一键 `uk_state_snapshot_domain_key (domain, snapshot_key)`：同域同 key 幂等 upsert，重复采集不产生重复可见快照。索引 `idx_state_snapshot_domain_fresh_until (domain, fresh_until)` 支持过期扫描。

**迁移规则**：新增 DDL 只能以新的可重复执行增量脚本（`V4__*.sql`）引入；必须通过 `scripts/migrate_state.py --check` 门禁；不得引用/修改 `transaction`、`category`、`user`、`auto_transaction`、`project`、`energy_log`、`vehicle_expense`。

---

## 3. (b) status / error 分类法

`status` 为**三值枚举**（Java `StateStatus`：`FRESH` / `STALE` / `UNAVAILABLE`），对应任务语义：

| 契约语义 | 枚举值 | 触发条件 | 展示规则 |
|---|---|---|---|
| ok | `FRESH` | 最近一次成功采集 ≤ TTL | 展示最新值；`error=null` |
| stale | `STALE` | 仍有最后成功值，但已过 `freshUntil`；或本轮刷新失败但存在旧快照 | **保留 data/observedAt/freshUntil**；可带 `error{code,reason}` 说明刷新失败原因 |
| unavailable | `UNAVAILABLE` | 无成功快照；源未配置/不可读/权限不足/格式无法解析/采集失败/超时；未知域 | `data={}`；**必须**带 `error{code,reason}` |
| error | （非独立枚举值） | 任何失败都以 `error{code, reason}` 结构化携带 | 与 stale/unavailable 同时出现；不新增第四种 status 值 |

**错误码登记（已有，K2 新增须续登）**：

- `SNAPSHOT_MISSING` — 尚无成功快照
- `UNKNOWN_DOMAIN` — 不支持的域
- `INTERNAL_ERROR` — 快照存储读取失败
- `CODEX_SOURCE_UNAVAILABLE` / `CODEX_SNAPSHOT_EXPIRED`
- `DEEPSEEK_TIMEOUT` / `DEEPSEEK_SOURCE_UNAVAILABLE` / `DEEPSEEK_PARSE_FAILED`
- `BABY_REPORT_MISSING` / `BABY_REPORT_PARSE_FAILED` / `TODAY_REPORT_MISSING`（回退最近日报时）
- `FINANCE_SOURCE_UNAVAILABLE`

错误摘要（reason）必须脱敏、面向用户；不得包含堆栈、路径、凭证或 Key 前缀。

---

## 4. (c) `/api/family-status` 响应形状

统一 `ApiResult` 包装：`{code, message, data, timestamp}`（沿用项目现有包装，不裸返回）。

### GET `/api/family-status`（四域整体）

```json
{
  "code": 200,
  "message": "success",
  "timestamp": "2026-08-15T21:30:00",
  "data": {
    "finance": {
      "domain": "finance",
      "status": "FRESH",
      "source": "ledger-service:monthlyStats",
      "observedAt": "2026-08-15T21:15:00",
      "freshUntil": "2026-08-15T21:30:00",
      "data": { "month": "2026-08", "monthlyStats": [] },
      "error": null
    },
    "codex": {
      "domain": "codex",
      "status": "STALE",
      "source": "codex-usage-monitor",
      "observedAt": "2026-08-15T20:00:00",
      "freshUntil": "2026-08-15T21:00:00",
      "data": { "weeklyUsedPercent": 24, "lastCheck": "2026-08-15 21:07" },
      "error": { "code": "CODEX_SNAPSHOT_EXPIRED", "reason": "Codex 用量采集快照已过期" }
    },
    "deepseek": {
      "domain": "deepseek",
      "status": "UNAVAILABLE",
      "source": "deepseek-platform-api-export",
      "observedAt": null,
      "freshUntil": null,
      "data": {},
      "error": { "code": "DEEPSEEK_SOURCE_UNAVAILABLE", "reason": "容器内无凭证可读快照源" }
    },
    "baby": {
      "domain": "baby",
      "status": "FRESH",
      "source": "baby-daily-report:2026-08-15.md",
      "observedAt": "2026-08-15T12:28:00",
      "freshUntil": "2026-08-16T12:28:00",
      "data": { "reportDate": "2026-08-15", "todaySummary": "…", "todos": ["…"], "trends": {} },
      "error": null
    }
  }
}
```

### GET `/api/family-status/{domain}`（单域）

`data` 为单个域对象（同上任一形状）。未知域返回 HTTP 200 + `status=UNAVAILABLE` + `error.code=UNKNOWN_DOMAIN`（不得 5xx）。

**强制约定**：

- 每个条目**必须**含 `source`、`observedAt`、`freshUntil`、`status`；失败时含 `error{code, reason}`。
- 时间格式：`yyyy-MM-dd'T'HH:mm:ss`（北京时间本地时间，无时区后缀）。
- **只读**：仅 GET；不得新增 POST/PUT/DELETE；`/api/family-status` 不写任何交易。
- 所有字符串字段经 `SecretSanitizer` 兜底脱敏：`sk-*`/`ghp_*`/`Bearer *`/token/authorization 等值一律 `***`；DeepSeek 按 Key 只显示 `api-key-N` 稳定标签，绝不回传 key 原文或前缀。

---

## 5. (d) 测试与证据工作流（强制 TDD）

每个新增采集器/聚合规则/API 行为都必须走以下流程并在提交中保留证据：

1. **RED**：先写失败测试（断言期望行为/字段/状态），运行并确认失败；记录失败证据（命令 + 失败摘要，如 `cannot find symbol` / `expected STALE got FRESH` / `ModuleNotFoundError`）。
2. **GREEN**：实现最小代码，运行同一测试确认通过；记录通过证据。
3. **真实源轮询测试**：对每个已确认源（第 1 节）有真实轮询测试（真实文件/脚本），源不可用时 `assumeTrue` 跳过，绝不伪造。
4. **失败路径测试**：每个采集器必须有 unavailable/error 路径测试（缺文件、超时、解析失败、无凭证）。
5. **全量回归**：`cd backend && mvn -q test` 全绿 + `mvn -q package -DskipTests` 通过；Python 侧 `python3 scripts/test_migrate_state.py` 通过。
6. **提交纪律**：只提交本任务必要代码/迁移/测试/文档；不含 `.htpasswd`、真实凭证、数据库 dump、API Key；不得覆盖工作树既有未提交改动；既有账本 API 回归保持（transactions/stats/categories/users HTTP 200）。

---

## 6. 冻结变更记录

| 日期 | 变更 |
|---|---|
| 2026-08-15 | 契约初版冻结：字段、三值状态枚举、API 形状、TDD 工作流、数据源清单（finance/codex/deepseek/baby） |
