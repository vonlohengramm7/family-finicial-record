#!/usr/bin/env python3
"""
喵喵记账 CSV → family_ledger 数据库 导入脚本

将 2024 和 2025 年的喵喵记账 CSV 数据映射到系统分类表后批量导入。
默认归属人 = 汤圆爸爸 (user_id=1)

用法: python3 import_csv.py
"""

import csv
import re
import mysql.connector
from datetime import datetime

# ======== 数据库连接 ========
DB_CONFIG = {
    'host': '127.0.0.1',
    'port': 3306,
    'user': 'hermes',
    'password': 'hermes_ledger_2026',
    'database': 'family_ledger',
    'charset': 'utf8mb4',
}

CSV_FILES = [
    '/home/vonlohengramm/records/爸爸/财务/喵喵记账2024-merged.csv',
    '/home/vonlohengramm/records/爸爸/财务/喵喵记账2025-merged.csv',
]

DEFAULT_USER_ID = 1  # 汤圆爸爸

# ======== CSV分类 → 系统二级分类ID 映射 ========
CSV_CATEGORY_MAP = {
    # 交通
    '交通': 101,      # 交通→充电
    '顺风车': 109,    # 交通→顺风车
    # 餐饮
    '餐饮': 110,      # 餐饮→外卖/在外就餐
    '零食': 112,      # 餐饮→零食
    '饮品': 113,      # 餐饮→饮料酒水
    # 购物
    '购物': 119,      # 购物→其他购物
    '书籍': 114,      # 购物→图书
    '服饰': 115,      # 购物→服饰
    '美容': 116,      # 购物→美容
    # 家居（原"家庭"）
    '家庭': 120,      # 家居→电费
    '通讯': 123,      # 家居→话费
    # 超市
    '超市': 145,      # 超市→批量采购
    # 日用
    '日用': 147,      # 日用→清洁用品
    # 收入
    '工资': 187,      # 收入→工资
    '分红': 188,      # 收入→分红
    '退款': 190,      # 收入→退款
    '报销': 191,      # 收入→报销
    '收款': 192,      # 收入→收款
    '红包': 189,      # 收入→红包（入）
    '旧物': 194,      # 收入→旧物出售
    # 人情
    '礼品': 157,      # 人情→礼物
    '礼金': 158,      # 人情→份子钱/随礼
    '社交': 155,      # 人情→红包（出）
    # 金融
    '还款': 173,      # 金融→还款
    '借款利息': 174,  # 金融→借款利息
    # 直接匹配
    '住房': 159,      # 住房→房贷
    '医疗': 139,      # 医疗→就诊
    '宠物': 135,      # 宠物→猫粮
    '数码': 143,      # 数码→硬件
    '汽车': 162,      # 汽车→保险
    '游戏': 150,      # 游戏→充值
    '娱乐': 152,      # 娱乐→电影
    '旅行': 165,      # 旅行→机票
    '快递': 168,      # 快递→邮费
    '运动': 170,      # 运动→健身
    '办公': 172,      # 办公→工作支出
    '维修': 176,      # 维修→家电维修
    '烟酒': 185,      # 烟酒→烟
}

# 需要根据金额正负判断的分类
CSV_DYNAMIC_MAP = {
    '租金': (193, 161),   # 正=收入·租金收入, 负=住房·租金
    '其他': (195, 195),   # 正=其他, 负=其他
}


def parse_time(time_str):
    """解析喵喵记账时间格式 '2024.12.31 18:06:04'"""
    try:
        dt = datetime.strptime(time_str.strip(), '%Y.%m.%d %H:%M:%S')
        return dt.date(), dt.time()
    except ValueError:
        try:
            dt = datetime.strptime(time_str.strip(), '%Y.%m.%d')
            return dt.date(), None
        except ValueError:
            return None, None


def parse_amount(amount_str):
    """解析金额字符串"""
    try:
        return float(amount_str.strip().replace(',', ''))
    except ValueError:
        return 0.0


def main():
    conn = mysql.connector.connect(**DB_CONFIG)
    cursor = conn.cursor()

    insert_sql = """INSERT INTO transaction 
    (user_id, category_id, amount, trans_date, trans_time, note, created_at)
    VALUES (%s, %s, %s, %s, %s, %s, NOW())"""

    total_count = 0
    errors = []
    skipped = 0

    for csv_path in CSV_FILES:
        year_label = "2024" if "2024" in csv_path else "2025"
        print(f"\n📂 正在导入 {csv_path} ...")

        with open(csv_path, encoding='utf-8-sig') as f:
            reader = csv.reader(f)
            header = next(reader, None)
            if not header:
                print(f"  ⚠️  空文件，跳过")
                continue

            batch = []
            for row_num, row in enumerate(reader, start=2):  # 行号从2开始（标题行+1）
                if not row or len(row) < 7:
                    skipped += 1
                    continue

                csv_category = row[0].strip()
                time_str = row[1].strip()
                amount_str = row[2].strip()
                note = row[6].strip() if len(row) > 6 else ''

                # 解析日期时间
                trans_date, trans_time = parse_time(time_str)
                if trans_date is None:
                    errors.append(f"{csv_path}:{row_num} 无法解析时间: {time_str}")
                    continue

                # 解析金额
                amount = parse_amount(amount_str)
                if amount == 0.0:
                    skipped += 1
                    continue

                # 获取分类ID
                if csv_category in CSV_DYNAMIC_MAP:
                    pos_id, neg_id = CSV_DYNAMIC_MAP[csv_category]
                    category_id = pos_id if amount > 0 else neg_id
                else:
                    category_id = CSV_CATEGORY_MAP.get(csv_category)
                    if category_id is None:
                        errors.append(f"{csv_path}:{row_num} 未知分类: '{csv_category}'")
                        skipped += 1
                        continue

                batch.append((
                    DEFAULT_USER_ID,
                    category_id,
                    amount,
                    trans_date,
                    trans_time,
                    note[:500] if note else None
                ))

                # 每500条批量提交一次
                if len(batch) >= 500:
                    cursor.executemany(insert_sql, batch)
                    conn.commit()
                    total_count += len(batch)
                    print(f"  ✓ 已导入 {total_count} 条...")
                    batch = []

        # 提交剩余批次
        if batch:
            cursor.executemany(insert_sql, batch)
            conn.commit()
            total_count += len(batch)

        print(f"  ✓ {year_label} 导入完成")

    cursor.close()
    conn.close()

    print(f"\n{'='*50}")
    print(f"✅ 导入完成!")
    print(f"   总导入: {total_count} 条")
    print(f"   跳过:   {skipped} 条")
    print(f"   错误:   {len(errors)} 条")
    if errors:
        print(f"\n   前10个错误:")
        for e in errors[:10]:
            print(f"     - {e}")
    print(f"{'='*50}")


if __name__ == '__main__':
    main()
