# Sprint 4 stories and working-tree audit — 2026-11-19

## Deliverable and story decisions

The updated local workbook is `outputs/sprint4-stories-2026-11-19/EasyLang_User_Stories_Sprint_4_Updated.xlsx`. Sprint 4 remains one contiguous block: US-13–US-23 followed by S4-A–S4-F. Sprint 5 US-24–US-36 starts six rows lower. All Sprint 5 cell values, formats and row heights were compared with the source workbook and matched. Earlier story rows, other sheets and the embedded image were also unchanged. `Sprint 4 QA` now has 37 rows including its header.

No new story ID was needed for the latest report: its product outcomes belong to existing goals. Demo portfolio reassignment is fixture maintenance, not a customer user story.

| Story | Added or clarified acceptance | Delivery evidence | Status |
| --- | --- | --- | --- |
| US-15 | Editor sees responsible versus assisting project roles; only one responsible project is assigned through the service. | Project-first UI and `ChiefEditorAssignmentService`; V8 demotes old duplicate responsible assignments. | **Partial invariant:** sequential service path works and current DB has no duplicates, but no editor-scoped DB guard/concurrency test. Manager assignment UI is Sprint 5. |
| US-16 | Queue identifies Weekly/Final, Cycle N, number of PDF marks and `Estimate needed`; project/kind/text filters find a version. | `ChiefEditorDashboardPage` and filtered queue API. | Delivered. |
| US-17 | Weekly approval leaves the activity active; Final approval after Finish completes it. Return creates the next correction cycle. | Review service and cross-role E2E. | Delivered. |
| US-23 | Whole open queue is compared with saved weekly hours; `exceeds one week` appears above capacity and clears after a higher availability is saved; unscored reviews remain separate. | Dashboard service/UI; live Elias data: 11.90 known hours versus 10.00 available, with one unscored review. | Delivered for known estimates; forecast explicitly incomplete while any estimate is missing. |
| S4-A | Friday Weekly submission while `IN_PROGRESS`; late send, idempotency, next-week version and continuing daily work. | Translator submission service, reminders and E2E. | Delivered. |
| S4-F | Final receipt, checking estimate and correction handoff. | Translator UI and API. | Delivered. |

`Cycle 1/2`, mark counts and `Estimate needed` are meaningful product labels, not developer placeholders. The capacity warning is a required product behavior, so it was retained.

## Verification performed today

- `cd backend && mvn test`: 80 tests, 0 failures/errors, 6 PostgreSQL-gated skips.
- `cd frontend && npm test`: 73 passed.
- `cd frontend && npm run lint`: passed.
- `cd frontend && npm run build`: passed.
- `cd frontend && npm run test:e2e:sprint4-isolated`: 3 PostgreSQL acceptance tests and 2 desktop cross-role E2E tests passed; the runner used and cleaned up a disposable database/services.
- Live local database read-only checks: Flyway version 8; Elias has 1 responsible project, 0 assisting, 4 open reviews, 11.90 known review hours, 1 without an estimate, and 10.00 weekly available hours. Sofia has 1 responsible and 11 assisting project assignments, including the 10 demo projects, and 20 open reviews. No editor currently has more than one responsible assignment.
- Reopened the saved `.xlsx`: ZIP structure valid; Sprint 4/Sprint 5 placement, 37 QA rows and the unchanged Sprint 5 cell/style/image comparison passed. No formulas exist in the workbook.

## Uncommitted changes: keep and sort

The working tree contains the integrated Sprint 4 implementation plus the November 18 refinements and the new V8 assignment policy. These files are not disposable build output. Keep the backend/frontend code, V5–V8 migrations, tests, PDF font resources and isolated E2E runner together for one reviewed Sprint 4 commit. Keep the API/design/run/state/status documentation with that code; screenshots and demo PDFs are review evidence. The original `docs/EasyLang_User_Story_Schedule_Week4_Updated (1).xlsx` is the comparison baseline; the workbook in `outputs/` is the current local deliverable. Older Sprint 4 plans, defense notes and night-runner files are historical records and can be archived in a later documentation cleanup after the implementation is committed.

I removed only the temporary workbook builder used in this session. I did not delete, stage or commit pre-existing product work: the broad uncommitted diff includes user work, and deleting files based only on their untracked status would risk losing it. `docs/SPRINT_5_TEAM_HANDOFF.md` was corrected because it reserved V6–V9 despite V6–V8 already existing; Sprint 5 now reserves V9–V12 in its A/B/D/C merge order. `docs/ARCHITECTURE.md` records the current migrations and the assignment-rule limitation.

## Remaining work and risks

1. The online workbook was **not** updated in this session. Google Drive is not connected in this environment and browser access was unavailable. The local workbook is ready for upload/synchronization.
2. Enforce the one-responsible-project rule under concurrent writes with an editor-scoped database constraint and a concurrency test before a manager assignment API uses it. V8 only cleans old rows; the current service count check can race.
3. Sprint 5 remains unimplemented: Project Manager creation/assignment UI, translator limits of 5 active responsible plus 10 active assisting, additional-editor Review transfer, and error/pace risk flags.
4. `AGENTS.md` introductory Sprint 3 text and some older Sprint 4 planning documents are historical and should not be used as current implementation status; `docs/STATE.md` is the current source of status.

No product code was changed for this story/audit task.
