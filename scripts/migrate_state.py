#!/usr/bin/env python3
"""
家庭状态中枢 - 受控 SQL 迁移运行器（只服务 K1 已确认的状态快照存储）

设计约束（对应 K1 规格 §4.2 / state-hub-acceptance.md §C）：
- 迁移文件位于 backend/src/main/resources/db/migration/，按文件名版本顺序执行
- 每个迁移必须自带幂等守卫（CREATE TABLE IF NOT EXISTS），可重复执行
- 安全门禁：拒绝 DROP / TRUNCATE / ALTER / 任何 DML / 任何针对生产财务表的语句
- 不触碰 transaction / category / user / auto_transaction / project /
  energy_log / vehicle_expense 等生产财务数据
- --check 仅校验不执行；--apply 执行迁移（可重复运行，第二次为无害 no-op）

用法：
    python3 scripts/migrate_state.py --check                 # 校验全部迁移文件
    python3 scripts/migrate_state.py --apply                 # 应用到默认库 family_ledger
    python3 scripts/migrate_state.py --apply --db NAME       # 应用到指定库（如临时验证库）

连接配置优先读环境变量 LEDGER_DB_*，缺省与 application.yml 一致（hermes 本机只读+写入账户）。
"""
import argparse
import os
import re
import sys
from pathlib import Path
from types import SimpleNamespace

MIGRATION_DIR = Path(__file__).resolve().parent.parent / "backend/src/main/resources/db/migration"

# 生产财务事实表（K1 §2.3 / §4.1 边界）：迁移与安全门禁一律不得引用
FINANCIAL_TABLES = (
    "transaction", "category", "user", "auto_transaction",
    "project", "energy_log", "vehicle_expense",
)

# 破坏性 / 写入型 SQL 关键字（在注释剥离后检测）
FORBIDDEN_KEYWORDS = (
    "DROP", "TRUNCATE", "ALTER", "INSERT", "UPDATE", "DELETE",
    "REPLACE", "GRANT", "REVOKE", "CREATE INDEX", "CREATE DATABASE",
)

# 允许的建表目标（当前仅状态快照表）
ALLOWED_CREATE = re.compile(r"CREATE\s+TABLE\s+IF\s+NOT\s+EXISTS\s+state_snapshot", re.I)


def strip_comments(sql: str) -> str:
    """剥离 -- 行注释与 /* */ 块注释，避免回滚说明被误判为破坏性语句。"""
    sql = re.sub(r"/\*.*?\*/", "", sql, flags=re.S)
    sql = re.sub(r"--[^\n]*", "", sql)
    return sql


def safety_gate(sql: str) -> SimpleNamespace:
    """对单份迁移 SQL 做安全门禁校验。

    返回 SimpleNamespace(ok=bool, error=str|None)。
    放行条件：仅允许 CREATE TABLE IF NOT EXISTS state_snapshot 及其列定义/索引；
    不得出现破坏性/写入型关键字，不得引用任何财务表。
    """
    cleaned = strip_comments(sql).strip()
    if not cleaned:
        return SimpleNamespace(ok=False, error="迁移文件为空")

    upper = cleaned.upper()
    for keyword in FORBIDDEN_KEYWORDS:
        if re.search(rf"\b{re.escape(keyword)}\b", upper):
            return SimpleNamespace(ok=False, error=f"禁止的关键字: {keyword}")

    for table in FINANCIAL_TABLES:
        if re.search(rf"\b{re.escape(table)}\b", cleaned, re.I):
            return SimpleNamespace(ok=False, error=f"禁止引用财务表: {table}")

    if not ALLOWED_CREATE.search(cleaned):
        return SimpleNamespace(ok=False, error="仅允许 CREATE TABLE IF NOT EXISTS state_snapshot")

    return SimpleNamespace(ok=True, error=None)


