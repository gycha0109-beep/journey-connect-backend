#!/usr/bin/env bash
set -euo pipefail

: "${DATABASE_URL:?DATABASE_URL is required}"
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
SETUP="$ROOT/verification/dp7/sql/dp7_concurrency_setup.sql"
WORK="$(mktemp -d)"
trap 'rm -rf "$WORK"' EXIT

psql "$DATABASE_URL" -v ON_ERROR_STOP=1 -f "$SETUP" >"$WORK/setup.log" 2>&1
call_sql="SELECT disposition FROM public.persist_data_cross_track_integration_v1((SELECT request FROM public.dp7_concurrency_request_v1 LIMIT 1));"
psql "$DATABASE_URL" -v ON_ERROR_STOP=1 -Atc "$call_sql" >"$WORK/a.out" 2>"$WORK/a.err" &
pid_a=$!
psql "$DATABASE_URL" -v ON_ERROR_STOP=1 -Atc "$call_sql" >"$WORK/b.out" 2>"$WORK/b.err" &
pid_b=$!
wait "$pid_a"
wait "$pid_b"

observed="$(cat "$WORK/a.out" "$WORK/b.out" | sed '/^$/d' | sort | tr '\n' ' ' | sed 's/ $//')"
if [[ "$observed" != "DUPLICATE NEW" ]]; then
  printf 'DP-7 concurrent dispositions mismatch: %s\n' "$observed" >&2
  cat "$WORK/a.err" "$WORK/b.err" >&2
  exit 1
fi

run_count="$(psql "$DATABASE_URL" -Atc "SELECT count(*) FROM public.data_cross_track_integration_run_v1 WHERE integration_run_ref='integration_run:concurrent-same-identity';")"
verdict_count="$(psql "$DATABASE_URL" -Atc "SELECT count(*) FROM public.data_cross_track_integration_verdict_v1 v JOIN public.data_cross_track_integration_run_v1 r ON r.integration_run_id=v.integration_run_ref WHERE r.integration_run_ref='integration_run:concurrent-same-identity';")"
conflict_count="$(psql "$DATABASE_URL" -Atc "SELECT count(*) FROM public.data_cross_track_integration_conflict_evidence_v1;")"
if [[ "$run_count" != "1" || "$verdict_count" != "1" || "$conflict_count" != "0" ]]; then
  printf 'DP-7 concurrency persistence mismatch: run=%s verdict=%s conflict=%s\n' \
    "$run_count" "$verdict_count" "$conflict_count" >&2
  exit 1
fi

printf 'DP-7 concurrent same logical identity exactly one NEW: PASS\n'
