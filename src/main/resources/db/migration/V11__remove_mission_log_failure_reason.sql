ALTER TABLE mission_log
    DROP CHECK chk_ml_status_decision_reason;

ALTER TABLE mission_log
    DROP COLUMN failure_reason;

ALTER TABLE mission_log
    ADD CONSTRAINT chk_ml_status_decision_reason
        CHECK (
            (
                certification_status = 'FAILED'
                AND (
                    (
                        decision_type = 'MANUAL_REJECT'
                        AND reject_reason_code IS NOT NULL
                        AND (
                            reject_reason_code <> 'OTHER'
                            OR (
                                reject_memo IS NOT NULL
                                AND TRIM(reject_memo) <> ''
                            )
                        )
                    )
                    OR
                    (
                        decision_type = 'AUTO_REJECT'
                        AND (
                            duplicate_hash = TRUE
                            OR exif_risk NOT IN ('NORMAL', 'MISSING')
                        )
                        AND reject_reason_code IS NULL
                        AND reject_memo IS NULL
                    )
                )
            )
            OR
            (
                certification_status <> 'FAILED'
                AND (
                    decision_type IS NULL
                    OR decision_type <> 'AUTO_APPROVE'
                    OR (
                        duplicate_hash = FALSE
                        AND exif_risk IN ('NORMAL', 'MISSING')
                    )
                )
                AND reject_reason_code IS NULL
                AND reject_memo IS NULL
            )
        );
