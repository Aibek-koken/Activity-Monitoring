# Sprint 3 Plan — Estimates, reminders, finish and submission

Last updated: 2026-10-22

Status: in progress. Section 5 defaults were adopted for implementation on 2026-10-22. V4 foundation and the weekly-estimate flow are implemented; finish, submission, and reminders remain open.

## 1. Sprint goal

Sprint 3 completes the Translator work-submission flow that starts with Sprint 2 daily records:

```text
Daily work records
        ↓
Weekly remaining-volume estimate
        ↓ remaining = 0
FINISHED
        ↓ translator submits
UNDER_CHECKING
        ↓
Chief Editor workflow in Sprint 4
```

The sprint also adds in-app daily and weekly reminders so required entries are not silently missed.

## 2. Source and traceability

The course sprint distribution assigns Sprint 3 to US-08 through US-12 with the focus “Progress estimates, reminders and submission.” The available software and database designs map the stories as follows:

| Story | Working title | Sprint 3 outcome |
| --- | --- | --- |
| US-08 | Daily work-record reminder | Notify a translator when an active work day has no daily record |
| US-09 | Weekly remaining-text estimate | Responsible translator records the activity's remaining volume for the current week |
| US-10 | Weekly estimate reminder | Notify the responsible translator when the current week's estimate is missing |
| US-11 | Finish activity | Responsible translator can move an activity to `FINISHED` only after reporting zero remaining |
| US-12 | Submit for checking | Responsible translator sends a finished activity to the assigned Chief Editor and status becomes `UNDER_CHECKING` |

The master user-story workbook is not present in this workspace. These titles and acceptance rules are a working interpretation of `../3.2_Software_Design (1).md`, `../3.3_Database_Design.md`, the available sprint distribution, and the current lifecycle. Exact master-backlog wording should be copied into the final course document when the workbook is available.

## 3. What already exists and will be reused

Sprint 3 extends the current layered monolith instead of building a parallel subsystem.

| Existing capability | Reuse in Sprint 3 |
| --- | --- |
| Cookie JWT, CSRF, `TRANSLATOR` route security | Protect every new translator read/write endpoint |
| `Activity`, `ActivityStatus`, assignments, work records | Keep the existing lifecycle and ownership model |
| `TranslatorActivityController` and detail response | Add estimate and allowed-action information to the current detail flow |
| `TranslatorActivityService` assignment checks | Reuse the same “assigned activity or 404” access boundary |
| `WorkspaceLayout`, detail page, breadcrumbs and status badges | Add Sprint 3 controls inside the existing translator workflow |
| Existing API error shape and `ApiError` client | Return consistent validation, conflict, and authorization feedback |
| Flyway V1–V3 | Add V4; never modify already-applied migrations |
| Backend integration tests and frontend Vitest setup | Extend current test suites and add focused service/repository tests |

## 4. Scope

### In scope

- Store one authoritative remaining-volume estimate per activity/week.
- Show the current estimate and estimate history on the activity detail page.
- Allow only the responsible translator to create/update the current week's estimate.
- Allow the responsible translator to finish an eligible activity when the current remaining volume is zero.
- Allow the responsible translator to submit a `FINISHED` activity for checking.
- Create the first `Review` cycle at submission time and assign it to the responsible Chief Editor for the project.
- Persist daily and weekly in-app reminders with duplicate protection.
- Show actionable reminders in the translator workspace and allow them to be marked read.
- Lock translator work/estimate writes after the activity becomes `FINISHED`.
- Add demo data, API examples, backend/frontend tests, E2E coverage, and updated documentation.

### NOT in scope

- Chief Editor queue, review form, result, mistakes, checking time, approval, and corrections — Sprint 4 owns the checking workflow.
- Project/Activity creation and assignment UI — Sprint 5 owns Project Manager staffing.
- Manager enforcement UI for 5 active Responsible + 10 active Assisting limits — Sprint 5.
- Estimated-total trend, stalled-progress detection, risk flags, and translator performance — Sprint 5.
- Email, SMS, push notifications, Kafka, Redis, or a separate notification service — in-app reminders are sufficient for the alpha.
- A new dashboard or a second translator detail route — Sprint 3 extends the existing pages.
- Reopening a finished/submitted activity — this needs an explicit later workflow and is not required by US-08–US-12.
- A final translation-file upload — no file entity or storage requirement exists in the available Sprint 3 sources.

