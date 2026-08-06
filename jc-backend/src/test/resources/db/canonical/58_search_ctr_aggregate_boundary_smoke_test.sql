-- SR-6F-C aggregate-only Search CTR boundary smoke test.
-- Run after 04_search_ctr_aggregate_boundary.sql.
BEGIN;

DO $$
DECLARE
  v_user_id bigint;
  v_region_id bigint;
  v_place_id bigint;
  v_post_id bigint;
  v_base timestamptz := date_trunc('second', CURRENT_TIMESTAMP - interval '1 hour');
BEGIN
  INSERT INTO public.app_users(email, password_hash, username, display_name)
  VALUES ('search-ctr-smoke@journey.test', 'hash', 'search_ctr_smoke', 'Search CTR Smoke')
  RETURNING id INTO v_user_id;

  SELECT id INTO v_region_id FROM public.regions WHERE slug = 'kr-seoul';
  IF v_region_id IS NULL THEN
    RAISE EXCEPTION 'Seed region kr-seoul is required';
  END IF;

  INSERT INTO public.places(region_id, name_local, name_ko, category, created_by_user_id)
  VALUES (v_region_id, 'Search CTR Smoke Place', '검색 CTR 테스트 장소', 'test', v_user_id)
  RETURNING id INTO v_place_id;

  INSERT INTO public.posts(author_id, main_region_id, title, content, visibility, status)
  VALUES (v_user_id, v_region_id, 'Search CTR Smoke Post', 'Search CTR aggregate boundary', 'public', 'draft')
  RETURNING id INTO v_post_id;
  INSERT INTO public.post_places(post_id, place_id, sort_order) VALUES (v_post_id, v_place_id, 0);
  UPDATE public.posts SET status = 'published' WHERE id = v_post_id;

  PERFORM set_config('jc.search_ctr_smoke_user_id', v_user_id::text, true);
  PERFORM set_config('jc.search_ctr_smoke_post_id', v_post_id::text, true);
  PERFORM set_config('jc.search_ctr_smoke_base', v_base::text, true);
END;
$$;

SET ROLE jc_recommendation;

INSERT INTO public.recommendation_snapshot(
  snapshot_id, snapshot_kind, schema_version, canonicalization_version,
  content_hash, canonical_payload, payload_json, payload_size_bytes
)
SELECT
  fixture.snapshot_id, fixture.snapshot_kind, fixture.schema_version,
  'recommendation-canonical-json-v1',
  public.recommendation_snapshot_sha256_hex(
    fixture.snapshot_kind, fixture.schema_version, fixture.payload
  ),
  fixture.payload, fixture.payload_json, octet_length(fixture.payload)
FROM (
  VALUES
    ('search-ctr-ranking', 'ranking_input_v1', 'ranking-input-v1',
      convert_to('{"kind":"ranking"}', 'UTF8'), '{"kind":"ranking"}'::jsonb),
    ('search-ctr-diversity', 'diversity_metadata_v1', 'diversity-metadata-v1',
      convert_to('{"kind":"diversity"}', 'UTF8'), '{"kind":"diversity"}'::jsonb),
    ('search-ctr-exploration', 'exploration_metadata_v1', 'exploration-metadata-v1',
      convert_to('{"kind":"exploration"}', 'UTF8'), '{"kind":"exploration"}'::jsonb),
    ('search-ctr-result', 'ranking_result_v1', 'ranking-result-v1',
      convert_to('{"kind":"result"}', 'UTF8'), '{"kind":"result"}'::jsonb)
) AS fixture(snapshot_id, snapshot_kind, schema_version, payload, payload_json);

INSERT INTO public.recommendation_run(
  run_id, request_id, run_mode, run_status, user_id, session_id, context_id, surface,
  reference_time, ranking_snapshot_id, metadata_snapshot_id, exploration_snapshot_id,
  result_snapshot_id, ranking_policy_version, base_integration_policy_version,
  base_ranking_policy_version, score_policy_version, component_policy_versions,
  diversity_policy_version, exploration_policy_version, exploration_seed,
  ranking_status, ranking_empty_reason, requested_limit, effective_limit,
  input_count, scored_candidate_count, final_ranked_candidate_count,
  terminal_candidate_count, result_fingerprint, core_build_id, duration_ms, fallback_reason
) VALUES (
  'search:ctr-smoke-run', 'search:ctr-smoke-request', 'shadow', 'succeeded',
  current_setting('jc.search_ctr_smoke_user_id')::bigint,
  'search-ctr-smoke-session', 'search-ctr-smoke-context', 'search',
  current_setting('jc.search_ctr_smoke_base')::timestamptz,
  'search-ctr-ranking', 'search-ctr-diversity', 'search-ctr-exploration', 'search-ctr-result',
  'search-ranking-policy-v1', 'ranking-integration-v3', 'ranking-v1', 'score-v1',
  '{"interest":"interest-v1","context":"context-v1","freshness":"freshness-v1","popularity":"popularity-v1"}'::jsonb,
  'diversity-v1', 'exploration-v1', 'search-ctr-smoke-seed',
  'ranked', NULL, 20, 20, 1, 1, 1, 0,
  public.recommendation_sha256_hex(convert_to('search-ctr-result', 'UTF8')),
  'java-core-1.0.0', 1, NULL
);

