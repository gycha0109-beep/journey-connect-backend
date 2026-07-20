-- Journey Connect DB v2.0 - Backend runtime smoke test
-- All test data is rolled back.

BEGIN;

DO $$
DECLARE
  v_user_id bigint;
  v_reviewer_id bigint;
  v_region_id bigint;
  v_crew_id bigint;
BEGIN
  INSERT INTO public.app_users (
    email, password_hash, username, display_name
  ) VALUES (
    'runtime-smoke@example.com', repeat('a', 60), 'runtime_smoke', 'Runtime Smoke'
  ) RETURNING id INTO v_user_id;

  INSERT INTO public.app_users (
    email, password_hash, username, display_name
  ) VALUES (
    'runtime-reviewer@example.com', repeat('b', 60), 'runtime_reviewer', 'Runtime Reviewer'
  ) RETURNING id INTO v_reviewer_id;

  SELECT id INTO v_region_id FROM public.regions WHERE slug = 'kr-seoul';
  IF v_region_id IS NULL THEN
    RAISE EXCEPTION 'Seed region kr-seoul is required.';
  END IF;

  INSERT INTO public.refresh_tokens (user_id, token_hash, expires_at)
  VALUES (v_user_id, repeat('a', 64), CURRENT_TIMESTAMP + interval '14 days');

  INSERT INTO public.crews (
    owner_id, region_id, title, description, travel_date, capacity,
    recruiting, approval_required
  ) VALUES (
    v_user_id, v_region_id, 'Runtime smoke crew', 'Runtime smoke description',
    CURRENT_DATE + 10, 4, true, true
  ) RETURNING id INTO v_crew_id;

  INSERT INTO public.crew_members (crew_id, user_id, status)
  VALUES (v_crew_id, v_user_id, 'OWNER');

  INSERT INTO public.crew_members (
    crew_id, user_id, status, reviewed_by, reviewed_at
  ) VALUES (
    v_crew_id, v_reviewer_id, 'APPROVED', v_user_id, CURRENT_TIMESTAMP
  );

  IF (SELECT count(*) FROM public.crew_members WHERE crew_id = v_crew_id) <> 2 THEN
    RAISE EXCEPTION 'Crew membership smoke assertion failed.';
  END IF;

  BEGIN
    UPDATE public.crew_members
    SET reviewed_by = v_reviewer_id
    WHERE crew_id = v_crew_id AND user_id = v_reviewer_id;
    RAISE EXCEPTION 'Changing an approved membership reviewer away from the crew owner must fail.';
  EXCEPTION
    WHEN check_violation THEN
      NULL;
  END;

  IF NOT has_function_privilege(
      'jc_app', 'public.can_user_view_post(bigint,bigint)', 'EXECUTE') THEN
    RAISE EXCEPTION 'jc_app must be able to execute can_user_view_post.';
  END IF;

  IF NOT has_function_privilege(
      'jc_recommendation', 'public.can_user_view_post(bigint,bigint)', 'EXECUTE') THEN
    RAISE EXCEPTION 'jc_recommendation must be able to execute can_user_view_post.';
  END IF;
END;
$$;

ROLLBACK;
