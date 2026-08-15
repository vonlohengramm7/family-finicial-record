-- 家庭状态中枢快照表：仅保存可重建、已脱敏的聚合状态，不复制 transaction。
-- 回滚：DROP TABLE IF EXISTS state_snapshot; 不影响既有账本事实表。
CREATE TABLE IF NOT EXISTS state_snapshot (
    id BIGINT NOT NULL AUTO_INCREMENT,
    domain VARCHAR(32) NOT NULL,
    snapshot_key VARCHAR(128) NOT NULL,
    payload_json LONGTEXT NULL,
    source VARCHAR(256) NOT NULL,
    observed_at DATETIME NULL,
    fresh_until DATETIME NULL,
    status VARCHAR(16) NOT NULL,
    error_code VARCHAR(64) NULL,
    error_message VARCHAR(500) NULL,
    created_at DATETIME NOT NULL,
    updated_at DATETIME NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_state_snapshot_domain_key (domain, snapshot_key),
    KEY idx_state_snapshot_domain_fresh_until (domain, fresh_until)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='家庭状态中枢可重建快照';
