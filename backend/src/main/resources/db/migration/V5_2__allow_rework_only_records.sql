-- A correction may be time-only when the translator records rework pages.
ALTER TABLE work_records DROP CONSTRAINT ck_work_records_translated_volume;
ALTER TABLE work_records ADD CONSTRAINT ck_work_records_translated_volume
    CHECK (translated_volume >= 0 AND translated_volume <= 1000);
