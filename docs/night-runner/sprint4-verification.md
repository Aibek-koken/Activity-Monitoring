> Superseded: this file records the earlier isolated Sprint 4 preview-less implementation. Fresh rework evidence is in `docs/SPRINT_4_REWORK_REPORT.md` and `docs/SPRINT_4_STATUS.md`.

# Sprint 4 verification — EL4-07

Date: 2026-11-10 (Asia/Almaty)

## Isolation and startup

The state-changing acceptance flow used `frontend/scripts/run-sprint4-e2e.sh`. The script creates a new `mktemp` directory, initializes a PostgreSQL 16.12 cluster and `easylang_sprint4_e2e` database inside it, chooses unused loopback ports, and uses a separate temporary document-storage root. It never connects to the Compose `easylang` database or its volume.

PostgreSQL startup has a 30-second `pg_ctl` bound. Backend and frontend readiness each have a 90-second bound with process-liveness checks. The exit trap stops both services and PostgreSQL and removes only the runner-owned temporary directory. The Docker daemon was unavailable, so the successful run used the installed Postgres.app 16.12 binaries rather than touching any developer database.

## Commands and results

1. `cd frontend && npm run test:e2e:sprint4-isolated`
   - PASS.
   - PostgreSQL phase: `mvn -Dtest=Sprint4PostgresAcceptanceTest test` with `POSTGRES_INTEGRATION_TESTS=true` and the temporary JDBC URL: 3 tests run, 0 failures, 0 errors, 0 skipped.
   - The PostgreSQL tests execute V1→V5 on an empty schema, V1→V4 followed by V5 on a seeded fileless Sprint 3 Review, V5 constraints, one-winner concurrent Review decisions, notification deduplication, and one-winner concurrent correction re-upload.
   - Browser phase: `playwright test e2e/sprint4.spec.ts --project=desktop`: 1 test passed, 0 failed.
   - The browser flow covers a forged PDF rejection, valid immutable cycle-1/cycle-2 bytes, translator→Chief Editor→Project Manager→foreign translator role changes, a missing-CSRF decision rejected with 403, Project Manager Review access rejected with 403, foreign-translator download hidden with 404 and no `Content-Disposition`, correction feedback/notification/re-upload, and final approval to `COMPLETED`.

2. `cd backend && mvn test`
   - PASS: 74 tests discovered, 0 failures, 0 errors, 6 skipped.
   - The 6 skips are the environment-gated PostgreSQL-profile tests in the default H2 run; the 3 Sprint 4 PostgreSQL acceptance tests were executed separately by command 1.
   - Security/file coverage in this run includes 33 translator-controller tests, 5 Chief Editor controller tests, 7 authentication tests, 2 upload-validation tests, and 4 private-storage tests. These cover CSRF on JSON and multipart writes, role/ownership boundaries, safe unauthorized downloads, unsafe/forged/malformed/oversize document rejection, immutable storage keys, traversal rejection, and rollback cleanup.

3. `cd frontend && npm test`
   - PASS on 2026-11-11 after the midnight form fix: 8 test files, 66 tests passed, 0 failed. The added test covers editing an older record across local midnight.

4. `cd frontend && npm run lint`
   - PASS: ESLint exited 0 with 0 errors and 0 warnings.

5. `cd frontend && npm run build`
   - PASS: TypeScript and Vite production build exited 0; 1,707 modules transformed.

6. `test -s docs/night-runner/sprint4-verification.md`
   - PASS.

7. `rg -q '^POSTGRESQL: PASS$' docs/night-runner/sprint4-verification.md`
   - PASS.

8. `rg -q '^SECURITY: PASS$' docs/night-runner/sprint4-verification.md`
   - PASS.

9. `rg -q '^E2E: PASS$' docs/night-runner/sprint4-verification.md`
   - PASS.

POSTGRESQL: PASS
SECURITY: PASS
E2E: PASS

## Final rerun after midnight-form fix

On 2026-11-11 (Asia/Almaty), `cd frontend && npm run test:e2e:sprint4-isolated` passed again on the final code: 3/3 PostgreSQL acceptance tests and 1/1 desktop Playwright test. The script used a fresh temporary PostgreSQL cluster and removed it on exit. This closes the earlier evidence gap between the initial E2E run and the later frontend form fix.
