# Sprint 4 Rework — PM Report

Date: 2026-11-14 (Asia/Almaty)  
Product: EasyLang Activity Monitoring  
Main checkout: `/Users/kuanyszarylkasyn/Desktop/Edu/SOE 202/soe_202_proj`  
Online workbook: https://docs.google.com/spreadsheets/d/12yScQnrZJ8FfmMFoqgZqo0hOc-9BqBg5/edit

## 1. Story matrix

| ID | User → goal | Delivered outcome |
| --- | --- | --- |
| US-13 | Translator → understand what to fix | Addressed notification opens the reviewed version; feedback, cycle, marks, pages to redo, reviewed file, and unfinished rework are visible. |
| US-14 | Translator → fix and resend | Rework pages/hours are recorded; a new PDF/DOCX creates the next cycle; all prior cycles remain readable. |
| US-15 | Chief Editor → see assigned projects | Only current project assignments and the role in each are shown. |
| US-16 | Chief Editor → find a submitted version | Newest-first queue shows activity/project, weekly/final kind, cycle, marks, estimate state, and submission time. |
| US-17 | Chief Editor → review PDF and decide | In-app PDF marks (highlight, strike, box including scans), comments/replacements, linked list/document, draft editing, expected time, one approve/return decision, repeat checks, immutable closure, DOCX honesty. |
| US-18 | Chief Editor → estimate checking time | Hours/minutes save into queue and workload. |
| US-19 | Chief Editor → store review result | Kept with old ID; “included in US-17”. |
| US-20 | Chief Editor → record mistakes | Kept with old ID; “included in US-17”. |
| US-21 | Chief Editor → return for correction | Kept with old ID; “included in US-17”; feedback + pages required. |
| US-22 | Chief Editor → approve corrected version | Kept with old ID; “included in US-17”; same flow for cycle 2+. |
| US-23 | Chief Editor → honest workload | Weekly availability is saved and compared with the whole open queue; unscored versions are separate. |
| S4-A | Translator → weekly version without Finish | Active weekly PDF, Friday reminder, late send, duplicate-file idempotency, weekly approval does not complete. |
| S4-B | Translator → pages and rework | Pages labels, remaining/rework/total, rework reduces only itself, hours count, estimate history preserved. |

Two new stories were added (S4-A, S4-B); the plan’s maximum of four was not exceeded.

## 2. UX concept delivered

- **Editor:** capacity strip → assigned projects → open-version queue → document workbench. The document is the primary object; marks, expected time, decision, and earlier cycles sit beside it.
- **Translator:** activity state → pages/rework strip → daily work → weekly/final version panels → correction panel → version history. A correction shows marks and pages before rework.
- **States:** loading, empty queue, saving, errors, closed review, missing document, DOCX honesty, duplicate/stale requests.
- **Mobile:** PDF fits width, tools collapse to icons, panels stack, no horizontal overflow.
- **Accessibility:** keyboard focus, visible outlines, semantic labels, mark buttons, dialog Escape handling, text selection in PDF.
- **References used for behavior, not copying:** Smartcat LQA (text/comments link), Acrobat redlining (familiar markup), Asana/monday workload (whole-queue capacity comparison). The visual system remains EasyLang’s existing semantic tokens.

## 3. Online workbook update

Updated the same online `.xlsx` file (not a copy):

- File ID: `12yScQnrZJ8FfmMFoqgZqo0hOc-9BqBg5`
- Name: `EasyLang_User_Story_Schedule_Week4_Updated.xlsx`
- MIME: `application/vnd.openxmlformats-officedocument.spreadsheetml.sheet`
- Drive version after update: `50`
- Uploaded bytes read back identically: SHA-256 `a10cc255b6c4f6c7586598f64dff577a942e4bbc6b8f2b5f6987a0953d9425d3`
- Before file SHA-256: `55f9bafcd2fe41b7f815978067311bbe858d31f53ce644b39dd4832c78212fe8`

Changes:

- US-13–US-18 and US-23 rewritten with one user/goal/outcome and concrete Given/When/Then QATest text.
- US-17 is the single review story. US-19–US-22 keep their old IDs, are titled “… (included in US-17)”, and point to its scenarios. The incorrect `Track Flex Status` title is gone.
- S4-A and S4-B were added as a separate block below the existing table (rows 40–41), not inside Sprint 5.
- A new `Sprint 4 QA` sheet lists detailed Given/When/Then scenarios for all Sprint 4 stories. No other sheet was replaced.

