ALTER TABLE crew
    ADD COLUMN cancelled_at DATETIME(6) NULL COMMENT '인원 미달 자동 폐쇄 시각' AFTER activated_at;