INSERT INTO public.recommendation_run_candidate(
  run_id, absolute_rank, entity_type, entity_key, source_entity_id, origin,
  score, score_is_negative_zero, base_absolute_rank, diversified_absolute_rank,
  score_policy_version, provenance
) VALUES (
  'search:ctr-smoke-run', 1, 'post',
  'post:' || current_setting('jc.search_ctr_smoke_post_id'),
  current_setting('jc.search_ctr_smoke_post_id')::bigint,
  'personalized', 0.5, false, 1, 1, 'score-v1', '{"source":"search-ctr-smoke"}'::jsonb
);

SELECT set_config(
  'jc.search_ctr_smoke_subject_ref',
  (SELECT subject_ref FROM public.resolve_platform_subject_v1(
    current_setting('jc.search_ctr_smoke_user_id')::bigint,
    'subject:search-ctr-smoke', 'search-exposure-write', 'intelligence-search'
  )),
  true
);

DO $$
DECLARE
  v_payload bytea := convert_to('{"schemaVersion":"search-exposure-v1","smoke":"ctr"}', 'UTF8');
  v_base timestamptz := current_setting('jc.search_ctr_smoke_base')::timestamptz;
BEGIN
  INSERT INTO public.search_exposure_event_v1(
    exposure_id, idempotency_key, schema_version, payload_fingerprint,
    canonical_payload, payload_size_bytes, batch_fingerprint,
    search_run_id, result_snapshot_ref, subject_ref, identity_scheme,
    identity_mapping_version, session_id, surface, query_fingerprint,
    ranking_policy_version, page_occurrence_id, result_entity_type,
    result_entity_id, absolute_rank, page_position, visibility_rule_version,
    visible_ratio_basis_points, dwell_milliseconds, exposed_at,
    retention_policy_version, retention_until, producer_build_id
  )
  SELECT
    fixture.exposure_id, fixture.idempotency_key, 'search-exposure-v1',
    encode(public.digest(v_payload, 'sha256'), 'hex'), v_payload, octet_length(v_payload),
    repeat('b', 64), 'search:ctr-smoke-run', repeat('c', 64),
    current_setting('jc.search_ctr_smoke_subject_ref'), 'platform_subject_v1',
    'identity-mapping-v1', 'search-ctr-smoke-session', 'search', repeat('d', 64),
    'search-ranking-policy-v1', fixture.page_occurrence_id, 'post',
    current_setting('jc.search_ctr_smoke_post_id')::bigint, 1, 1,
    'search-item-visible-v1', 5000, 1000, fixture.exposed_at,
    'search-exposure-retention-v1', fixture.exposed_at + interval '180 days',
    'search-frontend-smoke-v1'
  FROM (
    VALUES
      ('search-ctr-exposure-1', 'search-ctr-idem-1', 'search-ctr-page-1', v_base),
      ('search-ctr-exposure-2', 'search-ctr-idem-2', 'search-ctr-page-2', v_base + interval '5 minutes'),
      ('search-ctr-exposure-3', 'search-ctr-idem-3', 'search-ctr-page-3', v_base + interval '10 minutes')
  ) AS fixture(exposure_id, idempotency_key, page_occurrence_id, exposed_at);
END;
$$;

DO $$
DECLARE
  v_payload bytea := convert_to('{"schemaVersion":"search-behavior-event-v1","smoke":"ctr"}', 'UTF8');
  v_base timestamptz := current_setting('jc.search_ctr_smoke_base')::timestamptz;
BEGIN
  INSERT INTO public.recommendation_behavior_event(
    event_id, idempotency_key, schema_version, payload_fingerprint,
    canonical_payload, payload_size_bytes, user_id, session_id, run_id,
    event_type, entity_type, entity_key, source_entity_id, occurred_at, metadata
  )
  SELECT
    fixture.event_id, fixture.idempotency_key, 'search-behavior-event-v1',
    public.recommendation_sha256_hex(v_payload), v_payload, octet_length(v_payload),
    current_setting('jc.search_ctr_smoke_user_id')::bigint,
    'search-ctr-smoke-session', 'search:ctr-smoke-run', 'click', 'post',
    'post:' || current_setting('jc.search_ctr_smoke_post_id'),
    current_setting('jc.search_ctr_smoke_post_id')::bigint,
    fixture.occurred_at,
    jsonb_build_object(
      'surface', 'search', 'source', 'search-result-api',
      'searchRunId', 'search:ctr-smoke-run', 'queryFingerprint', repeat('d', 64),
      'snapshotFingerprint', repeat('c', 64), 'policyVersion', 'search-ranking-policy-v1',
      'absoluteRank', 1
    )
  FROM (
    VALUES
      ('search-ctr-click-1', 'search-ctr-click-idem-1', v_base + interval '6 minutes'),
      ('search-ctr-click-upper-exclusive', 'search-ctr-click-idem-2', v_base + interval '40 minutes')
  ) AS fixture(event_id, idempotency_key, occurred_at);
