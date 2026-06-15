ALTER TABLE mission_log_reaction
    MODIFY COLUMN reaction_type VARCHAR(20)
    CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL;

ALTER TABLE crew_notice_reaction
    MODIFY COLUMN reaction_type VARCHAR(20)
    CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL;