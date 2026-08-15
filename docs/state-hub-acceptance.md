# 家庭状态中枢：可执行验收清单

> 本清单先于实现规格建立。每一项均可通过 HTTP、数据库只读查询或受控源文件读取复核；不得以模拟数据代替失败的采集。

## A. 不变量（实施前门禁）

- [ ] 原始事实源不迁移：`family_ledger`、Hermes 用量脚本/平台、汤圆健康与日报原文件仍分别为权威源。
- [ ] 状态库只保存可追溯快照，不保存账本交易的主副本；所有快照均有 `source`、`observed_at`、`freshness`。
- [ ] 状态页没有创建、更新、删除 `transaction` 的 API 或 UI 调用；继续使用既有 `/api/transactions` 完成记账。
- [ ] 在 <=767px 下，四域卡片可纵向阅读，状态、来源与更新时间无需横向滚动。
- [ ] 采集失败时显示 `unavailable` 或 `stale` 和最近一次成功快照，绝不显示补零、估算值或“刚刚更新”。
- [ ] 不修改既有未提交的 `docker-compose.yml`、`frontend/nginx.conf`、`frontend/src/views/AddPage.vue`，不跟踪 `frontend/.htpasswd`。

## B. 采集与 API 验收

- [ ] 财务采集器仅通过现有只读 `GET /api/transactions`、`/api/stats/*`、`/api/categories`、`/api/users` 或数据库只读账户读取；金额语义保持正收入、负支出。
- [ ] GPT/Codex 采集器对当前窗口、重置时间、计划类型和采集错误保留原始来源标识；凭证不写入状态库、日志或 HTTP 响应。
- [ ] DeepSeek 采集器保留平台总览与按 API Key 的模型、输入 token、输出 token、请求数、费用；按 Key 导出失败时该维度为 `unavailable`，不能退化为伪造拆分。
- [ ] 汤圆今日卡片由当天日报优先，健康总览/体重时间线补充趋势与里程碑；缺当天日报时标 `stale` 并显示最后文件日期。
- [ ] 每个域的响应同时返回数值/状态、`source`、`observedAt`、`freshness`、`errorCode`（失败时）和 `nextRefreshAt`。
- [ ] 未配置或不可达的数据源返回 HTTP 200 的显式状态对象，不把“无数据”伪装成 0，也不以 5xx 让整页失败。

## C. 快照、迁移与安全验收

- [ ] 新状态表仅包含快照/采集执行元数据；没有账本交易写入、外键迁移或原始文件内容复制。
- [ ] 每一条 DDL 使用新的增量迁移脚本，含回滚说明或兼容策略；本阶段不执行 DDL。
- [ ] 快照保留期、去重键和幂等写入策略经过测试：同一域同一观测时间重复采集不会产生重复可见数据。
- [ ] `/api/state/*` 的 JSON 仍使用项目统一 `ApiResult` 包装；现有路由和响应字段不变。
- [ ] Basic Auth 仍在 nginx 前端入口生效；后端 8080 不新增公网暴露、第三方身份认证或 secret 响应字段。

## D. 实装回归验收命令

```bash
# 只读基线：服务与既有账本 API
cd /home/vonlohengramm/projects/family-ledger
docker compose ps
curl -fsS 'http://127.0.0.1:8080/api/transactions?page=1&size=1'
curl -fsS 'http://127.0.0.1:8080/api/stats/monthly?startDate=2026-08-01&endDate=2026-08-31'

# 后续实现后：构建与状态 API（示例，日期不应硬编码在实现中）
cd backend && mvn test && mvn package -DskipTests
curl -fsS 'http://127.0.0.1:8080/api/state/overview'
```

只有当 A–C 全部满足、D 的现有基线仍通过时，状态中枢功能才可进入用户验收。
