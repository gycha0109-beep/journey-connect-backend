#!/usr/bin/env bash
set -euo pipefail

: "${DATABASE_URL:?DATABASE_URL is required}"

suffix="${GITHUB_RUN_ID:-local}-$$-$(date +%s%N)"
source_ref="recommendation_behavior_event:dp45-concurrency-${suffix}"
result_a="$(mktemp)"
result_b="$(mktemp)"
trap 'rm -f "$result_a" "$result_b"' EXIT

call_sql=$(cat <<SQL
SELECT disposition
FROM public.persist_recommendation_adapter_shadow_evidence_v1(
  p_source_event_ref => '${source_ref}',
  p_source_fingerprint => repeat('a', 64),
  p_source_contract_version => 'recommendation-behavior-event-v1',
  p_source_schema_version => 'recommendation-behavior-event-v1',
  p_adapter_id => 'p0-recommendation-event-adapter-v1',
  p_adapter_version => 'recommendation-p0-event-adapter-v1',
  p_mapping_policy_version => 'recommendation-p0-mapping-policy-v1',
  p_output_fingerprint_version => 'recommendation-p0-adapter-output-sha256-v1',
  p_target_contract_version => 'platform-event-v1',
  p_target_schema_version => 'user-behavior-event-v1',
  p_producer_build_id => 'git:aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa',
  p_started_at => clock_timestamp() - interval '1 second',
  p_completed_at => clock_timestamp(),
  p_mapping_status => 'mapped_shadow',
  p_compatibility_class => 'semantic_compatible',
  p_mapped_event_type => 'post_like',
  p_mapped_actor_ref => 'subject:dp45-concurrency',
  p_mapped_session_ref => 'session:dp45-concurrency',
  p_mapped_entity_ref => 'post:450',
  p_mapped_occurred_at => clock_timestamp() - interval '2 seconds',
  p_mapped_payload => '{"stateTransitionRef":"recommendation_behavior_event:dp45-concurrency"}'::jsonb,
  p_output_fingerprint => repeat('b', 64)
);
SQL
)

psql "$DATABASE_URL" -X -v ON_ERROR_STOP=1 -At -c "$call_sql" >"$result_a" &
pid_a=$!
psql "$DATABASE_URL" -X -v ON_ERROR_STOP=1 -At -c "$call_sql" >"$result_b" &
pid_b=$!
wait "$pid_a"
wait "$pid_b"

mapfile -t dispositions < <(cat "$result_a" "$result_b" | sed '/^[[:space:]]*$/d' | sort)
if [[ "${#dispositions[@]}" -ne 2 || "${dispositions[0]}" != "DUPLICATE" || "${dispositions[1]}" != "NEW" ]]; then
  printf 'DP-4.5 concurrency dispositions invalid: %s\n' "${dispositions[*]:-none}" >&2
  exit 1
fi

read -r run_count output_count failure_count conflict_count duplicate_count < <(
  psql "$DATABASE_URL" -X -v ON_ERROR_STOP=1 -At -F ' ' -c "
    WITH identity AS (
      SELECT public.data_recommendation_adapter_logical_identity_hash_v1(
        '${source_ref}', repeat('a', 64),
        'p0-recommendation-event-adapter-v1',
        'recommendation-p0-event-adapter-v1',
        'platform-event-v1',
        'recommendation-p0-mapping-policy-v1') AS hash
    )
    SELECT
      (SELECT count(*) FROM public.data_recommendation_adapter_run_v1 run, identity
       WHERE run.logical_identity_hash = identity.hash),
      (SELECT count(*) FROM public.data_recommendation_adapter_output_v1 output
       JOIN public.data_recommendation_adapter_run_v1 run ON run.adapter_run_id = output.adapter_run_ref
       JOIN identity ON run.logical_identity_hash = identity.hash),
      (SELECT count(*) FROM public.data_recommendation_adapter_failure_v1 failure
       JOIN public.data_recommendation_adapter_run_v1 run ON run.adapter_run_id = failure.adapter_run_ref
       JOIN identity ON run.logical_identity_hash = identity.hash),
      (SELECT count(*) FROM public.data_recommendation_adapter_conflict_observation_v1 conflict, identity
       WHERE conflict.logical_identity_hash = identity.hash),
      (SELECT COALESCE(counter.duplicate_count, 0)
       FROM identity LEFT JOIN public.data_recommendation_adapter_duplicate_counter_v1 counter
         ON counter.logical_identity_hash = identity.hash);
  "
)

if [[ "$run_count" != "1" || "$output_count" != "1" || "$failure_count" != "0" \
      || "$conflict_count" != "0" || "$duplicate_count" != "1" ]]; then
  printf 'DP-4.5 concurrency counts invalid: run=%s output=%s failure=%s conflict=%s duplicate=%s\n' \
    "$run_count" "$output_count" "$failure_count" "$conflict_count" "$duplicate_count" >&2
  exit 1
fi

printf 'DP-4.5 concurrency: PASS (NEW=1 DUPLICATE=1 run=1 output=1)\n'
