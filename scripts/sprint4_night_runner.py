#!/usr/bin/env python3
"""Sequential Sprint 4 Codex runner; preparation/check mode never starts an agent."""

from __future__ import annotations

import argparse
import fcntl
import fnmatch
import hashlib
import json
import os
import re
import shutil
import subprocess
import sys
import time
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
RUN = ROOT / ".agent-runs" / "sprint4"
WORKTREE = RUN / "worktree"
TASKS = ROOT / "docs/night-runner/sprint4-tasks.json"
PROGRESS = RUN / "progress.json"
PROTECTED = {
    "scripts/sprint4_night_runner.py",
    "docs/night-runner/sprint4-tasks.json",
    "docs/SPRINT_4_PLAN.md",
}
RATE_LIMIT = re.compile(r"(rate limit|usage limit|session limit|try again after|resets? at)", re.I)
TRANSIENT_CONNECTION = re.compile(
    r"(workspace routing discovery failed|connection failed: error sending request|"
    r"http/request failed|failed to refresh available models: request timed out)", re.I,
)


def git(*args: str, cwd: Path = ROOT, input_bytes: bytes | None = None) -> bytes:
    return subprocess.run(
        ["git", *args], cwd=cwd, input=input_bytes, stdout=subprocess.PIPE,
        stderr=subprocess.PIPE, check=True,
    ).stdout


def load_tasks() -> list[dict]:
    data = json.loads(TASKS.read_text())
    tasks = data["tasks"]
    ids = [task["id"] for task in tasks]
    if not tasks or len(ids) != len(set(ids)):
        raise ValueError("Task queue must be nonempty with unique IDs")
    for task in tasks:
        if not all(task.get(key) for key in ("title", "stories", "scope", "allowed", "validation")):
            raise ValueError(f"Incomplete task: {task.get('id')}")
    return tasks


def resolve_codex(explicit: str | None = None) -> str:
    """Find the CLI in PATH, an explicit override, or the VS Code extension."""
    requested = explicit or os.environ.get("CODEX_BIN")
    if requested:
        candidate = shutil.which(requested)
        if candidate:
            return candidate
        raise RuntimeError(f"Codex CLI is not executable: {requested}")
    candidate = shutil.which("codex")
    if candidate:
        return candidate
    extensions = sorted(
        (Path.home() / ".vscode/extensions").glob("openai.chatgpt-*/bin/*/codex"),
        key=lambda path: path.stat().st_mtime,
        reverse=True,
    )
    for path in extensions:
        if path.is_file() and os.access(path, os.X_OK):
            return str(path)
    raise RuntimeError(
        "Codex CLI not found. Install it, add it to PATH, or pass --codex-bin /path/to/codex"
    )


def tracked_and_untracked(cwd: Path) -> list[str]:
    raw = git("ls-files", "-z", "--cached", "--others", "--exclude-standard", cwd=cwd)
    return sorted({part.decode() for part in raw.split(b"\0") if part})


def digest(path: Path) -> str:
    if path.is_symlink():
        return "symlink:" + str(path.readlink())
    if not path.exists():
        return "missing"
    h = hashlib.sha256()
    with path.open("rb") as stream:
        for chunk in iter(lambda: stream.read(1024 * 1024), b""):
            h.update(chunk)
    return h.hexdigest()


def snapshot() -> dict[str, str]:
    return {name: digest(WORKTREE / name) for name in tracked_and_untracked(WORKTREE)}


def changed(before: dict[str, str], after: dict[str, str]) -> list[str]:
    return sorted(name for name in before.keys() | after.keys() if before.get(name) != after.get(name))


def prepare_worktree() -> None:
    if WORKTREE.exists():
        if not (WORKTREE / ".git").exists():
            raise RuntimeError(f"Existing path is not a Git worktree: {WORKTREE}")
        return
    RUN.mkdir(parents=True, exist_ok=True)
    git("worktree", "add", "--detach", str(WORKTREE), "HEAD")
    diff = git("diff", "--binary", "HEAD")
    if diff:
        git("apply", "--binary", "-", cwd=WORKTREE, input_bytes=diff)
    tracked = set(git("ls-files", "-z", "--cached").split(b"\0"))
    for name in tracked_and_untracked(ROOT):
        source = ROOT / name
        if name.startswith(".agent-runs/") or not source.exists() or not source.is_file():
            continue
        # Tracked files were already copied by Git and patched above.
        if name.encode() in tracked:
            continue
        target = WORKTREE / name
        target.parent.mkdir(parents=True, exist_ok=True)
        shutil.copy2(source, target)


def save_progress(data: dict) -> None:
    RUN.mkdir(parents=True, exist_ok=True)
    temp = PROGRESS.with_suffix(".tmp")
    temp.write_text(json.dumps(data, indent=2) + "\n")
    temp.replace(PROGRESS)


