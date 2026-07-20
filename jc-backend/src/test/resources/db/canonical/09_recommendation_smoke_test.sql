-- Journey Connect DB v1.9 - P0 recommendation storage and authorization smoke test
-- Run after 01~08 as PostgreSQL superuser. All test data is rolled back.

BEGIN;

DO $$
DECLARE
  v_user_id bigint;
  v_country_id bigint;
  v_city_id bigint;
  v_place_id bigint;
  v_ranked_post_id bigint;
  v_terminal_post_id bigint;
BEGIN
  INSERT INTO public.app_users (email, password_hash, username, display_name)
  VALUES (
    'recommendation-smoke@example.com',
    '$2a$10$recommendationSmokeHashPlaceholder',
    'recommendation_smoke',
    '추천 저장소 테스트'
  )
  RETURNING id INTO v_user_id;

  INSERT INTO public.regions
    (name_local, name_ko, name_en, slug, region_type, country_code, timezone)
  VALUES
    ('Recommendation Smoke Country', '추천 테스트 국가', 'Recommendation Smoke Country',
     'recommendation-smoke-country', 'country', 'RX', 'UTC')
  RETURNING id INTO v_country_id;

  INSERT INTO public.regions
    (parent_id, name_local, name_ko, name_en, slug, region_type, country_code, timezone)
  VALUES
    (v_country_id, 'Recommendation Smoke City', '추천 테스트 도시', 'Recommendation Smoke City',
     'recommendation-smoke-country-city', 'city', 'RX', 'UTC')
  RETURNING id INTO v_city_id;

  INSERT INTO public.places
    (region_id, name_local, name_ko, category, created_by_user_id)
  VALUES
    (v_city_id, 'Recommendation Smoke Place', '추천 테스트 장소', 'test', v_user_id)
  RETURNING id INTO v_place_id;

  INSERT INTO public.posts
    (author_id, main_region_id, title, content, visibility, status)
  VALUES
    (v_user_id, v_city_id, '추천 후보 게시글', '추천 최종 순위에 포함되는 게시글입니다.', 'public', 'draft')
  RETURNING id INTO v_ranked_post_id;

  INSERT INTO public.post_places (post_id, place_id, sort_order)
  VALUES (v_ranked_post_id, v_place_id, 0);

  UPDATE public.posts SET status = 'published' WHERE id = v_ranked_post_id;

  INSERT INTO public.posts
    (author_id, main_region_id, title, content, visibility, status)
  VALUES
    (v_user_id, v_city_id, '추천 terminal 게시글', '추천 terminal 감사 대상 게시글입니다.', 'public', 'draft')
  RETURNING id INTO v_terminal_post_id;

  INSERT INTO public.post_places (post_id, place_id, sort_order)
  VALUES (v_terminal_post_id, v_place_id, 0);

  UPDATE public.posts SET status = 'published' WHERE id = v_terminal_post_id;

  SET CONSTRAINTS ALL IMMEDIATE;
  SET CONSTRAINTS ALL DEFERRED;

  PERFORM set_config('jc.recommendation_smoke_user_id', v_user_id::text, true);
  PERFORM set_config('jc.recommendation_smoke_ranked_post_id', v_ranked_post_id::text, true);
  PERFORM set_config('jc.recommendation_smoke_terminal_post_id', v_terminal_post_id::text, true);
END;
$$;

SET ROLE jc_recommendation;

INSERT INTO public.recommendation_snapshot (
  snapshot_id, snapshot_kind, schema_version, canonicalization_version,
  content_hash, canonical_payload, payload_json, payload_size_bytes
)
SELECT
  'smoke-ranking-snapshot',
  'ranking_input_v1',
  'ranking-input-v1',
  'recommendation-canonical-json-v1',
  public.recommendation_snapshot_sha256_hex(
    'ranking_input_v1', 'ranking-input-v1', payload
  ),
  payload,
  '{"kind":"ranking"}'::jsonb,
  octet_length(payload)
FROM (SELECT convert_to('{"kind":"ranking"}', 'UTF8') AS payload) p;

INSERT INTO public.recommendation_snapshot (
  snapshot_id, snapshot_kind, schema_version, canonicalization_version,
  content_hash, canonical_payload, payload_json, payload_size_bytes
)
SELECT
  'smoke-diversity-snapshot',
  'diversity_metadata_v1',
  'diversity-metadata-v1',
  'recommendation-canonical-json-v1',
  public.recommendation_snapshot_sha256_hex(
    'diversity_metadata_v1', 'diversity-metadata-v1', payload
  ),
  payload,
  '{"kind":"diversity"}'::jsonb,
  octet_length(payload)
