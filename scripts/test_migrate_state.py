#!/usr/bin/env python3
"""
受控 SQL 迁移运行器测试（strict TDD：先 RED 后 GREEN）

覆盖 K1 验收要求：
- 字段：迁移必须创建含 domain/snapshot_key/payload_json/source/observed_at/
       fresh_until/status/error_code/error_message 的表
- 幂等性：迁移可连续执行两次不报错；SQL 必须自带 IF NOT EXISTS 幂等守卫
- 对财务表的零改动：迁移文件不得引用/修改 transaction 等生产财务表；
      安全门禁必须拒绝任何 DROP/ALTER/DML 或财务表 DDL
"""
import os
import re
import sys
import unittest
from pathlib import Path

# 让被测模块可导入（scripts/ 下直接 import migrate_state）
sys.path.insert(0, str(Path(__file__).resolve().parent))

import migrate_state  # noqa: E402  (RED: 模块尚不存在 -> ImportError)


MIGRATION_DIR = Path(__file__).resolve().parent.parent / "backend/src/main/resources/db/migration"


class MigrationFileDiscoveryTest(unittest.TestCase):
    def test_discovers_v3_migration(self):
        files = migrate_state.discover_migrations(MIGRATION_DIR)
        names = [f.name for f in files]
        self.assertIn("V3__create_state_snapshot.sql", names)
        self.assertEqual(sorted(files, key=lambda f: f.name), files, "迁移文件应按文件名排序")


class RequiredFieldsTest(unittest.TestCase):
    """K1 §4.1：state_snapshot 必须包含的字段"""

    REQUIRED = [
        "domain",
        "snapshot_key",   # key
        "payload_json",   # value
        "source",
        "observed_at",
        "fresh_until",
        "status",
        "error_code",     # error
        "error_message",  # error
    ]

    def test_v3_sql_contains_all_required_fields(self):
        sql = migrate_state.load_migration_sql(MIGRATION_DIR, "V3__create_state_snapshot.sql")
        for field in self.REQUIRED:
            self.assertRegex(sql, re.compile(rf"\b{field}\b", re.I),
                             f"迁移缺少必需字段: {field}")

    def test_entity_fields_match_required_contract(self):
        entity_src = (Path(__file__).resolve().parent.parent
                      / "backend/src/main/java/com/familyledger/entity/StateSnapshot.java").read_text(encoding="utf-8")
        for field in self.REQUIRED:
            camel = "".join(part.capitalize() if i else part for i, part in enumerate(field.split("_")))
            self.assertIn(camel, entity_src, f"实体缺少字段映射: {camel}")


class IdempotencyTest(unittest.TestCase):
    """迁移必须可重复执行：SQL 自带 IF NOT EXISTS 守卫"""

    def test_v3_uses_idempotent_create_guard(self):
        sql = migrate_state.load_migration_sql(MIGRATION_DIR, "V3__create_state_snapshot.sql")
        self.assertRegex(sql, re.compile(r"CREATE\s+TABLE\s+IF\s+NOT\s+EXISTS\s+state_snapshot", re.I),
                         "迁移必须使用 CREATE TABLE IF NOT EXISTS 以保证可重复执行")

    def test_execute_twice_is_noop(self):
        """同一迁移连续执行两次：第二次不报错、不产生重复表"""
        result = migrate_state.apply_migrations(MIGRATION_DIR, dry_run=True, run_twice=True)
        self.assertTrue(result.ok, f"连续执行两次不应报错: {result.error}")


class ZeroFinancialModificationTest(unittest.TestCase):
    """对生产财务表零改动：迁移文件不得触碰任何财务表"""

    FINANCIAL_TABLES = [
        "transaction", "category", "user", "auto_transaction",
        "project", "energy_log", "vehicle_expense",
    ]

    def test_v3_does_not_reference_financial_tables(self):
        sql = migrate_state.load_migration_sql(MIGRATION_DIR, "V3__create_state_snapshot.sql")
        for table in self.FINANCIAL_TABLES:
            self.assertNotRegex(sql, re.compile(rf"\b{re.escape(table)}\b", re.I),
                                f"迁移不得引用财务表: {table}")

    def test_safety_gate_rejects_financial_ddl(self):
        bad = "ALTER TABLE transaction ADD COLUMN foo INT;"
        self.assertFalse(migrate_state.safety_gate(bad).ok,
                         "安全门禁必须拒绝针对财务表的 DDL")

    def test_safety_gate_rejects_destructive_sql(self):
        for bad in ("DROP TABLE state_snapshot;",
                    "DROP TABLE IF EXISTS state_snapshot;",
                    "TRUNCATE TABLE state_snapshot;",
                    "INSERT INTO state_snapshot VALUES (1);",
                    "UPDATE state_snapshot SET status='x';",
                    "DELETE FROM state_snapshot;",
                    "ALTER TABLE state_snapshot ADD COLUMN x INT;"):
            with self.subTest(sql=bad):
                self.assertFalse(migrate_state.safety_gate(bad).ok,
                                 f"安全门禁必须拒绝破坏性 SQL: {bad}")

    def test_safety_gate_allows_v3_migration(self):
        sql = migrate_state.load_migration_sql(MIGRATION_DIR, "V3__create_state_snapshot.sql")
        self.assertTrue(migrate_state.safety_gate(sql).ok,
                        "安全门禁必须放行合法的 state_snapshot 建表迁移")


if __name__ == "__main__":
    unittest.main(verbosity=2)
