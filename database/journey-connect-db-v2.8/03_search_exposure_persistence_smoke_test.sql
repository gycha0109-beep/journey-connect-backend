-- Journey Connect DB v2.7 extension - SR-6C Search exposure persistence smoke
-- Run after 55_search_exposure_persistence.sql.

BEGIN;

DO $$
DECLARE
  v_user_id bigint;
  v_country_id bigint;
  v_city_id bigint;
  v_place_id bigint;
  v_post_id bigint;
BEGIN
  INSERT INTO public.app_users(email, password_hash, username, display_name)
  VALUES ('search-exposure-smoke@journey.test', 'hash', 'search_exposure_smoke', 'Search Exposure Smoke')
  RETURNING id INTO v_user_id;

  INSERT INTO public.regions
    (name_local, name_ko, name_en, slug, region_type, country_code, timezone)
  VALUES
    ('Search Exposure Country', '검색 노출 국가', 'Search Exposure Country',
     'search-exposure-country', 'country', 'SX', 'UTC')
  RETURNING id INTO v_country_id;

  INSERT INTO public.regions
    (parent_id, name_local, name_ko, name_en, slug, region_type, country_code, timezone)
  VALUES
    (v_country_id, 'Search Exposure City', '검색 노출 도시', 'Search Exposure City',
     'search-exposure-country-city', 'city', 'SX', 'UTC')
  RETURNING id INTO v_city_id;

  INSERT INTO public.places(region_id, name_local, name_ko, category, created_by_user_id)
  VALUES (v_city_id, 'Search Exposure Place', '검색 노출 장소', 'test', v_user_id)
  RETURNING id INTO v_place_id;

  INSERT INTO public.posts(author_id, main_region_id, title, content, visibility, status)
  VALUES (v_user_id, v_city_id, '검색 노출 게시글', '검색 노출 저장 smoke 대상입니다.', 'public', 'draft')
  RETURNING id INTO v_post_id;

  INSERT INTO public.post_places(post_id, place_id, sort_order)
  VALUES (v_post_id, v_place_id, 0);
  UPDATE public.posts SET status = 'published' WHERE id = v_post_id;

  PERFORM set_config('jc.search_exposure_smoke_user_id', v_user_id::text, true);
  PERFORM set_config('jc.search_exposure_smoke_post_id', v_post_id::text, true);
END;
$$;

SET ROLE jc_recommendation;

SELECT set_config(
  'jc.search_exposure_smoke_subject_ref',
  (
    SELECT subject_ref
    FROM public.resolve_platform_subject_v1(
      current_setting('jc.search_exposure_smoke_user_id')::bigint,
      'subject:search-exposure-smoke',
      'search-exposure-write',
      'intelligence-search'
    )
  ),
  true
);

DO $$
DECLARE
  v_payload bytea := convert_to('{"schemaVersion":"search-exposure-v1","smoke":true}', 'UTF8');
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
    'search-exposure-smoke', 'search-exposure-smoke-idempotency',
    'search-exposure-v1', encode(public.digest(v_payload, 'sha256'), 'hex'),
    v_payload, octet_length(v_payload), repeat('b', 64),
    'search:smoke-run', repeat('c', 64),
    current_setting('jc.search_exposure_smoke_subject_ref'),
    'platform_subject_v1', 'identity-mapping-v1', 'search-smoke-session',
    'search', repeat('d', 64), 'search-ranking-policy-v1',
    'search-smoke-page', 'post',
    current_setting('jc.search_exposure_smoke_post_id')::bigint,
    1, 1, 'search-item-visible-v1', 5000, 1000,
    CURRENT_TIMESTAMP - interval '1 second', 'search-exposure-retention-v1',
    CURRENT_TIMESTAMP - interval '1 second' + interval '180 days', 'search-frontend-smoke-v1'
  );
END;
$$;

