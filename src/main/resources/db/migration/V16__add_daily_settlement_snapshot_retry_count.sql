ALTER TABLE daily_settlement_snapshot
    ADD COLUMN retry_count INT NOT NULL DEFAULT 0 AFTER failure_message;

ALTER TABLE daily_settlement_snapshot
    ADD CONSTRAINT chk_dss_retry_count_non_negative CHECK (retry_count >= 0);
