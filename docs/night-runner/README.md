# EasyLang Sprint 4 night runner

Run from the repository root:

```bash
python3 scripts/sprint4_night_runner.py --check
python3 scripts/sprint4_night_runner.py --run
```

On macOS, use `caffeinate -i python3 scripts/sprint4_night_runner.py --run` to keep the machine awake while the foreground runner works.
The runner looks for `codex` in `PATH` and in the installed VS Code OpenAI extension. If needed, pass `--codex-bin /absolute/path/to/codex` or set `CODEX_BIN`. It checks this before marking a task in progress.
After a network or usage-limit Codex exit, inspect `handoff.md` and resume with `caffeinate -i python3 scripts/sprint4_night_runner.py --run --retry-blocked-agent`. This retries only a task whose handoff says `Codex exited`; validation failures still require investigation. The worktree and completed tasks are preserved.
After fixing a failed validation in the isolated worktree, use `caffeinate -i python3 scripts/sprint4_night_runner.py --run --revalidate-blocked`. It reruns that task's validation without spending another Codex session, marks it done only if all checks pass, then continues the queue.

`--check` only validates the queue and CLI. `--run` creates an isolated Git worktree at `.agent-runs/sprint4/worktree`, overlays the current tracked and untracked working files, then launches one fresh Codex session per task. The original working tree is not changed by the run. Tasks continue automatically after successful validation. The runner does not commit or push. It stops on an agent failure, a file-scope violation or a failed check; limits trigger a retry of the same task after 15 minutes. There is no time or story-count cutoff.

Use `python3 scripts/sprint4_night_runner.py --status` for progress. Logs, prompts, progress and handoff are in `.agent-runs/sprint4/`. The resulting code and `docs/SPRINT_4_STATUS.md` live in the isolated worktree. Review and merge that work manually into the main working tree after completion; this avoids overwriting today's uncommitted changes.

Task source: `sprint4-tasks.json`; product rules and acceptance criteria: `../SPRINT_4_PLAN.md`. Sprint 4 application code has not been implemented by this preparation step.
