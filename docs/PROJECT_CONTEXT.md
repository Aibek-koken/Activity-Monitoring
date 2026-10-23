# Project Context

Last updated: 2026-10-22

## Product

EasyLang Activity Monitoring is a web application for a translation house with about 80 translators, 40 open projects, and up to 400 planned or active translation activities. Its main business goal is to detect delivery risk early.

Translators record daily translated volume and submit a weekly estimate of the remaining text. The system will use completed work plus remaining work to estimate total activity effort. A rising estimate or stalled progress signals that a project manager may need to intervene.

Every completed translation must pass a Chief Editor review. Work can move through correction and re-review until approval.

## Roles

- `TRANSLATOR` — sees assigned activities, records work and estimates, handles corrections.
- `CHIEF_EDITOR` — sees assigned projects and submitted translations, records review outcomes and mistakes.
- `PROJECT_MANAGER` — creates and staffs projects/activities, monitors progress, workload, and delivery risk.

Every request must be authenticated before project data is returned. Each role may access only its own functionality and data.

## Source documents reviewed

- `EasyLang - Technical Specification & Sprint 1 Plan.pdf` — primary Sprint 1 acceptance criteria and deliverables.
- `EasyLang_6_Sprints.pdf` — delivery sequence across six sprints.
- `PROJECT 3 · ACTIVITY MONITORING (2).pdf` — master requirements and US-01 through US-35.
- `Project_3_Task_3_Final.pdf` — architecture, design, sitemap, and ERD summary. Its screenshots are visual inspiration only and are not the required final UI.
- `3.2_Software_Design (1).md` — eight logical components and activity lifecycle.
- `3.3_Database_Design.md` — target eleven-entity data model.
- `EasyLang_Sitemap.svg` — role-specific navigation structure.
- `Project_3_Activity_Monitoring_Defense_8slides.pptx` — presentation of the same architecture/design direction.
- The repository screenshot — identifies the intended GitHub repository, but no remote operation was performed.

## Source-of-truth decisions

The master requirements retain US-01 through US-35. The six-sprint overview uses shortened story numbering for planning, so it must not replace the master IDs. The Sprint 1 technical specification additionally calls database-backed test users “US-03”; inside this repository it is treated as a Sprint 1 technical deliverable, while master US-03 remains “View Assigned Activities” for Sprint 2.

The master ERD remains the target model. Sprint 1 intentionally implements only the user slice required for authentication: id, email/login, initials, full name, password hash, role, and active status. Later entities will be added through Flyway migrations.

## Current implementation

Sprints 1 and 2 are complete locally. Authentication uses database users, BCrypt password hashes, stateless JWTs in `HttpOnly` cookies, CSRF tokens for state-changing browser requests, and server-side role checks. Translators can view/search their own assignments and create/edit daily translated-volume records. The activity list also has client-side role/status/assigned-date filters; detail pages have a collapsed record history with date filtering. Projects and assignments are demo-seeded until the manager workflow is built.

See `SPRINT_1_STATUS.md` and `SPRINT_2_STATUS.md` for delivered scope, `ARCHITECTURE.md` for the current system map, and `SPRINT_3_PLAN.md` for the next implementation slice.

## Planned sequence

1. Sprint 2 — complete locally: Project/Activity foundation, Translator activity list/details, daily work records.
2. Sprint 3 — next: daily/weekly reminders, weekly remaining-text estimates, `FINISHED` at zero remaining, and submission for Chief Editor checking.
3. Sprint 4 — Chief Editor review and correction cycle.
4. Sprint 5 — Project Manager staffing, monitoring, estimated-total trend, risk flags.
5. Sprint 6 — integrated workflow, alpha testing, demonstration.

Redis, Kafka, Kubernetes, and CI/CD may be introduced when a concrete requirement needs them. The current boundaries and environment-based configuration are designed to support that evolution without prematurely distributing the system.

The original six-sprint overview and master user-story file named above are not currently available in this workspace; the Sprint 3 working plan is cross-checked against the available Task 3 PDF, software design, and database design. Exact reminder timing, channel, and estimate ownership need an implementation decision.