def load_progress(tasks: list[dict]) -> dict:
    if PROGRESS.exists():
        data = json.loads(PROGRESS.read_text())
        if set(data) != {task["id"] for task in tasks}:
            raise RuntimeError("Queue IDs changed after run started; inspect progress manually")
        return data
    return {task["id"]: "pending" for task in tasks}


def prompt(task: dict) -> str:
    return f"""Implement exactly one EasyLang Sprint 4 task in this isolated worktree.

Read AGENTS.md, docs/STATE.md, docs/ARCHITECTURE.md and docs/SPRINT_4_PLAN.md first. For UI work also read docs/DESIGN.md and apply the frontend-design skill if available. Use the original workbook only to resolve ambiguity. Preserve existing Sprint 3 behavior and all initial changes in this worktree.
If this task was interrupted, inspect the current worktree changes first and continue from them; do not discard or redo completed work.

Task {task['id']}: {task['title']}
Stories: {', '.join(task['stories'])}
Scope: {task['scope']}
Allowed files: {', '.join(task['allowed'])}
Required validation: {'; '.join(task['validation'])}

Rules: Work autonomously through this task without asking the user. Do not implement later tasks, run this night runner, commit, push, deploy, delete user data, or change runner/queue/plan files. Do not add embedded document editing, DOCX preview, annotations, collaborative editing or document diff. Do not replace closed Review data or old files. Keep Spring Boot layers, Flyway compatibility, role/ownership and CSRF. Update docs/STATE.md with this task's actual outcome. If genuinely blocked, explain the blocker in the final answer and leave work reviewable. Report files changed and actual test results; do not claim unrun checks.
"""


def handoff(task_id: str, status: str, detail: str, files: list[str], log: Path) -> None:
    lines = ["# Sprint 4 night handoff", "", f"Task: {task_id}", f"Status: {status}",
             f"Log: {log}", f"Detail: {detail}", "", "Changed files:"]
    lines.extend(f"- {name}" for name in files)
    (RUN / "handoff.md").write_text("\n".join(lines) + "\n")


def validate_commands(task: dict, log_path: Path) -> str | None:
    with log_path.open("ab") as log:
        for command in task["validation"]:
            log.write(f"\n== runner validation: {command} ==\n".encode())
            log.flush()
            result = subprocess.run(["bash", "-lc", command], cwd=WORKTREE,
                                    stdout=log, stderr=subprocess.STDOUT)
            if result.returncode:
                return command
    return None


