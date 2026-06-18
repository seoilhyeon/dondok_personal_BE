CREATE INDEX idx_dss_retry_scan
    ON daily_settlement_snapshot (status, retry_count, frozen_at, id);
