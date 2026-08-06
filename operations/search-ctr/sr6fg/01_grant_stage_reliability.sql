\set ON_ERROR_STOP on

-- Usage:
-- psql ... \
--   -v sr6fg_environment=stage \
--   -v sr6fg_approval_ref=approval:sr6fg-stage-20260806t0800z \
--   -f operations/search-ctr/sr6fg/01_grant_stage_reliability.sql

BEGIN;

SELECT set_config('jc.sr6fg_environment', :'sr6fg_environment', true);
SELECT set_config('jc.sr6fg_approval_ref', :'sr6fg_approval_ref', true);

DO $sr6fg$
DECLARE
  backend_role record;
  reliability_role record;
BEGIN
  IF current_setting('jc.sr6fg_environment') <> 'stage' THEN
    RAISE EXCEPTION 'SR-6F-G membership grant is authorized only for stage'
      USING ERRCODE = '42501';
  END IF;
  IF current_setting('jc.sr6fg_approval_ref')
      <> 'approval:sr6fg-stage-20260806t0800z' THEN
    RAISE EXCEPTION 'SR-6F-G approval reference mismatch'
      USING ERRCODE = '42501';
  END IF;

  SELECT * INTO backend_role
  FROM pg_catalog.pg_roles
  WHERE rolname = 'jc_backend';
  IF NOT FOUND THEN
    RAISE EXCEPTION 'required restricted login jc_backend does not exist'
      USING ERRCODE = '42704';
  END IF;
  IF NOT backend_role.rolcanlogin
     OR backend_role.rolinherit
     OR backend_role.rolsuper
     OR backend_role.rolcreatedb
     OR backend_role.rolcreaterole
     OR backend_role.rolreplication
     OR backend_role.rolbypassrls THEN
    RAISE EXCEPTION 'jc_backend does not satisfy the restricted login contract'
      USING ERRCODE = '42501';
  END IF;

  SELECT * INTO reliability_role
  FROM pg_catalog.pg_roles
  WHERE rolname = 'jc_reliability';
  IF NOT FOUND THEN
    RAISE EXCEPTION 'required role jc_reliability does not exist'
      USING ERRCODE = '42704';
  END IF;
  IF reliability_role.rolcanlogin
     OR reliability_role.rolinherit
     OR reliability_role.rolsuper
     OR reliability_role.rolcreatedb
     OR reliability_role.rolcreaterole
     OR reliability_role.rolreplication
     OR reliability_role.rolbypassrls THEN
    RAISE EXCEPTION 'jc_reliability does not satisfy the isolated NOLOGIN contract'
      USING ERRCODE = '42501';
  END IF;

  IF pg_catalog.pg_has_role('jc_backend', 'jc_reliability', 'MEMBER') THEN
    RAISE EXCEPTION 'jc_backend already has jc_reliability membership; aborting ambiguous grant'
      USING ERRCODE = '55000';
  END IF;
END;
$sr6fg$;

GRANT jc_reliability TO jc_backend;

DO $sr6fg$
BEGIN
  IF NOT pg_catalog.pg_has_role('jc_backend', 'jc_reliability', 'MEMBER') THEN
    RAISE EXCEPTION 'SR-6F-G membership grant verification failed'
      USING ERRCODE = '42501';
  END IF;
END;
$sr6fg$;

COMMIT;
