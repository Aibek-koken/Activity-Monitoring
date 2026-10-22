# Sprint 3 Plan — Translator estimates and reminders

Last updated: 2026-10-22

Status: planned; implementation has not started.

## Goal and source

Give translators a reliable weekly remaining-text estimate, mark their work finished when the responsible translator reports zero remaining, and remind them about missing daily/weekly updates. This is the next slice after the completed local Sprint 2 translator work.

The working scope follows `PROJECT_CONTEXT.md` and the available `../Project 3_Practice CSS 342_01_p Task_3.pdf`, `../3.2_Software_Design (1).md`, and `../3.3_Database_Design.md`. The referenced original six-sprint overview and master user-story file are not present in this workspace. The software/database designs map daily and weekly reminders to US-08/US-10, weekly estimates to US-09, and the finish action to US-11. Confirm exact wording when those source files are available.

## User-facing outcomes

1. On an assigned activity, the translator can see the latest remaining-volume estimate and its history, and submit an estimate for the current week. The interface explains which date/week is being recorded and shows a clear success or validation error.
2. Only the responsible translator can report zero remaining and finish the activity. `FINISHED` means translation work ended and awaits the Sprint 4 checking flow; it is not `COMPLETED`.
3. Translators see timely daily and weekly reminders for assignments that still need updates. Already satisfied or finished assignments do not create noisy repeated reminders.

## Implementation sequence

### 1. Data and domain rules

- Add a new Flyway migration for `weekly_estimates` and, if the chosen reminder delivery needs stored state, a notification table. Never edit V1–V3.
- Define week start, one-estimate-per-week behavior (update vs. append), valid nonnegative decimal range, and whether assistants may submit estimates. The responsible translator must own the zero-remaining finish transition.
- Store weekly estimates so Sprint 5 can calculate estimated total work and trend on the server. Do not present Sprint 2 cumulative volume as a full progress/risk metric, and do not pull the Project Manager monitoring screen into Sprint 3.
- Make the finish transition safe against repeat requests and stale activity state. Preserve history and enforce assignment/role checks server-side.

### 2. API and backend

- Add translator-scoped read/write endpoints for weekly estimates and a finish action; document the exact HTTP contract in `API.md` when implemented.
- Reject access to unassigned activities, invalid dates/volumes, unsupported state transitions, and duplicate weekly submissions according to the chosen rule. Keep CSRF on writes and return the existing JSON error shape.
- Add reminder evaluation on a fixed, configurable schedule, with idempotent delivery per translator/activity/reminder period. Decide delivery channel before building it: the project architecture names email, while the alpha database design permits simple on-screen reminders.

### 3. Translator UI

- Place a concise remaining-volume input and estimate history in the existing activity detail page. Show the finish action only when eligible, with a clear distinction between `FINISHED` and later `COMPLETED`.
- Surface reminders in the translator workspace in a compact, accessible form, linking to the relevant activity. Use existing design tokens, mobile layouts, loading/error/empty states, and keyboard focus patterns.

### 4. Verification and exit criteria

- Backend tests: assigned vs. unassigned access, responsible vs. assistant permissions, valid/invalid estimates, week boundaries, duplicates/updates, zero-remaining transition, repeated/concurrent finish attempts, and reminder deduplication.
- Frontend tests: estimate form/history, pending/success/error/empty states, finish eligibility, reminder links, mobile layout, and keyboard navigation.
- Run `mvn test`, `npm test`, `npm run lint`, `npm run build`, and end-to-end tests with the database/API/web running. Record results in `STATE.md` and mark Sprint 3 complete only after the workflows pass.

## Outside Sprint 3

Chief Editor review and the correction cycle belong to Sprint 4. Project Manager creation/assignment, staffing quotas (5 responsible + 10 assisting), trend monitoring, and risk flags belong to Sprint 5. `COMPLETED` remains reserved for approval after checking.

## Decisions to settle before implementation

- Exact weekly boundary, reminder schedule/time zone, and which days count as required work days.
- Whether an assistant can enter a weekly estimate, and if so which estimate is authoritative for the activity.
- Whether resubmitting in the same week updates the row or creates another history entry.
- Reminder channel and acknowledgement model; the available sources permit different alpha implementations.
- Translation-volume unit, currently unspecified in the source requirements.
