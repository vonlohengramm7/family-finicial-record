-- 问界能耗百分比字段精度迁移。
-- 将 0-1 值域的 SOC/油量从两位小数扩展为三位小数：10.5% 可精确保存为 0.105。
-- 该 DDL 可重复执行；不修改、不删除任何能耗或交易数据。
ALTER TABLE energy_log
    MODIFY soc_before DECIMAL(5,3) NULL,
    MODIFY soc_after DECIMAL(5,3) NULL,
    MODIFY fuel_before DECIMAL(5,3) NULL,
    MODIFY fuel_after DECIMAL(5,3) NULL;
