-- crew 종료 D-3 알림 배치 쿼리(status = ACTIVE AND end_at between D+3 00:00 and D+4 00:00)를 위한 인덱스
CREATE INDEX idx_crew_status_end_at ON crew (status, end_at);