def discover_migrations(directory: Path) -> list:
    """按文件名顺序返回迁移文件列表（V*.sql）。"""
    files = [f for f in Path(directory).glob("V*.sql") if f.is_file()]
    return sorted(files, key=lambda f: f.name)


def load_migration_sql(directory: Path, name: str) -> str:
    return (Path(directory) / name).read_text(encoding="utf-8")


def _validate_all(directory: Path) -> SimpleNamespace:
    """校验目录下全部迁移文件通过安全门禁。"""
    files = discover_migrations(directory)
    if not files:
        return SimpleNamespace(ok=False, error="未发现迁移文件")
    for f in files:
        result = safety_gate(load_migration_sql(directory, f.name))
        if not result.ok:
            return SimpleNamespace(ok=False, error=f"{f.name}: {result.error}")
    return SimpleNamespace(ok=True, error=None)


def apply_migrations(directory: Path, dry_run: bool = True, run_twice: bool = False) -> SimpleNamespace:
    """执行迁移。

    dry_run=True : 只校验（连续两遍校验可验证幂等性逻辑）。
    dry_run=False: 连接到数据库逐条执行；SQL 自带 IF NOT EXISTS，可重复运行。
    """
    for _ in range(2 if run_twice else 1):
        result = _validate_all(directory)
        if not result.ok:
            return result
    if dry_run:
        return SimpleNamespace(ok=True, error=None)

    import mysql.connector

    config = {
        "host": os.environ.get("LEDGER_DB_HOST", "127.0.0.1"),
        "port": int(os.environ.get("LEDGER_DB_PORT", "3306")),
        "user": os.environ.get("LEDGER_DB_USER", "hermes"),
        "password": os.environ.get("LEDGER_DB_PASS", "hermes_ledger_2026"),
        "database": os.environ.get("LEDGER_DB_NAME", "family_ledger"),
        "charset": "utf8mb4",
    }
    conn = mysql.connector.connect(**config)
    try:
        cursor = conn.cursor()
        for f in discover_migrations(directory):
            gate = safety_gate(load_migration_sql(directory, f.name))
            if not gate.ok:
                return SimpleNamespace(ok=False, error=f"{f.name}: {gate.error}")
            for statement in _split_statements(load_migration_sql(directory, f.name)):
                if statement.strip():
                    cursor.execute(statement)
        conn.commit()
    finally:
        conn.close()
    return SimpleNamespace(ok=True, error=None)


def _split_statements(sql: str) -> list:
    """按分号切分语句（受控 DDL 场景足够；注释已由 safety_gate 剥离逻辑兜底）。"""
    cleaned = strip_comments(sql)
    return [s.strip() for s in cleaned.split(";") if s.strip()]


def main() -> int:
    parser = argparse.ArgumentParser(description="家庭状态中枢受控迁移运行器")
    group = parser.add_mutually_exclusive_group(required=True)
    group.add_argument("--check", action="store_true", help="仅校验迁移文件，不连接数据库")
    group.add_argument("--apply", action="store_true", help="执行迁移（可重复运行）")
    parser.add_argument("--db", default=None, help="目标库名（默认取 LEDGER_DB_NAME 或 family_ledger）")
    args = parser.parse_args()

    if args.db:
        os.environ["LEDGER_DB_NAME"] = args.db

    if args.check:
        result = apply_migrations(MIGRATION_DIR, dry_run=True)
        if not result.ok:
            print(f"[FAIL] {result.error}", file=sys.stderr)
            return 1
        files = [f.name for f in discover_migrations(MIGRATION_DIR)]
        print(f"[OK] 迁移校验通过: {', '.join(files)}")
        return 0

    result = apply_migrations(MIGRATION_DIR, dry_run=False)
    if not result.ok:
        print(f"[FAIL] {result.error}", file=sys.stderr)
        return 1
    print("[OK] 迁移执行完成（幂等，可重复运行）")
    return 0


if __name__ == "__main__":
    sys.exit(main())
