# 财务状态聚合器 · TDD 证据（t_5d7706ee）

> 任务：Implement financial status aggregation from ledger and assets
> 契约：`docs/state-snapshot-contract.md`（冻结版，3512c32；本任务按 §3 续登错误码、按 §1 续登财务子源）
> 日期：2026-08-16（Asia/Shanghai）
> 前置依赖：t_1653a6d3（快照存储层）、t_1ceaa2df（K2 契约冻结）

## 1. 聚合规则（新增行为）

财务域快照在保留 `month` / `monthlyStats`（`ledger-service:monthlyStats` 原样）的基础上，
data 新增四个子快照，各自携带 `status/source/observedAt/freshUntil/error`：

| 子快照 | 数据源 | 聚合规则 |
|---|---|---|
| `cashBalance` | `ledger-service:monthlyStats` | `TransactionService.monthlyStats(null,null,null)` 当月 income/expense/net + 全量累计净额 cumulativeNet |
| `portfolioValue` | `family-assets:家庭资产.md` | `FamilyAssetParser` 解析 爸爸/妈妈/家庭共有 三组小计与合计；文件 mtime 作 observedAt，TTL 86400s |
| `recentTxHealth` | `ledger-service:transactions` | lastTxDate（list 第1条）、last7dCount（近7日 count）、unsettledCount（新只读 `countUnsettled`） |
| `autoTrade` | `ledger-service:auto-transactions` | `AutoTransactionService.listActive`（is_active=1）activeCount + 最近 nextRunDate |

失败隔离：单一子源失败仅置该子快照 UNAVAILABLE（`FINANCE_SOURCE_UNAVAILABLE` /
`ASSET_SOURCE_UNAVAILABLE` / `ASSET_PARSE_FAILED` / `AUTO_TRADE_SOURCE_UNAVAILABLE`）并携带原因；
绝不伪造 0 余额；仅当全部子快照均不可用时期域状态为 UNAVAILABLE。

## 2. RED 证据（先写失败测试）

新增四个测试文件，此时 `AutoTransactionService`、`AutoTransactionServiceImpl` 尚不存在，
`FinanceStateCollector` 无 (TransactionService, AutoTransactionService, Path) 构造器，编译失败：

```
$ cd backend && mvn -q test -Dtest='FinanceStateCollectorTest,FamilyAssetParserTest,AutoTransactionServiceImplTest,TransactionServiceImplCountUnsettledTest'
[ERROR] COMPILATION ERROR :
[ERROR] FinanceStateCollectorTest.java:[5,32] cannot find symbol
[ERROR]   symbol:   class AutoTransactionService
[ERROR]   location: package com.familyledger.service
[ERROR] FinanceStateCollectorTest.java:[38,13] cannot find symbol
[ERROR]   symbol:   class AutoTransactionService
[ERROR] AutoTransactionServiceImplTest.java:[6,37] cannot find symbol
[ERROR]   symbol:   class AutoTransactionServiceImpl
[ERROR]   location: package com.familyledger.service.impl
```

→ RED 成立：cannot find symbol（缺失服务/构造器，即新聚合规则未实现）。

## 3. GREEN 证据（最小实现）

新增 `AutoTransactionService(+Impl)`、`FamilyAssetParser`、`TransactionService.countUnsettled`，
重写 `FinanceStateCollector` 为四子快照聚合器：

```
$ mvn -q test -Dtest='FinanceStateCollectorTest,FamilyAssetParserTest,AutoTransactionServiceImplTest,TransactionServiceImplCountUnsettledTest'
Tests run: 17, Failures: 0, Errors: 0, Skipped: 0
```

→ GREEN 成立：17/17 全绿（11 个聚合器用例 + 4 个解析器用例 + 2 个服务用例）。

## 4. 真实源轮询测试（契约 §5.3）

`FamilyAssetParserTest.parsesRealAssetFileWhenPresent` 直接读取真实
`~/records/财产/家庭资产.md`（源存在才执行，不存在 assumeTrue 跳过，绝不伪造）：

```
$ mvn -q test -Dtest=FamilyAssetParserTest
Tests run: 5, Failures: 0, Errors: 0, Skipped: 0   ← 含真实文件解析，byOwner 三组齐全、total>0
```

## 5. 失败路径测试（契约 §5.4）

`FinanceStateCollectorTest` 覆盖：账本聚合抛错→cashBalance UNAVAILABLE 且 value 为空（无 0 值）、
资产文件缺失→ASSET_SOURCE_UNAVAILABLE、资产文件坏格式→ASSET_PARSE_FAILED、
交易健康度失败不影响 cashBalance（域内隔离）、autoTrade 失败不影响整域、
全部子源失败→域 UNAVAILABLE(FINANCE_SOURCE_UNAVAILABLE)。

## 6. 全量回归（契约 §5.5）

```
$ cd backend && mvn test                    # Java 全量
Tests run: 69, Failures: 0, Errors: 0, Skipped: 0
$ mvn -q package -DskipTests                # 打包通过
target/family-ledger-backend-1.0.0.jar
$ python3 scripts/test_migrate_state.py     # Python 迁移测试
Ran 9 tests ... OK
$ python3 scripts/migrate_state.py --check  # 迁移门禁
[OK] 迁移校验通过
```

## 7. 生产财务表零改动（验收脚本）

聚合只走 `TransactionService` 只读服务聚合与资产文件只读解析，不执行任何 DDL/DML：

```
== 执行前后生产库基线 ==
transaction: count=4791, net=-3833.63, min_id=1, max_id=4806   ← 前后完全一致
auto_transaction: count=10                                      ← 未改动
state_snapshot: 表存在（既有 V3 迁移，本次无新 DDL）
```

→ 未迁移/复制/创建任何交易；未提交任何交易明细 dump；快照仅存聚合数值。

## 8. 结论

- 财务状态从既有 family_ledger（monthlyStats/list/count/countUnsettled）与资产文件/自动交易源聚合，
  未产生第二套账本事实源。
- 每个子快照带 source/observedAt/freshUntil/status；不可用数据以 error{code,reason} 呈现，绝不显示为当前余额。
- 新增错误码已按契约 §3 续登：`ASSET_SOURCE_UNAVAILABLE` / `ASSET_PARSE_FAILED` / `AUTO_TRADE_SOURCE_UNAVAILABLE`。
