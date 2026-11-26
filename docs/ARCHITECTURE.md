# Architecture

Last updated: 2026-11-26

## Project Map

- `backend/` is a Spring Boot 3 API. Entry point: `ActivityMonitoringApplication`.
- `frontend/` is a React + TypeScript Vite app. Entry points: `frontend/src/main.tsx` and `frontend/src/App.tsx`.
- `compose.yaml` runs local PostgreSQL on host port `55432`.
- `backend/src/main/resources/db/migration/` contains Flyway migrations: V1 creates users, V2 adds projects/activities/assignments/work records, V3 renames the translated-volume column, V4 adds weekly estimates, project editor assignments, reviews, and notifications, V5 adds immutable Review document metadata/decisions, V5.1/V5.2 add Sprint 4 rework, V6 adds notification history and record edit requests, V7 adds explicit correction page numbers, and V8 demotes duplicate responsible Chief Editor assignments. Applied migrations are immutable.
- `docs/` contains handoff, architecture, design, API, status, and run notes.

## Backend Structure

The backend is a layered modular monolith:

- `controller` exposes auth/workspace checks, translator activity/record/estimate/lifecycle endpoints, reminder endpoints, Review document access, and Chief Editor dashboard/decision endpoints.
- `service` handles auth, translator activity use cases, weekly estimates, document submission/re-submission, Review decisions, Chief Editor queue/workload queries, and reminder evaluation.
- `repository` owns JPA access to users, projects, activities, assignments, work records, estimates, reviews, and notifications.
- `model` contains users/roles, activities, assignments, records, estimates, typed Review results, document metadata, and notifications.
- `dto` contains request/response records for translator, Review, and Chief Editor APIs.
- `storage` validates PDF/DOCX uploads, stores immutable bytes in a private configurable filesystem root, calculates SHA-256 metadata, and removes newly stored files when the surrounding database transaction rolls back.
- `security` contains the authenticated principal and JWT filter.
- `config` wires Spring Security, CORS, password hashing, CSRF, JWT filter registration, business time, reminder scheduling, and non-production demo seeding.
- `common` contains JSON error shapes and exception/security error writers.

Controllers call services; services use repositories; repositories work with models. Controllers should not query repositories directly.

## Frontend Structure

- `App.tsx` defines routes: `/`, `/login`, `/translator`, `/translator/activities/:activityId`, `/chief-editor`, `/chief-editor/reviews/:reviewId`, `/project-manager`, `/project-manager/requests`, and `*`.
- `auth/` owns in-memory session state and protected routing.
- `lib/api.ts` is the HTTP boundary; it sends cookies and CSRF headers.
- `lib/roles.ts` maps backend roles to labels and routes.
- `pages/` contains landing/login, translator list/detail, Chief Editor queue/detail, Project Manager staffing and edit requests, and 404.
- `types/activity.ts`, `types/reminder.ts`, and `types/review.ts` define translator and Chief Editor API shapes; `lib/activity-format.ts` and `lib/validation.ts` support display and form validation.
- `styles.css` holds shared product styling; `landing.css` holds landing-page styling.

## Data And Auth Flow

1. Browser requests `GET /api/v1/auth/csrf`.
2. Login posts email/password to `POST /api/v1/auth/login` with the CSRF header.
3. Spring Security authenticates against `app_users` using BCrypt.
4. Backend returns safe user fields and sets a JWT in an `HttpOnly`, `SameSite=Lax` cookie.
5. React stores only the safe user profile in memory and redirects by role.
6. `JwtAuthenticationFilter` authenticates later requests from the cookie.
7. `SecurityConfig` enforces role access to each workspace endpoint.

There is no self-registration flow in the current application.

## Sprint 2 Activity Flow

