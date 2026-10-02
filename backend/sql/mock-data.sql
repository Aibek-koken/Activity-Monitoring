-- =============================================================================
-- EasyLang Activity Monitoring — local demo data generator
-- =============================================================================
-- Dev only. NOT a Flyway migration: this file is never picked up automatically,
-- so it will never run in a shared or production database.
--
-- How to run (PostgreSQL container from the repo docker-compose):
--   docker compose exec -T postgres psql -U easylang -d easylang \
--       < backend/sql/mock-data.sql
--
-- Or from psql:  \i backend/sql/mock-data.sql
--
-- What it creates:
--   * 7 extra translators + 2 project managers + 2 chief editors (login Demo123!)
--   * 6 extra projects
--   * 24 extra activities (all statuses, mixed responsible/assisting)
--   * ~6 weeks of weekday work records per assignment, including records with
--     work_hours = NULL so the "Not entered" path is exercised
--
-- The script is idempotent (ON CONFLICT DO NOTHING): run it as many times as you
-- like, it will never duplicate rows.
--
-- Requires the demo users to already exist (app.seed-demo-users=true), because the
-- generated accounts copy the password hash of translator@easylang.local so every
-- account can log in with the password: Demo123!
--
-- It only touches activities numbered EL-2026-1xx / EL-2026-2xx, so the original
-- Sprint demo data (EL-2026-001/002/003/900) is left untouched.
-- =============================================================================

BEGIN;

-- -----------------------------------------------------------------------------
-- 1. Users
--    All generated accounts share the demo password hash -> password: Demo123!
-- -----------------------------------------------------------------------------
INSERT INTO app_users (email, initials, full_name, password_hash, role)
SELECT
    seed.email,
    seed.initials,
    seed.full_name,
    (SELECT password_hash FROM app_users WHERE email = 'translator@easylang.local'),
    seed.role
FROM (
    VALUES
        -- Translators
        ('nina.petrov@easylang.local',  'NP', 'Nina Petrova',      'TRANSLATOR'),
        ('omar.haddad@easylang.local',  'OH', 'Omar Haddad',       'TRANSLATOR'),
        ('lucia.ferrari@easylang.local','LF', 'Lucia Ferrari',     'TRANSLATOR'),
        ('kenji.tanaka@easylang.local', 'KT', 'Kenji Tanaka',      'TRANSLATOR'),
        ('amara.osei@easylang.local',   'AO', 'Amara Osei',        'TRANSLATOR'),
        ('pavel.novak@easylang.local',  'PN', 'Pavel Novak',       'TRANSLATOR'),
        -- Project managers
        ('manager2@easylang.local',     'ID', 'Irina Dmitrieva',   'PROJECT_MANAGER'),
        ('manager3@easylang.local',     'TH', 'Tomas Herrera',     'PROJECT_MANAGER'),
        -- Chief editors
        ('editor2@easylang.local',      'SB', 'Sofia Bianchi',     'CHIEF_EDITOR'),
        ('editor3@easylang.local',      'MR', 'Mateo Ruiz',        'CHIEF_EDITOR')
) AS seed (email, initials, full_name, role)
WHERE EXISTS (SELECT 1 FROM app_users WHERE email = 'translator@easylang.local')
ON CONFLICT (email) DO NOTHING;

-- -----------------------------------------------------------------------------
-- 2. Projects
-- -----------------------------------------------------------------------------
INSERT INTO projects (project_name, project_manager_id, created_date)
SELECT seed.project_name, mgr.id, seed.created_date::date
FROM (
    VALUES
        ('Insurance Portal Localization',       'manager@easylang.local',  CURRENT_DATE - 62),
        ('Medical Device Manuals',              'manager2@easylang.local', CURRENT_DATE - 51),
        ('E-learning Platform Content',         'manager@easylang.local',  CURRENT_DATE - 44),
        ('Banking Mobile App Strings',          'manager3@easylang.local', CURRENT_DATE - 37),
        ('Automotive HMI Glossary',             'manager2@easylang.local', CURRENT_DATE - 26),
        ('Annual Report 2026',                  'manager@easylang.local',  CURRENT_DATE - 18)
) AS seed (project_name, manager_email, created_date)
JOIN app_users mgr ON mgr.email = seed.manager_email
ON CONFLICT (project_name) DO NOTHING;