DO $$
BEGIN
  IF NOT has_function_privilege(
          'jc_recommendation',
          'public.resolve_platform_subject_v1(bigint,varchar,varchar,varchar)',
          'EXECUTE')
     OR has_table_privilege(
          'jc_recommendation', 'public.platform_identity_mapping_v1', 'SELECT')
     OR NOT has_table_privilege(
          'jc_recommendation', 'public.search_exposure_event_v1', 'INSERT')
     OR has_table_privilege(
          'jc_recommendation', 'public.search_exposure_event_v1', 'UPDATE')
     OR has_function_privilege(
          'jc_recommendation',
          'public.purge_expired_search_exposure_v1(timestamptz,varchar)',
          'EXECUTE') THEN
    RAISE EXCEPTION 'Search exposure privilege contract failed';
  END IF;
END;
$$;

SAVEPOINT search_exposure_mutation_probe;
DO $$
BEGIN
  BEGIN
    UPDATE public.search_exposure_event_v1
    SET dwell_milliseconds = 2000
    WHERE exposure_id = 'search-exposure-smoke';
    RAISE EXCEPTION 'search exposure mutation succeeded';
  EXCEPTION WHEN SQLSTATE '55000' OR SQLSTATE '42501' THEN
    NULL;
  END;
END;
$$;
ROLLBACK TO SAVEPOINT search_exposure_mutation_probe;

SAVEPOINT search_exposure_duplicate_probe;
DO $$
DECLARE
  v_payload bytea := convert_to('{"schemaVersion":"search-exposure-v1","smoke":false}', 'UTF8');
BEGIN
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
      'search-exposure-smoke-conflict', 'search-exposure-smoke-idempotency',
      'search-exposure-v1', encode(public.digest(v_payload, 'sha256'), 'hex'),
      v_payload, octet_length(v_payload), repeat('e', 64),
      'search:smoke-run', repeat('c', 64),
      current_setting('jc.search_exposure_smoke_subject_ref'),
      'platform_subject_v1', 'identity-mapping-v1', 'search-smoke-session',
      'search', repeat('d', 64), 'search-ranking-policy-v1',
      'search-smoke-page-conflict', 'post',
      current_setting('jc.search_exposure_smoke_post_id')::bigint,
      1, 1, 'search-item-visible-v1', 6000, 2000,
      CURRENT_TIMESTAMP, 'search-exposure-retention-v1',
      CURRENT_TIMESTAMP + interval '180 days', 'search-frontend-smoke-v1'
    );
    RAISE EXCEPTION 'duplicate idempotency key succeeded';
  EXCEPTION WHEN unique_violation THEN
    NULL;
  END;
END;
$$;
ROLLBACK TO SAVEPOINT search_exposure_duplicate_probe;

RESET ROLE;
SET ROLE jc_security_owner;
SELECT public.invalidate_platform_subject_v1(
  current_setting('jc.search_exposure_smoke_subject_ref'),
  'SMOKE_INVALIDATION',
  'system-coordination'
);
RESET ROLE;

SET ROLE jc_recommendation;
DO $$
BEGIN
  BEGIN
    PERFORM *
    FROM public.resolve_platform_subject_v1(
      current_setting('jc.search_exposure_smoke_user_id')::bigint,
      'subject:replacement-must-not-be-created',
      'search-exposure-write',
      'intelligence-search'
    );
    RAISE EXCEPTION 'invalidated identity mapping resolved';
  EXCEPTION WHEN SQLSTATE '23514' THEN
    NULL;
  END;
END;
$$;
RESET ROLE;
SET ROLE jc_security_owner;
DO $$
DECLARE
  v_search_deleted bigint;
  v_audit_deleted bigint;
BEGIN
  SELECT public.purge_expired_search_exposure_v1(CURRENT_TIMESTAMP, 'system-coordination')
    INTO v_search_deleted;
  SELECT public.purge_expired_identity_mapping_audit_v1(CURRENT_TIMESTAMP, 'system-coordination')
    INTO v_audit_deleted;
  IF v_search_deleted <> 0 OR v_audit_deleted <> 0 THEN
    RAISE EXCEPTION 'unexpired retention evidence was deleted';
  END IF;
END;
$$;
RESET ROLE;

COMMIT;
