# Design

Last updated: 2026-10-01

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
