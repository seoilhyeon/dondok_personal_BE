-- crew 테이블에 optimistic locking용 version 컬럼 추가
ALTER TABLE crew ADD COLUMN version BIGINT NOT NULL DEFAULT 0 COMMENT 'optimistic locking용';
