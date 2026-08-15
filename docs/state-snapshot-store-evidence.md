# 状态快照存储层 + 可重复迁移 · TDD 证据（t_1653a6d3）

> 任务：Implement repeatable status snapshot store and migrations
> 契约：`docs/state-snapshot-contract.md`（冻结版，3512c32）
> 日期：2026-08-15（Asia/Shanghai）

## 1. RED 证据（先写失败测试）

新增 `backend/src/test/java/com/familyledger/state/StateSnapshotServiceTest.java` 六个按
domain/key 参数化的存储用例（store 显式 key、upsert 幂等、read-latest-by-domain/key、
未知 key→SNAPSHOT_MISSING、freshUntil 过期→STALE、overview 默认键向后兼容），
此时 `StateSnapshotService` 尚无 `store(String,String,StateDomainResponse)` /
`get(String,String)`，编译失败：

```
$ cd backend && mvn -q test -Dtest=StateSnapshotServiceTest
[ERROR] COMPILATION ERROR :
[ERROR] StateSnapshotServiceTest.java:[62,16] method store in class
        com.familyledger.state.StateSnapshotService cannot be applied to given types;
  required: java.lang.String,com.familyledger.state.StateDomainResponse
  found:    java.lang.String,java.lang.String,com.familyledger.state.StateDomainResponse
[ERROR] StateSnapshotServiceTest.java:[101,45] method get in class
        com.familyledger.state.StateSnapshotService cannot be applied to given types;
  required: java.lang.String
  found:    java.lang.String,java.lang.String
```
→ RED 成立：cannot find symbol（missing store(domain,key)/get(domain,key)）。

## 2. GREEN 证据（最小实现）

`StateSnapshotService` 增加 `store(domain, key, result)` / `get(domain, key)`，
原单参方法委托默认键 `DEFAULT_SNAPSHOT_KEY="overview"`（向后兼容）：

```
$ mvn -q test -Dtest=StateSnapshotServiceTest
Tests run: 7, Failures: 0, Errors: 0, Skipped: 0
```
→ GREEN 成立：7/7 全绿（1 个原有 + 6 个新增）。

## 3. 全量回归（契约 §5.5）

```
$ mvn -q test                                   # Java 全量
Java total -> tests: 51 failures: 0 errors: 0 skipped: 0
$ mvn -q package -DskipTests                    # 打包通过
target/family-ledger-backend-1.0.0.jar
$ python3 scripts/test_migrate_state.py         # Python 迁移测试
Ran 9 tests ... OK
$ python3 scripts/migrate_state.py --check      # 迁移门禁
[OK] 迁移校验通过: V3__create_state_snapshot.sql
```

## 4. 迁移可重复执行 + 财务表零改动（验收脚本）

`scripts/verify_migration_twice.py`（临时库 `family_ledger_state_mig_test` + 生产基线对比）：

```
== 2. 第一次执行迁移（--apply）==  第一次执行 ok = True
== 3. 第二次执行迁移（--apply）==  第二次执行 ok = True     ← 可重复执行，无报错
== 4. 校验表结构与字段 ==          字段数: 12 缺字段: 无；state_snapshot 表数量: 1
== 4.5 快照持久化往返 ==           写→读回字段核对 OK；同 domain+key 仅 1 行（唯一键）
== 5. 生产库基线（执行后）==
   before == after:
   {count: 4791, net: -3833.63, expense: -1236447.08, income: 1232613.45, min_id: 1, max_id: 4806}
   生产库基线一致: PASS                              ← transaction 零改动
[PASS] 迁移可连续执行两次不报错；字段完整；生产财务表零改动
```

## 5. 结论

- 存储层：write-snapshot（domain+key upsert 幂等）、read-latest-by-domain/key、
  过期→STALE、刷新失败保留最后成功值并置 STALE、缺失→UNAVAILABLE(SNAPSHOT_MISSING)。
- 迁移层：`V3__create_state_snapshot.sql`（CREATE TABLE IF NOT EXISTS 幂等守卫，
  9 个契约字段 + id/时间戳，唯一键 uk_state_snapshot_domain_key）+ 受控运行器
  `scripts/migrate_state.py`（--check/--apply，安全门禁拒绝破坏性 SQL 与财务表引用）。
- 不复制交易主数据：state_snapshot 仅存聚合快照；生产 `transaction` 基线前后一致。
