-- SR-6F-H smoke test for the isolated Search CTR Reliability role. PostgreSQL 15+.
BEGIN;

DO $sr6fh$
DECLARE
  reliability_role record;
  v_memberships integer;
BEGIN
  SELECT * INTO reliability_role
  FROM pg_catalog.pg_roles
  WHERE rolname = 'jc_reliability';
  IF NOT FOUND
     OR reliability_role.rolcanlogin
     OR reliability_role.rolinherit
     OR reliability_role.rolsuper
     OR reliability_role.rolcreatedb
     OR reliability_role.rolcreaterole
     OR reliability_role.rolreplication
     OR reliability_role.rolbypassrls THEN
    RAISE EXCEPTION 'jc_reliability does not satisfy NOLOGIN NOINHERIT isolation'
      USING ERRCODE = '42501';
  END IF;

  SELECT count(*) INTO v_memberships
  FROM pg_catalog.pg_auth_members membership
  JOIN pg_catalog.pg_roles member_role ON member_role.oid = membership.member
  JOIN pg_catalog.pg_roles granted_role ON granted_role.oid = membership.roleid
  WHERE member_role.rolname = 'jc_reliability'
     OR granted_role.rolname = 'jc_reliability';
  IF v_memberships <> 0 THEN
    RAISE EXCEPTION 'jc_reliability smoke test found unexpected memberships'
      USING ERRCODE = '42501';
  END IF;

  IF NOT pg_catalog.has_function_privilege(
       'jc_reliability',
       'public.execute_search_ctr_manual_v1(character varying,timestamp with time zone,timestamp with time zone,character varying,character varying,timestamp with time zone,character varying,character varying,character varying)',
       'EXECUTE') THEN
    RAISE EXCEPTION 'jc_reliability lost manual execution capability during convergence'
      USING ERRCODE = '42501';
  END IF;
END;
$sr6fh$;

ROLLBACK;
