# Sprint 4 — review of uploaded translations

Status: approved scope for the night-runner preparation; product implementation pending.
Source: `EasyLang_User_Story_Schedule_Week4_Updated (1).xlsx`, US-13–US-23; Sprint 3 V4 Review model and APIs.

## Product flow

1. The responsible translator finishes work as in Sprint 3, then submits a PDF or DOCX. Submission creates Review cycle 1, assigns the project's responsible Chief Editor and moves Activity `FINISHED → UNDER_CHECKING` in one transaction.
2. The assigned Chief Editor opens their queue and Review detail, sees file metadata and downloads the original. They check it outside EasyLang, enter textual mistakes/feedback, optionally enter expected checking hours, then choose `APPROVED` or `CORRECTION_REQUESTED`.
3. Correction closes that Review, sends an in-app notification to the responsible translator and moves Activity to `CORRECTION_REQUIRED`. The translator sees feedback and uploads a new PDF/DOCX. That creates the next Review cycle with a distinct immutable file and moves Activity to `UNDER_CHECKING`.
4. Approval closes the current Review and moves Activity to `COMPLETED`. Past cycles, files, decisions, feedback and timestamps remain readable and unchanged.

## Domain rules

- Activity status and Review result are separate concepts. Activity uses existing `FINISHED`, `UNDER_CHECKING`, `CORRECTION_REQUIRED`, `COMPLETED`; Review result uses `APPROVED` or `CORRECTION_REQUESTED` (nullable while open). US-19 “Track Flex Status” means persisting the review quality/result; do not add an Activity `FLEX` status.
- Only the responsible translator can submit/re-submit. Assistants cannot. Only the Chief Editor recorded on a Review can read/decide that Review, even if project assignments later change. Project list is scoped to active Chief Editor assignment. The responsible translator can see their own review history and files; nobody can use IDs to read another person's documents.
- Current Sprint 3 open Reviews without a file remain valid. The responsible translator may attach one file to such an open Review exactly once, under a row lock; this does not create a new cycle or alter another Review. No attachment change after that. Normal new submissions require a file in the request and never allow later replacement.
- An open Review's expected checking time can be set by its assigned editor as a positive, bounded number of hours. Closing requires a result; correction requires nonblank mistakes/feedback. Approval may have optional feedback. After closure, result, feedback, expected time and file metadata/content cannot be changed.
- Re-submission is legal only from `CORRECTION_REQUIRED` and only when the latest Review was `CORRECTION_REQUESTED`. The new cycle number is previous + 1. A retry/double click cannot create multiple cycles; lock Activity and enforce database uniqueness `(activity_id, cycle_number)` and at most one open Review per Activity.
- File upload accepts actual PDF or DOCX only, with configurable size cap (default 20 MiB). Validate extension plus content signature/ZIP structure, reject traversal/symlinks/unsafe names and malformed files; generate server-side storage keys, store outside web root, stream downloads with safe `Content-Disposition` and `nosniff`, never execute/parse document macros. Persist metadata and Review link transactionally; clean temporary/orphan files when DB write fails. Do not overwrite prior versions.
- Use one review decision transaction with activity lock and Review lock/version check; stale or duplicate decisions return `409` and do not send duplicate notifications. Persist notification with a unique review-based dedupe key. CSRF remains enabled for multipart and all writes.
- Workload is open assigned Reviews and sum of their expected checking hours, including an “unscheduled/no estimate” count. Do not call the entire backlog “this week” without calendar due dates. Keep dashboard compact: assigned projects, queue, one workload summary.

## API target (`/api/v1`)

