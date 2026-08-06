-- SR-6F-D Search CTR projection writer smoke test.
-- Run after 06_search_ctr_projection_writer.sql. All fixtures are rolled back.
BEGIN;

DO $$
DECLARE
  v_user_id bigint;
  v_region_id bigint;
  v_place_id bigint;
  v_post_id bigint;
  v_base timestamptz := date_trunc('second', CURRENT_TIMESTAMP - interval '2 hours');
BEGIN
  INSERT INTO public.app_users(email, password_hash, username, display_name)
  VALUES ('search-ctr-writer@journey.test', 'hash', 'search_ctr_writer', 'Search CTR Writer')
  RETURNING id INTO v_user_id;

  SELECT id INTO v_region_id FROM public.regions WHERE slug = 'kr-seoul';
  IF v_region_id IS NULL THEN
    RAISE EXCEPTION 'Seed region kr-seoul is required';
  END IF;

  INSERT INTO public.places(region_id, name_local, name_ko, category, created_by_user_id)
  VALUES (v_region_id, 'Search CTR Writer Place', '검색 CTR writer 장소', 'test', v_user_id)
  RETURNING id INTO v_place_id;

  INSERT INTO public.posts(author_id, main_region_id, title, content, visibility, status)
  VALUES (v_user_id, v_region_id, 'Search CTR Writer Post', 'Search CTR writer smoke', 'public', 'draft')
  RETURNING id INTO v_post_id;
  INSERT INTO public.post_places(post_id, place_id, sort_order) VALUES (v_post_id, v_place_id, 0);
  UPDATE public.posts SET status = 'published' WHERE id = v_post_id;

  PERFORM set_config('jc.search_ctr_writer_user_id', v_user_id::text, true);
  PERFORM set_config('jc.search_ctr_writer_post_id', v_post_id::text, true);
  PERFORM set_config('jc.search_ctr_writer_base', v_base::text, true);
END;
$$;

SET ROLE jc_recommendation;

INSERT INTO public.recommendation_snapshot(
  snapshot_id, snapshot_kind, schema_version, canonicalization_version,
  content_hash, canonical_payload, payload_json, payload_size_bytes
)
SELECT
  fixture.snapshot_id, fixture.snapshot_kind, fixture.schema_version,
  'search-ctr-writer-canonical-json-v1',
  public.recommendation_snapshot_sha256_hex(
    fixture.snapshot_kind, fixture.schema_version, fixture.payload
  ),
  fixture.payload, fixture.payload_json, octet_length(fixture.payload)
FROM (
  VALUES
    ('search-ctr-writer-ranking', 'ranking_input_v1', 'ranking-input-v1',
      convert_to('{"kind":"ranking"}', 'UTF8'), '{"kind":"ranking"}'::jsonb),
    ('search-ctr-writer-diversity', 'diversity_metadata_v1', 'diversity-metadata-v1',
      convert_to('{"kind":"diversity"}', 'UTF8'), '{"kind":"diversity"}'::jsonb),
    ('search-ctr-writer-exploration', 'exploration_metadata_v1', 'exploration-metadata-v1',
      convert_to('{"kind":"exploration"}', 'UTF8'), '{"kind":"exploration"}'::jsonb),
    ('search-ctr-writer-result', 'ranking_result_v1', 'ranking-result-v1',
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
  'search:ctr-writer-run', 'search:ctr-writer-request', 'shadow', 'succeeded',
  current_setting('jc.search_ctr_writer_user_id')::bigint,
  'search-ctr-writer-session', 'search-ctr-writer-context', 'search',
  current_setting('jc.search_ctr_writer_base')::timestamptz,
  'search-ctr-writer-ranking', 'search-ctr-writer-diversity',
  'search-ctr-writer-exploration', 'search-ctr-writer-result',
  'search-ranking-policy-v1', 'ranking-integration-v3', 'ranking-v1', 'score-v1',
  '{"interest":"interest-v1","context":"context-v1","freshness":"freshness-v1","popularity":"popularity-v1"}'::jsonb,
  'diversity-v1', 'exploration-v1', 'search-ctr-writer-seed',
  'ranked', NULL, 20, 20, 1, 1, 1, 0,
  public.recommendation_sha256_hex(convert_to('search-ctr-writer-result', 'UTF8')),
  'java-core-1.0.0', 1, NULL
);

INSERT INTO public.recommendation_run_candidate(
  run_id, absolute_rank, entity_type, entity_key, source_entity_id, origin,
  score, score_is_negative_zero, base_absolute_rank, diversified_absolute_rank,
  score_policy_version, provenance
) VALUES (
  'search:ctr-writer-run', 1, 'post',
  'post:' || current_setting('jc.search_ctr_writer_post_id'),
  current_setting('jc.search_ctr_writer_post_id')::bigint,
  'personalized', 0.5, false, 1, 1, 'score-v1', '{"source":"search-ctr-writer"}'::jsonb
);

SELECT set_config(
  'jc.search_ctr_writer_subject_ref',
  (SELECT subject_ref FROM public.resolve_platform_subject_v1(
    current_setting('jc.search_ctr_writer_user_id')::bigint,
    'subject:search-ctr-writer', 'search-exposure-write', 'intelligence-search'
  )),
  true
);

DO $$
DECLARE
  v_payload bytea := convert_to('{"schemaVersion":"search-exposure-v1","writer":true}', 'UTF8');
  v_base timestamptz := current_setting('jc.search_ctr_writer_base')::timestamptz;
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
  ) VALUES (
    'search-ctr-writer-exposure-1', 'search-ctr-writer-exposure-idem-1',
    'search-exposure-v1', encode(public.digest(v_payload, 'sha256'), 'hex'),
    v_payload, octet_length(v_payload), repeat('b', 64),
    'search:ctr-writer-run', repeat('c', 64),
    current_setting('jc.search_ctr_writer_subject_ref'),
    'platform_subject_v1', 'identity-mapping-v1', 'search-ctr-writer-session',
    'search', repeat('d', 64), 'search-ranking-policy-v1',
    'search-ctr-writer-page-1', 'post',
    current_setting('jc.search_ctr_writer_post_id')::bigint,
    1, 1, 'search-item-visible-v1', 5000, 1000, v_base,
    'search-exposure-retention-v1', v_base + interval '180 days',
    'search-frontend-smoke-v1'
  );
