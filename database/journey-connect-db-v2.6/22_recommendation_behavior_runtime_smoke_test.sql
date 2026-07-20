-- Journey Connect DB v2.5 - P0-7 behavior runtime smoke test
-- Run after 01~21 as PostgreSQL superuser. All test data is rolled back.

BEGIN;

DO $$
DECLARE
  v_user_id bigint;
  v_region_id bigint;
  v_place_id bigint;
  v_post_id bigint;
BEGIN
  INSERT INTO public.app_users (email, password_hash, username, display_name)
  VALUES ('behavior-runtime@journey.test', 'hash', 'behavior_runtime', 'Behavior Runtime')
  RETURNING id INTO v_user_id;

  SELECT id INTO v_region_id
  FROM public.regions
  WHERE slug = 'kr-seoul';
  IF v_region_id IS NULL THEN
    RAISE EXCEPTION 'Seed region kr-seoul is required.';
  END IF;

  INSERT INTO public.places
    (region_id, name_local, name_ko, category, created_by_user_id)
  VALUES (v_region_id, 'Behavior Place', '행동 테스트 장소', 'test', v_user_id)
  RETURNING id INTO v_place_id;

  INSERT INTO public.posts
    (author_id, main_region_id, title, content, visibility, status)
  VALUES
    (v_user_id, v_region_id, 'Behavior Post', 'Behavior runtime post', 'public', 'draft')
  RETURNING id INTO v_post_id;

  INSERT INTO public.post_places (post_id, place_id, sort_order)
  VALUES (v_post_id, v_place_id, 0);
  UPDATE public.posts SET status = 'published' WHERE id = v_post_id;

  PERFORM set_config('jc.behavior_runtime_user_id', v_user_id::text, true);
  PERFORM set_config('jc.behavior_runtime_post_id', v_post_id::text, true);
  PERFORM set_config(
    'jc.behavior_runtime_like_hash',
    public.recommendation_sha256_hex(convert_to('{"event":"like"}', 'UTF8')),
    true
  );
  PERFORM set_config(
    'jc.behavior_runtime_different_hash',
    public.recommendation_sha256_hex(convert_to('{"event":"different"}', 'UTF8')),
    true
  );
END;
$$;

SET ROLE jc_app;
SELECT set_config('jc.current_user_id', current_setting('jc.behavior_runtime_user_id'), true);

DO $$
DECLARE
  v_payload bytea := convert_to('{"event":"like"}', 'UTF8');
  v_result varchar;
BEGIN
  SELECT public.apply_recommendation_post_interaction(
    current_setting('jc.behavior_runtime_user_id')::bigint,
    current_setting('jc.behavior_runtime_post_id')::bigint,
    'like', 'behavior-runtime-like', 'behavior-runtime-like-key',
    'recommendation-behavior-event-v1', current_setting('jc.behavior_runtime_like_hash'),
    v_payload, 'behavior-runtime-session', NULL, CURRENT_TIMESTAMP,
    '{"source":"smoke"}'::jsonb
  ) INTO v_result;
  IF v_result <> 'applied' THEN
    RAISE EXCEPTION 'Expected applied like, got %', v_result;
  END IF;

  SELECT public.apply_recommendation_post_interaction(
    current_setting('jc.behavior_runtime_user_id')::bigint,
    current_setting('jc.behavior_runtime_post_id')::bigint,
    'like', 'behavior-runtime-like', 'behavior-runtime-like-key',
    'recommendation-behavior-event-v1', current_setting('jc.behavior_runtime_like_hash'),
    v_payload, 'behavior-runtime-session', NULL, CURRENT_TIMESTAMP,
    '{"source":"smoke"}'::jsonb
  ) INTO v_result;
  IF v_result NOT IN ('duplicate', 'no_change') THEN
    RAISE EXCEPTION 'Expected duplicate/no_change retry, got %', v_result;
  END IF;

  SELECT public.apply_recommendation_post_interaction(
    current_setting('jc.behavior_runtime_user_id')::bigint,
    current_setting('jc.behavior_runtime_post_id')::bigint,
    'like', 'behavior-runtime-conflict', 'behavior-runtime-like-key',
    'recommendation-behavior-event-v1',
    current_setting('jc.behavior_runtime_different_hash'),
    convert_to('{"event":"different"}', 'UTF8'),
    'behavior-runtime-session', NULL, CURRENT_TIMESTAMP,
    '{"source":"smoke"}'::jsonb
  ) INTO v_result;
  IF v_result <> 'idempotency_conflict' THEN
    RAISE EXCEPTION 'Expected idempotency conflict, got %', v_result;
  END IF;

  IF (SELECT count(*) FROM public.post_likes
      WHERE post_id = current_setting('jc.behavior_runtime_post_id')::bigint
        AND user_id = current_setting('jc.behavior_runtime_user_id')::bigint) <> 1 THEN
    RAISE EXCEPTION 'Atomic like mutation failed';
  END IF;
END;
$$;

RESET ROLE;

DO $$
BEGIN
  IF NOT has_function_privilege(
    'jc_app',
    'public.apply_recommendation_post_interaction(bigint,bigint,character varying,character varying,character varying,character varying,character varying,bytea,character varying,character varying,timestamp with time zone,jsonb)',
    'EXECUTE') THEN
    RAISE EXCEPTION 'jc_app must execute atomic recommendation interaction function';
  END IF;

  IF has_table_privilege('jc_app', 'public.recommendation_behavior_event', 'INSERT') THEN
    RAISE EXCEPTION 'jc_app must not insert behavior history directly';
  END IF;
END;
$$;

ROLLBACK;
