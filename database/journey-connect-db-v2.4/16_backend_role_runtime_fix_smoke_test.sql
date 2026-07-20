-- Journey Connect DB v2.2 - Restricted-role runtime regression smoke test
-- All test data is rolled back.

BEGIN;

DO $$
DECLARE
  v_owner_id bigint;
  v_region_id bigint;
BEGIN
  INSERT INTO public.app_users (
    email, password_hash, username, display_name
  ) VALUES (
    'role-runtime-smoke@example.com', repeat('c', 60),
    'role_runtime_smoke', 'Role Runtime Smoke'
  ) RETURNING id INTO v_owner_id;

  SELECT id INTO v_region_id
  FROM public.regions
  WHERE slug = 'kr-seoul';

  IF v_region_id IS NULL THEN
    RAISE EXCEPTION 'Seed region kr-seoul is required.';
  END IF;

  PERFORM set_config('jc.role_runtime_smoke_owner', v_owner_id::text, true);
  PERFORM set_config('jc.role_runtime_smoke_region', v_region_id::text, true);
END;
$$;

SET LOCAL ROLE jc_app;

DO $$
DECLARE
  v_owner_id bigint := current_setting('jc.role_runtime_smoke_owner')::bigint;
  v_region_id bigint := current_setting('jc.role_runtime_smoke_region')::bigint;
  v_crew_id bigint;
BEGIN
  INSERT INTO public.crews (
    owner_id, region_id, title, description, travel_date, capacity,
    recruiting, approval_required
  ) VALUES (
    v_owner_id, v_region_id, 'Role runtime smoke crew',
    'Deferred trigger must run under restricted role',
    CURRENT_DATE + 10, 2, true, false
  ) RETURNING id INTO v_crew_id;

  INSERT INTO public.crew_members (crew_id, user_id, status)
  VALUES (v_crew_id, v_owner_id, 'OWNER');
END;
$$;

-- Force the deferred aggregate trigger while jc_app is still the current role.
SET CONSTRAINTS ALL IMMEDIATE;
RESET ROLE;

DO $$
DECLARE
  v_invalid_functions text;
BEGIN
  SELECT string_agg(p.oid::regprocedure::text, ', ' ORDER BY p.oid::regprocedure::text)
    INTO v_invalid_functions
  FROM pg_proc p
  JOIN pg_namespace n ON n.oid = p.pronamespace
  JOIN pg_roles owner_role ON owner_role.oid = p.proowner
  WHERE n.nspname = 'public'
    AND p.oid IN (
      'public.validate_crew_member_owner_binding()'::regprocedure,
      'public.assert_crew_membership_integrity(bigint)'::regprocedure,
      'public.check_crew_integrity_from_crew()'::regprocedure,
      'public.check_crew_integrity_from_member()'::regprocedure
    )
    AND (
      NOT p.prosecdef
      OR owner_role.rolname <> 'jc_security_owner'
      OR NOT (coalesce(p.proconfig, ARRAY[]::text[])
              @> ARRAY['search_path=pg_catalog, pg_temp']::text[])
    );

  IF v_invalid_functions IS NOT NULL THEN
    RAISE EXCEPTION 'Crew integrity function hardening is incomplete: %',
      v_invalid_functions;
  END IF;

  IF NOT has_table_privilege('jc_security_owner', 'public.crews', 'SELECT')
     OR NOT has_table_privilege('jc_security_owner', 'public.crew_members', 'SELECT') THEN
    RAISE EXCEPTION 'jc_security_owner lacks crew integrity read privileges.';
  END IF;

  IF has_function_privilege(
      'jc_app', 'public.assert_crew_membership_integrity(bigint)', 'EXECUTE')
     OR has_function_privilege(
      'jc_app', 'public.check_crew_integrity_from_crew()', 'EXECUTE')
     OR has_function_privilege(
      'jc_app', 'public.check_crew_integrity_from_member()', 'EXECUTE') THEN
    RAISE EXCEPTION 'Crew integrity internals must not be callable by jc_app.';
  END IF;
END;
$$;

ROLLBACK;
