\set ON_ERROR_STOP on

BEGIN;

SELECT set_config('jc.sr6fh_environment', :'sr6fh_environment', true);
SELECT set_config('jc.sr6fh_approval_ref', :'sr6fh_approval_ref', true);
SELECT set_config('jc.sr6fh_window_start', :'sr6fh_window_start', true);
SELECT set_config('jc.sr6fh_producer_build_id', :'sr6fh_producer_build_id', true);

DO $sr6fh$
DECLARE
  admin_role record;
  backend_role record;
  reliability_role record;
  v_window_start timestamptz;
  v_prior_runs integer;
  v_head_count integer;
  v_can_admin boolean;
BEGIN
  IF current_setting('jc.sr6fh_environment') <> 'stage' THEN
    RAISE EXCEPTION 'SR-6F-H preflight is authorized only for stage' USING ERRCODE = '42501';
  END IF;
  IF current_setting('jc.sr6fh_approval_ref')
      <> 'approval:sr6fg-stage-20260806t0800z' THEN
    RAISE EXCEPTION 'SR-6F-H approval reference mismatch' USING ERRCODE = '42501';
  END IF;
  IF current_setting('jc.sr6fh_producer_build_id')
      !~ '^sr6fg-stage-[0-9a-f]{40}$' THEN
    RAISE EXCEPTION 'SR-6F-H producer build must bind the exact source SHA'
      USING ERRCODE = '22023';
  END IF;

  v_window_start := current_setting('jc.sr6fh_window_start')::timestamptz;
  IF v_window_start <> '2026-08-06T08:00:00Z'::timestamptz THEN
    RAISE EXCEPTION 'SR-6F-H window is outside the bounded authorization'
      USING ERRCODE = '42501';
  END IF;
  IF clock_timestamp() < v_window_start + interval '1 hour 35 minutes' THEN
    RAISE EXCEPTION 'SR-6F-H window is not provisionally eligible'
      USING ERRCODE = '22023';
  END IF;
  IF pg_catalog.pg_is_in_recovery() THEN
    RAISE EXCEPTION 'SR-6F-H stage endpoint is read-only/in recovery'
      USING ERRCODE = '25006';
  END IF;
  IF current_setting('server_version_num')::integer < 150000 THEN
    RAISE EXCEPTION 'SR-6F-H requires PostgreSQL 15 or newer'
      USING ERRCODE = '0A000';
  END IF;

  SELECT * INTO admin_role FROM pg_catalog.pg_roles WHERE rolname = current_user;
  IF NOT FOUND THEN
    RAISE EXCEPTION 'SR-6F-H cannot resolve the administrative operator role'
      USING ERRCODE = '42501';
  END IF;
  SELECT admin_role.rolsuper
      OR admin_role.rolcreaterole
      OR EXISTS (
        SELECT 1
        FROM pg_catalog.pg_auth_members membership
        JOIN pg_catalog.pg_roles granted_role ON granted_role.oid = membership.roleid
        JOIN pg_catalog.pg_roles member_role ON member_role.oid = membership.member
        WHERE granted_role.rolname = 'jc_reliability'
          AND member_role.rolname = current_user
          AND membership.admin_option
      )
    INTO v_can_admin;
  IF NOT v_can_admin THEN
    RAISE EXCEPTION 'SR-6F-H operator cannot grant and revoke jc_reliability'
      USING ERRCODE = '42501';
  END IF;

  SELECT * INTO backend_role FROM pg_catalog.pg_roles WHERE rolname = 'jc_backend';
  IF NOT FOUND
     OR NOT backend_role.rolcanlogin
     OR backend_role.rolinherit
     OR backend_role.rolsuper
     OR backend_role.rolcreatedb
     OR backend_role.rolcreaterole
     OR backend_role.rolreplication
     OR backend_role.rolbypassrls THEN
    RAISE EXCEPTION 'jc_backend does not satisfy the restricted login contract'
      USING ERRCODE = '42501';
  END IF;

  SELECT * INTO reliability_role FROM pg_catalog.pg_roles WHERE rolname = 'jc_reliability';
  IF NOT FOUND
     OR reliability_role.rolcanlogin
     OR reliability_role.rolinherit
     OR reliability_role.rolsuper
     OR reliability_role.rolcreatedb
     OR reliability_role.rolcreaterole
     OR reliability_role.rolreplication
     OR reliability_role.rolbypassrls THEN
    RAISE EXCEPTION 'jc_reliability does not satisfy the isolated NOLOGIN contract'
      USING ERRCODE = '42501';
  END IF;

  IF pg_catalog.to_regclass('public.search_ctr_projection_snapshot_v1') IS NULL
     OR pg_catalog.to_regclass('public.search_ctr_manual_run_audit_v1') IS NULL
     OR pg_catalog.to_regprocedure(
       'public.execute_search_ctr_manual_v1(character varying,timestamp with time zone,timestamp with time zone,character varying,character varying,timestamp with time zone,character varying,character varying,character varying)'
     ) IS NULL THEN
    RAISE EXCEPTION 'SR-6F-H required Search CTR database objects are absent'
      USING ERRCODE = '42P01';
  END IF;

  IF pg_catalog.pg_has_role('jc_backend', 'jc_reliability', 'MEMBER') THEN
    RAISE EXCEPTION 'SR-6F-H preflight requires jc_reliability membership to be absent'
      USING ERRCODE = '55000';
  END IF;

  SELECT count(*) INTO v_prior_runs
  FROM public.search_ctr_manual_run_audit_v1
  WHERE environment = 'stage'
    AND window_start = v_window_start
    AND producer_build_id = current_setting('jc.sr6fh_producer_build_id');
  IF v_prior_runs <> 0 THEN
    RAISE EXCEPTION 'SR-6F-H exact producer build already has execution evidence'
      USING ERRCODE = '55000';
  END IF;

  SELECT count(*) INTO v_head_count
  FROM public.search_ctr_projection_snapshot_v1 candidate
  WHERE candidate.metric_id = 'search-click-through-rate-v1'
    AND candidate.metric_version = 'search-ctr-projection-v1'
    AND candidate.window_start = v_window_start
    AND candidate.window_end = v_window_start + interval '1 hour'
    AND NOT EXISTS (
      SELECT 1
      FROM public.search_ctr_projection_snapshot_v1 child
      WHERE child.predecessor_projection_id = candidate.projection_id
    );
  IF v_head_count > 1 THEN
    RAISE EXCEPTION 'SR-6F-H projection lineage has multiple heads'
      USING ERRCODE = '23514';
  END IF;
END;
$sr6fh$;

ROLLBACK;
\echo SR6FH_PREFLIGHT=PASS