FROM (SELECT convert_to('{"kind":"diversity"}', 'UTF8') AS payload) p;

INSERT INTO public.recommendation_snapshot (
  snapshot_id, snapshot_kind, schema_version, canonicalization_version,
  content_hash, canonical_payload, payload_json, payload_size_bytes
)
SELECT
  'smoke-exploration-snapshot',
  'exploration_metadata_v1',
  'exploration-metadata-v1',
  'recommendation-canonical-json-v1',
  public.recommendation_snapshot_sha256_hex(
    'exploration_metadata_v1', 'exploration-metadata-v1', payload
  ),
  payload,
  '{"kind":"exploration"}'::jsonb,
  octet_length(payload)
FROM (SELECT convert_to('{"kind":"exploration"}', 'UTF8') AS payload) p;

INSERT INTO public.recommendation_snapshot (
  snapshot_id, snapshot_kind, schema_version, canonicalization_version,
  content_hash, canonical_payload, payload_json, payload_size_bytes
)
SELECT
  'smoke-result-snapshot',
  'ranking_result_v1',
  'ranking-result-v1',
  'recommendation-canonical-json-v1',
  public.recommendation_snapshot_sha256_hex(
    'ranking_result_v1', 'ranking-result-v1', payload
  ),
  payload,
  '{"kind":"result"}'::jsonb,
  octet_length(payload)
FROM (SELECT convert_to('{"kind":"result"}', 'UTF8') AS payload) p;

INSERT INTO public.recommendation_run (
  run_id, request_id, run_mode, run_status, user_id, session_id, context_id, surface,
  reference_time, ranking_snapshot_id, metadata_snapshot_id, exploration_snapshot_id,
  result_snapshot_id,
  ranking_policy_version, base_integration_policy_version, base_ranking_policy_version,
  score_policy_version, component_policy_versions, diversity_policy_version,
  exploration_policy_version, exploration_seed, ranking_status, ranking_empty_reason,
  requested_limit, effective_limit, input_count, scored_candidate_count,
  final_ranked_candidate_count, terminal_candidate_count, result_fingerprint,
  core_build_id, duration_ms, fallback_reason
)
VALUES (
  'smoke-run',
  'smoke-request',
  'shadow',
  'succeeded',
  current_setting('jc.recommendation_smoke_user_id')::bigint,
  'smoke-session',
  'smoke-context',
  'home',
  CURRENT_TIMESTAMP,
  'smoke-ranking-snapshot',
  'smoke-diversity-snapshot',
  'smoke-exploration-snapshot',
  'smoke-result-snapshot',
  'ranking-v3',
  'ranking-integration-v3',
  'ranking-v1',
  'score-v1',
  '{"interest":"interest-v1","context":"context-v1","freshness":"freshness-v1","popularity":"popularity-v1"}'::jsonb,
  'diversity-v1',
  'exploration-v1',
  'smoke-seed',
  'ranked',
  NULL,
  20,
  20,
  2,
  1,
  1,
  1,
  public.recommendation_sha256_hex(convert_to('smoke-result', 'UTF8')),
  'java-core-1.0.0',
  9,
  NULL
);

INSERT INTO public.recommendation_run_candidate (
  run_id, absolute_rank, entity_type, entity_key, source_entity_id, origin,
  score, score_is_negative_zero, base_absolute_rank, diversified_absolute_rank,
  score_policy_version, provenance
)
VALUES (
  'smoke-run',
  1,
  'post',
  'post:' || current_setting('jc.recommendation_smoke_ranked_post_id'),
  current_setting('jc.recommendation_smoke_ranked_post_id')::bigint,
  'personalized',
  '-0.0'::double precision,
  true,
  1,
  1,
  'score-v1',
  '{"selectionReason":"strict"}'::jsonb
);

INSERT INTO public.recommendation_run_terminal_candidate (
  run_id, entity_type, entity_key, source_entity_id, score_status,
  not_applicable_reason, hard_exclusion_reason, score_policy_version, audit_payload
)
VALUES (
  'smoke-run',
  'post',
  'post:' || current_setting('jc.recommendation_smoke_terminal_post_id'),
  current_setting('jc.recommendation_smoke_terminal_post_id')::bigint,
  'hard_excluded',
  NULL,
  'context_hard_exclusion',
  'score-v1',
  '{"reason":"smoke"}'::jsonb
);

