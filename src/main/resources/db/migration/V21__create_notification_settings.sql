CREATE TABLE notification_settings
(
    id                      BIGINT       NOT NULL AUTO_INCREMENT,
    member_id               BIGINT       NOT NULL,
    cat_emoji_reaction      BOOLEAN      NOT NULL DEFAULT TRUE,
    cat_host_verification   BOOLEAN      NOT NULL DEFAULT TRUE,
    cat_deadline_approaching BOOLEAN     NOT NULL DEFAULT TRUE,
    cat_daily_result        BOOLEAN      NOT NULL DEFAULT TRUE,
    cat_settlement          BOOLEAN      NOT NULL DEFAULT TRUE,
    cat_crew_disbanded      BOOLEAN      NOT NULL DEFAULT TRUE,
    cat_crew_news           BOOLEAN      NOT NULL DEFAULT TRUE,
    quiet_start_time        TIME         NULL,
    quiet_end_time          TIME         NULL,
    created_at              DATETIME(6)  NOT NULL,
    updated_at              DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_notification_settings_member UNIQUE (member_id),
    CONSTRAINT fk_notification_settings_member FOREIGN KEY (member_id) REFERENCES member (id)
);
