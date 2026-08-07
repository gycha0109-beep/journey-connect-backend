\set ON_ERROR_STOP on

BEGIN;

DO $sr6fh$
BEGIN
  IF pg_catalog.pg_has_role('jc_backend', 'jc_reliability', 'MEMBER') THEN
    RAISE EXCEPTION 'SR-6F-H revoke verification found residual membership'
      USING ERRCODE = '42501';
  END IF;
  IF pg_catalog.has_table_privilege(
       'jc_backend', 'public.search_ctr_projection_snapshot_v1', 'SELECT,INSERT,UPDATE,DELETE')
     OR pg_catalog.has_table_privilege(
       'jc_backend', 'public.search_ctr_manual_run_audit_v1', 'SELECT,INSERT,UPDATE,DELETE') THEN
    RAISE EXCEPTION 'SR-6F-H revoke verification found direct table privileges'
      USING ERRCODE = '42501';
  END IF;
END;
$sr6fh$;

ROLLBACK;
\echo SR6FH_REVOKE_CATALOG_VERIFY=PASS
