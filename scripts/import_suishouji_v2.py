#!/usr/bin/env python3
"""
随手记Excel → family_ledger 导入 补充版：也处理收入sheet
"""
import openpyxl, mysql.connector
from datetime import datetime

DB_CONFIG = {
    'host': '127.0.0.1', 'port': 3306,
    'user': 'hermes', 'password': 'hermes_ledger_2026',
    'database': 'family_ledger', 'charset': 'utf8mb4',
}

EXCEL_PATH = '/home/vonlohengramm/.hermes/cache/documents/doc_d71d16390f82_随手记家庭账本120260515.xlsx'

EXPENSE_CAT_MAP = {
    ('食品酒水', '伙食费'): 110, ('食品酒水', '饮料酒水'): 113, ('食品酒水', '买菜'): 111,
    ('食品酒水', '零食'): 112, ('食品酒水', '中餐'): 110, ('食品酒水', '水果'): 112,
    ('食品酒水', '早餐'): 110, ('食品酒水', '晚餐'): 110,
    ('行车交通', '停车'): 103, ('行车交通', '充电'): 101, ('行车交通', '自行车'): 106,
    ('行车交通', '加油'): 102, ('行车交通', '打车'): 105, ('行车交通', '地铁'): 104,
    ('行车交通', '违章罚款'): 108, ('行车交通', '保险'): 107, ('行车交通', '维修'): 163,
    ('行车交通', '洗车'): 163,
    ('宝宝费用', '宝宝用品'): 133, ('宝宝费用', '医疗护理'): 130, ('宝宝费用', '妈妈用品'): 133,
    ('宝宝费用', '宝宝食品'): 133, ('宝宝费用', '宝宝其他'): 133,
    ('人情费用', '孝敬长辈'): 156, ('人情费用', '礼物'): 157, ('人情费用', '请客'): 157,
    ('人情费用', '红包'): 155,
    ('购物消费', '衣裤鞋帽'): 115, ('购物消费', '宠物支出'): 135, ('购物消费', '美妆护肤'): 116,
    ('购物消费', '日常用品'): 149, ('购物消费', '家居饰品'): 119, ('购物消费', '电子数码'): 143,
    ('购物消费', '办公用品'): 172, ('购物消费', '清洁用品'): 147, ('购物消费', '家用纺织'): 119,
    ('购物消费', '家具家电'): 127, ('购物消费', '厨房用品'): 149, ('购物消费', '珠宝首饰'): 117,
    ('购物消费', '洗护用品'): 147, ('购物消费', '书报杂志'): 114,
    ('休闲娱乐', '网游'): 150, ('休闲娱乐', '其他娱乐'): 154, ('休闲娱乐', '温泉洗浴'): 154,
    ('休闲娱乐', '彩票'): 154, ('休闲娱乐', '话剧'): 154, ('休闲娱乐', '电影'): 152,
    ('医疗教育', '药品费'): 141, ('医疗教育', '治疗费'): 139,
    ('金融保险', '房贷'): 173, ('金融保险', '税费'): 175, ('金融保险', '人身保险'): 175,
    ('居家生活', '水费'): 121, ('居家生活', '快递费'): 168, ('居家生活', '维修费'): 176,
    ('居家生活', '燃气费'): 122, ('居家生活', '理发'): 116, ('居家生活', '电费'): 120,
    ('居家生活', '物业费'): 126,
    ('出差旅游', '娱乐费'): 167,
    ('交流通讯', '手机话费'): 123, ('交流通讯', '网费'): 124,
    ('其他杂项', '其他支出'): 195,
    ('装修费用', '家电家具'): 128,
}

INCOME_CAT_MAP = {
    ('职业收入', '工资收入'): 187,   # 收入·工资
    ('人情收礼', '所收红包'): 189,   # 收入·红包（入）
    ('其他收入', '意外来钱'): 192,   # 收入·收款
    ('其他收入', '报销收入'): 191,   # 收入·报销
}

MEMBER_MAP = {'爸爸👨': 1, '妈妈👩': 2}

def parse_row(row, is_income):
    """Parse a row, return (user_id, category_id, amount, date, time, note) or None"""
    tx_type = (row[0] or '').strip()
    date_str = (row[1] or '').strip()
    cat1 = (row[2] or '').strip()
    cat2 = (row[3] or '').strip()
    amount = float(row[7])
    member = (row[8] or '').strip()
    note = (row[13] or '').strip() if len(row) > 13 else ''
    
    if not date_str:
        return None
    
    dt = datetime.strptime(date_str, '%Y-%m-%d %H:%M:%S')
    
    # Amount sign
    amount_val = amount if is_income else -abs(amount)
    
    cat_map = INCOME_CAT_MAP if is_income else EXPENSE_CAT_MAP
    cat_key = (cat1, cat2)
    if cat_key not in cat_map:
        return ('unknown_cat', str(cat_key))
    
    return (
        MEMBER_MAP.get(member, 1),
        cat_map[cat_key],
        amount_val,
        dt.date(),
        dt.time(),
        note[:500] if note else None
    )

def main():
    wb = openpyxl.load_workbook(EXCEL_PATH)
    conn = mysql.connector.connect(**DB_CONFIG)
    c = conn.cursor()
    
    # Load existing dedup index
    c.execute("SELECT DATE(trans_date), ABS(amount), IFNULL(note,'') FROM transaction")
    existing = set()
    for dt, amt, note in c.fetchall():
        existing.add((str(dt), float(amt), note))
    
    total_new = 0
    total_dup = 0
    total_unknown = 0
    
    for sheet_name, is_income in [('支出', False), ('收入', True)]:
        ws = wb[sheet_name]
        new_records = []
        unknown = {}
        dup = 0
        
        for row in ws.iter_rows(min_row=2, values_only=True):
            if not row or not row[0]:
                continue
            parsed = parse_row(row, is_income)
            if parsed is None:
                continue
            if parsed[0] == 'unknown_cat':
                unknown[parsed[1]] = unknown.get(parsed[1], 0) + 1
                continue
            
            # Dedup
            dedup_key = (str(parsed[3]), abs(parsed[2]), parsed[5] or '')
            if dedup_key in existing:
                dup += 1
                continue
            
            new_records.append(parsed)
            existing.add(dedup_key)
        
        # Insert
        ins_sql = """INSERT INTO transaction (user_id, category_id, amount, trans_date, trans_time, note, created_at)
                     VALUES (%s, %s, %s, %s, %s, %s, NOW())"""
        for i in range(0, len(new_records), 500):
            batch = new_records[i:i+500]
            c.executemany(ins_sql, batch)
            conn.commit()
        
        print(f"📂 {sheet_name}: 新增{len(new_records)}笔, 重复跳过{dup}笔", end='')
        if unknown:
            print(f", 未知分类{sum(unknown.values())}笔")
            for k,v in unknown.items():
                print(f"    {k}: {v}")
        else:
            print()
        
        total_new += len(new_records)
        total_dup += dup
        total_unknown += sum(unknown.values())
    
    c.execute("SELECT COUNT(*) FROM transaction")
    total = c.fetchone()[0]
    c.close(); conn.close()
    
    print(f"\n======= 完成 =======")
    print(f"  新增: {total_new}笔  重复: {total_dup}笔  未知: {total_unknown}笔")
    print(f"  数据库总计: {total}笔")

if __name__ == '__main__':
    main()
