-- 미션 이미지 reEncode(원본 EXIF 제거를 위한 S3 GET+PUT) 작업을 위한 transactional outbox.
-- mission_log는 인증/증거 도메인 레코드이므로 reEncode 처리 상태를 섞지 않고, 인프라가 소유하는 별도 테이블로 분리한다.
-- createMissionLog가 mission_log save와 같은 트랜잭션에서 PENDING task를 insert해 작업을 durable하게 남긴다.
--  → 커밋 후 즉시 시도(@TransactionalEventListener AFTER_COMMIT + @Async)가 유실/실패해도 배치가 주워 EXIF 제거를 보장한다.
-- status/retry_count/last_error로 실패를 가시화하고, DB default를 두지 않은 컬럼은 값의 단일 출처를 앱에 고정한다.
CREATE TABLE image_reencode_task (
    id                 BIGINT          NOT NULL AUTO_INCREMENT,
    mission_log_id     BIGINT          NOT NULL,                            -- 추적/가시성용 FK
    s3_key             VARCHAR(255)    NOT NULL,                            -- reEncode 대상 object key (작업 페이로드)
    status             VARCHAR(20)     NOT NULL DEFAULT 'PENDING',          -- PENDING | DONE | FAILED
    retry_count        INT             NOT NULL DEFAULT 0,                  -- 누적 시도 실패 횟수 (3회 초과 시 FAILED)
    last_error         VARCHAR(500)    NULL,                                -- 관측용 마지막 실패 사유
    next_attempt_at    DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6), -- 백오프 재시도 기준 시각 (배치 polling)
    created_at         DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at         DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uq_irt_mission_log           (mission_log_id),               -- mission_log당 outbox row 1개
    INDEX idx_irt_status_next_attempt       (status, next_attempt_at),      -- 재처리 배치 polling
    CONSTRAINT fk_irt_mission_log
        FOREIGN KEY (mission_log_id) REFERENCES mission_log (id)
            ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
