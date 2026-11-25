WITH ranked_responsible AS (
    SELECT
        id,
        ROW_NUMBER() OVER (
            PARTITION BY activity_id
            ORDER BY assigned_date ASC, id ASC
        ) AS priority
    FROM activity_translators
    WHERE is_responsible = TRUE
)
UPDATE activity_translators assignment
SET is_responsible = FALSE
FROM ranked_responsible ranked
WHERE assignment.id = ranked.id
  AND ranked.priority > 1;

CREATE UNIQUE INDEX uq_activity_single_responsible_translator
    ON activity_translators (activity_id)
    WHERE is_responsible = TRUE;