## 5. Recommended product rules to confirm

These defaults make the plan executable while keeping ambiguous source requirements visible.

| Area | Recommended Sprint 3 rule | Reason |
| --- | --- | --- |
| Week boundary | Monday 00:00 through Sunday 23:59 in `Asia/Almaty` | Clear ISO-style weeks and one project business zone |
| Estimate owner | Responsible translator only; assistants have read-only visibility | One activity needs one authoritative remaining-volume value |
| Estimate identity | One row per activity and week; saving again updates that week's row | Prevents conflicting estimates while preserving prior-week history |
| Estimate date | Server calculates `weekStartDate`; the client cannot backdate it | Avoids timezone and tampering inconsistencies |
| Estimate value | Decimal with up to 2 places, minimum 0; no small arbitrary cap while the unit is undefined | Works for pages, words, or another future unit |
| Finish action | Separate explicit confirmation; latest current-week estimate must equal 0 | A mistaken zero does not silently change lifecycle state |
| Submit action | Separate action available only in `FINISHED` | Preserves the documented `FINISHED → UNDER_CHECKING` transition |
| Post-finish edits | Daily records and weekly estimate become read-only after `FINISHED` | Keeps the submitted snapshot internally consistent |
| Daily reminder | Weekdays at/after 18:00 when the translator has active assignments but no daily record anywhere that day | One useful reminder instead of one message per assigned activity |
| Weekly reminder | Friday at/after 16:00 for each active responsible activity missing its current-week estimate | Weekly estimate is activity-specific and owned by the responsible translator |
| Reminder channel | Persisted in-app notification shown in the translator workspace | Meets alpha scope without mail infrastructure |
| Reminder dismissal | User can mark a reminder read; reminders whose requirement is later satisfied are hidden automatically | Avoids stale or noisy messages |

These working rules were accepted as the implementation baseline when Sprint 3 work started. Changes should be recorded in `DECISIONS.md` before the related behavior is changed.

## 6. Domain and state rules

### Activity lifecycle for Sprint 3

```text
ASSIGNED ──daily record──▶ IN_PROGRESS
    │                         │
    └────zero estimate + Finish action────┐
                                          ▼
                                      FINISHED
                                          │
                                   Submit for checking
                                          ▼
                                   UNDER_CHECKING
```

- A weekly estimate does not change activity status by itself.
- `finish` requires the authenticated translator to be the responsible assignment.
- `finish` accepts `ASSIGNED` or `IN_PROGRESS` only and requires the current week's saved estimate to be exactly zero.
- `submit-for-checking` accepts `FINISHED` only and requires one responsible Chief Editor on the parent project.
- Submission creates one `Review` row and changes the activity to `UNDER_CHECKING` in the same transaction.
- Repeated or concurrent finish/submit requests must not create invalid transitions or duplicate review cycles.
- After `FINISHED`, daily records and the weekly estimate are read-only. The UI explains this before confirmation.
- `COMPLETED` remains unreachable from translator APIs; only the future Chief Editor approval flow can complete an activity.

### Authorization matrix

| Action | Responsible translator | Assisting translator | Other translator |
| --- | --- | --- | --- |
| View assigned activity and estimate history | Yes | Yes | No (404) |
| Save current weekly estimate | Yes | No (403) | No (404) |
| Finish activity | Yes, when eligible | No (403) | No (404) |
| Submit for checking | Yes, when eligible | No (403) | No (404) |
| View own daily reminder | Yes | Yes | No |
| Receive weekly estimate reminder | Yes | No | No |
| Mark own reminder read | Yes | Yes | No (404) |

The backend remains authoritative. Frontend visibility is a usability aid, not an authorization control.

## 7. Database plan

Add `V4__create_estimates_notifications_and_reviews.sql`. V1–V3 remain unchanged.

### `weekly_estimates`

