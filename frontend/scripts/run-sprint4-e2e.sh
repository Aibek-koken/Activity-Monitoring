#!/usr/bin/env bash
set -euo pipefail

frontend_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd -P)"
repo_dir="$(cd "$frontend_dir/.." && pwd -P)"
backend_dir="$repo_dir/backend"
run_dir="$(mktemp -d "${TMPDIR:-/tmp}/easylang-sprint4-e2e.XXXXXX")"
pg_data_dir="$run_dir/postgres"
pg_socket_dir="$run_dir/socket"
document_dir="$run_dir/documents"
backend_log="$run_dir/backend.log"
frontend_log="$run_dir/frontend.log"
backend_pid=""
frontend_pid=""
postgres_start_attempted=false

if command -v initdb >/dev/null 2>&1; then
  postgres_bin="$(dirname "$(command -v initdb)")"
elif [[ -x /Applications/Postgres.app/Contents/Versions/latest/bin/initdb ]]; then
  postgres_bin="/Applications/Postgres.app/Contents/Versions/latest/bin"
else
  echo "PostgreSQL tools are required (initdb, pg_ctl, createdb)." >&2
  exit 1
fi

choose_port() {
  local base="$1"
  local candidate
  local attempt
  for attempt in $(seq 1 50); do
    candidate=$((base + RANDOM % 900))
    if ! nc -z 127.0.0.1 "$candidate" >/dev/null 2>&1; then
      echo "$candidate"
      return 0
    fi
  done
  echo "Could not find a free local port." >&2
  return 1
}

pg_port="$(choose_port 56000)"
backend_port="$(choose_port 18000)"
frontend_port="$(choose_port 15000)"
database_name="easylang_sprint4_e2e"
database_url="jdbc:postgresql://127.0.0.1:${pg_port}/${database_name}"

cleanup() {
  local exit_code=$?
  set +e
  if [[ -n "$frontend_pid" ]]; then
    kill "$frontend_pid" >/dev/null 2>&1
    wait "$frontend_pid" >/dev/null 2>&1
  fi
  if [[ -n "$backend_pid" ]]; then
    kill "$backend_pid" >/dev/null 2>&1
    wait "$backend_pid" >/dev/null 2>&1
  fi
  if [[ "$postgres_start_attempted" == true ]] \
      && "$postgres_bin/pg_ctl" -D "$pg_data_dir" status >/dev/null 2>&1; then
    "$postgres_bin/pg_ctl" -D "$pg_data_dir" -m fast -w -t 20 stop >/dev/null 2>&1
  fi
  if [[ $exit_code -ne 0 ]]; then
    [[ -s "$backend_log" ]] && { echo "--- backend log ---" >&2; tail -n 120 "$backend_log" >&2; }
    [[ -s "$frontend_log" ]] && { echo "--- frontend log ---" >&2; tail -n 120 "$frontend_log" >&2; }
  fi
  rm -rf -- "$run_dir"
  exit "$exit_code"
}
trap cleanup EXIT INT TERM

wait_for_url() {
  local name="$1"
  local url="$2"
  local pid="$3"
  local log_file="$4"
  local deadline=$(( $(date +%s) + 90 ))
  local now
  local remaining
  while true; do
    now=$(date +%s)
    remaining=$((deadline - now))
    if (( remaining <= 0 )); then
      break
    fi
    if curl -fsS --max-time "$((remaining < 2 ? remaining : 2))" "$url" >/dev/null 2>&1; then
      return 0
    fi
    if ! kill -0 "$pid" >/dev/null 2>&1; then
      echo "$name stopped before becoming ready." >&2
      tail -n 120 "$log_file" >&2 || true
      return 1
    fi
    now=$(date +%s)
    if (( now < deadline )); then
      sleep 1
    fi
  done
  echo "$name did not become ready within 90 seconds." >&2
  tail -n 120 "$log_file" >&2 || true
  return 1
}

mkdir -p "$pg_socket_dir" "$document_dir"
"$postgres_bin/initdb" -D "$pg_data_dir" --auth=trust --encoding=UTF8 --locale=C --username=easylang >/dev/null
postgres_start_attempted=true
"$postgres_bin/pg_ctl" -D "$pg_data_dir" -o "-h 127.0.0.1 -p $pg_port -k $pg_socket_dir" -w -t 30 start >/dev/null
"$postgres_bin/createdb" -h 127.0.0.1 -p "$pg_port" -U easylang "$database_name"

echo "Running Sprint 4 PostgreSQL migration and concurrency acceptance tests..."
(
  cd "$backend_dir"
  POSTGRES_INTEGRATION_TESTS=true \
  APP_SEED_DEMO_PORTFOLIO=false \
  POSTGRES_TEST_DB_URL="$database_url" \
  POSTGRES_TEST_DB_USERNAME=easylang \
  POSTGRES_TEST_DB_PASSWORD=e2e-only \
  DOCUMENT_STORAGE_ROOT="$run_dir/postgres-test-documents" \
  mvn -Dtest=Sprint4PostgresAcceptanceTest test
)

echo "Starting isolated backend on $backend_port..."
(
  cd "$backend_dir"
  DB_URL="$database_url" \
  DB_USERNAME=easylang \
  DB_PASSWORD=e2e-only \
  SERVER_PORT="$backend_port" \
  DOCUMENT_STORAGE_ROOT="$document_dir" \
  APP_SEED_DEMO_PORTFOLIO=false \
  REMINDER_SCHEDULING_ENABLED=false \
  ALLOWED_ORIGINS="http://127.0.0.1:$frontend_port" \
  mvn spring-boot:run
) >"$backend_log" 2>&1 &
backend_pid=$!
wait_for_url "Backend" "http://127.0.0.1:$backend_port/actuator/health" "$backend_pid" "$backend_log"

echo "Starting isolated frontend on $frontend_port..."
(
  cd "$frontend_dir"
  VITE_BACKEND_PROXY_TARGET="http://127.0.0.1:$backend_port" \
  npm run dev -- --host 127.0.0.1 --port "$frontend_port" --strictPort
) >"$frontend_log" 2>&1 &
frontend_pid=$!
wait_for_url "Frontend" "http://127.0.0.1:$frontend_port/login" "$frontend_pid" "$frontend_log"

echo "Running isolated Sprint 4 cross-role E2E flow..."
cd "$frontend_dir"
E2E_ISOLATED_DB=true \
E2E_BASE_URL="http://127.0.0.1:$frontend_port" \
E2E_API_BASE_URL="http://127.0.0.1:$frontend_port/api" \
npm run test:e2e -- e2e/sprint4.spec.ts --project=desktop
