\set ON_ERROR_STOP on

-- Usage:
-- psql ... \
--   -v sr6fg_environment=stage \
--   -v sr6fg_approval_ref=approval:sr6fg-stage-20260806t0800z \
--   -f operations/search-ctr/sr6fg/03_revoke_stage_reliability.sql

BEGIN;

SELECT set_config('jc.sr6fg_environment', :'sr6fg_environment', true);
SELECT set_config('jc.sr6fg_approval_ref', :'sr6fg_approval_ref', true);

DO $sr6fg$
BEGIN
  IF current_setting('jc.sr6fg_environment') <> 'stage' THEN
    RAISE EXCEPTION 'SR-6F-G membership revoke is authorized only for stage'
      USING ERRCODE = '42501';
  END IF;
  IF current_setting('jc.sr6fg_approval_ref')
      <> 'approval:sr6fg-stage-20260806t0800z' THEN
    RAISE EXCEPTION 'SR-6F-G approval reference mismatch'
      USING ERRCODE = '42501';
  END IF;
  IF NOT pg_catalog.pg_has_role('jc_backend', 'jc_reliability', 'MEMBER') THEN
    RAISE EXCEPTION 'jc_backend lacks jc_reliability membership; revoke evidence is ambiguous'
      USING ERRCODE = '55000';
  END IF;
END;
$sr6fg$;

REVOKE jc_reliability FROM jc_backend;

DO $sr6fg$
BEGIN
  IF pg_catalog.pg_has_role('jc_backend', 'jc_reliability', 'MEMBER') THEN
    RAISE EXCEPTION 'SR-6F-G membership revoke verification failed'
      USING ERRCODE = '42501';
  END IF;
END;
$sr6fg$;

COMMIT;
