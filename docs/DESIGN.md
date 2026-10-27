# Design

Last updated: 2026-10-28

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
- `WorkspacePage` uses a protected starter workspace shell with role-specific labels and logout.
- `TranslatorActivitiesPage` uses a breadcrumb, a restrained page title, search, and a compact All / Responsible / Assisting assignment filter. These quick assignment views omit `FINISHED` activities; selecting `Finished` in the status popover reveals them. A filter button opens status and assigned-date controls; there is no top summary-card row.
- `TranslatorNotificationCenter` keeps reminders in a persistent circular bell at the right side of the workspace top bar on both translator pages. Its badge shows the unread count, and the top bar owns the popover's stacking layer so page controls cannot cover it. The popover distinguishes daily-record reminders with teal and a record icon, and weekly-estimate reminders with blue and a calendar icon; labels repeat the distinction without relying on color. Each item has an explicit action label, opens its target, and becomes read when opened; the header also supports marking all visible reminders read.
- `TranslatorActivityDetailPage` uses a wider working canvas. The activity banner and editable daily-record form share one bordered work surface because they are the page's primary context and action. Weekly estimate and history are quieter full-width rows separated by horizontal rules rather than repeated white cards. Daily-record count and last-entry metadata stay in history rather than being repeated below the banner. Weekly completion guidance lives inside the weekly remaining estimate disclosure instead of a separate card and duplicate action. Finish and Submit keep bordered next-step panels because they represent consequential state changes; both histories start collapsed. Daily history date filtering does not change the record form or lifetime personal volume.
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
