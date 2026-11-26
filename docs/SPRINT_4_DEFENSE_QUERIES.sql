-- Sprint 4 defense: read-only checks for the three seeded local activities.
-- Seed with: python3 scripts/seed_sprint4_defense.py (Sprint 4 worktree API must run).
-- US-22 is intentionally pending until you approve EL-S4-903 in the UI.

-- Quick view of all six responsible demo activities on the translator list.
SELECT a.activity_number, a.activity_name, a.status, u.email AS translator,
       COUNT(r.id) AS review_cycles
FROM activities a
JOIN activity_translators at ON at.activity_id = a.id AND at.is_responsible
JOIN app_users u ON u.id = at.translator_id
LEFT JOIN reviews r ON r.activity_id = a.id
WHERE a.activity_number IN ('EL-S4-901', 'EL-S4-902', 'EL-S4-903',
                            'EL-S4-904', 'EL-S4-905', 'EL-S4-906')
GROUP BY a.activity_number, a.activity_name, a.status, u.email
ORDER BY a.activity_number;

-- Additional translator cards changed from Finished/Completed to In progress.
SELECT activity_number, activity_name, status
FROM activities
WHERE activity_number IN ('EL-2026-117', 'EL-2026-112', 'EL-2026-002',
                          'EL-2026-001', 'EL-2026-106', 'EL-2026-101')
ORDER BY activity_number;

-- US-13: correction notification and feedback visible to Maya.
SELECT n.id, u.email, n.type, n.message, n.is_read, n.created_at,
       r.cycle_number, r.mistakes_notes
FROM notifications n
JOIN app_users u ON u.id = n.user_id
JOIN activities a ON a.id = n.activity_id
JOIN reviews r ON r.activity_id = a.id
              AND r.result = 'CORRECTION_REQUESTED'
WHERE a.activity_number = 'EL-S4-902'
  AND u.email = 'translator@easylang.local'
  AND n.type = 'CORRECTION_REQUESTED'
ORDER BY n.created_at DESC;

-- US-14: two immutable review cycles; SHA-256 values should differ.
SELECT a.activity_number, a.status, r.cycle_number, r.result,
       r.document_original_name, r.document_sha256, r.submitted_at
FROM reviews r
JOIN activities a ON a.id = r.activity_id
WHERE a.activity_number = 'EL-S4-903'
ORDER BY r.cycle_number;

-- US-15: current Chief Editor project assignment.
SELECT u.email AS chief_editor, p.project_name, pce.is_responsible
FROM project_chief_editors pce
JOIN app_users u ON u.id = pce.chief_editor_id
JOIN projects p ON p.id = pce.project_id
WHERE u.email = 'editor@easylang.local'
ORDER BY p.project_name;

-- US-16: submitted/open Review in Elias's queue.
SELECT a.activity_number, r.id AS review_id, r.cycle_number,
       u.email AS assigned_editor, r.submitted_at
FROM reviews r
JOIN activities a ON a.id = r.activity_id
JOIN app_users u ON u.id = r.chief_editor_id
WHERE a.activity_number IN ('EL-S4-901', 'EL-S4-903')
  AND u.email = 'editor@easylang.local'
  AND r.result IS NULL;

-- US-17: stored document metadata; check actual bytes via the authorized download.
SELECT r.id, r.cycle_number, r.document_original_name,
       r.document_content_type, r.document_size_bytes, r.document_sha256
FROM reviews r
JOIN activities a ON a.id = r.activity_id
WHERE a.activity_number = 'EL-S4-901'
ORDER BY r.cycle_number;

-- US-18: saved expected checking time for an open or closed cycle.
SELECT r.cycle_number, r.expected_checking_hours
FROM reviews r
JOIN activities a ON a.id = r.activity_id
WHERE a.activity_number = 'EL-S4-901'
ORDER BY r.cycle_number;

-- US-19: Review result and decision time, separate from Activity status.
SELECT a.status AS activity_status, r.cycle_number,
       r.result AS review_result, r.decided_at
FROM reviews r
JOIN activities a ON a.id = r.activity_id
WHERE a.activity_number IN ('EL-S4-902', 'EL-S4-903')
ORDER BY r.cycle_number;

-- US-20: recorded mistake/feedback on cycle 1.
SELECT r.cycle_number, r.mistakes_notes
FROM reviews r
JOIN activities a ON a.id = r.activity_id
WHERE a.activity_number = 'EL-S4-902'
  AND r.cycle_number = 1;

-- US-21: correction request closed cycle 1 and notified Maya once.
SELECT a.status, r.result, r.decided_at, u.email AS notified_user,
       n.type, n.dedupe_key
FROM reviews r
JOIN activities a ON a.id = r.activity_id
JOIN notifications n ON n.activity_id = a.id
JOIN app_users u ON u.id = n.user_id
WHERE a.activity_number = 'EL-S4-902'
  AND r.cycle_number = 1
  AND n.type = 'CORRECTION_REQUESTED'
  AND u.email = 'translator@easylang.local';

-- US-22: run after approving the corrected cycle in the UI.
SELECT a.activity_number, a.status, r.cycle_number, r.result, r.decided_at
FROM reviews r
JOIN activities a ON a.id = r.activity_id
WHERE a.activity_number = 'EL-S4-903'
  AND r.cycle_number = 2;

-- US-23: Elias's current open workload. Compare with the dashboard.
SELECT COUNT(*) AS open_count,
       COALESCE(SUM(r.expected_checking_hours), 0) AS known_expected_hours,
       COUNT(*) FILTER (WHERE r.expected_checking_hours IS NULL) AS missing_estimate_count
FROM reviews r
JOIN app_users u ON u.id = r.chief_editor_id
WHERE u.email = 'editor@easylang.local'
  AND r.result IS NULL;