END;
$$;

RESET ROLE;

DO $$
BEGIN
  IF NOT has_function_privilege(
      'jc_reliability',
      'public.evaluate_search_ctr_v1(timestamp with time zone,timestamp with time zone,character varying)',
      'EXECUTE')
     OR has_table_privilege('jc_reliability', 'public.platform_identity_mapping_v1', 'SELECT')
     OR has_table_privilege('jc_reliability', 'public.search_exposure_event_v1', 'SELECT')
     OR has_table_privilege('jc_reliability', 'public.recommendation_behavior_event', 'SELECT')
     OR has_table_privilege('jc_reliability', 'public.search_ctr_evaluation_access_audit_v1', 'SELECT')
     OR has_function_privilege(
       'jc_reliability',
       'public.purge_expired_search_ctr_audit_v1(timestamp with time zone,character varying)',
       'EXECUTE') THEN
    RAISE EXCEPTION 'Search CTR aggregate privilege contract failed';
  END IF;

  IF pg_get_function_result(
       'public.evaluate_search_ctr_v1(timestamp with time zone,timestamp with time zone,character varying)'::regprocedure
     ) ~* '(user_id|subject_ref|session_id|exposure_id|click_event_id|raw_query)' THEN
    RAISE EXCEPTION 'Search CTR function leaks identity-bearing result columns';
  END IF;
END;
$$;

SET ROLE jc_reliability;
DO $$
DECLARE
  v_result record;
  v_base timestamptz := current_setting('jc.search_ctr_smoke_base')::timestamptz;
BEGIN
  SELECT * INTO v_result
  FROM public.evaluate_search_ctr_v1(v_base, v_base + interval '20 minutes', 'reliability-search-ctr');
  IF v_result.metric_id <> 'search-click-through-rate-v1'
     OR v_result.status <> 'PROVISIONAL'
     OR v_result.eligible_exposure_count <> 3
     OR v_result.attributed_exposure_count <> 1
     OR v_result.ctr_basis_points <> 3333 THEN
    RAISE EXCEPTION 'Search CTR aggregate result mismatch: %', row_to_json(v_result);
  END IF;

  SELECT * INTO v_result
  FROM public.evaluate_search_ctr_v1(v_base - interval '2 hours', v_base - interval '1 hour', 'reliability-search-ctr');
  IF v_result.eligible_exposure_count <> 0
     OR v_result.attributed_exposure_count <> 0
     OR v_result.ctr_basis_points IS NOT NULL THEN
    RAISE EXCEPTION 'Search CTR zero-denominator contract failed';
  END IF;

  BEGIN
    PERFORM * FROM public.evaluate_search_ctr_v1(v_base, v_base + interval '20 minutes', 'wrong-requester');
    RAISE EXCEPTION 'Unapproved Search CTR requester succeeded';
  EXCEPTION WHEN SQLSTATE '42501' THEN
    NULL;
  END;
END;
$$;
RESET ROLE;

SET ROLE jc_security_owner;
SELECT public.invalidate_platform_subject_v1(
  current_setting('jc.search_ctr_smoke_subject_ref'),
  'SEARCH_CTR_SMOKE_INVALIDATION',
  'system-coordination'
);
RESET ROLE;

SET ROLE jc_reliability;
DO $$
DECLARE
  v_base timestamptz := current_setting('jc.search_ctr_smoke_base')::timestamptz;
BEGIN
  BEGIN
    PERFORM * FROM public.evaluate_search_ctr_v1(
      v_base, v_base + interval '20 minutes', 'reliability-search-ctr'
    );
    RAISE EXCEPTION 'Invalidated identity mapping was aggregated';
  EXCEPTION WHEN SQLSTATE '23514' THEN
    NULL;
  END;
END;
$$;
RESET ROLE;

SET ROLE jc_security_owner;
DO $$
DECLARE
  v_deleted bigint;
BEGIN
  SELECT public.purge_expired_search_ctr_audit_v1(CURRENT_TIMESTAMP, 'system-coordination')
    INTO v_deleted;
  IF v_deleted <> 0 THEN
    RAISE EXCEPTION 'Unexpired Search CTR audit evidence was deleted';
  END IF;
END;
$$;
RESET ROLE;

COMMIT;