1. `GET /api/v1/translator/activities` scopes results to assignments for the authenticated translator. Optional `q` searches the activity number/name on the server. Assignment role, status, and assigned-date controls currently filter that authorized result in the client and persist in the URL.
2. `GET /api/v1/translator/activities/{id}` checks the same assignment and returns detail, the translator's own work records, and cumulative translated volume.
3. `POST`/`PUT` work-record endpoints validate the signed-in translator's assignment and data. The database enforces one record per translator/activity/date; creating the first record moves `ASSIGNED` to `IN_PROGRESS`.
4. The history date range only changes which records the page displays. It does not change the server's cumulative total or the record form.

## Sprint 3 Progress, Submission, And Reminders

`TranslatorProgressService` locks the activity row for current-week estimate upserts, Finish, and Submit. It verifies the responsible assignment and active state. Finish requires a saved zero estimate for the current Monday–Sunday week; Submit requires `FINISHED` and an active responsible Chief Editor for the project. Review cycle 1 and the `UNDER_CHECKING` transition commit in one transaction. The row lock and database uniqueness protect against simultaneous writes and duplicate reviews.

`TranslatorActivityService` includes estimate history and server-computed action permissions in detail responses. It uses the same activity lock for record writes and rejects writes after `IN_PROGRESS`. Assistants can view estimates but cannot change the authoritative value or transition the activity. The detail permission payload also exposes whether the project has a responsible Chief Editor; `canSubmitForChecking` remains false until that assignment exists, while the submit service independently enforces the same rule.

`BusinessTimeConfig` supplies an injectable `Clock` defaulting to `Asia/Almaty`. `ReminderScheduler` evaluates due work hourly and on startup. `ReminderService` queries due assignments in batches, checks daily records and current-week estimates, and inserts persisted notifications through `NotificationWriter` with a separate transaction. The notification dedupe key handles concurrent scheduler runs. The reminder API returns only unread reminders whose day/week, assignment, and missing work are still relevant. It marks only the owning user's reminder read.

`TranslatorNotificationCenter` is mounted by the translator `WorkspaceLayout`, so reminders stay available on both the list and detail routes. Its bell badge shows the unread count, the popover provides direct links and read actions, and daily versus weekly reminder styling is type-specific. The detail page prioritizes the daily record form and presents the activity identity, project, status, translator role, and personal recorded volume in one banner without duplicating history counts. Zero remaining exposes an explicit Finish confirmation. In `FINISHED`, weekly estimate history is collapsed by default and Submit is active only when a responsible Chief Editor exists; otherwise a disabled assignment-waiting state is shown. After submission, the work forms are replaced by the `UNDER_CHECKING` state. The backend remains authoritative for permissions and transitions.

## Sprint 4 Document Review Flow

Initial submission and correction re-upload both lock the Activity before validating lifecycle state and creating a Review. Each accepted PDF/DOCX is validated in `DocumentUploadService`, written under an opaque UUID key by `PrivateDocumentStorageService`, and attached to one Review with original name, server-selected content type, byte size, and SHA-256. The database transaction creates the Review and changes the Activity together; transaction synchronization deletes the newly written file if that transaction fails. V4's `(activity_id, cycle_number)` uniqueness and V5's `(activity_id, open_slot)` uniqueness protect cycle numbering and allow at most one open Review per Activity.

Review ownership has two distinct meanings. `GET /chief-editor/projects` reflects current project assignments, while an existing Review remains owned by the Chief Editor recorded on that Review even if the project assignment changes. A corrected re-upload creates a new cycle for the project's then-current responsible Chief Editor. The responsible translator can read every cycle and download its document; the recorded editor can read, download, estimate, and decide their cycle. Other authenticated users receive a role failure or a not-found response that does not expose document metadata.

`ChiefEditorReviewService` locks Activity first and Review second before changing expected time or recording a decision. `CORRECTION_REQUESTED` requires nonblank feedback, closes the Review, moves the Activity to `CORRECTION_REQUIRED`, and inserts a responsible-translator notification with a Review-based dedupe key in the same transaction. `APPROVED` closes the Review and moves the Activity to `COMPLETED`. Re-upload is allowed only from `CORRECTION_REQUIRED` when the latest Review requested correction; it preserves all prior metadata and bytes and moves the Activity back to `UNDER_CHECKING`.