END;
$$;

DO $$
DECLARE
  v_payload bytea := convert_to('{"schemaVersion":"search-behavior-event-v1","writer":true}', 'UTF8');
  v_base timestamptz := current_setting('jc.search_ctr_writer_base')::timestamptz;
BEGIN
  INSERT INTO public.recommendation_behavior_event(
    event_id, idempotency_key, schema_version, payload_fingerprint,
    canonical_payload, payload_size_bytes, user_id, session_id, run_id,
    event_type, entity_type, entity_key, source_entity_id, occurred_at, metadata
  ) VALUES (
    'search-ctr-writer-click-1', 'search-ctr-writer-click-idem-1',
    'search-behavior-event-v1', public.recommendation_sha256_hex(v_payload),
    v_payload, octet_length(v_payload),
    current_setting('jc.search_ctr_writer_user_id')::bigint,
    'search-ctr-writer-session', 'search:ctr-writer-run', 'click', 'post',
    'post:' || current_setting('jc.search_ctr_writer_post_id'),
    current_setting('jc.search_ctr_writer_post_id')::bigint,
    v_base + interval '1 minute',
    jsonb_build_object(
      'surface', 'search', 'source', 'search-result-api',
      'searchRunId', 'search:ctr-writer-run', 'queryFingerprint', repeat('d', 64),
      'snapshotFingerprint', repeat('c', 64), 'policyVersion', 'search-ranking-policy-v1',
      'absoluteRank', 1
    )
  );
END;
$$;
RESET ROLE;

DO $$
BEGIN
  IF NOT has_function_privilege(
       'jc_reliability',
       'public.write_search_ctr_projection_v1(timestamp with time zone,timestamp with time zone,character varying,character varying,character varying,character varying)',
       'EXECUTE')
     OR has_table_privilege('jc_reliability', 'public.search_ctr_projection_snapshot_v1', 'SELECT')
     OR has_table_privilege('jc_reliability', 'public.search_ctr_projection_snapshot_v1', 'INSERT')
     OR has_table_privilege('jc_recommendation', 'public.search_ctr_projection_snapshot_v1', 'SELECT') THEN
    RAISE EXCEPTION 'Search CTR projection writer privilege contract failed';
  END IF;

  IF pg_get_function_result(
       'public.write_search_ctr_projection_v1(timestamp with time zone,timestamp with time zone,character varying,character varying,character varying,character varying)'::regprocedure
     ) ~* '(user_id|subject_ref|session_id|exposure_id|click_event_id|raw_query)' THEN
    RAISE EXCEPTION 'Search CTR writer leaks identity-bearing result columns';
  END IF;
END;
$$;

SET ROLE jc_reliability;
DO $$
DECLARE
  v_result record;
  v_root_id varchar;
  v_base timestamptz := current_setting('jc.search_ctr_writer_base')::timestamptz;
BEGIN
  SELECT * INTO v_result FROM public.write_search_ctr_projection_v1(
    v_base, v_base + interval '20 minutes', 'reliability-search-ctr', NULL,
    'search-ctr-writer-idem-1', 'search-ctr-writer-smoke-v1'
  );
  IF v_result.write_status <> 'STORED'
     OR v_result.eligible_exposure_count <> 1
     OR v_result.attributed_exposure_count <> 1
     OR v_result.ctr_basis_points <> 10000
     OR v_result.predecessor_projection_id IS NOT NULL THEN
    RAISE EXCEPTION 'Search CTR root projection mismatch: %', row_to_json(v_result);
  END IF;
  v_root_id := v_result.projection_id;
  PERFORM set_config('jc.search_ctr_writer_root_id', v_root_id, true);

  SELECT * INTO v_result FROM public.write_search_ctr_projection_v1(
    v_base, v_base + interval '20 minutes', 'reliability-search-ctr', NULL,
    'search-ctr-writer-idem-2', 'search-ctr-writer-smoke-v1'
  );
  IF v_result.write_status <> 'DUPLICATE' OR v_result.projection_id <> v_root_id THEN
    RAISE EXCEPTION 'Search CTR semantic duplicate contract failed';
  END IF;