- Required FKs: `activity_id`, `translator_id`.
- `week_start_date` is the required Monday date.
- `remaining_text_volume` uses `NUMERIC(14,2)` with a minimum of 0.
- `created_at` and `updated_at` provide audit timestamps.
- Unique `(activity_id, week_start_date)` provides one authoritative estimate per week.
- Indexes support activity history and translator/week lookups.

### `project_chief_editors`

- Columns: `id`, `project_id`, `chief_editor_id`, `is_responsible`.
- Unique `(project_id, chief_editor_id)`.
- A nullable unique `responsible_project_id`, checked against `project_id`, allows at most one responsible Chief Editor per project while allowing multiple assisting editors. This works in both PostgreSQL and the existing H2 test profile.
- Sprint 3 seeds the demo responsible editor; Project Manager assignment UI remains deferred.

### `reviews`

- Columns: `id`, `activity_id`, `chief_editor_id`, `cycle_number`.
- `submitted_at` is required; `checking_date`, `result`, and `mistakes_notes` stay nullable until Sprint 4.
- Unique `(activity_id, cycle_number)` prevents duplicate cycles.
- Sprint 3 creates cycle 1 only. Later correction/resubmission increments the cycle in Sprint 4.

### `notifications`

- Columns: `id`, `user_id`, nullable `activity_id`, `type`, `period_date`, `message`.
- Audit/read fields: `created_at`, `is_read`, nullable `read_at`.
- Initial types: `DAILY_RECORD_REMINDER`, `WEEKLY_ESTIMATE_REMINDER`.
- Daily reminder is user-level (`activity_id` null); weekly reminder points to an activity.
- A required unique `dedupe_key` generated from user/type/period/activity prevents duplicates in PostgreSQL and H2. The database also checks whether `activity_id` is null for daily and present for weekly reminders.
- Index unread notifications by user and creation time.

```text
Project ──< Activity ──< WeeklyEstimate
   │           │
   │           ├──< WorkRecord       (Sprint 2)
   │           └──< Review           (created on submission)
   │
   └──< ProjectChiefEditor >── User(CHIEF_EDITOR)

User(TRANSLATOR) ──< Notification >── optional Activity
```

## 8. Backend architecture

Keep controller → service → repository boundaries.

### Models and repositories

- Add `WeeklyEstimate`, `Notification`, `NotificationType`, `ProjectChiefEditor`, and `Review` models with matching repositories.
- Add narrow queries for current/history estimates, responsible editor lookup, review-cycle uniqueness, unread reminders, and due-work checks.
- Add explicit `Activity.finish()` and `Activity.submitForChecking()` methods that validate prior state; do not expose a generic status setter.
- Lock the activity row during finish/submission with `PESSIMISTIC_WRITE` so concurrent requests serialize.

### Services

Use two new services rather than growing one large service:

1. `TranslatorProgressService`
   - save/update the current weekly estimate;
   - verify responsible assignment;
   - finish after zero remaining;
   - create Review cycle and submit for checking;
   - expose server-computed action permissions.
2. `ReminderService`
   - evaluate daily/weekly reminders using an injected `Clock`;
   - persist reminders idempotently;
   - list visible reminders for the authenticated translator;
   - mark only the translator's own reminder read;
   - suppress reminders whose missing entry was later supplied.

`TranslatorActivityService` keeps list/detail and daily-record responsibilities. It reads estimate history for detail and rejects record writes once the activity is `FINISHED` or later.

### Scheduling and time

- Add a configurable business zone, default `Asia/Almaty`.
- Inject `Clock` so week boundaries and cutoffs are deterministic in tests.
- Run one Spring `@Scheduled` evaluator hourly; it inserts only reminders whose cutoff has passed and unique key is absent.
- Keep the Sprint 2 UTC+14 “latest possible today” validation rule separate; it solves a different global-date problem.
- Do not add queues or distributed locks. Database uniqueness is sufficient for the alpha.

### Failure behavior

| Failure | API behavior | User behavior |
| --- | --- | --- |
| Activity not assigned | 404 | Existing activity-unavailable screen |
| Assistant tries responsible action | 403 | Explain responsible-only action |
| Estimate invalid | 400 with field error | Preserve value and show inline error |
| Finish without current zero | 409 | Explain current week's remaining volume must be 0 |
| No responsible Chief Editor | 409 | Keep `FINISHED`; explain submission cannot be routed |
| Stale/concurrent state | 409 or idempotent current result | Reload detail and show current status |
| Duplicate scheduler run | Ignore database conflict/idempotent insert | No duplicate reminder |
| Other user reads reminder | 404 | Do not reveal ownership |
| Unexpected server error | Existing problem response | Recoverable error and retry |

