-- Journey Connect DB v2.6 - P1 profile/policy/comparison smoke test
BEGIN;

DO $$
DECLARE
  v_user bigint;
  v_region bigint;
  v_place bigint;
  v_post bigint;
  v_reference timestamptz := '2026-07-19T00:00:00Z'::timestamptz;
BEGIN
  INSERT INTO public.app_users(email, password_hash, username, display_name)
  VALUES ('p1-smoke@journey.test', 'hash', 'p1_smoke', 'P1 Smoke')
  RETURNING id INTO v_user;

  SELECT id INTO v_region FROM public.regions WHERE slug = 'kr-seoul';
  INSERT INTO public.places(region_id, name_local, name_ko, category, created_by_user_id)
  VALUES (v_region, 'P1 Place', 'P1 장소', 'test', v_user)
  RETURNING id INTO v_place;
  INSERT INTO public.posts(author_id, main_region_id, title, content, visibility, status)
  VALUES (v_user, v_region, 'P1 Post', 'P1 smoke', 'public', 'draft')
  RETURNING id INTO v_post;
  INSERT INTO public.post_places(post_id, place_id, sort_order) VALUES (v_post, v_place, 0);
  UPDATE public.posts SET status = 'published', published_at = v_reference WHERE id = v_post;

  PERFORM set_config('jc.current_user_id', v_user::text, true);
  IF public.replace_recommendation_user_preferences(
      '[{"featureId":"theme:food","preferenceKind":"prefer","strength":0.9}]'::jsonb
    ) <> 1 THEN
    RAISE EXCEPTION 'P1 preference replacement count mismatch';
  END IF;

  INSERT INTO public.recommendation_snapshot(
    snapshot_id, snapshot_kind, schema_version, canonicalization_version,
    hash_algorithm, content_hash, canonical_payload, payload_json, payload_size_bytes
  ) VALUES
  ('p1-smoke-ranking', 'ranking_input_v1', 'p1.0.0', 'canonical-json-v1', 'sha256',
    public.recommendation_snapshot_sha256_hex('ranking_input_v1', 'p1.0.0', convert_to('{}', 'UTF8')),
    convert_to('{}', 'UTF8'), '{}'::jsonb, 2),
  ('p1-smoke-metadata', 'diversity_metadata_v1', 'p1.0.0', 'canonical-json-v1', 'sha256',
    public.recommendation_snapshot_sha256_hex('diversity_metadata_v1', 'p1.0.0', convert_to('[]', 'UTF8')),
    convert_to('[]', 'UTF8'), '[]'::jsonb, 2),
  ('p1-smoke-exposure', 'exploration_metadata_v1', 'p1.0.0', 'canonical-json-v1', 'sha256',
    public.recommendation_snapshot_sha256_hex('exploration_metadata_v1', 'p1.0.0', convert_to('[0]', 'UTF8')),
    convert_to('[0]', 'UTF8'), '[0]'::jsonb, 3),
  ('p1-smoke-baseline-result', 'ranking_result_v1', '1.0.0', 'canonical-json-v1', 'sha256',
    public.recommendation_snapshot_sha256_hex('ranking_result_v1', '1.0.0', convert_to('{"baseline":true}', 'UTF8')),
    convert_to('{"baseline":true}', 'UTF8'), '{"baseline":true}'::jsonb, 17),
  ('p1-smoke-treatment-result', 'ranking_result_v1', 'p1.0.0', 'canonical-json-v1', 'sha256',
    public.recommendation_snapshot_sha256_hex('ranking_result_v1', 'p1.0.0', convert_to('{"treatment":true}', 'UTF8')),
    convert_to('{"treatment":true}', 'UTF8'), '{"treatment":true}'::jsonb, 18);

  INSERT INTO public.recommendation_run(
    run_id, request_id, run_mode, run_status, user_id, session_id, context_id,
    surface, reference_time, ranking_snapshot_id, metadata_snapshot_id,
    exploration_snapshot_id, result_snapshot_id, ranking_policy_version,
    base_integration_policy_version, base_ranking_policy_version,
    score_policy_version, component_policy_versions, diversity_policy_version,
    exploration_policy_version, exploration_seed, ranking_status,
    effective_limit, input_count, scored_candidate_count,
    final_ranked_candidate_count, terminal_candidate_count,
    result_fingerprint, core_build_id, duration_ms
  ) VALUES
  ('p1-smoke-baseline-run', 'p1-smoke-baseline-request', 'shadow', 'succeeded',
    v_user, 'p1-smoke-session', 'p1-smoke-context', 'home', v_reference,
    'p1-smoke-ranking', 'p1-smoke-metadata', 'p1-smoke-exposure',
    'p1-smoke-baseline-result', 'ranking-policy-v1', 'integration-policy-v1',
    'ranking-policy-v1', 'score-policy-v1', '{}'::jsonb,
    'diversity-policy-v1', 'exploration-policy-v2', 'baseline-seed',
    'ranked', 1, 1, 1, 1, 0, repeat('a', 64), 'java-core-1.0.0', 1),
  ('p1-smoke-treatment-run', 'p1-smoke-treatment-request', 'shadow', 'succeeded',
    v_user, 'p1-smoke-session', 'p1-smoke-context', 'home', v_reference,
    'p1-smoke-ranking', 'p1-smoke-metadata', 'p1-smoke-exposure',
    'p1-smoke-treatment-result', 'p1-policy-bundle-v1:home_feed:explicit_only',
    'p1-integration-v1', 'ranking-policy-v1', 'ranking-policy-v2-explicit',
    '{}'::jsonb, 'diversity-policy-home-v2', 'exploration-policy-v2',
    'p1-deterministic-no-random-v1', 'ranked', 1, 1, 1, 1, 0,
    repeat('b', 64), 'java-core-1.1.0-p1', 1);

  INSERT INTO public.recommendation_run_candidate(
    run_id, absolute_rank, entity_type, entity_key, source_entity_id, origin,
    score, score_is_negative_zero, base_absolute_rank, diversified_absolute_rank,
    score_policy_version, provenance
  ) VALUES
  ('p1-smoke-baseline-run', 1, 'post', 'post:' || v_post::text, v_post,
    'personalized', 0.7, false, 1, 1, 'score-policy-v1', '{}'::jsonb),
  ('p1-smoke-treatment-run', 1, 'post', 'post:' || v_post::text, v_post,
    'personalized', 0.8, false, 1, 1, 'ranking-policy-v2-explicit',
    '{"lowExposureBoost":0.08}'::jsonb);

  INSERT INTO public.recommendation_p1_profile_snapshot(
    profile_snapshot_id, user_id, reference_time, profile_policy_version,
    feature_vocabulary_version, segment, explicit_preference_count,
    input_event_count, accepted_event_count, ignored_event_count,
    duplicate_event_count, accepted_behavior_weight, signal_count, signals,
    fingerprint
  ) VALUES (
    'p1-smoke-profile', v_user, v_reference, 'behavior-profile-policy-v1',
    'feature-vocabulary-v2', 'explicit_only', 1, 0, 0, 0, 0, 0.0,
    1, '[{"featureId":"theme:food"}]'::jsonb, repeat('c', 64)
  );

  INSERT INTO public.recommendation_p1_policy_assignment(
    assignment_id, baseline_run_id, treatment_run_id, user_id, session_id,
    profile_snapshot_id, release_id, experiment_assignment, segment,
    selection_reasons, profile_policy_version, feature_vocabulary_version,
    retrieval_policy_version, policy_bundle_version, score_policy_version,
    diversity_policy_version, low_exposure_policy_version,
    exploration_policy_version
  ) VALUES (
    'p1-smoke-assignment', 'p1-smoke-baseline-run', 'p1-smoke-treatment-run',
    v_user, 'p1-smoke-session', 'p1-smoke-profile', 'p1-smoke-release',
    'treatment', 'explicit_only', '["segment:explicit_only"]'::jsonb,
    'behavior-profile-policy-v1', 'feature-vocabulary-v2',
    'retrieval-policy-v2', 'p1-policy-bundle-v1:home_feed:explicit_only',
    'ranking-policy-v2-explicit', 'diversity-policy-home-v2',
    'low-exposure-policy-v2', 'exploration-policy-v2'
  );

  INSERT INTO public.recommendation_p1_comparison(
    comparison_id, baseline_run_id, treatment_run_id,
    baseline_result_fingerprint, treatment_result_fingerprint,
    baseline_policy_version, treatment_policy_version, cutoff,
    baseline_count, treatment_count, overlap_count, overlap_rate,
    mean_absolute_rank_displacement, treatment_unique_author_count,
    treatment_unique_region_count, treatment_unique_theme_count,
    treatment_low_exposure_share, treatment_top_author_share,
    treatment_top_region_share, treatment_mean_adjusted_popularity,
    comparison_fingerprint
  ) VALUES (
    'p1-smoke-comparison', 'p1-smoke-baseline-run', 'p1-smoke-treatment-run',
    repeat('a', 64), repeat('b', 64), 'ranking-policy-v1',
    'p1-policy-bundle-v1:home_feed:explicit_only', 20, 1, 1, 1, 1.0,
    0.0, 1, 1, 1, 1.0, 1.0, 1.0, 0.5, repeat('d', 64)
  );
