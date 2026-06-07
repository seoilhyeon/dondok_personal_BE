-- mission_log에 제출 시점 risk 판정 결과를 스냅샷으로 저장한다.
-- reEncode가 원본 EXIF를 지운 뒤에도 방장 검수 콘솔이 제출 당시 판정을 그대로 볼 수 있도록 동결한다.
-- exif_taken_at/image_hash(raw 증거)는 그대로 두고, resolved 신호 컬럼만 추가한다.
ALTER TABLE mission_log
    ADD COLUMN exif_risk      VARCHAR(20) NOT NULL DEFAULT 'NORMAL' COMMENT 'NORMAL | MISSING | TIME_INVALID' AFTER exif_taken_at,
    ADD COLUMN duplicate_hash BOOLEAN     NOT NULL DEFAULT FALSE    COMMENT '제출 시점 동일 crew 내 동일 해시 존재 여부' AFTER exif_risk;