## 9. API plan

All paths use `/api/v1`, require authentication, and preserve CSRF on writes.

| Method | Path | Purpose |
| --- | --- | --- |
| `PUT` | `/translator/activities/{activityId}/weekly-estimates/current` | Create/update the server-calculated current-week estimate |
| `POST` | `/translator/activities/{activityId}/finish` | Move an eligible activity to `FINISHED` |
| `POST` | `/translator/activities/{activityId}/submit-for-checking` | Create Review cycle and move to `UNDER_CHECKING` |
| `GET` | `/translator/reminders` | List unread, still-relevant reminders |
| `PATCH` | `/translator/reminders/{notificationId}/read` | Mark the current translator's reminder read |

Extend `ActivityDetailResponse` with:

- `weeklyEstimates` — newest first;
- `currentWeek` — server-calculated start/end dates;
- `currentWeekEstimate` — nullable;
- `permissions` — `canEditWorkRecords`, `canEditWeeklyEstimate`, `canFinish`, `canSubmitForChecking`.

The server computes permissions so React does not duplicate lifecycle/role rules. Estimate, finish, and submit endpoints return refreshed `ActivityDetailResponse`, matching Sprint 2 record writes.

## 10. Frontend and UX plan

Sprint 3 reuses existing semantic tokens and Inter typography. It adds no new dashboard statistics, gradients, or visual system.

### Translator activity list

- Show a compact reminder region above search/filters only when reminders exist.
- Group weekly reminders visually while retaining a direct activity link.
- Show one daily reminder linking to the activity list rather than duplicating a card per activity.
- Allow dismiss/read without navigation.
- Preserve URL filters and the compact heading hierarchy.

### Activity detail

Place weekly estimation after activity summary and before daily entry:

```text
Activities / EL-2026-001

Activity title                         Status
[Translated] [Daily records] [Last record] [Assigned]

┌ Weekly remaining ─────────────────────────────────────┐
│ Week 19–25 Oct     Remaining [________ units] [Save] │
│ Latest: 240 units · Updated 22 Oct                   │
│ [Show estimate history]                              │
└───────────────────────────────────────────────────────┘

┌ Record translated volume ─────────────────────────────┐
│ existing Sprint 2 daily form                         │
└───────────────────────────────────────────────────────┘

When remaining = 0:
┌ Ready to finish ──────────────────────────────────────┐
│ Confirm translation is finished.          [Finish]  │
└───────────────────────────────────────────────────────┘

When status = FINISHED:
┌ Ready for checking ───────────────────────────────────┐
│ Records are locked.             [Submit for checking]│
└───────────────────────────────────────────────────────┘
```

- Responsible translator gets the estimate input and lifecycle actions.
- Assistant sees read-only estimate/history and a plain ownership explanation.
- Estimate history starts collapsed, matching daily history.
- `Finish activity` uses inline confirmation describing record locking.
- `Submit for checking` becomes primary only after finish.
- After submission, replace forms with a clear `Under checking` state instead of showing many disabled controls.
- Keep errors beside the action, use `aria-live` for success, and preserve mobile stacking, keyboard focus, and reduced motion.

The interface emphasizes the next required action rather than adding summary-card noise.

## 11. Implementation stages

### Stage 0 — Confirm policy and freeze contracts

- Confirm Section 5 defaults.
- Freeze V4 schema, DTOs, transitions, errors, and reminder schedule.
- Record accepted rules in `docs/DECISIONS.md`.

Exit: no ambiguity about ownership, week calculation, finish/submission, reminder timing, or locking.

### Stage 1 — Migration and domain foundation

- Add V4, models, enums, repositories, fixed-Clock test configuration, and demo Chief Editor assignment. **Completed locally.**
- Validate V1→V4 on a fresh PostgreSQL database and V3→V4 upgrade.