-- -----------------------------------------------------------------------------
-- 3. Activities
--    Statuses are spread over the full enum so filtering/status pills are visible.
-- -----------------------------------------------------------------------------
INSERT INTO activities (activity_number, activity_name, project_id, status, created_date)
SELECT seed.activity_number, seed.activity_name, p.id, seed.status, seed.created_date::date
FROM (
    VALUES
        -- Insurance Portal Localization
        ('EL-2026-101', 'Policy wording review',                'Insurance Portal Localization',       'FINISHED',           CURRENT_DATE - 60),
        ('EL-2026-102', 'Claims form labels',                   'Insurance Portal Localization',       'COMPLETED',          CURRENT_DATE - 58),
        ('EL-2026-103', 'Insurance glossary build',             'Insurance Portal Localization',       'UNDER_CHECKING',     CURRENT_DATE - 40),
        ('EL-2026-104', 'Complaint centre scripts',             'Insurance Portal Localization',       'CORRECTION_REQUIRED',CURRENT_DATE - 33),
        -- Medical Device Manuals
        ('EL-2026-105', 'Infusion pump IFU',                     'Medical Device Manuals',              'IN_PROGRESS',        CURRENT_DATE - 49),
        ('EL-2026-106', 'MRI scanner user manual',              'Medical Device Manuals',              'IN_PROGRESS',        CURRENT_DATE - 45),
        ('EL-2026-107', 'Sterilisation warnings',               'Medical Device Manuals',              'FINISHED',           CURRENT_DATE - 30),
        ('EL-2026-108', 'Device symbols and pictograms',        'Medical Device Manuals',              'ASSIGNED',           CURRENT_DATE - 12),
        -- E-learning Platform Content
        ('EL-2026-109', 'Onboarding module captions',           'E-learning Platform Content',         'IN_PROGRESS',        CURRENT_DATE - 41),
        ('EL-2026-110', 'Compliance course scripts',            'E-learning Platform Content',         'UNDER_CHECKING',     CURRENT_DATE - 28),
        ('EL-2026-111', 'Certification quiz text',              'E-learning Platform Content',         'ASSIGNED',           CURRENT_DATE - 9),
        ('EL-2026-112', 'Instructor notes',                      'E-learning Platform Content',         'ASSIGNED',           CURRENT_DATE - 5),
        -- Banking Mobile App Strings
        ('EL-2026-113', 'Payment flow microcopy',                'Banking Mobile App Strings',          'IN_PROGRESS',        CURRENT_DATE - 35),
        ('EL-2026-114', 'Error message catalogue',               'Banking Mobile App Strings',          'CORRECTION_REQUIRED',CURRENT_DATE - 22),
        ('EL-2026-115', 'Biometric login screens',               'Banking Mobile App Strings',          'CORRECTED',          CURRENT_DATE - 20),
        ('EL-2026-116', 'Statement e-mail templates',            'Banking Mobile App Strings',          'FINISHED',           CURRENT_DATE - 15),
        ('EL-2026-117', 'Accessibility alt texts',               'Banking Mobile App Strings',          'ASSIGNED',           CURRENT_DATE - 4),
        -- Automotive HMI Glossary
        ('EL-2026-118', 'Dashboard terminology',                 'Automotive HMI Glossary',             'IN_PROGRESS',        CURRENT_DATE - 24),
        ('EL-2026-119', 'Navigation voice prompts',              'Automotive HMI Glossary',             'IN_PROGRESS',        CURRENT_DATE - 19),
        ('EL-2026-120', 'Safety alert wording',                  'Automotive HMI Glossary',             'UNDER_CHECKING',     CURRENT_DATE - 11),
        ('EL-2026-121', 'Charging system messages',               'Automotive HMI Glossary',             'ASSIGNED',           CURRENT_DATE - 3),
        -- Annual Report 2026
        ('EL-2026-122', 'Letter to shareholders',                'Annual Report 2026',                  'IN_PROGRESS',        CURRENT_DATE - 14),
        ('EL-2026-123', 'Financial highlights section',          'Annual Report 2026',                  'ASSIGNED',           CURRENT_DATE - 7),
        ('EL-2026-124', 'Sustainability appendix',               'Annual Report 2026',                  'ASSIGNED',           CURRENT_DATE - 2)
) AS seed (activity_number, activity_name, project_name, status, created_date)
JOIN projects p ON p.project_name = seed.project_name
ON CONFLICT (activity_number) DO NOTHING;

