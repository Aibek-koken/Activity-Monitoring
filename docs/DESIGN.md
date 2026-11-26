# Design

Last updated: 2026-11-26

## Translation workflow references

Use the product's own tokens and components. These references guide information hierarchy and workflow, not a literal visual copy.

| Reference | Role in EasyLang | Source |
| --- | --- | --- |
| Lokalise Incoming task widget | **Current translator UI reference.** Lead with the assigned task and the next useful action; keep remaining work and the explicit Done transition understandable. | https://docs.lokalise.com/en/articles/1457148-incoming-task-widget |
| Smartcat My Tasks and translation progress | Future reference for assignment-level versus whole-document progress once EasyLang has an authoritative total scope and collective progress. | https://help.smartcat.com/linguist-content-review-training/ and https://help.smartcat.com/translation-progress/ |
| Phrase Linguist portal | Future reference for denser assigned-job lists, status views, and filtering when the translator workspace grows. | https://support.phrase.com/hc/en-us/articles/5709735838492-Linguist-TMS |

Current product rule: `progress.totalTranslatedVolume` is the signed-in translator's own recorded volume. The weekly remaining estimate belongs to the activity and is set by its responsible translator. No authoritative total-volume target is stored, so do not infer a percentage or imply that the personal volume is the whole activity's progress.

## Tokens

Source of truth is `frontend/src/styles.css`.

- Font: `Inter Variable`, then Inter/system sans-serif fallbacks.
- Canvas/surfaces: `--background: hsl(215 36% 97%)`, `--surface: hsl(0 0% 100%)`, `--surface-subtle: hsl(214 32% 95%)`.
- Text: `--foreground: hsl(215 35% 16%)`, `--muted: hsl(215 14% 42%)`, `--muted-soft: hsl(215 12% 58%)`.
- Borders: `--border: hsl(214 23% 87%)`, `--border-strong: hsl(214 20% 76%)`.
- Primary: `--primary: hsl(215 36% 25%)`, `--primary-hover: hsl(215 39% 19%)`, `--primary-foreground: hsl(0 0% 100%)`.
- Brand accent: `--brand: hsl(170 55% 37%)`, `--brand-soft: hsl(169 44% 92%)`.
- Error/focus: `--danger: hsl(3 66% 43%)`, `--danger-soft: hsl(4 70% 96%)`, `--focus: hsl(211 84% 47%)`.
- Shape/shadow: `--radius-card: 18px`, `--radius-control: 10px`, `--shadow-card: 0 18px 50px hsl(215 38% 20% / 0.09)`.

Landing-only tokens live in `frontend/src/landing.css`: dark slate hero (`--landing-dark`), raised slate panels (`--landing-raised`), translucent white lines (`--landing-line`), soft text (`--landing-soft`), and teal glow (`--landing-glow`).

## Main Components

- `BrandMark` is the shared logo/wordmark.
- `LoginPage` uses an auth card, field groups, password visibility button, alert, primary submit, back-to-landing button, and quick-access role buttons.
- `LandingPage` uses a full-viewport dark hero, monitoring preview, capability blocks, activity flow, role cards, final CTA, and footer.
- `WorkspacePage` remains the protected starter shell for the Project Manager role; the Chief Editor now uses its dedicated dashboard and Review detail pages.
- `TranslatorActivitiesPage` uses a breadcrumb, a restrained page title, search, and a compact All / Responsible / Assisting assignment filter. These quick assignment views keep `FINISHED` activities visible because submission is still the translator's next action, while `UNDER_CHECKING` activities are hidden after handoff; selecting `Under checking` in the status popover reveals them. A filter button opens status and assigned-date controls; there is no top summary-card row.
- `TranslatorNotificationCenter` keeps reminders in a persistent circular bell at the right side of the workspace top bar on both translator pages. Its badge shows the unread count, and the top bar owns the popover's stacking layer so page controls cannot cover it. The popover distinguishes daily-record reminders with teal and a record icon, weekly-estimate reminders with blue and a calendar icon, and correction requests with a warning treatment and file icon; labels repeat the distinction without relying on color. Correction links open the activity at its feedback/re-upload panel. Each item has an explicit action label, opens its target, and becomes read when opened; the header also supports marking all visible reminders read.
- `TranslatorActivityDetailPage` uses a wider working canvas. The activity banner, the three-part pages/rework strip, and the editable daily-record form share one bordered work surface because they are the page's primary context and action. Weekly versions, final submission, and correction re-upload keep bordered next-step panels because they represent consequential version changes. Daily, estimate, and version histories start collapsed. Daily history date filtering does not change the record form or lifetime personal volume.
- `TranslatorReviewHistory` presents immutable weekly/final cycles newest-first with result, feedback, correction pages, marks count, checking estimate, decision time, file metadata, and original/annotated downloads. It exposes one attachment form only for the newest open fileless legacy Review; prior and closed cycles stay read-only.
- `ChiefEditorDashboardPage` uses a capacity strip above a two-column layout: current assigned projects in a quiet side list and paged open versions as the primary work queue. The strip compares known queue hours with the editor's saved weekly availability, counts unscored versions separately, and never calls the forecast complete while estimates are missing. Queue rows show activity/project identity, weekly/final kind, cycle, marks, expected-time state, and Almaty submission time.
- `ChiefEditorReviewDetailPage` is a document-first workbench: the submitted PDF and mark tools are the primary column; linked marks, expected time, decision, and earlier cycles are the side panel. The original and generated annotated PDF remain downloadable. Closing replaces all edit controls with a read-only result, expected time, correction pages, decision time, and feedback while retaining the document and marks.
- `ProjectManagerDashboardPage` shows translator staffing by project and a replacement action for the responsible translator. `ProjectManagerRequestsPage` holds daily-record edit approvals. Both pages share the existing workspace styling and show both destinations in the manager navigation.
- `FullPageStatus` and `NotFoundPage` provide status/error states.

