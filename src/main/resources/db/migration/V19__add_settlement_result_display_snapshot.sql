-- 정산 결과 표시값 스냅샷: 정산 시점의 크루 정보/미션 진행일수/참여자 닉네임을 보존한다.
-- 배포 전이라 기존 정산 데이터가 없으므로 backfill 없이 nullable 컬럼만 추가한다.
ALTER TABLE settlement
    ADD COLUMN crew_name       VARCHAR(255) NULL,
    ADD COLUMN crew_started_at DATETIME     NULL,
    ADD COLUMN crew_ended_at   DATETIME     NULL,
    ADD COLUMN mission_days    INT          NULL;

ALTER TABLE settlement_item
    ADD COLUMN nickname VARCHAR(100) NULL;