| Method | Endpoint | Rule |
| --- | --- | --- |
| POST multipart | `/translator/activities/{id}/submit-for-checking` | Existing route evolves to require `file`; `FINISHED` only; returns refreshed detail. |
| GET | `/translator/activities/{id}/reviews` | Responsible translator's cycle history and feedback. |
| POST multipart | `/translator/activities/{id}/reviews/{reviewId}/legacy-attachment` | One-time attachment for an open Sprint 3 fileless Review only. |
| POST multipart | `/translator/activities/{id}/resubmit-for-checking` | `CORRECTION_REQUIRED` only; creates next cycle. |
| GET | `/chief-editor/projects` | Assigned projects only. |
| GET | `/chief-editor/reviews?status=open` | Assigned Reviews; newest submissions first. |
| GET | `/chief-editor/reviews/{reviewId}` | Review metadata/history needed for checking. |
| GET | `/reviews/{reviewId}/document` | Authorized translator or assigned editor downloads original PDF/DOCX. |
| PUT | `/chief-editor/reviews/{reviewId}/expected-time` | Open Review only, bounded positive hours. |
| POST | `/chief-editor/reviews/{reviewId}/decision` | `{result, feedback}`; closes once and transitions Activity. |
| GET | `/chief-editor/workload` | Open count, sum of known hours, unknown-hours count; no invented weekly claim. |

Keep error conventions: `400` invalid request/file, `401` unauthenticated, `403` wrong role/assigned editor, `404` outside translator ownership or missing object, `409` stale/duplicate transition. Downloads must not leak filename or metadata on unauthorized requests.

## Database and architecture

- New Flyway V5 migration only; never edit V1–V4. Add file metadata bound to each Review (nullable for legacy), expected checking time, decision timestamp, typed result constraint and indexes/uniqueness for cycles/open Reviews. Adapt existing `result`/`mistakes_notes` columns compatibly; preserve old rows. Add correction notification type and unique dedupe key in a compatible migration. Follow actual existing schema names after inspection.
- Spring Boot layers remain controller → service → repository/model, with DTOs for API. Store file bytes in private configurable filesystem storage and immutable metadata in PostgreSQL. File handling, Review closure and notification writes should have clear transaction boundaries and compensation for filesystem failure.
- Reuse `ActivityRepository.findByIdForUpdate`, existing responsible assignment checks, `ProjectChiefEditor`, CSRF/cookie auth, `NotificationWriter`, existing React API helper and semantic design tokens. Avoid business rules in React.

## Scope decisions

Removed from earlier document-heavy plan: DOCX preview, built-in document editor, fragment annotations, collaborative editing, document diff. PDF preview is optional only after all acceptance criteria; downloading both formats is mandatory. No extra dashboard widgets.

Retained: per-cycle immutable file/history, textual feedback, Review result and Activity transitions, Chief Editor assigned-project queue, expected time and simple workload, notification/correction loop, security, concurrency, Flyway, tests, Sprint 3 compatibility.

## Acceptance tests

1. PDF and DOCX submit/download; `FINISHED → UNDER_CHECKING`, exactly one assigned Review/file on concurrent/double submit.
2. Invalid type, forged extension, malformed PDF/DOCX, oversize, unsafe filename and unauthorized download are rejected; no orphan/overwritten file.
3. Assigned editor can record expected hours and close with correction feedback; other editor cannot read/download/decide it. Blank correction feedback rejected.
4. Correction notification reaches only responsible translator; translator sees exact cycle feedback, re-uploads corrected file; cycle 2 preserves cycle 1 bytes, result and notes. No post-submit replacement except one-time legacy attachment.
5. Approval of initial or corrected cycle yields `COMPLETED`; duplicate/stale/concurrent decisions do not mutate history or duplicate notification.
6. Legacy Sprint 3 fileless Review is readable and can receive one authorized attachment; no new cycle; closed Review cannot attach.
7. Chief Editor projects/queue/workload are ownership-scoped; unknown estimates are counted separately and no unscheduled backlog is called a week.
8. Backend tests (including PostgreSQL/Flyway and concurrency), frontend tests, lint/build, security/CSRF and isolated E2E flow pass; failures or unavailable E2E prerequisites are reported honestly in `SPRINT_4_STATUS.md`.

## Night execution

`docs/night-runner/sprint4-tasks.json` splits implementation into sequential, independently checked sessions. The final task updates existing architecture/API/design/state docs and creates `SPRINT_4_STATUS.md` with actual verification evidence. The runner stops only on a real failed gate or completed queue, not between successful user stories.
