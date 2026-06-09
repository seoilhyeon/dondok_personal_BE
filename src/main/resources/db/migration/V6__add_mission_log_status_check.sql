UPDATE mission_log
SET decision_type = 'AUTO_REJECT'
WHERE certification_status = 'FAILED'
    AND decision_type IS NULL
    AND failure_reason IS NOT NULL
    AND reject_reason_code IS NULL
    AND reject_memo IS NULL;

ALTER TABLE mission_log
    ADD CONSTRAINT chk_ml_status_decision_reason
        CHECK (
            (
                certification_status = 'FAILED'
                AND (
                    (
                        decision_type = 'MANUAL_REJECT'
                        AND failure_reason IS NULL
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
                        AND failure_reason IS NOT NULL
                        AND reject_reason_code IS NULL
                        AND reject_memo IS NULL
                    )
                )
            )
            OR
            (
                certification_status <> 'FAILED'
                AND failure_reason IS NULL
                AND reject_reason_code IS NULL
                AND reject_memo IS NULL
            )
        );