INSERT INTO public.recommendation_exposure_event (
  event_id, idempotency_key, schema_version, payload_fingerprint,
  canonical_payload, payload_size_bytes, run_id, user_id, session_id, context_id,
  surface, served_at, replay_key, page_fingerprint, cursor_version,
  page_start_rank, page_end_rank, page_candidate_count, has_next_page
)
SELECT
  'smoke-exposure-event',
  'smoke-exposure-idempotency',
  'recommendation-exposure-event-v1',
  public.recommendation_sha256_hex(payload),
  payload,
  octet_length(payload),
  'smoke-run',
  current_setting('jc.recommendation_smoke_user_id')::bigint,
  'smoke-session',
  'smoke-context',
  'home',
  CURRENT_TIMESTAMP,
  'smoke-replay-key',
  public.recommendation_sha256_hex(convert_to('smoke-page', 'UTF8')),
  'ranking-cursor-v3',
  1,
  1,
  1,
  false
FROM (SELECT convert_to('{"event":"exposure"}', 'UTF8') AS payload) p;

INSERT INTO public.recommendation_exposure_candidate (
  exposure_event_id, absolute_rank, page_position, entity_type, entity_key,
  source_entity_id, origin, score, score_is_negative_zero, provenance
)
VALUES (
  'smoke-exposure-event',
  1,
  1,
  'post',
  'post:' || current_setting('jc.recommendation_smoke_ranked_post_id'),
  current_setting('jc.recommendation_smoke_ranked_post_id')::bigint,
  'personalized',
  '-0.0'::double precision,
  true,
  '{"baseAbsoluteRank":1,"diversifiedAbsoluteRank":1}'::jsonb
);

INSERT INTO public.recommendation_behavior_event (
  event_id, idempotency_key, schema_version, payload_fingerprint,
  canonical_payload, payload_size_bytes, user_id, session_id, run_id,
  event_type, entity_type, entity_key, source_entity_id,
  occurred_at, metadata
)
SELECT
  'smoke-behavior-event',
  'smoke-behavior-idempotency',
  'recommendation-behavior-event-v1',
  public.recommendation_sha256_hex(payload),
  payload,
  octet_length(payload),
  current_setting('jc.recommendation_smoke_user_id')::bigint,
  'smoke-session',
  'smoke-run',
  'click',
  'post',
  'post:' || current_setting('jc.recommendation_smoke_ranked_post_id'),
  current_setting('jc.recommendation_smoke_ranked_post_id')::bigint,
  CURRENT_TIMESTAMP,
  '{"surface":"home","position":1}'::jsonb
FROM (SELECT convert_to('{"event":"click"}', 'UTF8') AS payload) p;

SET CONSTRAINTS ALL IMMEDIATE;
SET CONSTRAINTS ALL DEFERRED;