The Chief Editor dashboard reads a stable newest-first page of open Reviews and a matching aggregate of open count, known expected hours, and missing-estimate count. It compares that aggregate with manually entered weekly availability; it does not infer deadlines or per-day capacity. PDF documents can be previewed and marked in the browser, while DOCX stays download-only.

## Sprint 4 Review Rework

Sprint 4 adds versioned checking on top of the Sprint 3 lifecycle. Flyway V5 keeps the original immutable Review/document shape; V5.1 adds weekly/final review kinds, in-app review marks, correction pages, rework accounting, and Chief Editor availability; V5.2 allows a correction record with zero first-time pages when it contains rework pages. V1–V5 remain unchanged.

`TranslatorProgressService` locks the Activity row for every submission, finish, and resubmit. A weekly version may be sent from `ASSIGNED`/`IN_PROGRESS`, requires a responsible editor, does not change Activity status, and returns the existing detail when the same SHA-256 document is sent again for the same week. A final version keeps the `FINISHED → UNDER_CHECKING → COMPLETED` flow. Weekly correction keeps the activity active; final correction uses `CORRECTION_REQUIRED`. Only the latest correction can receive rework, and rework pages are tied to its `review_id` on the work record.

`ReviewAnnotationService` owns PDF marks and annotated-PDF generation. Marks store a page number, kind, normalized rectangles, optional selected text, comment, and replacement. Mutations are limited to the Review-recorded editor while the Review is open and the document is a PDF. The shared read route allows only the responsible translator or that editor. The annotated download is generated from the stored original with PDFBox; it never replaces the original and includes an appended notes page. After a decision, annotations are read-only.

`TranslatorActivityService` returns a `rework` summary computed from the latest correction cycle: correction pages, recorded rework, outstanding rework, current-week remaining pages, and the total. Daily records store rework pages and working hours without adding rework to first-time translated pages. UI labels use pages, while existing numeric columns keep their values and precision.

`ChiefEditorDashboardService` reads `chief_editor_availability`, compares it with the known hours of the whole open queue, and reports missing estimates separately. `completeForecast` is false until every open version has an estimate. The dashboard never calls an unscheduled backlog a week.

`ChiefEditorAssignmentService` currently assigns at most one responsible project to an editor on its sequential service path and makes additional assignments assisting. V8 demotes older duplicate responsible assignments. The database does not yet have an editor-scoped uniqueness constraint for this rule, so future manager assignment writes must add concurrency protection and a database guard.

The Chief Editor review page combines a PDF.js viewer, a mark list, expected time, and one decision panel. PDF.js renders the page canvas and a text layer; marks are positioned from normalized coordinates. Highlight/strike tools capture a text selection, while the box tool captures a rectangle for scanned pages. The UI links marks and the document in both directions. DOCX files remain download-only and say so explicitly.

`frontend/src/components/PdfReviewViewer.tsx` loads the PDF only when a review document is shown and fits the initial zoom to the available width, including mobile. `ReviewMarkedDocumentDialog` lets the responsible translator read the same marks without mutating them.

## Notification and daily record approval flow

Flyway V6 adds soft deletion to notifications and a `work_record_edit_requests` table. `NotificationHistoryService` owns paginated history, read state, unread counts, and explicit soft deletion. `WorkRecordEditService` owns requests, manager decisions, and one-time consumption of an approval before `TranslatorActivityService` updates a record. Controllers only map HTTP requests; Spring Security enforces translator and manager routes, while services check record ownership and project manager identity. Notifications use a distinct dedupe key per request transition.

Flyway V7 records explicit correction page numbers alongside the derived correction count. The Chief Editor queue accepts project, kind, and text filters. Annotation counts are batched for each queue page. The translator activity list also batches assignment and progress reads across its activities, avoiding per-row database queries as the demo portfolio grows. The PDF renderer uses translucent highlight graphics for readable exports.
