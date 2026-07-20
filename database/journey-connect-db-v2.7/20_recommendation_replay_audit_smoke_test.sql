-- Journey Connect DB v2.4 - Persisted replay audit smoke test
-- Self-contained and rolled back.

BEGIN;

DO $$
DECLARE
  v_user_id bigint;
  v_payload bytea := convert_to('{}', 'UTF8');
  v_fingerprint varchar(64) := repeat('a', 64);
BEGIN
  INSERT INTO public.app_users (email, password_hash, username, display_name)
  VALUES ('replay-smoke@journey.test', 'smoke-hash', 'replay_smoke', 'Replay Smoke')
  RETURNING id INTO v_user_id;

  INSERT INTO public.recommendation_snapshot (
    snapshot_id, snapshot_kind, schema_version, canonicalization_version,
    content_hash, canonical_payload, payload_json, payload_size_bytes
  ) VALUES
    ('replay-smoke:ranking', 'ranking_input_v1', '1.0.0', 'canonical-json-v1',
     public.recommendation_snapshot_sha256_hex('ranking_input_v1', '1.0.0', v_payload),
     v_payload, '{}'::jsonb, octet_length(v_payload)),
    ('replay-smoke:metadata', 'diversity_metadata_v1', '1.0.0', 'canonical-json-v1',
     public.recommendation_snapshot_sha256_hex('diversity_metadata_v1', '1.0.0', v_payload),
     v_payload, '{}'::jsonb, octet_length(v_payload)),
    ('replay-smoke:exploration', 'exploration_metadata_v1', '1.0.0', 'canonical-json-v1',
     public.recommendation_snapshot_sha256_hex('exploration_metadata_v1', '1.0.0', v_payload),
     v_payload, '{}'::jsonb, octet_length(v_payload)),
    ('replay-smoke:result', 'ranking_result_v1', '1.0.0', 'canonical-json-v1',
     public.recommendation_snapshot_sha256_hex('ranking_result_v1', '1.0.0', v_payload),
     v_payload, '{}'::jsonb, octet_length(v_payload));

  INSERT INTO public.recommendation_run (
    run_id, request_id, run_mode, run_status, user_id, session_id, context_id,
    surface, reference_time, ranking_snapshot_id, metadata_snapshot_id,
    exploration_snapshot_id, result_snapshot_id, ranking_policy_version,
    base_integration_policy_version, base_ranking_policy_version,
    score_policy_version, component_policy_versions, diversity_policy_version,
    exploration_policy_version, exploration_seed, ranking_status,
    ranking_empty_reason, requested_limit, effective_limit, input_count,
    scored_candidate_count, final_ranked_candidate_count, terminal_candidate_count,
    result_fingerprint, core_build_id, duration_ms
  ) VALUES (
    'replay-smoke:run', 'replay-smoke:request', 'shadow', 'succeeded', v_user_id,
    'replay-smoke:session', 'replay-smoke:context', 'home', CURRENT_TIMESTAMP,
    'replay-smoke:ranking', 'replay-smoke:metadata', 'replay-smoke:exploration',
    'replay-smoke:result', 'ranking-v3', 'integration-v1', 'ranking-v2',
    'score-v1', '{}'::jsonb, 'diversity-v1', 'exploration-v1', 'replay-smoke:seed',
    'empty', 'no_scored_candidates', NULL, 0, 0, 0, 0, 0,
    v_fingerprint, 'smoke-build', 0
  );

  INSERT INTO public.recommendation_replay_audit (
    audit_id, run_id, evaluator_version, evaluator_build_id, replay_status,
    mismatch_categories, ranking_input_hash, result_snapshot_hash,
    expected_result_fingerprint, actual_result_fingerprint,
    ranked_candidate_count, terminal_candidate_count, duration_ms
  ) VALUES (
    'replay-smoke:audit', 'replay-smoke:run', 'smoke-v1', 'smoke-build',
    'exact_match', '[]'::jsonb, repeat('b', 64), repeat('c', 64),
    v_fingerprint, v_fingerprint, 0, 0, 0
  );

  IF NOT has_table_privilege('jc_recommendation',
       'public.recommendation_replay_audit', 'SELECT, INSERT') THEN
    RAISE EXCEPTION 'jc_recommendation replay audit privileges are incomplete';
  END IF;
  IF has_table_privilege('jc_app',
       'public.recommendation_replay_audit', 'SELECT, INSERT, UPDATE, DELETE, TRUNCATE') THEN
    RAISE EXCEPTION 'jc_app must not access replay audit history';
  END IF;

  BEGIN
    UPDATE public.recommendation_replay_audit
      SET duration_ms = 1
    WHERE audit_id = 'replay-smoke:audit';
    RAISE EXCEPTION 'Replay audit update unexpectedly succeeded';
  EXCEPTION WHEN SQLSTATE '55000' THEN
    NULL;
  END;
END;
$$;

ROLLBACK;