END;
$$;

DO $$
DECLARE
  v_p1_tag_count integer;
BEGIN
  SELECT count(*)::integer INTO v_p1_tag_count
  FROM public.tags
  WHERE slug IN ('history', 'adventure', 'wellness', 'running', 'plogging', 'pilgrimage', 'cycling')
    AND is_active = true;
  IF v_p1_tag_count <> 7 THEN
    RAISE EXCEPTION 'P1 feature vocabulary tag seed mismatch: %', v_p1_tag_count;
  END IF;
END;
$$;

DO $$
BEGIN
  IF NOT has_table_privilege(
      'jc_recommendation', 'public.recommendation_p1_profile_snapshot', 'INSERT') THEN
    RAISE EXCEPTION 'jc_recommendation missing P1 profile insert';
  END IF;
  IF has_table_privilege(
      'jc_recommendation', 'public.recommendation_p1_profile_snapshot', 'UPDATE') THEN
    RAISE EXCEPTION 'jc_recommendation unexpectedly has P1 profile update';
  END IF;
  IF has_table_privilege(
      'jc_app', 'public.recommendation_p1_policy_assignment', 'INSERT') THEN
    RAISE EXCEPTION 'jc_app unexpectedly has P1 assignment insert';
  END IF;
  IF has_table_privilege(
      'jc_recommendation', 'public.recommendation_user_preference', 'INSERT') THEN
    RAISE EXCEPTION 'jc_recommendation unexpectedly has direct preference insert';
  END IF;
  IF NOT has_function_privilege(
      'jc_recommendation', 'public.replace_recommendation_user_preferences(jsonb)', 'EXECUTE') THEN
    RAISE EXCEPTION 'jc_recommendation missing preference replacement function';
  END IF;
