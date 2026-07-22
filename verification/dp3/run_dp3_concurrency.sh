#!/usr/bin/env bash
set -euo pipefail

: "${DATABASE_URL:?DATABASE_URL is required}"
WORK_DIR="$(mktemp -d)"
trap 'rm -rf "$WORK_DIR"' EXIT

psqlq() {
  psql "$DATABASE_URL" -X -v ON_ERROR_STOP=1 -Atq "$@"
}

seed_event() {
  local id="$1"
  psqlq <<SQL
WITH observed AS (
  SELECT clock_timestamp() AS now_value
)
INSERT INTO public.data_platform_event_v1 (
  event_id, tenant, contract_version, schema_version, canonicalization_version,
  fingerprint_version, payload_fingerprint, fingerprint_canonical_bytes,
  producer_version, producer_build_id, event_family, event_type,
  occurred_at, received_at, actor_ref, entity_ref, request_ref, correlation_ref,
  idempotency_key, canonical_payload, expires_at
)
SELECT 'event:${id}', 'journey-connect', 'platform-event-v1', 'user-behavior-event-v1',
  'platform-event-canonical-json-v1', 'platform-event-fingerprint-sha256-v1',
  encode(public.digest(convert_to('{}','UTF8'),'sha256'),'hex'), convert_to('{}','UTF8'),
  'dp3-concurrency-v1', 'git:9999999999999999999999999999999999999999',
  'user_behavior', 'post_view', observed.now_value, observed.now_value,
  'subject:${id}', 'post:1', 'request:${id}', 'correlation:dp3-concurrency',
  'key-${id}', '{}'::jsonb, observed.now_value + interval '365 days'
FROM observed;
SQL
}

seed_event "concurrent-one"
psqlq -c "SELECT public.register_data_projection_work_v1('work:concurrent-one','event:concurrent-one','concurrency_projection','concurrency-projection-v1',clock_timestamp());" >/dev/null

(psqlq -c "SELECT work_id,claim_token FROM public.claim_data_projection_work_v1('worker:concurrent-a',1);" >"$WORK_DIR/a.out") &
PID_A=$!
(psqlq -c "SELECT work_id,claim_token FROM public.claim_data_projection_work_v1('worker:concurrent-b',1);" >"$WORK_DIR/b.out") &
PID_B=$!
wait "$PID_A" "$PID_B"

CLAIM_LINES=$(cat "$WORK_DIR/a.out" "$WORK_DIR/b.out" | grep -c '|' || true)
if [[ "$CLAIM_LINES" -ne 1 ]]; then
  echo "Expected exactly one claim for the same work, got $CLAIM_LINES" >&2
  cat "$WORK_DIR/a.out" "$WORK_DIR/b.out" >&2
  exit 1
fi
echo "DUPLICATE_CLAIM_BLOCKED"

seed_event "independent-one"
seed_event "independent-two"
psqlq -c "SELECT public.register_data_projection_work_v1('work:independent-one','event:independent-one','concurrency_projection_a','concurrency-projection-v1',clock_timestamp());" >/dev/null
psqlq -c "SELECT public.register_data_projection_work_v1('work:independent-two','event:independent-two','concurrency_projection_b','concurrency-projection-v1',clock_timestamp());" >/dev/null

(psqlq -c "SELECT work_id FROM public.claim_data_projection_work_v1('worker:independent-a',1);" >"$WORK_DIR/ia.out") &
PID_IA=$!
(psqlq -c "SELECT work_id FROM public.claim_data_projection_work_v1('worker:independent-b',1);" >"$WORK_DIR/ib.out") &
PID_IB=$!
wait "$PID_IA" "$PID_IB"

INDEPENDENT_LINES=$(cat "$WORK_DIR/ia.out" "$WORK_DIR/ib.out" | grep -cE '^[0-9a-f-]{36}$' || true)
UNIQUE_LINES=$(cat "$WORK_DIR/ia.out" "$WORK_DIR/ib.out" | grep -E '^[0-9a-f-]{36}$' | sort -u | wc -l | tr -d ' ')
if [[ "$INDEPENDENT_LINES" -ne 2 || "$UNIQUE_LINES" -ne 2 ]]; then
  echo "Independent jobs were not distributed uniquely" >&2
  cat "$WORK_DIR/ia.out" "$WORK_DIR/ib.out" >&2
  exit 1
fi
echo "INDEPENDENT_CLAIMS_DISTRIBUTED"

echo "DP3_CONCURRENCY_PASS"
