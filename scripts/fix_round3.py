#!/usr/bin/env python3
"""
第三次全面修正——清理剩余的错误分类映射
"""
import mysql.connector

DB_CONFIG = {
    'host': '127.0.0.1', 'port': 3306,
    'user': 'hermes', 'password': 'hermes_ledger_2026',
    'database': 'family_ledger', 'charset': 'utf8mb4',
}

conn = mysql.connector.connect(**DB_CONFIG)
c = conn.cursor()

stats = {}

def move(keyword, src_cat, dst_cat, desc):
    """移动包含关键词的记录"""
    c.execute("UPDATE transaction SET category_id = %s WHERE category_id = %s AND note = %s",
              (dst_cat, src_cat, keyword))
    n = c.rowcount
    if n > 0:
        stats[desc] = stats.get(desc, 0) + n

def move_like(keyword, src_cat, dst_cat, desc):
    """移动包含关键词的记录(LIKE)"""
    c.execute("UPDATE transaction SET category_id = %s WHERE category_id = %s AND note LIKE %s",
              (dst_cat, src_cat, f"%{keyword}%"))
    n = c.rowcount
    if n > 0:
        stats[desc] = stats.get(desc, 0) + n

def move_all(src_cat, dst_cat, desc):
    """移动整个分类"""
    c.execute("UPDATE transaction SET category_id = %s WHERE category_id = %s",
              (dst_cat, src_cat))
    n = c.rowcount
    if n > 0:
        stats[desc] = stats.get(desc, 0) + n

# ====== 家居·电费(120) → 拆分 ======
move('椅子', 120, 129, '→ 家居·家具')       # 家具
move_like('宜家', 120, 129, '→ 家居·家具')   # 宜家
move('计时器', 120, 129, '→ 家居·家具')
move('壶', 120, 129, '→ 家居·家具')
move('窗帘挂钩', 120, 129, '→ 家居·家具')
move('地漏网', 120, 129, '→ 家居·家具')

move_like('奶', 120, 111, '→ 餐饮·超市买菜')    # 奶/奶粉
move('粥', 120, 110, '→ 外卖/在外就餐')
move('洗衣', 120, 198, '→ 家居·家政保洁')        # 洗衣
move('洗浴', 120, 198, '→ 家居·家政保洁')
move_like('清洁', 120, 147, '→ 日用·清洁用品')
# 油刷是厨房工具
move('油刷', 120, 147, '→ 日用·清洁用品')

# 旅行类
move('杭州车票', 120, 165, '→ 旅行·机票')
move('乐乐高铁', 120, 165, '→ 旅行·机票')
move_like('车票', 120, 165, '→ 旅行·机票')
move('杭州住宿', 120, 166, '→ 旅行·酒店')
move('机票', 120, 165, '→ 旅行·机票')
move('乐乐妈妈车票', 120, 165, '→ 旅行·机票')

# fudi/盒马 → 超市
move_like('fudi', 120, 145, '→ 超市·批量采购')
move_like('盒马', 120, 145, '→ 超市·批量采购')

# 婚纱照 → 购物·节庆消费(197)
move('婚纱照定金', 120, 197, '→ 购物·节庆消费')
move('婚纱照', 120, 197, '→ 购物·节庆消费')
move('婚纱照+婚纱', 120, 197, '→ 购物·节庆消费')
move('孕妇照补', 120, 197, '→ 购物·节庆消费')

# 赡养 → 人情·孝敬长辈(156)
move_like('赡养', 120, 156, '→ 人情·孝敬长辈')
move('妈妈赡养', 120, 156, '→ 人情·孝敬长辈')

# 医疗
move('安妈妈ct', 120, 140, '→ 医疗·检查')
move('上门感冒检测', 120, 140, '→ 医疗·检查')

# 母婴
move('月子中心定金', 120, 133, '→ 母婴·用品')
move('a2奶粉', 120, 133, '→ 母婴·用品')

# 数码
move('充电宝', 120, 144, '→ 数码·配件')

# 个护美容
move('身体乳', 120, 116, '→ 购物·美容')

# 娱乐
move('小拼图', 120, 154, '→ 娱乐·玩乐')

# 食物
move('乐乐轻食', 120, 110, '→ 外卖/在外就餐')
move('取件', 120, 168, '→ 快递·邮费')
move('瑜伽球补', 120, 171, '→ 运动·装备')
move('乐乐滤芯', 120, 127, '→ 家居·家用电器')


# ====== 购物·其他购物(119) → 拆分 ======
move('大衣太平鸟', 119, 115, '→ 购物·服饰')
move('金豆', 119, 183, '→ 投资·黄金')
move('卡西欧电池', 119, 149, '→ 日用·其他日用')
move('吹风机架子', 119, 149, '→ 日用·其他日用')


# ====== 日用·其他日用(149) → 拆分 ======
move('理发', 149, 116, '→ 购物·美容')
move('修脚', 149, 116, '→ 购物·美容')
move('洗衣鞋', 149, 198, '→ 家居·家政保洁')
move('清洁刷头', 149, 147, '→ 日用·清洁用品')

# ====== 数码·硬件(143) → 会员/订阅移出 ======
move('华为会员', 143, 118, '→ 购物·电子订阅')
move('小米会员', 143, 118, '→ 购物·电子订阅')
move('迅雷', 143, 118, '→ 购物·电子订阅')
move_like('b站', 143, 118, '→ 购物·电子订阅')
move_like('剪映', 143, 118, '→ 购物·电子订阅')
move('dji care', 143, 118, '→ 购物·电子订阅')

# ====== 旅行·门票(167) → 机票分离 ======
move_like('机票', 167, 165, '→ 旅行·机票')
move_like('车票', 167, 165, '→ 旅行·机票')

# ====== 游戏·充值(150) → 手柄是硬件 ======
move('手柄', 150, 119, '→ 购物·其他购物')

# ====== 收入·收款(192) 退税归报销 ======
move('退税', 192, 191, '→ 收入·报销')

conn.commit()

# 汇总
print("✅ 第三次全面修正完成\n")
for desc, cnt in sorted(stats.items(), key=lambda x: -x[1]):
    print(f"  {desc}: {cnt}笔")

# 验证 家居·电费 剩余
c.execute("SELECT COUNT(*), ROUND(SUM(amount),2) FROM transaction WHERE category_id = 120")
cnt, amt = c.fetchone()
print(f"\n📊 家居·电费(120) 剩余: {cnt}笔, ¥{amt}")

c.execute("SELECT COUNT(*) FROM transaction")
total = c.fetchone()[0]
print(f"📊 数据总量: {total}")

c.close()
conn.close()
