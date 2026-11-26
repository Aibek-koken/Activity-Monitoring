# Sprint 4 Status: Versioned Review, Marks, Pages, and Editor Capacity

Date: 2026-11-14 (Asia/Almaty)  
Scope: US-13 through US-23, plus S4-A and S4-B  
Status: Complete locally; ready for PM review  
PM report: `docs/SPRINT_4_REWORK_REPORT.md`

## Outcome

Sprint 4 now delivers one continuous editorial loop:

1. The responsible translator can send a **weekly version** while the activity stays active, or a **final version** after Finish.
2. The assigned Chief Editor opens the PDF inside EasyLang, highlights or strikes text, draws a box around a scanned area, writes a comment and suggested replacement, then records one decision: approve or return.
3. A return requires feedback and the number of pages to redo. The translator sees the addressed correction, the marks on the submitted version, normal remaining pages, unfinished rework, and total remaining.
4. Rework pages reduce only outstanding correction pages; they never count as first-time translated pages, while their working time is recorded.
5. Weekly approval closes the weekly version but keeps the activity active. Only final approval completes the activity.
6. The Chief Editor can save weekly availability and compare it with the whole open queue; unscored versions are counted separately.

## Story result

| Story | Result |
| --- | --- |
| US-13 Receive Correction Request | Delivered. Notification deep-links to the review; feedback, cycle, marks count, reviewed file, pages to redo, and rework state are visible. |
| US-14 Correct Translation | Delivered. Rework pages/hours can be recorded; a corrected PDF/DOCX creates the next cycle and keeps all previous cycles readable. |
| US-15 View Assigned Project | Delivered. Editor workspace lists only current project assignments and the editor's role in each. |
| US-16 View Submitted Translations | Delivered. Newest-first queue shows weekly/final kind, cycle, marks, estimate state, and submission time. |
| US-17 Check Translation and Decide | Delivered as one story. In-app PDF marks, linked list/document, draft editing, approve/return, pages to redo, repeat checks, immutable closure, and DOCX honesty are covered. |
| US-18 Specify Checking Time | Delivered. Hours/minutes are stored as decimal hours and feed the queue/workload. |
| US-19 Record Review Result (included in US-17) | Kept with old ID; result is a typed `ReviewResult` stored with the version. |
| US-20 Record Mistakes (included in US-17) | Kept with old ID; mistakes are page marks with comments and replacements. |
| US-21 Send Version for Correction (included in US-17) | Kept with old ID; return requires feedback and pages. |
| US-22 Approve Corrected Version (included in US-17) | Kept with old ID; corrected cycles use the same review flow. |
| US-23 View Checking Workload | Delivered. Editor sets weekly available hours; the screen reports known queue hours, remaining hours, over/within verdict, and unscored versions separately. |
| S4-A Send Weekly Translation Version | Delivered. Active weekly submission, Friday reminder, late send, duplicate-file idempotency, and weekly approval without completion. |
| S4-B Track Pages and Rework | Delivered. Pages labels, remaining/rework/total, rework-only records, time accounting, and preserved estimate history. |

The online workbook rows US-13–US-23 were updated in place. US-19–US-22 carry “(included in US-17)” in the title and point to US-17 scenarios. S4-A/S4-B were added as a separate block below the existing table. A dedicated `Sprint 4 QA` sheet lists Given/When/Then scenarios. **No Sprint 5 cell, formula, style, row height, column width, filter, or sheet was changed**; the before/after comparison is in the PM report.

## Lifecycle and rules

```text
ACTIVE (ASSIGNED / IN_PROGRESS)
  ├─ weekly version + required PDF/DOCX
  │    → WEEKLY review opens, activity stays active
  │         ├─ APPROVED → version closed, activity stays active
  │         └─ CORRECTION_REQUESTED + feedback + pages
  │              → translator records rework/time and uploads corrected weekly version
  │              → next WEEKLY cycle, activity stays active
  └─ Finish (zero remaining, no open review, no pending correction)
       → FINISHED → final version + required PDF/DOCX
            → UNDER_CHECKING
                 ├─ APPROVED → COMPLETED
                 └─ CORRECTION_REQUESTED + feedback + pages
                      → CORRECTION_REQUIRED → corrected final version → UNDER_CHECKING
```

