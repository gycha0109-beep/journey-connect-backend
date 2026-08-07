-- SR-6F-H convergence for the isolated Search CTR Reliability role. PostgreSQL 15+.
-- Prerequisite: journey-connect-db-v2.8/01..09.
BEGIN;

DO $sr6fh$
DECLARE
  reliability_role record;
  v_memberships text;
BEGIN
  SELECT * INTO reliability_role
  FROM pg_catalog.pg_roles
  WHERE rolname = 'jc_reliability';
  IF NOT FOUND THEN
    RAISE EXCEPTION 'required role jc_reliability does not exist'
      USING ERRCODE = '42704';
  END IF;

  IF reliability_role.rolcanlogin
     OR reliability_role.rolsuper
     OR reliability_role.rolcreatedb
     OR reliability_role.rolcreaterole
     OR reliability_role.rolreplication
     OR reliability_role.rolbypassrls THEN
    RAISE EXCEPTION 'jc_reliability has unsafe role attributes before convergence'
      USING ERRCODE = '42501';
  END IF;

  SELECT string_agg(member_role.rolname || ' -> ' || granted_role.rolname, ', ')
    INTO v_memberships
  FROM pg_catalog.pg_auth_members membership
  JOIN pg_catalog.pg_roles member_role ON member_role.oid = membership.member
  JOIN pg_catalog.pg_roles granted_role ON granted_role.oid = membership.roleid
  WHERE member_role.rolname = 'jc_reliability'
     OR granted_role.rolname = 'jc_reliability';
  IF v_memberships IS NOT NULL THEN
    RAISE EXCEPTION 'jc_reliability memberships must be empty before convergence: %',
      v_memberships USING ERRCODE = '42501';
  END IF;
END;
$sr6fh$;

ALTER ROLE jc_reliability NOINHERIT;

DO $sr6fh$
DECLARE
  reliability_role record;
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
    RAISE EXCEPTION 'jc_reliability NOLOGIN NOINHERIT convergence failed'
      USING ERRCODE = '42501';
  END IF;
END;
$sr6fh$;

COMMIT;