Exit: application starts on clean/upgraded databases and repository constraints pass.

### Stage 2 — Weekly estimate flow

- Add current-week upsert, history, DTO permissions, API examples, UI panel, and tests. **Implemented locally; broader concurrency and E2E checks remain.**
- Cover role rules, week boundaries, decimals, updates, history, CSRF, and concurrent writes.

Exit: responsible translator saves one current estimate; both assigned roles read history.

### Stage 3 — Finish and submission flow

- Add locked transitions, finish, responsible editor lookup, Review creation, submit, write locking, UI actions, and tests.
- Cover invalid states, assistant access, missing editor, duplicate/concurrent requests, and recovery.

Exit: zero estimate → `FINISHED` → Review cycle 1 + `UNDER_CHECKING` works atomically.

### Stage 4 — Reminder flow

- Add persistence, scheduler, due queries, dedupe, list/read APIs, reminder UI, demo cases, and tests.

Exit: missing daily/weekly input creates one relevant reminder; satisfying it removes it from the visible list.

### Stage 5 — Integration, QA and documentation

- Run backend/frontend/PostgreSQL/E2E checks, responsive and keyboard QA.
- Update API, architecture, design, decisions, README, run guide, examples, and state.
- Create `SPRINT_3_STATUS.md` only after Definition of Done passes.

Exit: Sprint 3 is demonstrable end to end and documentation matches behavior.

## 12. Two-week execution schedule

| Day | Main work | Deliverable |
| --- | --- | --- |
| 1 | Confirm rules, API and schema | Approved contract and decisions |
| 2 | V4 migration, models, repositories | Clean/upgrade migration path |
| 3 | Weekly estimate service/API | Backend estimate flow |
| 4 | Estimate UI/history and tests | Working US-09 |
| 5 | Finish transition and tests | Working US-11 |
| 6 | Submission/Review integration | Working US-12 |
| 7 | Reminder scheduler and APIs | Backend US-08/US-10 |
| 8 | Reminder UI and responsive polish | Visible reminders |
| 9 | PostgreSQL, integration and E2E QA | Defects fixed, gates passing |
| 10 | Documentation and defense rehearsal | Sprint status and demo ready |

## 13. Test plan

### Backend and database

- V1→V4 clean migration and V3→V4 upgrade.
- Estimate create/update same week, next-week row, newest-first history.
- Sunday/Monday and year-boundary week calculation with fixed `Clock`.
- Zero/positive decimal, negative, excessive precision, oversized input.
- Responsible, assistant, unassigned translator, wrong role, and CSRF.
- Finish only with current zero and valid starting status.
- Daily/estimate writes rejected after finish/submission.
- Submit creates exactly one Review and changes status atomically.
- Missing responsible editor leaves activity safely `FINISHED`.
- Concurrent finish/submit/estimate requests create no duplicates or lost update.
- Reminder cutoffs, weekdays, inactive/no assignments, satisfied daily record, missing/present weekly estimate.
- Dedupe repeated scheduler runs, reminder ownership, stale-reminder suppression.
- PostgreSQL uniqueness/check constraints and clean V1→V4 plus V3→V4 upgrade.

### Frontend

- Responsible edit vs. assistant read-only estimate.
- Week label, validation, loading, success, field/general errors.
- Collapsed/empty/multi-week estimate history.
- Finish eligibility, confirmation, double-click prevention, conflict recovery.
- Submit only in `FINISHED`, missing-editor error, success to `UNDER_CHECKING`.
- Daily form locks according to server permissions.
- No/daily/single/multiple weekly reminders, read action and links.
- Mobile layout, keyboard order, focus behavior, screen-reader labels.

### End-to-end critical paths

```text
[E2E 1] Login → Activity → Save positive estimate → refresh → value/history preserved
[E2E 2] Save zero → Finish confirmation → FINISHED → Submit → UNDER_CHECKING
[E2E 3] Assistant → estimate read-only → no finish/submit actions
[E2E 4] Due reminder → open target → satisfy requirement → reminder disappears
[E2E 5] Unassigned/wrong-role request → backend rejects access
```

Required gates:

```bash
cd backend && mvn test
cd frontend && npm test
cd frontend && npm run lint
cd frontend && npm run build
docker compose up -d --wait
cd frontend && npm run test:e2e
```