- Only the responsible translator can submit, send weekly versions, attach a legacy file, read translator history, or resubmit.
- Only the Chief Editor recorded on a review can read marks, edit marks, download documents, or decide it.
- A weekly version is allowed only while active, with a responsible editor and no open review; a different second version while one is open returns 409.
- Sending the same SHA-256 document again for the same week returns the current detail and creates no new cycle.
- A correction return requires nonblank feedback and 1–10000 pages.
- Marks can be created/edited/deleted only while the review is open and only for a PDF. The decision locks them.
- Original files are immutable. The annotated PDF is generated on download and never replaces the original.

## Schema

V5 (from the earlier Sprint 4 work) remains unchanged. V5.1 and V5.2 are additive:

| Migration | Contents |
| --- | --- |
| V5.1 | `reviews.review_kind`, `reviews.week_start_date`, `reviews.correction_pages`; `review_annotations`; `work_records.rework_pages`, `work_records.rework_review_id`; `chief_editor_availability`; `WEEKLY_SUBMISSION_REMINDER` notification type |
| V5.2 | Allows a work record with zero first-time pages when it contains rework pages |

Existing numeric columns and rows were not converted. New constraints preserve one open review per activity, valid review kinds/weeks, positive correction pages, valid normalized annotation geometry, and rework ranges. Sprint 5 migration reservations remain V6–V9.

## API and UI

The full contract is in `docs/API.md`. New/changed surfaces:

- `POST /translator/activities/{id}/weekly-versions`
- `GET /reviews/{id}/annotations`
- `POST/PUT/DELETE /chief-editor/reviews/{id}/annotations[/{annotationId}]`
- `GET /reviews/{id}/annotated-document`
- `GET/PUT /chief-editor/availability`
- Richer activity detail `rework` and permissions; review cycle `kind`, `weekStartDate`, `correctionPages`, `annotationCount`
- Richer workload response with availability comparison
- Richer decision body with `correctionPages`

UI highlights:

- Chief Editor capacity strip with weekly availability, known queue hours, remaining/over, and missing-estimate separation.
- Version queue with weekly/final labels, cycles, marks, estimate state, and submission time.
- PDF.js workbench with text selection, strikethrough, box, zoom, draft comments/replacements, linked marks, annotated PDF download, expected time, one decision, and earlier cycles.
- Translator pages strip, weekly/final submission panels, correction panel with reviewed file/marks/pages, rework field, corrected upload, and version history.
- Marked-document dialog for the responsible translator.
- Mobile fit-to-width PDF and stacked workbench with no horizontal overflow.
- DOCX reviews are plainly download-only; no fake preview.

## Verification

Fresh commands run on 2026-11-14:

| Command | Result |
| --- | --- |
| `cd backend && mvn test` | 77 tests, 0 failures, 6 PostgreSQL-gated skips |
| `cd frontend && npm test` | 70 tests passed |
| `cd frontend && npm run lint` | passed |
| `cd frontend && npm run build` | passed |
| `cd frontend && npm run test:e2e:sprint4-isolated` | 3 PostgreSQL acceptance tests passed; 2 desktop E2E loops passed |

The isolated run creates a disposable PostgreSQL cluster/database, document root, and random loopback ports, then tears everything down. E2E covers the final-version correction loop and the weekly loop through marks, return, rework, corrected version, weekly approval without completion, final submission, final approval, and Completed status.

Representative screenshots are in `docs/screenshots/sprint4/`: editor capacity/queue, PDF review with marks, translator correction/rework, translator weekly version, marked-document dialog, and mobile editor/translator.

## Limitations

- In-app marks and generated annotated PDFs are PDF-only. DOCX remains download-only.
- The PDF text layer is an in-browser approximation. Scanned pages use the box tool.
- Chief Editor capacity is manually entered weekly availability; there is no due-date calendar or automatic per-day distribution.
- The Chief Editor queue is open-only; closed reviews remain readable by direct URL, not through a closed-history screen.
- Review bytes live in a private filesystem; production needs coordinated database/file backup, retention/orphan handling, secure cookies, a real JWT secret, narrowed CORS, and demo seeding disabled.
- Existing historical `correction_pages` may be null; only new decisions require a page count.
- The demo database was seeded locally for screenshots; the tests and E2E remain independent of those demo rows.
