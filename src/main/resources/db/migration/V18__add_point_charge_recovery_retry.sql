ALTER TABLE point_charge
    ADD COLUMN recovery_attempt_count INT NOT NULL DEFAULT 0 AFTER failure_message,
    ADD COLUMN next_recovery_at DATETIME(6) NULL AFTER recovery_attempt_count;

CREATE INDEX idx_point_charge_recovery_scan
    ON point_charge (status, recovery_attempt_count, next_recovery_at, created_at, id);
