-- Keep the earliest responsible translator on each activity.
UPDATE activity_translators assignment
SET is_responsible = FALSE
WHERE assignment.is_responsible = TRUE
  AND EXISTS (
      SELECT 1
      FROM activity_translators earlier
      WHERE earlier.activity_id = assignment.activity_id
        AND earlier.is_responsible = TRUE
        AND (earlier.assigned_date < assignment.assigned_date
             OR (earlier.assigned_date = assignment.assigned_date AND earlier.id < assignment.id))
  );

-- A nullable unique key works in PostgreSQL and the H2 test database.
ALTER TABLE activity_translators ADD COLUMN responsible_activity_id BIGINT;

UPDATE activity_translators
SET responsible_activity_id = CASE WHEN is_responsible THEN activity_id ELSE NULL END;

ALTER TABLE activity_translators ADD CONSTRAINT uq_activity_single_responsible_translator
    UNIQUE (responsible_activity_id);

ALTER TABLE activity_translators ADD CONSTRAINT ck_activity_translators_responsible
    CHECK ((is_responsible = TRUE AND responsible_activity_id = activity_id)
        OR (is_responsible = FALSE AND responsible_activity_id IS NULL));
