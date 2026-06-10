CREATE TABLE point_charge (
    id BIGINT NOT NULL AUTO_INCREMENT,
    member_id BIGINT NOT NULL,
    payment_id VARCHAR(200) NOT NULL,
    order_id VARCHAR(64) NOT NULL,
    amount BIGINT NOT NULL,
    status VARCHAR(30) NOT NULL,
    point_history_id BIGINT NULL,
    failure_code VARCHAR(80) NULL,
    failure_message VARCHAR(500) NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uk_point_charge_payment_id (payment_id),
    UNIQUE KEY uk_point_charge_order_id (order_id),
    UNIQUE KEY uk_point_charge_point_history (point_history_id),
    INDEX idx_point_charge_member_created (member_id, created_at),
    INDEX idx_point_charge_status_created (status, created_at),
    CONSTRAINT fk_point_charge_member
        FOREIGN KEY (member_id) REFERENCES member (id)
        ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT fk_point_charge_point_history
        FOREIGN KEY (point_history_id) REFERENCES point_history (id)
        ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT chk_point_charge_amount CHECK (amount > 0),
    CONSTRAINT chk_point_charge_status
        CHECK (status IN ('PENDING_CONFIRM', 'CONFIRM_FAILED', 'COMPLETED')),
    CONSTRAINT chk_point_charge_completed_history
        CHECK (
            (status = 'COMPLETED' AND point_history_id IS NOT NULL)
            OR (status <> 'COMPLETED' AND point_history_id IS NULL)
        )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