DO $$
BEGIN
  BEGIN
    INSERT INTO public.recommendation_snapshot (
      snapshot_id, snapshot_kind, schema_version, canonicalization_version,
      content_hash, canonical_payload, payload_size_bytes
    )
    VALUES (
      'smoke-bad-hash',
      'ranking_input_v1',
      'ranking-input-v1',
      'recommendation-canonical-json-v1',
      repeat('0', 64),
      convert_to('{"bad":true}', 'UTF8'),
      octet_length(convert_to('{"bad":true}', 'UTF8'))
    );
    RAISE EXCEPTION 'Smoke test failed: invalid snapshot hash was accepted.';
  EXCEPTION
    WHEN check_violation THEN NULL;
  END;

  BEGIN
    INSERT INTO public.recommendation_snapshot (
      snapshot_id, snapshot_kind, schema_version, canonicalization_version,
      content_hash, canonical_payload, payload_size_bytes
    )
    SELECT
      'smoke-bad-domain-hash',
      'ranking_input_v1',
      'ranking-input-v1',
      'recommendation-canonical-json-v1',
      public.recommendation_sha256_hex(payload),
      payload,
      octet_length(payload)
    FROM (SELECT convert_to('{"domain":"missing"}', 'UTF8') AS payload) p;
    RAISE EXCEPTION 'Smoke test failed: non-domain-separated snapshot hash was accepted.';
  EXCEPTION
    WHEN check_violation THEN NULL;
  END;

  BEGIN
    INSERT INTO public.recommendation_run_terminal_candidate (
      run_id, entity_type, entity_key, source_entity_id, score_status,
      not_applicable_reason, hard_exclusion_reason, score_policy_version
    )
    VALUES (
      'smoke-run', 'post', 'post:9223372036854775807', 9223372036854775807,
      'hard_excluded', NULL, 'context_hard_exclusion', 'score-v1'
    );
    RAISE EXCEPTION 'Smoke test failed: inaccessible terminal candidate source was accepted.';
  EXCEPTION
    WHEN check_violation THEN NULL;
  END;

  BEGIN
    INSERT INTO public.recommendation_exposure_event (
      event_id, idempotency_key, schema_version, payload_fingerprint,
      canonical_payload, payload_size_bytes, run_id, user_id, session_id, context_id,
      surface, served_at, replay_key, page_fingerprint, cursor_version,
      page_start_rank, page_end_rank, page_candidate_count, has_next_page
    )
    SELECT
      'smoke-exposure-surface-mismatch',
      'smoke-exposure-surface-mismatch',
      'recommendation-exposure-event-v1',
      public.recommendation_sha256_hex(payload), payload, octet_length(payload),
      'smoke-run', current_setting('jc.recommendation_smoke_user_id')::bigint,
      'smoke-session', 'smoke-context', 'search', CURRENT_TIMESTAMP,
      'smoke-replay-key-mismatch',
      public.recommendation_sha256_hex(convert_to('smoke-page-mismatch', 'UTF8')),
      'ranking-cursor-v3', NULL, NULL, 0, false
    FROM (SELECT convert_to('{"event":"surface-mismatch"}', 'UTF8') AS payload) p;
    RAISE EXCEPTION 'Smoke test failed: exposure surface mismatch was accepted.';
  EXCEPTION
    WHEN check_violation THEN NULL;
  END;

  BEGIN
    INSERT INTO public.recommendation_exposure_candidate (
      exposure_event_id, absolute_rank, page_position, entity_type, entity_key,
      source_entity_id, origin, score, score_is_negative_zero
    )
    VALUES (
      'smoke-exposure-event',
      2,
      2,
      'post',
      'post:' || current_setting('jc.recommendation_smoke_terminal_post_id'),
      current_setting('jc.recommendation_smoke_terminal_post_id')::bigint,
      'personalized',
      0.5,
      false
    );
    RAISE EXCEPTION 'Smoke test failed: exposure candidate absent from run ranking was accepted.';
  EXCEPTION
    WHEN check_violation THEN NULL;
  END;

  BEGIN
    INSERT INTO public.recommendation_behavior_event (
      event_id, idempotency_key, schema_version, payload_fingerprint,
      canonical_payload, payload_size_bytes, user_id, session_id, run_id,
      event_type, entity_type, entity_key, source_entity_id,
      occurred_at, metadata
    )
    SELECT
      'smoke-behavior-binding-mismatch',
      'smoke-behavior-binding-mismatch',
      'recommendation-behavior-event-v1',
      public.recommendation_sha256_hex(payload),
      payload,
      octet_length(payload),
      current_setting('jc.recommendation_smoke_user_id')::bigint,
      'wrong-session',
      'smoke-run',
      'click',
      'post',
      'post:' || current_setting('jc.recommendation_smoke_ranked_post_id'),
      current_setting('jc.recommendation_smoke_ranked_post_id')::bigint,
      CURRENT_TIMESTAMP,
      '{}'::jsonb
    FROM (SELECT convert_to('{"event":"binding-mismatch"}', 'UTF8') AS payload) p;
    RAISE EXCEPTION 'Smoke test failed: behavior event with mismatched run binding was accepted.';
  EXCEPTION
    WHEN check_violation THEN NULL;
  END;

  BEGIN
    INSERT INTO public.recommendation_behavior_event (
      event_id, idempotency_key, schema_version, payload_fingerprint,
      canonical_payload, payload_size_bytes, user_id, session_id, run_id,
      event_type, occurred_at, metadata
    )
    SELECT
      'smoke-behavior-idempotency-conflict',
      'smoke-behavior-idempotency',
      'recommendation-behavior-event-v1',
      public.recommendation_sha256_hex(payload),
      payload,
      octet_length(payload),
      current_setting('jc.recommendation_smoke_user_id')::bigint,
      'smoke-session',
      'smoke-run',
      'search',
      CURRENT_TIMESTAMP,
      '{"query":"conflict"}'::jsonb
    FROM (SELECT convert_to('{"event":"conflict"}', 'UTF8') AS payload) p;
    RAISE EXCEPTION 'Smoke test failed: duplicate behavior idempotency key was accepted.';
  EXCEPTION
    WHEN unique_violation THEN NULL;
  END;

  BEGIN
    UPDATE public.recommendation_run
    SET duration_ms = 10
    WHERE run_id = 'smoke-run';
    RAISE EXCEPTION 'Smoke test failed: jc_recommendation received UPDATE privilege.';
  EXCEPTION
    WHEN insufficient_privilege THEN NULL;
  END;

  BEGIN
    DELETE FROM public.recommendation_behavior_event
    WHERE event_id = 'smoke-behavior-event';
    RAISE EXCEPTION 'Smoke test failed: jc_recommendation received DELETE privilege.';
  EXCEPTION
    WHEN insufficient_privilege THEN NULL;
  END;
