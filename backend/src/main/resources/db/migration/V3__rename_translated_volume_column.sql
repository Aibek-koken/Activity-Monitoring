ALTER TABLE work_records
    RENAME COLUMN translated_volume_pages TO translated_volume;

ALTER TABLE work_records
    RENAME CONSTRAINT ck_work_records_translated_volume_pages TO ck_work_records_translated_volume;
