CREATE TABLE daily_settlement_snapshot (
    id                              BIGINT          NOT NULL AUTO_INCREMENT,
    crew_id                         BIGINT          NOT NULL,
    mission_date                    DATE            NOT NULL,
    daily_settlement_type           CHAR(1)         NOT NULL,
    frequency_type_snapshot         VARCHAR(20)     NOT NULL,
    phase                           VARCHAR(20)     NOT NULL,
    status                          VARCHAR(20)     NOT NULL,
    batch_run_key                   VARCHAR(100)    NOT NULL,
    frozen_at                       DATETIME(6)     NOT NULL,
    total_participants              INT             NOT NULL DEFAULT 0,
    total_recognized_success_count  INT             NOT NULL DEFAULT 0,
    total_locked_amount             BIGINT          NOT NULL DEFAULT 0,
    failure_message                 VARCHAR(500)    NULL,
    created_at                      DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at                      DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uk_dss_crew_date_type_phase (crew_id, mission_date, daily_settlement_type, phase),
    INDEX idx_dss_status (status),
    INDEX idx_dss_phase (phase),
    CONSTRAINT chk_dss_aggregate_non_negative
        CHECK (
            total_participants >= 0
            AND total_recognized_success_count >= 0
            AND total_locked_amount >= 0
        ),
    CONSTRAINT fk_dss_crew
        FOREIGN KEY (crew_id) REFERENCES crew (id)
            ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE daily_settlement_participant_snapshot (
    id                              BIGINT           NOT NULL AUTO_INCREMENT,
    daily_settlement_snapshot_id    BIGINT           NOT NULL,
    crew_participant_id             BIGINT           NOT NULL,
    member_id                       BIGINT           NOT NULL,
    participant_status_snapshot     VARCHAR(20)      NOT NULL,
    success_count                   INT              NOT NULL DEFAULT 0,
    share_ratio                     DECIMAL(10, 6)   NOT NULL,
    expected_refund_amount          BIGINT           NOT NULL DEFAULT 0,
    created_at                      DATETIME(6)      NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at                      DATETIME(6)      NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uk_dsps_snapshot_participant (daily_settlement_snapshot_id, crew_participant_id),
    INDEX idx_dsps_member_id (member_id),
    INDEX idx_dsps_crew_participant_id (crew_participant_id),
    CONSTRAINT chk_dsps_projection_non_negative
        CHECK (
            success_count >= 0
            AND share_ratio >= 0
            AND expected_refund_amount >= 0
        ),
    CONSTRAINT fk_dsps_snapshot
        FOREIGN KEY (daily_settlement_snapshot_id) REFERENCES daily_settlement_snapshot (id)
            ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT fk_dsps_crew_participant
        FOREIGN KEY (crew_participant_id) REFERENCES crew_participant (id)
            ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT fk_dsps_member
        FOREIGN KEY (member_id) REFERENCES member (id)
            ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