### Sprint 5 unchanged proof

Comparison of the original downloaded workbook and the re-downloaded updated workbook:

- Rows 26–38 (US-24 through US-36): values, formulas, number formats, fonts, fills, borders, alignment, and protection are identical.
- Row heights 26–38 identical.
- Column widths, freeze panes, autofilter `$A$2:$N$38`, merged ranges, and images identical.
- All other sheets (`User Story1`, `US1QATest`, `User Story 2`, `US2QATest`, `...`, `Source Notes`, `Sprint 6 Integration`, `Week 4 Plan`) identical in values and styles.
- Result: **PASS**.

Full proof output is saved in `docs/SPRINT_4_REWORK_SPRINT5_PROOF.txt`.

## 4. Implementation summary

- Integrated the isolated Sprint 4 code into `main` without losing existing local changes; the pre-integration state is backed up as a Git stash plus `/tmp/sprint4-rework-backup/main-worktree.tgz`.
- V5.1 adds weekly/final review kind + week, review marks, correction pages, rework pages/review link, editor availability, and weekly-submission reminder type. V5.2 allows rework-only records with zero first-time pages. V1–V5 untouched.
- Backend adds weekly version submission, PDF-only mark CRUD, generated annotated PDF, availability, weekly/final decisions, correction pages, rework accounting, Friday weekly-submission reminders, and honest workload fields.
- Frontend adds a PDF.js workbench, linked marks, draft notes, annotated download, capacity strip, weekly/final panels, rework field, marked-document dialog, page labels, and mobile fit-to-width behavior.
- DOCX remains downloadable; no fake preview. Original files are immutable.
- Existing daily records, estimates, Finish, reminders, auth, CSRF, role checks, and Sprint 1–3 screens remain working.

## 5. Verification

| Command | Result |
| --- | --- |
| `cd backend && mvn test` | **77 tests, 0 failures, 6 PostgreSQL-gated skips** |
| `cd frontend && npm test` | **70 tests passed** |
| `cd frontend && npm run lint` | passed |
| `cd frontend && npm run build` | passed |
| `cd frontend && npm run test:e2e:sprint4-isolated` | **3 PostgreSQL acceptance tests passed; 2 desktop E2E loops passed** |
| Mobile screenshots at 390 px | 0 px horizontal overflow on editor review and translator correction |

The isolated E2E loop covers:

- final version → return with pages → rework → corrected final → approval → Completed;
- weekly version while active → marks/annotated PDF → return → rework → corrected weekly → weekly approval without completion → Finish → final version → final approval → Completed;
- CSRF/role/ownership denial, forged file rejection, immutable cycle bytes/history.

## 6. Screenshots

All in `docs/screenshots/sprint4/`:

| File | Screen |
| --- | --- |
| `editor-capacity-and-queue.png` | Capacity strip, assigned projects, open-version queue |
| `editor-pdf-marks.png` | PDF workbench with linked marks and decision |
| `editor-closed-review.png` | Closed review history |
| `translator-correction-rework.png` | Correction, marks, pages/rework strip, upload |
| `translator-weekly-version.png` | Weekly version under checking while activity stays active |
| `translator-marked-document.png` | Translator read-only marked PDF dialog |
| `mobile-editor-review.png` | Mobile editor workbench |
| `mobile-translator-correction.png` | Mobile translator correction/rework |

## 7. Known limitations

- Marks and annotated PDFs are PDF-only. DOCX is download-only by design.
- Scanned PDFs have no text layer; the box tool is the intended path.
- PDF text selection is a browser approximation; the original file is never edited.
- Editor capacity is manually entered weekly availability, not a due-date calendar.
- Closed reviews are readable by direct URL but there is no closed-history queue.
- Files live in private filesystem storage; production needs coordinated DB/file backup, retention/orphan handling, secure cookies, a real JWT secret, narrowed CORS, and demo seeding disabled.
- Historical correction rows may have `correction_pages = null`; new decisions require the number.

## 8. PM acceptance checklist

- [x] Every current Sprint 4 story has a visible, working scenario.
- [x] Chief Editor and translator can complete the cycle without external PDF checking.
- [x] Online Sprint 4 stories are corrected; old IDs are preserved.
- [x] Sprint 5 cells are unchanged (value/style/metadata comparison).
- [x] Backend/frontend tests, lint, build, PostgreSQL acceptance, and isolated E2E pass.
- [x] Screenshots and known limitations are provided.
