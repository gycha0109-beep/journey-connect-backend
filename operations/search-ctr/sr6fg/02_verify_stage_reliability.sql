\set ON_ERROR_STOP on

-- Usage:
-- psql ... \
--   -v sr6fg_environment=stage \
--   -v sr6fg_approval_ref=approval:sr6fg-stage-20260806t0800z \
--   -f operations/search-ctr/sr6fg/02_verify_stage_reliability.sql

BEGIN;

SELECT set_config('jc.sr6fg_environment', :'sr6fg_environment', true);
SELECT set_config('jc.sr6fg_approval_ref', :'sr6fg_approval_ref', true);

DO $sr6fg$
BEGIN
  IF current_setting('jc.sr6fg_environment') <> 'stage' THEN
    RAISE EXCEPTION 'SR-6F-G capability verification is authorized only for stage'
      USING ERRCODE = '42501';
  END IF;
  IF current_setting('jc.sr6fg_approval_ref')
      <> 'approval:sr6fg-stage-20260806t0800z' THEN
    RAISE EXCEPTION 'SR-6F-G approval reference mismatch'
      USING ERRCODE = '42501';
  END IF;
  IF NOT pg_catalog.pg_has_role('jc_backend', 'jc_reliability', 'MEMBER') THEN
    RAISE EXCEPTION 'jc_backend lacks the approved jc_reliability membership'
      USING ERRCODE = '42501';
  END IF;
  IF pg_catalog.has_table_privilege(
        'jc_backend',
        'public.search_ctr_projection_snapshot_v1',
        'SELECT') THEN
    RAISE EXCEPTION 'jc_backend must not receive direct projection table SELECT'
      USING ERRCODE = '42501';
  END IF;
  IF pg_catalog.has_table_privilege(
        'jc_backend',
        'public.search_ctr_manual_run_audit_v1',
        'SELECT,INSERT,UPDATE,DELETE') THEN
    RAISE EXCEPTION 'jc_backend must not receive direct manual audit table privileges'
      USING ERRCODE = '42501';
  END IF;
  IF NOT pg_catalog.has_function_privilege(
        'jc_reliability',
        'public.execute_search_ctr_manual_v1(character varying,timestamp with time zone,timestamp with time zone,character varying,character varying,timestamp with time zone,character varying,character varying,character varying)',
        'EXECUTE') THEN
    RAISE EXCEPTION 'jc_reliability lacks execute_search_ctr_manual_v1 EXECUTE'
      USING ERRCODE = '42501';
  END IF;
END;
$sr6fg$;

SET LOCAL ROLE jc_reliability;
DO $sr6fg$
BEGIN
  IF current_user <> 'jc_reliability' THEN
    RAISE EXCEPTION 'SET LOCAL ROLE jc_reliability verification failed'
      USING ERRCODE = '42501';
  END IF;
END;
$sr6fg$;
RESET ROLE;

ROLLBACK;
