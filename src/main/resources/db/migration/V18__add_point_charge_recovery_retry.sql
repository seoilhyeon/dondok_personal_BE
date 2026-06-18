ALTER TABLE point_charge
    ADD COLUMN recovery_attempt_count INT NOT NULL DEFAULT 0 AFTER failure_message,
    ADD COLUMN next_recovery_at DATETIME(6) NULL AFTER recovery_attempt_count;
