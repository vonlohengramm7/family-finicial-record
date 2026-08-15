#!/usr/bin/env python3
"""
验收脚本（t_19fc0b96）：迁移可连续执行两次不报错 + 对财务表零改动
- 在临时库 family_ledger_state_mig_test 上执行迁移两次
- 校验表结构字段完整、两次执行均无异常
- 执行前后对比生产库 family_ledger.transaction 精确基线
- 结束后删除临时库（不影响任何生产数据）
"""
import os
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))

import mysql.connector  # noqa: E402

# DDL 账户凭据仅从环境读取（本地维护账户，不写入任何文件/提交）
DDL_USER = os.environ.get("LEDGER_DB_USER", "debian-sys-maint")
DDL_PASS = os.environ.get("LEDGER_DB_PASS", "")
SCRATCH_DB = "family_ledger_state_mig_test"
PROD_DB = "family_ledger"

EXPECTED_COLUMNS = {
    "id", "domain", "snapshot_key", "payload_json", "source",
    "observed_at", "fresh_until", "status", "error_code",
    "error_message", "created_at", "updated_at",
}


def conn(database=None):
    return mysql.connector.connect(
        host="127.0.0.1", port=3306, user=DDL_USER, password=DDL_PASS,
        database=database, charset="utf8mb4",
    )


def prod_baseline():
    c = conn(PROD_DB)
    cur = c.cursor()
    cur.execute("SELECT COUNT(*), COALESCE(SUM(amount),0) FROM transaction")
    count, net = cur.fetchone()
    cur.execute("SELECT COALESCE(SUM(CASE WHEN amount<0 THEN amount ELSE 0 END),0), "
                "COALESCE(SUM(CASE WHEN amount>0 THEN amount ELSE 0 END),0) FROM transaction")
    expense, income = cur.fetchone()
    cur.execute("SELECT MIN(id), MAX(id) FROM transaction")
    min_id, max_id = cur.fetchone()
    c.close()
    return {"count": count, "net": float(net), "expense": float(expense),
            "income": float(income), "min_id": min_id, "max_id": max_id}


def main():
    print("== 0. 生产库基线（执行前） ==")
    before = prod_baseline()
    print(before)

    print(f"\n== 1. 创建临时库 {SCRATCH_DB} ==")
    c = conn()
    cur = c.cursor()
    cur.execute(f"CREATE DATABASE IF NOT EXISTS {SCRATCH_DB} CHARACTER SET utf8mb4")
    c.commit()
    c.close()

    print("\n== 2. 第一次执行迁移（--apply） ==")
    os.environ["LEDGER_DB_USER"] = DDL_USER
    os.environ["LEDGER_DB_PASS"] = DDL_PASS
    os.environ["LEDGER_DB_NAME"] = SCRATCH_DB
    import migrate_state
    first = migrate_state.apply_migrations(migrate_state.MIGRATION_DIR, dry_run=False)
    print("第一次执行 ok =", first.ok, first.error)

    print("\n== 3. 第二次执行迁移（--apply，验收：不报错） ==")
    second = migrate_state.apply_migrations(migrate_state.MIGRATION_DIR, dry_run=False)
    print("第二次执行 ok =", second.ok, second.error)
    if not (first.ok and second.ok):
        print("[FAIL] 迁移不能连续执行两次")
        sys.exit(1)

    print("\n== 4. 校验表结构与字段 ==")
    c = conn(SCRATCH_DB)
    cur = c.cursor()
    cur.execute("SHOW COLUMNS FROM state_snapshot")
    cols = {row[0] for row in cur.fetchall()}
    missing = EXPECTED_COLUMNS - cols
    print("字段数:", len(cols), "缺字段:", missing or "无")
    cur.execute("SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=%s AND table_name='state_snapshot'", (SCRATCH_DB,))
    table_count = cur.fetchone()[0]
    print("state_snapshot 表数量（应为 1）:", table_count)
    cur.execute("SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=%s AND table_name NOT IN ('state_snapshot')", (SCRATCH_DB,))
    extra = cur.fetchone()[0]
    print("临时库中其他表数量（应为 0）:", extra)
    c.close()
    if missing or table_count != 1 or extra != 0:
        print("[FAIL] 表结构或唯一性异常")
        sys.exit(1)

    print("\n== 4.5 快照持久化往返（写→读回→字段核对） ==")
    c = conn(SCRATCH_DB)
    cur = c.cursor()
    cur.execute(
        "INSERT INTO state_snapshot "
        "(domain, snapshot_key, payload_json, source, observed_at, fresh_until, status, error_code, error_message, created_at, updated_at) "
        "VALUES (%s,%s,%s,%s,%s,%s,%s,%s,%s,NOW(),NOW())",
        ("deepseek", "by_api_key:1", '{"todayCost":"0.12"}', "deepseek-platform-api-export",
         "2026-08-15 20:00:00", "2026-08-16 20:00:00", "FRESH", None, None))
    c.commit()
    cur.execute("SELECT domain, snapshot_key, payload_json, source, observed_at, fresh_until, status, error_code, error_message "
                "FROM state_snapshot WHERE domain=%s AND snapshot_key=%s", ("deepseek", "by_api_key:1"))
    row = cur.fetchone()
    assert row is not None, "写入的快照读不回来"
    domain, key, payload, source, observed, fresh_until, status, err_code, err_msg = row
    assert (domain, key, payload, source, status) == ("deepseek", "by_api_key:1", '{"todayCost":"0.12"}',
                                                      "deepseek-platform-api-export", "FRESH")
    assert err_code is None and err_msg is None
    print("往返 OK:", {"domain": domain, "key": key, "payload": payload, "status": status})
    cur.execute("SELECT COUNT(*) FROM state_snapshot WHERE domain=%s AND snapshot_key=%s", ("deepseek", "by_api_key:1"))
    assert cur.fetchone()[0] == 1, "同 domain+key 不应产生重复行（唯一键）"
    print("唯一键约束 OK: 同 domain+key 仅 1 行")
    c.close()

    print("\n== 5. 生产库基线（执行后，验收：完全一致） ==")
    after = prod_baseline()
    print(after)
    if after != before:
        print(f"[FAIL] 生产库被改动: {before} -> {after}")
        sys.exit(1)
    print("生产库基线一致: PASS")

    print("\n== 6. 清理临时库 ==")
    c = conn()
    cur = c.cursor()
    cur.execute(f"DROP DATABASE IF EXISTS {SCRATCH_DB}")
    c.commit()
    c.close()
    print("已删除临时库")

    print("\n[PASS] 迁移可连续执行两次不报错；字段完整；生产财务表零改动")


if __name__ == "__main__":
    main()
