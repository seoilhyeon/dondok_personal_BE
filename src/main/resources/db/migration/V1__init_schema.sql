-- =============================================================
-- Dondok MVP — V1__init_schema.sql
-- Flyway initial schema migration
-- Charset  : utf8mb4 / utf8mb4_unicode_ci
-- Engine   : InnoDB
-- Timezone : Asia/Seoul (KST) — canonical server timezone
-- FK policy: RESTRICT / NO ACTION on money/audit/entity refs
-- PK policy: BIGINT AUTO_INCREMENT
-- Amount   : BIGINT (원화 원단위, 소수점 없음)
-- Enum     : VARCHAR STRING 저장 (JPA EnumType.STRING)
-- Audit    : created_at / updated_at DATETIME(6)
-- Optimistic lock: version BIGINT (point_account, crew_participant, settlement)
-- =============================================================

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;


-- =============================================================
-- 1. MEMBER
-- =============================================================
CREATE TABLE member (
    id                    BIGINT          NOT NULL AUTO_INCREMENT,
    uuid                  BINARY(16)      NOT NULL,                          -- immutable external canonical identifier; API 노출 시 CHAR(36) 직렬화
    email                 VARCHAR(255)    NOT NULL,                          -- 로그인 식별자 / PII, canonical identity 아님
    password_hash         VARCHAR(255)    NULL,                              -- 소셜 로그인 사용자는 NULL 가능
    nickname              VARCHAR(50)     NOT NULL,
    profile_image_s3_key  VARCHAR(255)    NULL,
    status_message        VARCHAR(100)    NULL,
    status                VARCHAR(20)     NOT NULL DEFAULT 'ACTIVE',         -- ACTIVE | DEACTIVATED
    created_at            DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at            DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uq_member_uuid     (uuid),
    UNIQUE KEY uq_member_email    (email),
    UNIQUE KEY uq_member_nickname (nickname),
    INDEX idx_member_status       (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;


-- =============================================================
-- 2. MEMBER_REFRESH_TOKEN
-- =============================================================
CREATE TABLE member_refresh_token (
    id           BIGINT         NOT NULL AUTO_INCREMENT,
    member_id    BIGINT         NOT NULL,
    token_hash   CHAR(64)       NOT NULL,                                    -- SHA-256 hex; raw token 저장 금지
    expires_at   DATETIME(6)    NOT NULL,
    revoked_at   DATETIME(6)    NULL,
    created_at   DATETIME(6)    NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uq_mrt_token_hash  (token_hash),
    INDEX idx_mrt_member_expires  (member_id, expires_at),
    CONSTRAINT fk_mrt_member
      FOREIGN KEY (member_id) REFERENCES member (id)
          ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;


-- =============================================================
-- 3. NOTIFICATION_DEVICE
-- MVP: Android-first FCM device/token lifecycle 최소 persistence
-- Transport persistence only; no domain/lifecycle/settlement authority
-- =============================================================
CREATE TABLE notification_device (
    id           BIGINT          NOT NULL AUTO_INCREMENT,
    member_id    BIGINT          NOT NULL,
    device_id    VARCHAR(100)    NOT NULL,                                   -- 클라이언트 기기/설치 식별자
    platform     VARCHAR(20)     NOT NULL DEFAULT 'ANDROID',                 -- MVP active: ANDROID
    fcm_token    VARCHAR(512)    NOT NULL,
    app_version  VARCHAR(50)     NULL,
    enabled      TINYINT(1)      NOT NULL DEFAULT 1,                         -- 알림 수신 활성 여부
    created_at   DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at   DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uq_nd_member_device  (member_id, device_id),
    INDEX idx_nd_member_enabled     (member_id, enabled),
    INDEX idx_nd_fcm_token          (fcm_token),                             -- optional: token lookup 필요 시
    CONSTRAINT fk_nd_member
     FOREIGN KEY (member_id) REFERENCES member (id)
         ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;


-- =============================================================
-- 4. NOTIFICATION
-- Thin non-authoritative UX hint / inbox read/unread surface
-- read_at IS NULL = unread; no status workflow
-- =============================================================
CREATE TABLE notification (
    id               BIGINT          NOT NULL AUTO_INCREMENT,
    uuid             BINARY(16)      NOT NULL,                               -- API notification_id; CHAR(36) 직렬화
    member_id        BIGINT          NOT NULL,
    event_type       VARCHAR(80)     NOT NULL,                               -- 앱 라우팅 vocabulary; DB enum 아님
    resource_type    VARCHAR(50)     NOT NULL,
    resource_id      VARCHAR(100)    NOT NULL,
    deep_link        VARCHAR(255)    NOT NULL,
    display_text     VARCHAR(500)    NOT NULL,
    requires_refetch TINYINT(1)      NOT NULL DEFAULT 1,                     -- MVP: 항상 true
    occurred_at      DATETIME(6)     NOT NULL,
    read_at          DATETIME(6)     NULL,                                   -- NULL = unread
    created_at       DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at       DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uq_notification_uuid          (uuid),
    INDEX idx_notification_member_occurred   (member_id, occurred_at, id),   -- cursor 페이지네이션
    INDEX idx_notification_member_read_at    (member_id, read_at),           -- unread count / read-all
    CONSTRAINT fk_notification_member
      FOREIGN KEY (member_id) REFERENCES member (id)
          ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;


-- =============================================================
-- 5. POINT_ACCOUNT
-- available_balance / reserved_balance / locked_balance 세 버킷
-- point_history + crew_participant + settlement_item 기준 reconciliation
-- version: optimistic locking
-- =============================================================
CREATE TABLE point_account (
    id                  BIGINT         NOT NULL AUTO_INCREMENT,
    member_id           BIGINT         NOT NULL,
    available_balance   BIGINT         NOT NULL DEFAULT 0,
    reserved_balance    BIGINT         NOT NULL DEFAULT 0,                   -- PENDING reserve 총액
    locked_balance      BIGINT         NOT NULL DEFAULT 0,                   -- LOCKED 보증금 총액
    version             BIGINT         NOT NULL DEFAULT 0,
    created_at          DATETIME(6)    NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at          DATETIME(6)    NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uq_pa_member_id (member_id),
    CONSTRAINT fk_pa_member
       FOREIGN KEY (member_id) REFERENCES member (id)
           ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;


-- =============================================================
-- 6. POINT_HISTORY
-- Append-only authoritative ledger / source of truth
-- idempotency_key: 중복 반영 방지 (unique)
-- =============================================================
CREATE TABLE point_history (
    id                 BIGINT          NOT NULL AUTO_INCREMENT,
    member_id          BIGINT          NOT NULL,
    amount             BIGINT          NOT NULL,                             -- 증감 금액 (양수/음수 모두 가능)
    available_after    BIGINT          NOT NULL,                             -- 반영 후 available snapshot
    reserved_after     BIGINT          NOT NULL,                             -- 반영 후 reserved snapshot
    locked_after       BIGINT          NOT NULL,                             -- 반영 후 locked snapshot
    transaction_type   VARCHAR(40)     NOT NULL,                             -- POINT_CHARGE | CREW_DEPOSIT_RESERVE | CREW_RESERVE_RELEASE | CREW_SETTLEMENT_REFUND
    reference_type     VARCHAR(40)     NOT NULL,                             -- POINT_CHARGE | CREW_PARTICIPANT | SETTLEMENT_ITEM
    reference_id       BIGINT          NOT NULL,
    idempotency_key    VARCHAR(160)    NOT NULL,                             -- 이벤트별 고정 규칙 키; NOT NULL UNIQUE
    created_at         DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uq_ph_idempotency_key         (idempotency_key),
    INDEX idx_ph_member_created_at           (member_id, created_at),
    INDEX idx_ph_reference_type_reference_id (reference_type, reference_id),
    CONSTRAINT fk_ph_member
       FOREIGN KEY (member_id) REFERENCES member (id)
           ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;


-- =============================================================
-- 7. CREW
-- 크루 모집/진행/종료 루트 aggregate
-- settlement_status 저장 컬럼 없음 — Settlement-design §5.3 projection
-- =============================================================
CREATE TABLE crew (
    id                      BIGINT          NOT NULL AUTO_INCREMENT,
    host_member_id          BIGINT          NOT NULL,
    title                   VARCHAR(100)    NOT NULL,
    description             TEXT            NOT NULL,
    image_s3_key            VARCHAR(255)    NULL,                            -- NULL이면 카테고리 fallback 이미지
    category                VARCHAR(30)     NOT NULL,                        -- catalog 형태 deferred; 컬럼 존재만 freeze
    host_agreement_snapshot JSON            NOT NULL,                        -- 동의서 당시 표현 audit snapshot; payload shape deferred
    host_agreement_version  VARCHAR(20)     NOT NULL,
    host_agreed_at          DATETIME(6)     NOT NULL,
    status                  VARCHAR(20)     NOT NULL DEFAULT 'RECRUITING',   -- RECRUITING | ACTIVE | CLOSED | CANCELLED
    deposit_amount          BIGINT          NOT NULL,
    min_participants        INT             NOT NULL DEFAULT 2,
    max_participants        INT             NOT NULL,
    recruitment_deadline    DATETIME(6)     NOT NULL,
    start_at                DATETIME(6)     NOT NULL,                        -- system auto-activation 기준 시각
    activated_at            DATETIME(6)     NULL,                            -- 실제 ACTIVE 전이 시각 (system authority)
    end_at                  DATETIME(6)     NOT NULL,
    created_at              DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at              DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    INDEX idx_crew_host_created_at          (host_member_id, created_at),
    INDEX idx_crew_status_recruitment       (status, recruitment_deadline),
    INDEX idx_crew_status_start_end         (status, start_at, end_at),
    INDEX idx_crew_status_activated_at      (status, activated_at),
    CONSTRAINT chk_crew_participants
        CHECK (min_participants >= 2
            AND min_participants <= max_participants
            AND max_participants <= 15),
    CONSTRAINT fk_crew_host_member
        FOREIGN KEY (host_member_id) REFERENCES member (id)
            ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;


-- =============================================================
-- 8. CREW_PARTICIPANT
-- 정산 계산 단위 (crew_participant_id)
-- version: optimistic locking
-- released_point_history_id: nullable unique — 현재 사이클 reserve-release evidence
-- =============================================================
CREATE TABLE crew_participant (
    id                          BIGINT         NOT NULL AUTO_INCREMENT,
    crew_id                     BIGINT         NOT NULL,
    member_id                   BIGINT         NOT NULL,
    status                      VARCHAR(30)    NOT NULL DEFAULT 'PENDING',   -- PENDING | LOCKED | REJECTED | CANCELLED | EXPIRED
    deposit_amount              BIGINT         NOT NULL,                     -- crew.deposit_amount 복사 snapshot
    pending_at                  DATETIME(6)    NOT NULL,                     -- 신청/예치 reserve 시각; CANCELLED reopen 시 갱신
    locked_at                   DATETIME(6)    NULL,
    rejected_at                 DATETIME(6)    NULL,
    cancelled_at                DATETIME(6)    NULL,
    expired_at                  DATETIME(6)    NULL,
    released_point_history_id   BIGINT         NULL,                         -- nullable unique; 현재 사이클 reserve-release ledger FK
    version                     BIGINT         NOT NULL DEFAULT 0,
    created_at                  DATETIME(6)    NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at                  DATETIME(6)    NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uq_cp_crew_member              (crew_id, member_id),
    UNIQUE KEY uq_cp_released_point_history   (released_point_history_id),   -- nullable unique
    INDEX idx_cp_crew_status                  (crew_id, status),
    INDEX idx_cp_member_status                (member_id, status),
    CONSTRAINT fk_cp_crew
        FOREIGN KEY (crew_id) REFERENCES crew (id)
            ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT fk_cp_member
        FOREIGN KEY (member_id) REFERENCES member (id)
            ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT fk_cp_released_point_history
        FOREIGN KEY (released_point_history_id) REFERENCES point_history (id)
            ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;


-- =============================================================
-- 9. CREW_NOTICE
-- 크루 공지 communication metadata
-- lifecycle/settlement authority 없음
-- =============================================================
CREATE TABLE crew_notice (
    id                BIGINT          NOT NULL AUTO_INCREMENT,
    crew_id           BIGINT          NOT NULL,
    author_member_id  BIGINT          NOT NULL,
    title             VARCHAR(100)    NULL,
    content           TEXT            NOT NULL,
    status            VARCHAR(20)     NOT NULL DEFAULT 'VISIBLE',            -- VISIBLE | HIDDEN | DELETED
    created_at        DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at        DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    INDEX idx_cn_crew_status_created      (crew_id, status, created_at),
    INDEX idx_cn_author_created           (author_member_id, created_at),
    CONSTRAINT fk_cn_crew
        FOREIGN KEY (crew_id) REFERENCES crew (id)
            ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT fk_cn_author
        FOREIGN KEY (author_member_id) REFERENCES member (id)
            ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;


-- =============================================================
-- 10. CREW_NOTICE_COMMENT
-- social metadata only
-- =============================================================
CREATE TABLE crew_notice_comment (
    id                BIGINT          NOT NULL AUTO_INCREMENT,
    crew_notice_id    BIGINT          NOT NULL,
    member_id         BIGINT          NOT NULL,
    content           VARCHAR(500)    NOT NULL,
    status            VARCHAR(20)     NOT NULL DEFAULT 'VISIBLE',            -- VISIBLE | HIDDEN | DELETED
    created_at        DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at        DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    INDEX idx_cnc_notice_status_created   (crew_notice_id, status, created_at),
    INDEX idx_cnc_member_created          (member_id, created_at),
    CONSTRAINT fk_cnc_notice
        FOREIGN KEY (crew_notice_id) REFERENCES crew_notice (id)
            ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT fk_cnc_member
        FOREIGN KEY (member_id) REFERENCES member (id)
            ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;


-- =============================================================
-- 11. CREW_NOTICE_REACTION
-- social metadata only; reaction_type은 FE-selected emoji string
-- =============================================================
CREATE TABLE crew_notice_reaction (
    id                BIGINT         NOT NULL AUTO_INCREMENT,
    crew_notice_id    BIGINT         NOT NULL,
    member_id         BIGINT         NOT NULL,
    reaction_type     VARCHAR(20)    NOT NULL,                               -- FE-selected emoji grapheme/token; trim/blank/length 검증만
    created_at        DATETIME(6)    NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at        DATETIME(6)    NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uq_cnr_notice_member_type  (crew_notice_id, member_id, reaction_type),
    INDEX idx_cnr_notice_id               (crew_notice_id),
    INDEX idx_cnr_member_created          (member_id, created_at),
    CONSTRAINT fk_cnr_notice
        FOREIGN KEY (crew_notice_id) REFERENCES crew_notice (id)
            ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT fk_cnr_member
        FOREIGN KEY (member_id) REFERENCES member (id)
            ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;


-- =============================================================
-- 12. MISSION_RULE
-- 방 1:1. frequency_type MVP active: DAILY | SPECIFIC_DAYS
-- daily_settlement_type A/B/C: cadence anchor
-- =============================================================
CREATE TABLE mission_rule (
    id                      BIGINT         NOT NULL AUTO_INCREMENT,
    crew_id                 BIGINT         NOT NULL,
    frequency_type          VARCHAR(20)    NOT NULL,                         -- DAILY | SPECIFIC_DAYS (WEEKLY_N: Deferred)
    frequency_count         INT            NULL,                             -- Deferred WEEKLY_N reference only; MVP active 미사용
    daily_settlement_type   CHAR(1)        NOT NULL,                         -- A | B | C
    created_at              DATETIME(6)    NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at              DATETIME(6)    NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uq_mr_crew_id (crew_id),
    CONSTRAINT fk_mr_crew
        FOREIGN KEY (crew_id) REFERENCES crew (id)
            ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;


-- =============================================================
-- 13. MISSION_SCHEDULE_DAY
-- SPECIFIC_DAYS 허용 요일 (반복 요일, 특정 날짜 아님)
-- day_of_week: 1=MONDAY ... 7=SUNDAY
-- =============================================================
CREATE TABLE mission_schedule_day (
    id                BIGINT       NOT NULL AUTO_INCREMENT,
    mission_rule_id   BIGINT       NOT NULL,
    day_of_week       TINYINT      NOT NULL,                                 -- 1(MON) ~ 7(SUN)
    created_at        DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uq_msd_rule_day (mission_rule_id, day_of_week),
    CONSTRAINT chk_msd_day_of_week
        CHECK (day_of_week BETWEEN 1 AND 7),
    CONSTRAINT fk_msd_mission_rule
        FOREIGN KEY (mission_rule_id) REFERENCES mission_rule (id)
            ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;


-- =============================================================
-- 14. MISSION_LOG
-- 인증 업로드 원본 로그 / 정산 계산 입력
-- append-only 성격 (기존 row overwrite 금지)
-- moderator_id → member(host)
-- =============================================================
CREATE TABLE mission_log (
    id                      BIGINT          NOT NULL AUTO_INCREMENT,
    crew_participant_id     BIGINT          NOT NULL,
    image_url               VARCHAR(500)    NULL,                            -- 조회용 serving URL; nullable
    image_s3_key            VARCHAR(255)    NOT NULL,                        -- 저장소 키; caption과 함께 필수
    caption                 VARCHAR(100)    NOT NULL,                        -- 5~100자 필수 인증 텍스트
    image_hash              CHAR(64)        NULL,                            -- 서버 계산 SHA-256; fraud signal; nullable
    server_time             DATETIME(6)     NOT NULL,                        -- 서버 수신 시각 (정산 cutoff 기준)
    exif_taken_at           DATETIME(6)     NULL,                            -- 서버가 S3 object에서 추출; nullable
    certification_status    VARCHAR(20)     NOT NULL DEFAULT 'PENDING_REVIEW', -- PENDING_REVIEW | SUCCESS | FAILED
    failure_reason          VARCHAR(50)     NULL,                            -- EXIF_MISSING | EXIF_TIME_INVALID | BEFORE_START | AFTER_END
    moderator_id            BIGINT          NULL,                            -- host moderation 결정자 FK
    moderator_decided_at    DATETIME(6)     NULL,
    decision_type           VARCHAR(20)     NULL,                            -- MANUAL_APPROVE | MANUAL_REJECT | AUTO_APPROVE | AUTO_REJECT
    reject_reason_code      VARCHAR(30)     NULL,                            -- TIME_VIOLATION | DUPLICATE | MISSION_MISMATCH | UNCLEAR | INAPPROPRIATE | OTHER
    reject_memo             VARCHAR(50)     NULL,                            -- OTHER 시 필수; internal/private; 50자
    created_at              DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at              DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    INDEX idx_ml_participant_server_time          (crew_participant_id, server_time),
    INDEX idx_ml_participant_status_server_time   (crew_participant_id, certification_status, server_time),
    CONSTRAINT chk_ml_caption_length
        CHECK (CHAR_LENGTH(caption) BETWEEN 5 AND 100),
    CONSTRAINT fk_ml_crew_participant
        FOREIGN KEY (crew_participant_id) REFERENCES crew_participant (id)
            ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT fk_ml_moderator
        FOREIGN KEY (moderator_id) REFERENCES member (id)
            ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;


-- =============================================================
-- 15. MODERATION_HISTORY
-- host moderation 결정 append-only audit chain
-- UPDATE/DELETE 금지; 변경은 새 row INSERT
-- =============================================================
CREATE TABLE moderation_history (
    id                    BIGINT         NOT NULL AUTO_INCREMENT,
    mission_log_id        BIGINT         NOT NULL,
    before_state          JSON           NULL,                               -- 변경 직전 effective state snapshot
    after_state           JSON           NOT NULL,                           -- 변경 직후 effective state snapshot
    decision_type         VARCHAR(20)    NOT NULL,                           -- MANUAL_APPROVE | MANUAL_REJECT | AUTO_APPROVE | AUTO_REJECT
    reject_reason_code    VARCHAR(30)    NULL,
    reject_memo           VARCHAR(50)    NULL,                               -- OTHER 시 필수; internal/private
    moderator_id          BIGINT         NOT NULL,
    changed_at            DATETIME(6)    NOT NULL,
    PRIMARY KEY (id),
    INDEX idx_mh_log_changed_at (mission_log_id, changed_at),
    CONSTRAINT fk_mh_mission_log
        FOREIGN KEY (mission_log_id) REFERENCES mission_log (id)
            ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT fk_mh_moderator
        FOREIGN KEY (moderator_id) REFERENCES member (id)
            ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;


-- =============================================================
-- 16. MISSION_LOG_REACTION
-- 인증 SUCCESS 로그 소셜 리액션 / social metadata only
-- reaction_type: FE-selected emoji grapheme/token string
-- =============================================================
CREATE TABLE mission_log_reaction (
    id                BIGINT         NOT NULL AUTO_INCREMENT,
    mission_log_id    BIGINT         NOT NULL,
    member_id         BIGINT         NOT NULL,
    reaction_type     VARCHAR(20)    NOT NULL,                               -- emoji grapheme/token; trim/blank/length 검증
    created_at        DATETIME(6)    NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at        DATETIME(6)    NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uq_mlr_log_member_type   (mission_log_id, member_id, reaction_type),
    INDEX idx_mlr_mission_log_id        (mission_log_id),
    INDEX idx_mlr_member_created        (member_id, created_at),
    CONSTRAINT fk_mlr_mission_log
        FOREIGN KEY (mission_log_id) REFERENCES mission_log (id)
            ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT fk_mlr_member
        FOREIGN KEY (member_id) REFERENCES member (id)
            ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;


-- =============================================================
-- 17. SETTLEMENT
-- 방 종료/취소 후 정산 헤더; crew당 1개 (unique)
-- version: optimistic locking
-- baseline_frozen_at: freeze evidence (retry/recalc 권한 아님)
-- =============================================================
CREATE TABLE settlement (
    id                          BIGINT          NOT NULL AUTO_INCREMENT,
    crew_id                     BIGINT          NOT NULL,
    status                      VARCHAR(20)     NOT NULL DEFAULT 'PENDING',  -- PENDING | RUNNING | SUCCEEDED | FAILED | RETRY_WAIT
    baseline_frozen_at          DATETIME(6)     NOT NULL,                    -- frozen LOCKED participant baseline selection evidence
    batch_run_key               VARCHAR(100)    NULL,                        -- 배치 실행 식별자
    retry_count                 INT             NOT NULL DEFAULT 0,
    total_participants          INT             NOT NULL DEFAULT 0,          -- 정산 대상 LOCKED participant 수
    total_locked_amount         BIGINT          NOT NULL DEFAULT 0,          -- 정산 시점 crew_participant.deposit_amount 합계 스냅샷
    total_recognized_success    INT             NOT NULL DEFAULT 0,          -- 전체 인정 성공 횟수
    total_base_refund_amount    BIGINT          NOT NULL DEFAULT 0,          -- FLOOR 절사 후 지급 합계
    total_remainder_amount      BIGINT          NOT NULL DEFAULT 0,          -- 절사 잔액 합계
    remainder_policy            VARCHAR(30)     NOT NULL DEFAULT 'HOST_REMAINDER', -- MVP fixed: HOST_REMAINDER
    failure_code                VARCHAR(50)     NULL,                        -- INPUT_LOAD_FAILED | CALCULATION_FAILED | POINT_CREDIT_FAILED | DUPLICATE_SETTLEMENT | LOCK_ACQUIRE_FAILED | UNKNOWN
    failure_message             VARCHAR(500)    NULL,
    algorithm_version           VARCHAR(50)     NOT NULL,                    -- 정산 semantic version (e.g. "1.0.0")
    rule_context_snapshot       JSON            NOT NULL,                    -- cadence/timezone/cutoff/lifecycle/remainder/reason context snapshot
    started_at                  DATETIME(6)     NULL,
    finished_at                 DATETIME(6)     NULL,
    created_at                  DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at                  DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    version                     BIGINT          NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uq_settlement_crew_id            (crew_id),
    INDEX idx_settlement_status_retry_created   (status, retry_count, created_at),
    CONSTRAINT fk_settlement_crew
        FOREIGN KEY (crew_id) REFERENCES crew (id)
            ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;


-- =============================================================
-- 18. SETTLEMENT_ITEM
-- 참여자별 정산 스냅샷 + 결과
-- refund_amount: per-item payout source of truth
-- invariant: refund_amount = base_refund_amount + remainder_bonus_amount
-- point_history_id: nullable → SUCCEEDED 시 모두 채워져야 함
-- =============================================================
CREATE TABLE settlement_item (
    id                              BIGINT           NOT NULL AUTO_INCREMENT,
    settlement_id                   BIGINT           NOT NULL,
    crew_participant_id             BIGINT           NOT NULL,
    member_id                       BIGINT           NOT NULL,
    participant_status_snapshot     VARCHAR(20)      NOT NULL DEFAULT 'LOCKED', -- MVP: 항상 LOCKED
    deposit_amount                  BIGINT           NOT NULL,               -- 잠긴 보증금 스냅샷
    success_count_raw               INT              NOT NULL DEFAULT 0,     -- 원시 성공 로그 수
    recognized_success_count        INT              NOT NULL DEFAULT 0,     -- 인정 성공 횟수
    recognized_dates_count          INT              NOT NULL DEFAULT 0,     -- 인정 날짜 수
    excluded_success_count          INT              NOT NULL DEFAULT 0,     -- 제외된 성공 수
    period_start_at                 DATETIME(6)      NOT NULL,
    period_end_at                   DATETIME(6)      NOT NULL,
    share_ratio                     DECIMAL(10, 6)   NOT NULL,               -- 지분율 소수점 6자리
    base_refund_amount              BIGINT           NOT NULL DEFAULT 0,     -- FLOOR 적용 후, remainder 합산 전 기본 환급액
    remainder_bonus_amount          BIGINT           NOT NULL DEFAULT 0,     -- HOST_REMAINDER fixed policy로 host item에 배정된 잔액
    refund_amount                   BIGINT           NOT NULL DEFAULT 0,     -- 최종 지급 source of truth = base_refund_amount + remainder_bonus_amount
    effective_moderation_snapshot   JSON             NULL,                   -- 정산 시점 latest-effective moderation; read-only audit
    moderation_chain_ref            JSON             NULL,                   -- moderation_history chain ref {"latest_id":..., "count":...}
    calculation_reason              JSON             NOT NULL,               -- 포함/제외 근거 vocabulary
    point_history_id                BIGINT           NULL,                   -- nullable; SUCCEEDED 시 모두 채워져야 함
    created_at                      DATETIME(6)      NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at                      DATETIME(6)      NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uq_si_settlement_participant (settlement_id, crew_participant_id),
    UNIQUE KEY uq_si_point_history_id       (point_history_id),              -- nullable unique
    INDEX idx_si_member_id                  (member_id),
    CONSTRAINT fk_si_settlement
        FOREIGN KEY (settlement_id) REFERENCES settlement (id)
            ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT fk_si_crew_participant
        FOREIGN KEY (crew_participant_id) REFERENCES crew_participant (id)
            ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT fk_si_member
        FOREIGN KEY (member_id) REFERENCES member (id)
            ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT fk_si_point_history
        FOREIGN KEY (point_history_id) REFERENCES point_history (id)
            ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT chk_si_refund_invariant
        CHECK (refund_amount = base_refund_amount + remainder_bonus_amount)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;


SET FOREIGN_KEY_CHECKS = 1;

-- =============================================================
-- End of V1__init_schema.sql
-- =============================================================
