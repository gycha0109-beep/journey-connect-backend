-- Journey Connect DB v2.7 - ADM-3 admin control-plane hardening smoke test
-- Target: PostgreSQL 15+

BEGIN;

DO $$
DECLARE
  v_definition text;
  v_owner text;
BEGIN
  FOREACH v_definition IN ARRAY ARRAY[
    pg_get_functiondef('public.admin_suspend_user(bigint,character varying)'::regprocedure),
    pg_get_functiondef('public.admin_withdraw_user(bigint,character varying)'::regprocedure),
    pg_get_functiondef('public.admin_change_user_role(bigint,character varying,character varying)'::regprocedure)
  ] LOOP
    IF position('pg_advisory_xact_lock(1245789, 3)' IN v_definition) = 0 THEN
      RAISE EXCEPTION 'Admin control-plane function is missing transaction advisory serialization.';
    END IF;
    IF position('ORDER BY u.id' IN v_definition) = 0 OR position('FOR UPDATE' IN v_definition) = 0 THEN
      RAISE EXCEPTION 'Admin control-plane function is missing deterministic actor/target row locking.';
    END IF;
    IF position('At least one active admin account must remain.' IN v_definition) = 0 THEN
      RAISE EXCEPTION 'Admin control-plane function is missing last-active-admin protection.';
    END IF;
  END LOOP;

  SELECT r.rolname
    INTO v_owner
  FROM pg_proc p
  JOIN pg_roles r ON r.oid = p.proowner
  WHERE p.oid = 'public.admin_suspend_user(bigint,character varying)'::regprocedure;

  IF v_owner <> 'jc_security_owner' THEN
    RAISE EXCEPTION 'admin_suspend_user owner mismatch: %', v_owner;
  END IF;

  IF NOT has_function_privilege('jc_admin', 'public.admin_suspend_user(bigint,character varying)', 'EXECUTE') THEN
    RAISE EXCEPTION 'jc_admin must execute admin_suspend_user.';
  END IF;
  IF has_function_privilege('jc_app', 'public.admin_suspend_user(bigint,character varying)', 'EXECUTE')
     OR has_function_privilege('jc_auth', 'public.admin_suspend_user(bigint,character varying)', 'EXECUTE') THEN
    RAISE EXCEPTION 'Non-admin runtime roles must not execute admin_suspend_user.';
  END IF;

  IF EXISTS (
    SELECT 1
    FROM pg_proc p
    CROSS JOIN LATERAL aclexplode(COALESCE(p.proacl, acldefault('f', p.proowner))) acl
    WHERE p.oid = 'public.admin_suspend_user(bigint,character varying)'::regprocedure
      AND acl.grantee = 0
      AND acl.privilege_type = 'EXECUTE'
  ) THEN
    RAISE EXCEPTION 'PUBLIC must not execute admin_suspend_user.';
  END IF;
END;
$$;

ROLLBACK;