## 14. Performance and reliability constraints

- Expected scale is about 80 translators and 400 activities; one application and PostgreSQL are sufficient.
- Do not add a per-activity query to the list page; history loads only in detail.
- Reminder due checks use batch repository queries, not one query per assignment.
- Add indexes for activity/week estimates and unread notifications.
- Scheduler must be idempotent and safe after restarts.
- State changes and Review creation are transactional.
- Failures must produce explicit logs and recoverable user errors.

## 15. Failure-mode checklist

| Code path | Realistic failure | Prevention/test | User result |
| --- | --- | --- | --- |
| Estimate save | Two tabs save same week | Unique constraint + transaction + concurrency test | One refreshed value |
| Finish | Page has stale estimate | Recheck under row lock + 409 test | Eligibility explanation |
| Submit | Double click creates two reviews | Row lock + unique cycle + disabled button | One submission |
| Submit routing | No responsible editor | Check before status change | Remains `FINISHED` |
| Scheduler | Period evaluated twice | Unique key + idempotency test | No duplicate |
| Scheduler restart | App was down at cutoff | Hourly current-period evaluation | Reminder still created |
| Reminder list | Requirement later completed | Relevance filter test | Stale reminder hidden |
| Week boundary | Client/server dates differ | Server dates + fixed Clock tests | Consistent week |
| Network error | Browser does not know save result | Reload/retry path test | No silent failure |

## 16. Work split and dependencies

| Workstream | Modules | Depends on |
| --- | --- | --- |
| A. Domain foundation | migrations, models, repositories | Stage 0 |
| B. Estimate/finish/submission backend | services, controllers, DTOs, backend tests | A |
| C. Reminder backend | scheduler, services, controller, backend tests | A |
| D. Translator UI | pages, API client, types, CSS, frontend tests | Frozen B/C contracts |
| E. Integration/docs | E2E, docs, API examples | B + C + D |

- Lane A: foundation → estimate/finish/submission backend.
- Lane B: reminder backend after foundation, parallel with latter Lane A work.
- Lane C: mocked UI after contracts freeze.
- Final lane: integrate, run E2E, update docs.

Conflict warning: estimate and reminder UI touch the same pages/types/API client, so keep them in one frontend lane or coordinate ownership.

## 17. Definition of Done

- US-08 through US-12 work against PostgreSQL.
- Responsible translator saves/updates current estimate and sees history.
- Assistant sees but cannot modify authoritative estimate.
- Only zero remaining allows `FINISHED`.
- Only `FINISHED` submits; submission creates one Review and atomically becomes `UNDER_CHECKING`.
- Daily/weekly reminders are relevant, deduplicated, readable, and dismissible.
- Server owns authorization, week calculation, transitions, and validation.
- Writes after finish/submission are rejected consistently.
- Loading, empty, error, success, stale-data, mobile, and keyboard states exist.
- Clean migration and V3 upgrade pass.
- Backend, frontend, lint, build, PostgreSQL profile, and Playwright E2E pass.
- API, architecture, design, decisions, run guide, README, and state match implementation.
- Sprint 4 checking UI and Sprint 5 manager monitoring remain outside scope.

## 18. Defense demonstration

1. Log in as responsible translator.
2. Open an activity; show current week and estimate history.
3. Save a positive estimate and refresh to prove persistence.
4. Save zero, confirm Finish, and show `FINISHED` with locked inputs.
5. Submit and show `UNDER_CHECKING`.
6. Verify exactly one Review cycle routed to the responsible Chief Editor.
7. Log in as assistant; show read-only estimate and absent lifecycle actions.
8. Show reminders, mark one read, and satisfy another so it disappears.
9. Demonstrate a rejected assistant/nonzero/duplicate/CSRF action.

## 19. Documentation outputs

- Update `docs/API.md`, `docs/ARCHITECTURE.md`, `docs/DESIGN.md`, and `docs/DECISIONS.md`.
- Update `docs/run.md`, `README.md`, `docs/STATE.md`, and `backend/src/main/resources/api.http`.
- Create `docs/SPRINT_3_STATUS.md` only after completion.