END;
$$;

SAVEPOINT p1_profile_append_only_probe;
DO $$
BEGIN
  BEGIN
    UPDATE public.recommendation_p1_profile_snapshot
    SET segment = 'established'
    WHERE profile_snapshot_id = 'p1-smoke-profile';
    RAISE EXCEPTION 'P1 profile update unexpectedly succeeded';
  EXCEPTION WHEN SQLSTATE '55000' THEN
    NULL;
  END;
END;
$$;
ROLLBACK TO SAVEPOINT p1_profile_append_only_probe;

SAVEPOINT p1_assignment_binding_probe;
DO $$
BEGIN
  BEGIN
    INSERT INTO public.recommendation_p1_policy_assignment(
      assignment_id, baseline_run_id, treatment_run_id, user_id, session_id,
      profile_snapshot_id, release_id, experiment_assignment, segment,
      selection_reasons, profile_policy_version, feature_vocabulary_version,
      retrieval_policy_version, policy_bundle_version, score_policy_version,
      diversity_policy_version, low_exposure_policy_version,
      exploration_policy_version
    )
    SELECT 'p1-bad-assignment', baseline_run_id, treatment_run_id, user_id,
           'wrong-session', profile_snapshot_id, release_id, experiment_assignment,
           segment, selection_reasons, profile_policy_version,
           feature_vocabulary_version, retrieval_policy_version,
           policy_bundle_version, score_policy_version, diversity_policy_version,
           low_exposure_policy_version, exploration_policy_version
    FROM public.recommendation_p1_policy_assignment
    WHERE assignment_id = 'p1-smoke-assignment';
    RAISE EXCEPTION 'P1 assignment binding mismatch unexpectedly succeeded';
  EXCEPTION WHEN SQLSTATE '23514' THEN
    NULL;
  END;
END;
$$;
ROLLBACK TO SAVEPOINT p1_assignment_binding_probe;

ROLLBACK;