## UX Rules Already Used

- Keep the application calm, operational, and compact; avoid roadmap/test-status copy in product UI.
- Use semantic CSS variables instead of ad hoc colors.
- Use backend roles as the source of truth; frontend guards are only UX.
- Keep login failures generic so the UI does not reveal whether an email exists.
- Show field-level validation for client-detectable input issues.
- Keep focus styles visible with a 3px focus outline.
- Support responsive layouts around mobile, tablet, and desktop widths.
- Respect `prefers-reduced-motion` for non-essential landing animations.
- Preserve list search, assignment role, status, and assigned-date filters in the URL when opening an activity and returning through the breadcrumb.
- Align daily-record form inputs on the same row even when the optional-hours label wraps; use fewer columns before the form becomes too narrow.
- Use two native date inputs for exact history ranges; empty bounds are open-ended, an inverted range shows an error, and clearing dates restores all records.
- On narrow screens, show history records as compact stacked rows so the date, volume, work time, and edit action remain visible without horizontal page scrolling.

## Sprint 3 UI

The current translator UI applies the task-first hierarchy seen in Lokalise, Smartcat, and Phrase: activity identity and current state come first, while histories stay secondary. Logging a daily record is the primary active-state task. The weekly estimate disclosure keeps its header compact; the field label and save action communicate what to enter without a repeated explanatory paragraph. Saving 0 enables the separate Finish action. The estimate remains read-only for assistants. At 0 remaining, Finish is visible without opening that disclosure. Once the activity leaves `ASSIGNED`/`IN_PROGRESS`, the current-week control is replaced by collapsible weekly estimate history, avoiding a misleading new-week `No estimate yet` state. In `FINISHED`, Submit is green and actionable only when the project has a responsible Chief Editor; otherwise a neutral disabled panel explains the missing assignment. After submission, work forms disappear and the status is clear.

The activity list keeps search and assignment filtering visible. Reminders live in the persistent top-right notification control instead of occupying list content; a daily reminder opens the activity list and a weekly reminder opens the estimate on its target activity. The activity rows label volume as recorded by the signed-in translator. Histories remain available on demand. Responsive stacking, keyboard focus, and server-owned lifecycle permissions use the existing system.

Weekly estimate history uses one compact line: a Monday-to-Sunday range followed by the last update date and time. The instant is still converted to `Asia/Almaty`, but the redundant timezone label and repeated year are omitted from the visible copy.

## Sprint 4 UI

The Sprint 4 rework follows a document-first editorial desk. The Chief Editor workspace shows the whole-queue capacity strip and a searchable project list. Selecting a project opens only its newest-first version queue; weekly and final versions have distinct labels, and each row shows cycle, marks, estimate state, and submission time. The capacity strip compares known open-queue hours with the editor's saved weekly availability and shows unscored versions separately rather than pretending the forecast is complete.

Review detail uses a two-column workbench. The left side is the submitted document with a PDF.js viewer, text layer, zoom, and highlight/strike/box tools. The right side contains the linked feedback marks, expected time, one decision panel, and earlier cycles. Selecting a mark scrolls to its page; selecting a mark on the page activates its list item. Marks are drafts until the single approval/return decision; then the whole version becomes read-only. The PDF-with-notes download is generated from the original and includes an appended notes page.

The translator detail page leads with the activity state and a three-part pages strip: current-week remaining, unfinished rework, and total remaining. Weekly versions can be sent while daily work continues; final versions remain a separate step after Finish. A correction panel brings feedback, mark count, pages to redo, reviewed-file download, rework progress, and corrected-file upload into one place. The daily form shows a rework-pages field only when a correction is outstanding. Version history keeps weekly/final cycles, feedback, correction pages, marks count, and downloads. The marked-document dialog reuses the same read-only PDF viewer.

On mobile, the workbench becomes one column. The PDF fits the viewport at load, tool labels collapse to icons, and the marks/decision panels stack under the document. All actions remain reachable, focus is visible, and pages do not create horizontal overflow.

Sprint 4 deliberately keeps the original file immutable and does not add direct PDF editing or a fake DOCX preview. DOCX reviews download the file and use the feedback/pages fields; PDF reviews add in-app marks. All surfaces reuse the existing Inter typography, semantic color tokens, border/radius hierarchy, and keyboard focus treatment.

## Notification, approval, and completion refinements

The bell opens retained notification history. Each item has a `Read details` action that opens a dialog with the full message, context, and a separate destination action. Opening marks it read and dims the item; only its Delete action removes it. Past reminders remain visible in history.

An existing daily record shows `Request edit`; the translator enters a reason, and the Project Manager's request page shows the reason and approve/decline actions. An approval unlocks one edit of that record. After final submission, a quiet confirmation screen shows the activity number, checking state or approval, and buttons for activities and the detail/history page. A correction request shows the correction workflow instead.

The Chief Editor lands on a searchable project list, selects a project, then sees its open versions with version-kind and text filters. Review time saved by the editor appears in the translator's checking state. Correction input accepts page numbers and ranges, and the UI shows the derived total. Deleting a mark asks for confirmation and offers a local browser preference to skip the prompt next time. Exported PDF highlights use a translucent yellow so the text stays legible.
