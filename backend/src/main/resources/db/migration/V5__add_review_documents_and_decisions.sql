ALTER TABLE reviews ADD COLUMN document_storage_key VARCHAR(160);
ALTER TABLE reviews ADD COLUMN document_original_name VARCHAR(255);
ALTER TABLE reviews ADD COLUMN document_content_type VARCHAR(100);
ALTER TABLE reviews ADD COLUMN document_size_bytes BIGINT;
ALTER TABLE reviews ADD COLUMN document_sha256 VARCHAR(64);
ALTER TABLE reviews ADD COLUMN expected_checking_hours NUMERIC(6, 2);
ALTER TABLE reviews ADD COLUMN decided_at TIMESTAMP WITH TIME ZONE;
ALTER TABLE reviews ADD COLUMN open_slot SMALLINT;

UPDATE reviews
SET open_slot = CASE WHEN result IS NULL THEN 1 ELSE NULL END;

ALTER TABLE reviews ALTER COLUMN open_slot SET DEFAULT 1;

ALTER TABLE reviews ADD CONSTRAINT uq_reviews_document_storage_key
    UNIQUE (document_storage_key);
ALTER TABLE reviews ADD CONSTRAINT uq_reviews_one_open_per_activity
    UNIQUE (activity_id, open_slot);
ALTER TABLE reviews ADD CONSTRAINT ck_reviews_result
    CHECK (result IS NULL OR result IN ('APPROVED', 'CORRECTION_REQUESTED'));
ALTER TABLE reviews ADD CONSTRAINT ck_reviews_open_slot
    CHECK (
        (result IS NULL AND open_slot = 1)
        OR (result IS NOT NULL AND open_slot IS NULL)
    );
ALTER TABLE reviews ADD CONSTRAINT ck_reviews_document_metadata
    CHECK (
        (document_storage_key IS NULL
            AND document_original_name IS NULL
            AND document_content_type IS NULL
            AND document_size_bytes IS NULL
            AND document_sha256 IS NULL)
        OR
        (document_storage_key IS NOT NULL
            AND document_original_name IS NOT NULL
            AND document_content_type IS NOT NULL
            AND document_size_bytes IS NOT NULL
            AND document_size_bytes > 0
            AND document_sha256 IS NOT NULL
            AND CHAR_LENGTH(document_sha256) = 64)
    );
ALTER TABLE reviews ADD CONSTRAINT ck_reviews_expected_checking_hours
    CHECK (expected_checking_hours IS NULL
        OR (expected_checking_hours > 0 AND expected_checking_hours <= 1000));

CREATE INDEX idx_reviews_activity_submitted
    ON reviews (activity_id, submitted_at);
CREATE INDEX idx_reviews_chief_editor_result_submitted
    ON reviews (chief_editor_id, result, submitted_at);

ALTER TABLE notifications DROP CONSTRAINT ck_notifications_type;
ALTER TABLE notifications DROP CONSTRAINT ck_notifications_activity;

ALTER TABLE notifications ADD CONSTRAINT ck_notifications_type CHECK (
    type IN ('DAILY_RECORD_REMINDER', 'WEEKLY_ESTIMATE_REMINDER', 'CORRECTION_REQUESTED')
);
ALTER TABLE notifications ADD CONSTRAINT ck_notifications_activity CHECK (
    (type = 'DAILY_RECORD_REMINDER' AND activity_id IS NULL)
    OR (type IN ('WEEKLY_ESTIMATE_REMINDER', 'CORRECTION_REQUESTED') AND activity_id IS NOT NULL)
);