END;
$$;

RESET ROLE;

DO $$
DECLARE
  v_count integer;
BEGIN
  SELECT count(*) INTO v_count
  FROM public.recommendation_snapshot
  WHERE snapshot_id LIKE 'smoke-%-snapshot';

  IF v_count <> 4 THEN
    RAISE EXCEPTION 'Smoke test failed: expected 4 snapshots, got %.', v_count;
  END IF;

  IF NOT EXISTS (
    SELECT 1
    FROM public.recommendation_run_candidate
    WHERE run_id = 'smoke-run'
      AND absolute_rank = 1
      AND score = 0.0
      AND score_is_negative_zero = true
  ) THEN
    RAISE EXCEPTION 'Smoke test failed: signed-zero provenance was not preserved.';
  END IF;

  IF NOT EXISTS (
    SELECT 1
    FROM public.recommendation_exposure_event e
    JOIN public.recommendation_exposure_candidate c
      ON c.exposure_event_id = e.event_id
    WHERE e.event_id = 'smoke-exposure-event'
      AND e.page_candidate_count = 1
      AND c.page_position = 1
      AND c.absolute_rank = 1
  ) THEN
    RAISE EXCEPTION 'Smoke test failed: exposure event/candidate persistence is incomplete.';
  END IF;

  BEGIN
    UPDATE public.recommendation_run
    SET duration_ms = 10
    WHERE run_id = 'smoke-run';
    RAISE EXCEPTION 'Smoke test failed: append-only UPDATE trigger did not fire.';
  EXCEPTION
    WHEN SQLSTATE '55000' THEN NULL;
  END;

  BEGIN
    DELETE FROM public.recommendation_snapshot
    WHERE snapshot_id = 'smoke-ranking-snapshot';
    RAISE EXCEPTION 'Smoke test failed: append-only DELETE trigger did not fire.';
  EXCEPTION
    WHEN SQLSTATE '55000' THEN NULL;
  END;
END;
$$;

SET ROLE jc_app;
DO $$
BEGIN
  BEGIN
    PERFORM count(*) FROM public.recommendation_run;
    RAISE EXCEPTION 'Smoke test failed: jc_app can read recommendation history.';
  EXCEPTION
    WHEN insufficient_privilege THEN NULL;
  END;

  BEGIN
    INSERT INTO public.recommendation_behavior_event (
      event_id, idempotency_key, schema_version, payload_fingerprint,
      canonical_payload, payload_size_bytes, session_id, event_type,
      occurred_at, metadata
    )
    VALUES (
      'jc-app-forbidden',
      'jc-app-forbidden',
      'recommendation-behavior-event-v1',
      repeat('0', 64),
      decode('', 'hex'),
      0,
      'forbidden',
      'search',
      CURRENT_TIMESTAMP,
      '{}'::jsonb
    );
    RAISE EXCEPTION 'Smoke test failed: jc_app can insert recommendation events.';
  EXCEPTION
    WHEN insufficient_privilege THEN NULL;
  END;
END;
$$;
RESET ROLE;

SET ROLE jc_auth;
DO $$
BEGIN
  BEGIN
    PERFORM count(*) FROM public.recommendation_snapshot;
    RAISE EXCEPTION 'Smoke test failed: jc_auth can read recommendation snapshots.';
  EXCEPTION
    WHEN insufficient_privilege THEN NULL;
  END;
END;
$$;
RESET ROLE;

SET ROLE jc_admin;
DO $$
DECLARE
  v_count integer;
BEGIN
  SELECT count(*) INTO v_count FROM public.recommendation_run WHERE run_id = 'smoke-run';
  IF v_count <> 1 THEN
    RAISE EXCEPTION 'Smoke test failed: jc_admin cannot read recommendation history.';
  END IF;

  BEGIN
    DELETE FROM public.recommendation_run WHERE run_id = 'smoke-run';
    RAISE EXCEPTION 'Smoke test failed: jc_admin can mutate recommendation history.';
  EXCEPTION
    WHEN insufficient_privilege THEN NULL;
  END;
END;
$$;
RESET ROLE;

ROLLBACK;
