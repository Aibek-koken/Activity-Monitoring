-- A Chief Editor is responsible for one project. Additional projects are assisting
-- assignments assigned by a Project Manager when the editor has spare capacity.
-- Keep the earliest responsible assignment per editor and demote the rest.
UPDATE project_chief_editors pce
SET is_responsible = FALSE,
    responsible_project_id = NULL
WHERE pce.is_responsible = TRUE
  AND EXISTS (
      SELECT 1
      FROM project_chief_editors earlier
      WHERE earlier.chief_editor_id = pce.chief_editor_id
        AND earlier.is_responsible = TRUE
        AND earlier.project_id < pce.project_id
  );