-- -----------------------------------------------------------------------------
-- 4. Assignments
--    Each activity gets one responsible translator plus up to 2 assisting ones,
--    so both the "Responsible" and "Assisting" labels are exercised.
--    Translator selection is deterministic (based on the activity id), so re-runs
--    stay stable. The translator count is resolved dynamically, which means every
--    translator gets work no matter how many demo accounts exist.
-- -----------------------------------------------------------------------------
INSERT INTO activity_translators (activity_id, translator_id, is_responsible, assigned_date)
SELECT a.id, slot.translator_id, slot.is_responsible, a.created_date
FROM activities a
CROSS JOIN LATERAL (
    -- slot 0 -> responsible, slots 1-2 -> assisting
    SELECT tr.id AS translator_id, (slots.slot_index = 0) AS is_responsible
    FROM generate_series(0, 2) AS slots (slot_index)
    JOIN LATERAL (
        SELECT u.id
        FROM app_users u
        WHERE u.role = 'TRANSLATOR'
        ORDER BY u.id
        OFFSET MOD(a.id + slots.slot_index * 3,
                   (SELECT count(*) FROM app_users WHERE role = 'TRANSLATOR'))
        LIMIT 1
    ) tr ON TRUE
) AS slot
WHERE a.activity_number ~ '^EL-2026-1[0-9]{2}$'
ON CONFLICT (activity_id, translator_id) DO NOTHING;

-- -----------------------------------------------------------------------------
-- 5. Work records
--    ~6 weeks of weekday records per assignment, in the 0.01 - 1000 volume range
--    and 0 - 24 work hours range required by the DB checks.
--      * ~15% of records deliberately have work_hours = NULL
--      * a few have work_hours = 0.00 (boundary value)
--      * non-zero work_hours are generated from whole minutes, then converted to
--        decimal hours because the database stores hours as NUMERIC(5, 2)
--      * volumes are random decimals, so decimal formatting gets exercised
--    Records stop at yesterday, so every activity still has no record for today
--    and the "create today's record" flow stays available for manual testing.
-- -----------------------------------------------------------------------------
INSERT INTO work_records (activity_id, translator_id, record_date, translated_volume, work_hours)
SELECT
    at.activity_id,
    at.translator_id,
    day.ts::date AS record_date,
    ROUND((1 + RANDOM() * 60)::numeric, 2) AS translated_volume,
    CASE
        WHEN RANDOM() < 0.15 THEN NULL
        WHEN RANDOM() < 0.20 THEN 0.00
        ELSE ROUND(((30 + FLOOR(RANDOM() * 481)) / 60.0)::numeric, 2)
    END AS work_hours
FROM activity_translators at
JOIN activities a ON a.id = at.activity_id
CROSS JOIN generate_series(
    CURRENT_DATE - INTERVAL '42 days',
    CURRENT_DATE - INTERVAL '1 day',
    INTERVAL '1 day'
) AS day (ts)
WHERE a.activity_number ~ '^EL-2026-1[0-9]{2}$'
  AND EXTRACT(ISODOW FROM day.ts) BETWEEN 1 AND 5
ON CONFLICT (activity_id, translator_id, record_date) DO NOTHING;

COMMIT;

-- -----------------------------------------------------------------------------
-- 6. Report
-- -----------------------------------------------------------------------------
SELECT
    (SELECT count(*) FROM app_users)                                          AS users,
    (SELECT count(*) FROM projects)                                           AS projects,
    (SELECT count(*) FROM activities)                                         AS activities,
    (SELECT count(*) FROM activity_translators)                               AS assignments,
    (SELECT count(*) FROM work_records)                                       AS work_records,
    (SELECT count(*) FROM work_records WHERE work_hours IS NULL)             AS records_without_hours,
    (SELECT count(*) FROM work_records WHERE work_hours = 0)                 AS records_with_zero_hours;

-- Logins created by this script (all use the password Demo123!):
--   translator@easylang.local, translator2@easylang.local,
--   nina.petrov@easylang.local, omar.haddad@easylang.local,
--   lucia.ferrari@easylang.local, kenji.tanaka@easylang.local,
--   amara.osei@easylang.local, pavel.novak@easylang.local
