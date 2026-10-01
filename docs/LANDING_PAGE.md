# Public Landing Page

Last updated: 2026-09-24

## Delivered scope

The public `/` route is a responsive, three-section landing page for EasyLang Activity Monitoring:

1. Product promise and an illustrative delivery-monitoring preview.
2. The activity flow from progress capture to early intervention.
3. Role context for Translators, Chief Editors, and Project Managers, followed by a sign-in call to action.

Primary calls to action link to `/login`. The page does not change authentication, API, or backend behavior.

## Product accuracy

The monitoring preview describes the product direction documented in `PROJECT_CONTEXT.md`. It is illustrative marketing content, not a live dashboard or a claim that post-Sprint-1 monitoring features are already implemented.

## Design and accessibility

- Uses the palette, typography, and voice from `brand.md`.
- Keeps all navigation and calls to action keyboard accessible with visible focus styles.
- Supports 375 px, 768 px, and 1280 px layouts.
- Keeps essential hero content visible immediately and limits motion to decorative details.
- Disables non-essential animation when `prefers-reduced-motion` is enabled.