def run_task(task: dict, progress: dict, codex_bin: str) -> bool:
    task_id = task["id"]
    before = snapshot()
    prompt_path = RUN / f"{task_id}.prompt.md"
    log_path = RUN / f"{task_id}.log"
    prompt_path.write_text(prompt(task))
    progress[task_id] = "in_progress"
    save_progress(progress)
    print(f"Starting {task_id}: {task['title']} — {log_path}", flush=True)
    while True:
        log_offset = log_path.stat().st_size if log_path.exists() else 0
        with prompt_path.open("rb") as source, log_path.open("ab") as log:
            result = subprocess.run(
                [codex_bin, "exec", "-C", str(WORKTREE), "--approve-for-me", "-"],
                stdin=source, stdout=log, stderr=subprocess.STDOUT,
            )
        if result.returncode == 0:
            break
        with log_path.open("rb") as log:
            log.seek(log_offset)
            log_text = log.read().decode(errors="replace")[-8000:]
        if RATE_LIMIT.search(log_text) or TRANSIENT_CONNECTION.search(log_text):
            print(f"{task_id}: Codex limit or connection failure; retrying same task after 15 minutes", flush=True)
            time.sleep(900)
            continue
        files = changed(before, snapshot())
        progress[task_id] = "blocked"
        save_progress(progress)
        handoff(task_id, "blocked", f"Codex exited {result.returncode}", files, log_path)
        return False

    files = changed(before, snapshot())
    bad = [name for name in files if name in PROTECTED or not any(
        fnmatch.fnmatchcase(name, pattern) for pattern in task["allowed"]
    )]
    substantive = [name for name in files if name != "docs/STATE.md"]
    if bad or not substantive:
        detail = f"File scope violation {bad}" if bad else "No task output beyond STATE.md"
        progress[task_id] = "blocked"
        save_progress(progress)
        handoff(task_id, "blocked", detail, files, log_path)
        return False

    failed_command = validate_commands(task, log_path)
    if failed_command:
        progress[task_id] = "blocked"
        save_progress(progress)
        handoff(task_id, "blocked", f"Validation failed: {failed_command}", files, log_path)
        return False
    # Validation must not change files outside the task scope either.
    files = changed(before, snapshot())
    bad = [name for name in files if name in PROTECTED or not any(
        fnmatch.fnmatchcase(name, pattern) for pattern in task["allowed"]
    )]
    if bad:
        progress[task_id] = "blocked"
        save_progress(progress)
        handoff(task_id, "blocked", f"Validation changed disallowed files: {bad}", files, log_path)
        return False
    progress[task_id] = "done"
    save_progress(progress)
    handoff(task_id, "done", "Agent and runner validation passed", files, log_path)
    print(f"Completed {task_id}", flush=True)
    return True


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    mode = parser.add_mutually_exclusive_group(required=True)
    mode.add_argument("--check", action="store_true", help="validate preparation only; no agent/worktree")
    mode.add_argument("--status", action="store_true", help="show saved task status")
    mode.add_argument("--run", action="store_true", help="run tasks until done or a failed gate")
    parser.add_argument("--codex-bin", help="path to Codex CLI when it is not in PATH")
    parser.add_argument(
        "--retry-blocked-agent", action="store_true",
        help="resume a task blocked only because Codex exited; preserve its worktree",
    )
    parser.add_argument(
        "--revalidate-blocked", action="store_true",
        help="rerun checks for a task blocked only by validation, then continue",
    )
    args = parser.parse_args()
    tasks = load_tasks()
    if args.check:
        if not (ROOT / "docs/SPRINT_4_PLAN.md").is_file():
            raise RuntimeError("Sprint 4 plan missing")
        codex_bin = resolve_codex(args.codex_bin)
        print(f"Ready: {len(tasks)} sequential tasks; Codex: {codex_bin}; no agent started")
        return 0
    progress = load_progress(tasks)
    if args.status:
        for task in tasks:
            print(f"{task['id']}: {progress[task['id']]} — {task['title']}")
        return 0
    codex_bin = resolve_codex(args.codex_bin)
    RUN.mkdir(parents=True, exist_ok=True)
    with (RUN / "runner.lock").open("w") as lock:
        try:
            fcntl.flock(lock, fcntl.LOCK_EX | fcntl.LOCK_NB)
        except BlockingIOError as error:
            raise RuntimeError("Another Sprint 4 runner is already active") from error
        prepare_worktree()
        blocked = [task_id for task_id, value in progress.items() if value == "blocked"]
        if blocked and args.revalidate_blocked:
            handoff_text = (RUN / "handoff.md").read_text() if (RUN / "handoff.md").exists() else ""
            if len(blocked) != 1 or f"Task: {blocked[0]}\nStatus: blocked\n" not in handoff_text or not re.search(
                r"^Detail: Validation failed: .+$", handoff_text, re.M,
            ):
                raise RuntimeError("Only a validation failure can be revalidated; inspect handoff")
            task = next(task for task in tasks if task["id"] == blocked[0])
            log_path = RUN / f"{blocked[0]}.log"
            before = snapshot()
            print(f"Revalidating {blocked[0]} without starting Codex", flush=True)
            failed_command = validate_commands(task, log_path)
            files = changed(before, snapshot())
            bad = [name for name in files if name in PROTECTED or not any(
                fnmatch.fnmatchcase(name, pattern) for pattern in task["allowed"]
            )]
            if failed_command or bad:
                detail = f"Validation failed: {failed_command}" if failed_command else f"Validation changed disallowed files: {bad}"
                handoff(blocked[0], "blocked", detail, files, log_path)
                print(detail, file=sys.stderr)
                return 1
            progress[blocked[0]] = "done"
            save_progress(progress)
            handoff(blocked[0], "done", "Revalidation passed after scoped fix", files, log_path)
            print(f"Completed {blocked[0]} after revalidation", flush=True)
        elif blocked and args.retry_blocked_agent:
            handoff_text = (RUN / "handoff.md").read_text() if (RUN / "handoff.md").exists() else ""
            if len(blocked) != 1 or f"Task: {blocked[0]}\nStatus: blocked\n" not in handoff_text or not re.search(
                r"^Detail: Codex exited [1-9][0-9]*$", handoff_text, re.M,
            ):
                raise RuntimeError("Only a Codex exit can be retried automatically; inspect handoff")
            progress[blocked[0]] = "pending"
            save_progress(progress)
            print(f"Resuming {blocked[0]} after Codex exit; existing worktree retained", flush=True)
        elif blocked:
            print(f"Blocked task in {PROGRESS}; inspect {RUN / 'handoff.md'}", file=sys.stderr)
            return 1
        for task in tasks:
            if progress[task["id"]] == "done":
                continue
            if not run_task(task, progress, codex_bin):
                print(f"Stopped on {task['id']}; inspect {RUN / 'handoff.md'}", file=sys.stderr)
                return 1
        print(f"Sprint 4 queue complete. Code and docs: {WORKTREE}")
        return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except (RuntimeError, ValueError, subprocess.CalledProcessError, OSError) as error:
        print(f"Runner error: {error}", file=sys.stderr)
        raise SystemExit(2)
