# Design

Last updated: 2026-10-25

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
- `TranslatorActivitiesPage` uses a breadcrumb, a restrained page title, search, and a compact All / Responsible / Assisting assignment filter. A filter button opens status and assigned-date controls; there is no top summary-card row. A small reminder region appears above the filters only when relevant reminders or a reminder loading error exist.
- `TranslatorActivityDetailPage` uses a breadcrumb and compact activity context. The daily record form is first. A single next-step panel explains the path to Finish or Submit for the responsible translator; the weekly remaining estimate and both histories start collapsed. Daily history date filtering does not change the record form or lifetime personal volume.
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

The current translator UI applies the Lokalise task-first reference. The activity title, status, assignment role, and *personal* recorded volume are compact context. Logging a daily record is the primary active-state task. A short next-step panel explains that a responsible translator finishes by saving 0 remaining for the current week and confirming Finish; recording an arbitrarily large daily volume does not finish the activity. The weekly estimate is a secondary disclosure after the daily form, and is read-only for assistants. At 0 remaining, Finish is visible without opening that disclosure. Submit appears only in `FINISHED`; after submission, work forms disappear and the status is clear.

The activity list keeps search and assignment filtering visible. Relevant reminders are summarized in one disclosure and expand to direct links and read actions; a weekly reminder opens the estimate on its target activity. The activity rows label volume as recorded by the signed-in translator. Histories remain available on demand. Responsive stacking, keyboard focus, and server-owned lifecycle permissions use the existing system.
