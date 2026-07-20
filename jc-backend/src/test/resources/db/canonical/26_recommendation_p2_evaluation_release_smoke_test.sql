-- Journey Connect DB v2.7 - P2 smoke
BEGIN;

DO $$
DECLARE
  smoke_user_id bigint;
  dataset_payload bytea := convert_to('{"dataset":"p2-smoke"}', 'UTF8');
BEGIN
  INSERT INTO public.app_users(email, password_hash, username, display_name)
  VALUES ('p2-smoke@journey.test', 'hash', 'p2_smoke', 'P2 Smoke')
  RETURNING id INTO smoke_user_id;

  INSERT INTO public.recommendation_snapshot(
    snapshot_id, snapshot_kind, schema_version, canonicalization_version,
    hash_algorithm, content_hash, canonical_payload, payload_json, payload_size_bytes
  ) VALUES
    ('p2-rank', 'ranking_input_v1', 'p1.0.0', 'canonical-json-v1', 'sha256',
      public.recommendation_snapshot_sha256_hex('ranking_input_v1', 'p1.0.0', convert_to('{}', 'UTF8')),
      convert_to('{}', 'UTF8'), '{}', 2),
    ('p2-meta', 'diversity_metadata_v1', 'p1.0.0', 'canonical-json-v1', 'sha256',
      public.recommendation_snapshot_sha256_hex('diversity_metadata_v1', 'p1.0.0', convert_to('[]', 'UTF8')),
      convert_to('[]', 'UTF8'), '[]', 2),
    ('p2-exp', 'exploration_metadata_v1', 'p1.0.0', 'canonical-json-v1', 'sha256',
      public.recommendation_snapshot_sha256_hex('exploration_metadata_v1', 'p1.0.0', convert_to('[]', 'UTF8')),
      convert_to('[]', 'UTF8'), '[]', 2),
    ('p2-result', 'ranking_result_v1', 'p1.0.0', 'canonical-json-v1', 'sha256',
      public.recommendation_snapshot_sha256_hex('ranking_result_v1', 'p1.0.0', convert_to('{}', 'UTF8')),
      convert_to('{}', 'UTF8'), '{}', 2);

  INSERT INTO public.recommendation_run(
    run_id, request_id, run_mode, run_status, user_id, session_id, context_id, surface,
    reference_time, ranking_snapshot_id, metadata_snapshot_id, exploration_snapshot_id,
    result_snapshot_id, ranking_policy_version, base_integration_policy_version,
    base_ranking_policy_version, score_policy_version, component_policy_versions,
    diversity_policy_version, exploration_policy_version, exploration_seed,
    ranking_status, ranking_empty_reason, effective_limit, input_count,
    scored_candidate_count, final_ranked_candidate_count, terminal_candidate_count,
    result_fingerprint, core_build_id, duration_ms
  ) VALUES
    ('p2-baseline', 'p2-request-baseline', 'canary', 'succeeded', smoke_user_id,
      'p2-session', 'p2-context', 'home', '2026-07-19T00:00:00Z',
      'p2-rank', 'p2-meta', 'p2-exp', 'p2-result', 'ranking-v3',
      'p0-integration-v1', 'ranking-policy-v1', 'ranking-policy-v1', '{}',
      'diversity-policy-v1', 'exploration-policy-v1', 'p0-deterministic-v1',
      'empty', 'no_scored_candidates', 0, 0, 0, 0, 0, repeat('b', 64),
      'java-core-1.0.0', 1),
    ('p2-treatment', 'p2-request-treatment', 'canary', 'succeeded', smoke_user_id,
      'p2-session', 'p2-context', 'home', '2026-07-19T00:00:00Z',
      'p2-rank', 'p2-meta', 'p2-exp', 'p2-result',
      'p1-policy-bundle-v1:home_feed:empty', 'p1-integration-v1',
      'ranking-policy-v1', 'ranking-policy-v2-empty', '{}',
      'diversity-policy-home-v2', 'exploration-policy-v2',
      'p1-deterministic-no-random-v1', 'empty', 'no_scored_candidates',
      0, 0, 0, 0, 0, repeat('a', 64), 'java-core-1.1.0-p1', 1);

  INSERT INTO public.recommendation_p1_profile_snapshot(
    profile_snapshot_id, user_id, reference_time, profile_policy_version,
    feature_vocabulary_version, segment, explicit_preference_count, input_event_count,
    accepted_event_count, ignored_event_count, duplicate_event_count,
    accepted_behavior_weight, signal_count, signals, fingerprint
  ) VALUES (
    'p2-profile', smoke_user_id, '2026-07-19T00:00:00Z',
    'behavior-profile-policy-v1', 'feature-vocabulary-v1', 'empty',
    0, 0, 0, 0, 0, 0, 0, '[]', repeat('c', 64)
  );

  INSERT INTO public.recommendation_p1_policy_assignment(
    assignment_id, baseline_run_id, treatment_run_id, user_id, session_id,
    profile_snapshot_id, release_id, experiment_assignment, segment, selection_reasons,
    profile_policy_version, feature_vocabulary_version, retrieval_policy_version,
    policy_bundle_version, score_policy_version, diversity_policy_version,
    low_exposure_policy_version, exploration_policy_version
  ) VALUES (
    'p2-p1-assignment', 'p2-baseline', 'p2-treatment', smoke_user_id, 'p2-session',
    'p2-profile', 'p2-treatment-v1', 'treatment', 'empty', '["empty_profile"]',
    'behavior-profile-policy-v1', 'feature-vocabulary-v1', 'retrieval-policy-v1',
    'p1-policy-bundle-v1:home_feed:empty', 'ranking-policy-v2-empty',
    'diversity-policy-home-v2', 'low-exposure-policy-v1', 'exploration-policy-v2'
  );

  INSERT INTO public.recommendation_p2_experiment_assignment(
    assignment_id, experiment_id, experiment_version, subject_ref, user_id,
    assignment_unit, variant, bucket, assignment_fingerprint, assigned_at, producer_build_id
  ) VALUES (
    'p2-assignment', 'recommendation-p1', 'experiment-v1', 'user:' || smoke_user_id,
    smoke_user_id, 'user', 'treatment', 1234, repeat('e', 64),
    '2026-07-19T00:00:00Z', 'p2-assignment-build-v1'
  );

  INSERT INTO public.recommendation_p2_experiment_exposure(
    exposure_id, assignment_id, run_id, user_id, session_id, variant,
    exposed_at, exposure_fingerprint
  ) VALUES (
    'p2-exposure', 'p2-assignment', 'p2-treatment', smoke_user_id,
    'p2-session', 'treatment', '2026-07-19T00:00:01Z', repeat('f', 64)
  );

  -- A treatment assignment cannot be bound to its baseline run.
  BEGIN
    INSERT INTO public.recommendation_p2_experiment_exposure(
      exposure_id, assignment_id, run_id, user_id, session_id, variant,
      exposed_at, exposure_fingerprint
    ) VALUES (
      'p2-forged-exposure', 'p2-assignment', 'p2-baseline', smoke_user_id,
      'p2-session', 'treatment', '2026-07-19T00:00:01Z', repeat('0', 64)
    );
    RAISE EXCEPTION 'forged treatment exposure succeeded';
  EXCEPTION WHEN SQLSTATE '23514' THEN
    NULL;
  END;

  INSERT INTO public.recommendation_p2_metric_definition(
    metric_definition_version, metric_id, metric_role, direction,
    minimum_effect, maximum_allowed_regression, attribution_window_seconds,
    numerator_definition, denominator_definition, eligibility_definition,
    deduplication_definition
  ) VALUES
    ('recommendation-metrics-v1', 'engagement_rate', 'primary', 'higher_is_better',
      0.01, 0, 604800, 'engaged exposed subjects', 'exposed subjects',
      'assigned exposed eligible', 'one observation per subject'),
    ('recommendation-metrics-v1', 'fallback_rate', 'guardrail', 'lower_is_better',
      0, 0.02, 86400, 'fallback exposed runs', 'exposed runs',
      'assigned exposed eligible', 'distinct run IDs');

  INSERT INTO public.recommendation_p2_dataset_snapshot(
    dataset_snapshot_id, dataset_schema_version, metric_definition_version,
    experiment_id, experiment_version, observed_from, observed_to,
    observation_count, canonicalization_version, canonical_payload,
    payload_size_bytes, content_hash
  ) VALUES (
    'p2-dataset', 'recommendation-evaluation-dataset-v1', 'recommendation-metrics-v1',
    'recommendation-p1', 'experiment-v1', '2026-07-01', '2026-07-15', 20,
    'canonical-json-v1', dataset_payload, octet_length(dataset_payload),
    public.recommendation_sha256_hex(dataset_payload)
  );

  INSERT INTO public.recommendation_p2_evaluation_run(
    evaluation_run_id, dataset_snapshot_id, metric_definition_version,
    evaluation_policy_version, experiment_id, experiment_version,
    baseline_policy_version, treatment_policy_version, evaluator_build_id,
    evaluated_at, current_state, requested_state, operational_approval,
    final_decision, target_state, evaluation_fingerprint
  ) VALUES (
    'p2-evaluation', 'p2-dataset', 'recommendation-metrics-v1',
    'recommendation-evaluation-policy-v1', 'recommendation-p1', 'experiment-v1',
    'ranking-policy-v1', 'p1-policy-bundle-v1:home_feed:empty',
    'p2-evaluator-build-v1', '2026-07-16', 'shadow', 'canary', true,
    'canary', 'canary', repeat('1', 64)
  );

  INSERT INTO public.recommendation_p2_metric_result(
    evaluation_run_id, segment, metric_definition_version, metric_id,
    baseline_count, treatment_count, eligible_exposed_count, missing_metric_count,
    common_support_rate, baseline_mean, treatment_mean, raw_effect, oriented_effect,
    effect_size, confidence_lower, confidence_upper, confidence_level,
    p_value, adjusted_p_value, sample_sufficient, data_quality_pass, performance_pass
  ) VALUES
    ('p2-evaluation', 'all', 'recommendation-metrics-v1', 'engagement_rate',
      10, 10, 20, 0, 1, 0.5, 0.7, 0.2, 0.2, 0.5, 0.1, 0.3,
      0.95, 0.01, 0.02, true, true, true),
    ('p2-evaluation', 'all', 'recommendation-metrics-v1', 'fallback_rate',
      10, 10, 20, 0, 1, 0.1, 0.05, -0.05, 0.05, 0.3, -0.08, -0.02,
      0.95, 0.02, 0.04, true, true, true);

  INSERT INTO public.recommendation_p2_gate_result(
    evaluation_run_id, gate_id, gate_status, reason_codes
  ) VALUES
    ('p2-evaluation', 'gate_a_contract_integrity', 'pass', '[]'),
    ('p2-evaluation', 'gate_b_data_quality', 'pass', '[]'),
    ('p2-evaluation', 'gate_c_sample_sufficiency', 'pass', '[]'),
    ('p2-evaluation', 'gate_d_performance_guardrail', 'pass', '[]'),
    ('p2-evaluation', 'gate_e_operational_approval', 'pass', '[]');

  INSERT INTO public.recommendation_p2_release_decision(
    decision_id, evaluation_run_id, experiment_id, experiment_version,
    from_state, to_state, final_decision, actor_ref, reason_code, decided_at
  ) VALUES (
    'p2-decision', 'p2-evaluation', 'recommendation-p1', 'experiment-v1',
    'shadow', 'canary', 'canary', 'system:p2-smoke', 'ALL_GATES_PASS',
    '2026-07-16T00:00:01Z'
  );

  -- A release transition cannot be persisted before all five gate rows exist.
  BEGIN
    INSERT INTO public.recommendation_p2_evaluation_run(
      evaluation_run_id, dataset_snapshot_id, metric_definition_version,
      evaluation_policy_version, experiment_id, experiment_version,
      baseline_policy_version, treatment_policy_version, evaluator_build_id,
      evaluated_at, current_state, requested_state, operational_approval,
      final_decision, target_state, evaluation_fingerprint
    ) VALUES (
      'p2-forged-evaluation', 'p2-dataset', 'recommendation-metrics-v1',
      'recommendation-evaluation-policy-v1', 'recommendation-p1', 'experiment-v1',
      'ranking-policy-v1', 'p1-policy-bundle-v1:home_feed:empty',
      'p2-evaluator-build-v1', '2026-07-16', 'shadow', 'canary', true,
      'canary', 'canary', repeat('2', 64)
    );
    INSERT INTO public.recommendation_p2_release_decision(
      decision_id, evaluation_run_id, experiment_id, experiment_version,
      from_state, to_state, final_decision, actor_ref, reason_code, decided_at
    ) VALUES (
      'p2-forged-decision', 'p2-forged-evaluation', 'recommendation-p1',
      'experiment-v1', 'shadow', 'canary', 'canary', 'system:p2-smoke',
      'ALL_GATES_PASS', '2026-07-16T00:00:01Z'
    );
    RAISE EXCEPTION 'release without gates succeeded';
  EXCEPTION WHEN SQLSTATE '23514' THEN
    NULL;
  END;
END;
$$;

DO $$
BEGIN
  IF NOT has_table_privilege(
          'jc_recommendation', 'public.recommendation_p2_evaluation_run', 'INSERT')
     OR has_table_privilege(
          'jc_recommendation', 'public.recommendation_p2_evaluation_run', 'UPDATE')
     OR has_table_privilege(
          'jc_app', 'public.recommendation_p2_experiment_assignment', 'INSERT') THEN
    RAISE EXCEPTION 'P2 privilege contract failed';
  END IF;
END;
$$;

SAVEPOINT p2_probe;
DO $$
BEGIN
  BEGIN
    UPDATE public.recommendation_p2_evaluation_run
    SET final_decision = 'hold'
    WHERE evaluation_run_id = 'p2-evaluation';
    RAISE EXCEPTION 'mutation succeeded';
  EXCEPTION WHEN SQLSTATE '55000' THEN
    NULL;
  END;
END;
$$;
ROLLBACK TO SAVEPOINT p2_probe;

COMMIT;