END;
$$;
RESET ROLE;

SET ROLE jc_recommendation;
DO $$
DECLARE
  v_payload bytea := convert_to('{"schemaVersion":"search-exposure-v1","writer":true,"second":true}', 'UTF8');
  v_base timestamptz := current_setting('jc.search_ctr_writer_base')::timestamptz;
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
  ) VALUES (
    'search-ctr-writer-exposure-2', 'search-ctr-writer-exposure-idem-2',
    'search-exposure-v1', encode(public.digest(v_payload, 'sha256'), 'hex'),
    v_payload, octet_length(v_payload), repeat('e', 64),
    'search:ctr-writer-run', repeat('c', 64),
    current_setting('jc.search_ctr_writer_subject_ref'),
    'platform_subject_v1', 'identity-mapping-v1', 'search-ctr-writer-session',
    'search', repeat('d', 64), 'search-ranking-policy-v1',
    'search-ctr-writer-page-2', 'post',
    current_setting('jc.search_ctr_writer_post_id')::bigint,
    1, 2, 'search-item-visible-v1', 5000, 1000, v_base + interval '5 minutes',
    'search-exposure-retention-v1', v_base + interval '180 days' + interval '5 minutes',
    'search-frontend-smoke-v1'
  );
END;
$$;
RESET ROLE;

SET ROLE jc_reliability;
DO $$
DECLARE
  v_result record;
  v_base timestamptz := current_setting('jc.search_ctr_writer_base')::timestamptz;
  v_root_id varchar := current_setting('jc.search_ctr_writer_root_id');
BEGIN
  SELECT * INTO v_result FROM public.write_search_ctr_projection_v1(
    v_base, v_base + interval '20 minutes', 'reliability-search-ctr', v_root_id,
    'search-ctr-writer-idem-1', 'search-ctr-writer-smoke-v1'
  );
  IF v_result.write_status <> 'IDEMPOTENCY_CONFLICT' THEN
    RAISE EXCEPTION 'Search CTR idempotency conflict contract failed';
  END IF;

  SELECT * INTO v_result FROM public.write_search_ctr_projection_v1(
    v_base, v_base + interval '20 minutes', 'reliability-search-ctr', NULL,
    'search-ctr-writer-idem-3', 'search-ctr-writer-smoke-v1'
  );
  IF v_result.write_status <> 'PREDECESSOR_CONFLICT'
     OR v_result.projection_id <> v_root_id THEN
    RAISE EXCEPTION 'Search CTR predecessor conflict contract failed';
  END IF;

  SELECT * INTO v_result FROM public.write_search_ctr_projection_v1(
    v_base, v_base + interval '20 minutes', 'reliability-search-ctr', v_root_id,
    'search-ctr-writer-idem-3', 'search-ctr-writer-smoke-v1'
  );
  IF v_result.write_status <> 'STORED'
     OR v_result.predecessor_projection_id <> v_root_id
     OR v_result.eligible_exposure_count <> 2
     OR v_result.attributed_exposure_count <> 1
     OR v_result.ctr_basis_points <> 5000 THEN
    RAISE EXCEPTION 'Search CTR replacement projection mismatch: %', row_to_json(v_result);
  END IF;
END;
$$;
RESET ROLE;

DO $$
DECLARE
  v_payload text;
BEGIN
  IF (SELECT count(*) FROM public.search_ctr_projection_snapshot_v1) <> 2 THEN
    RAISE EXCEPTION 'Search CTR writer must persist exactly root and replacement snapshots';
  END IF;

  SELECT convert_from(canonical_payload, 'UTF8') INTO v_payload
  FROM public.search_ctr_projection_snapshot_v1
  ORDER BY computed_at DESC, projection_id DESC
  LIMIT 1;

  IF v_payload ~* '(userId|subjectRef|sessionId|exposureId|clickEventId|rawQuery|computedAt)'
     OR v_payload NOT LIKE '%"eligibleExposureCount":2%'
     OR v_payload NOT LIKE '%"attributedExposureCount":1%'
     OR v_payload NOT LIKE '%"ctrBasisPoints":5000%' THEN
    RAISE EXCEPTION 'Search CTR canonical projection privacy/content contract failed: %', v_payload;
  END IF;

  BEGIN
    UPDATE public.search_ctr_projection_snapshot_v1
    SET producer_build_id = 'mutated'
    WHERE projection_id = current_setting('jc.search_ctr_writer_root_id');
    RAISE EXCEPTION 'Search CTR projection mutation succeeded';
  EXCEPTION WHEN SQLSTATE '55000' THEN
    NULL;
  END;
END;
$$;

ROLLBACK;
