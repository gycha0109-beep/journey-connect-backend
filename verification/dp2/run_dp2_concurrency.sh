#!/usr/bin/env bash
set -euo pipefail

: "${DATABASE_URL:?DATABASE_URL is required}"
PSQL=(psql "$DATABASE_URL" -v ON_ERROR_STOP=1 -qAt -F '|')
WORK_DIR="${RUNNER_TEMP:-/tmp}/dp2-concurrency"
rm -rf "$WORK_DIR"
mkdir -p "$WORK_DIR"

call_event() {
  local event_id="$1"
  local idem_key="$2"
  local canonical_json="$3"
  local payload_json="$4"
  local build_id="$5"
  local request_ref="$6"
  local received_at="$7"
  local output_file="$8"
  local fingerprint
  fingerprint="$(printf '%s' "$canonical_json" | sha256sum | awk '{print $1}')"

  "${PSQL[@]}" \
    -v event_id="$event_id" \
    -v idem_key="$idem_key" \
    -v canonical_json="$canonical_json" \
    -v payload_json="$payload_json" \
    -v fingerprint="$fingerprint" \
    -v build_id="$build_id" \
    -v request_ref="$request_ref" \
    -v received_at="$received_at" >"$output_file" <<'SQL'
SET ROLE jc_data_event_writer;
SELECT disposition, canonical_event_id, idempotency_binding_id, COALESCE(error_code, '')
FROM public.ingest_data_platform_event_v1(
  'journey-connect', :'event_id', 'platform-event-v1',
  'user-behavior-event-v1', 'platform-event-canonical-json-v1',
  'platform-event-fingerprint-sha256-v1', :'fingerprint',
  convert_to(:'canonical_json', 'UTF8'),
  'jc-backend-event-producer-v1', :'build_id',
  'user_behavior', 'post_view', '2026-07-22T01:00:00Z'::timestamptz,
  :'received_at'::timestamptz, 'subject:dp2-concurrency-user',
  'session:dp2-concurrency-session', 'post:456', :'request_ref',
  'correlation:dp2-concurrency', NULL, :'idem_key', :'payload_json'::jsonb
);
SQL
}

CANONICAL_SAME='{"actorRef":"subject:dp2-concurrency-user","canonicalizationVersion":"platform-event-canonical-json-v1","causationId":null,"contractVersion":"platform-event-v1","entityRef":"post:456","eventFamily":"user_behavior","eventType":"post_view","occurredAt":"2026-07-22T01:00:00Z","payload":{"surface":"feed"},"schemaVersion":"user-behavior-event-v1","sessionRef":"session:dp2-concurrency-session"}'
CANONICAL_CONFLICT='{"actorRef":"subject:dp2-concurrency-user","canonicalizationVersion":"platform-event-canonical-json-v1","causationId":null,"contractVersion":"platform-event-v1","entityRef":"post:456","eventFamily":"user_behavior","eventType":"post_view","occurredAt":"2026-07-22T01:00:00Z","payload":{"surface":"search"},"schemaVersion":"user-behavior-event-v1","sessionRef":"session:dp2-concurrency-session"}'

call_event event:dp2-concurrent-same-a dp2-concurrent-same "$CANONICAL_SAME" '{"surface":"feed"}' \
  git:1111111111111111111111111111111111111111 request:dp2-same-a 2026-07-22T01:00:00.100Z "$WORK_DIR/same-a.out" &
pid_a=$!
call_event event:dp2-concurrent-same-b dp2-concurrent-same "$CANONICAL_SAME" '{"surface":"feed"}' \
  git:2222222222222222222222222222222222222222 request:dp2-same-b 2026-07-22T01:00:00.200Z "$WORK_DIR/same-b.out" &
pid_b=$!
wait "$pid_a"
wait "$pid_b"

same_results="$(cat "$WORK_DIR/same-a.out" "$WORK_DIR/same-b.out" | cut -d'|' -f1 | sort | tr '\n' ' ')"
[[ "$same_results" == "DUPLICATE NEW " ]] || {
  echo "same/same concurrency result invalid: $same_results" >&2
  cat "$WORK_DIR/same-a.out" "$WORK_DIR/same-b.out" >&2
  exit 1
}

call_event event:dp2-concurrent-diff-a dp2-concurrent-different "$CANONICAL_SAME" '{"surface":"feed"}' \
  git:3333333333333333333333333333333333333333 request:dp2-diff-a 2026-07-22T01:00:01.100Z "$WORK_DIR/diff-a.out" &
pid_a=$!
call_event event:dp2-concurrent-diff-b dp2-concurrent-different "$CANONICAL_CONFLICT" '{"surface":"search"}' \
  git:4444444444444444444444444444444444444444 request:dp2-diff-b 2026-07-22T01:00:01.200Z "$WORK_DIR/diff-b.out" &
pid_b=$!
wait "$pid_a"
wait "$pid_b"

diff_results="$(cat "$WORK_DIR/diff-a.out" "$WORK_DIR/diff-b.out" | cut -d'|' -f1 | sort | tr '\n' ' ')"
[[ "$diff_results" == "CONFLICT NEW " ]] || {
  echo "same/different concurrency result invalid: $diff_results" >&2
  cat "$WORK_DIR/diff-a.out" "$WORK_DIR/diff-b.out" >&2
  exit 1
}

read -r event_count binding_count conflict_count duplicate_count < <(
  "${PSQL[@]}" <<'SQL' | tr '|' ' '
RESET ROLE;
SELECT
  (SELECT count(*) FROM public.data_platform_event_v1
    WHERE idempotency_key IN ('dp2-concurrent-same', 'dp2-concurrent-different')),
  (SELECT count(*) FROM public.data_event_idempotency_binding_v1
    WHERE idempotency_key IN ('dp2-concurrent-same', 'dp2-concurrent-different')),
  (SELECT count(*) FROM public.data_event_conflict_observation_v1
    WHERE conflicting_event_id IN ('event:dp2-concurrent-diff-a', 'event:dp2-concurrent-diff-b')),
  (SELECT count(*) FROM public.data_event_duplicate_observation_v1
    WHERE observed_event_id IN ('event:dp2-concurrent-same-a', 'event:dp2-concurrent-same-b'));
SQL
)

[[ "$event_count" == "2" && "$binding_count" == "2" \
   && "$conflict_count" == "1" && "$duplicate_count" == "1" ]] || {
  echo "concurrency evidence counts invalid: events=$event_count bindings=$binding_count conflicts=$conflict_count duplicates=$duplicate_count" >&2
  exit 1
}

echo "DP-2 concurrency PASS: same/same=NEW+DUPLICATE same/different=NEW+CONFLICT"
