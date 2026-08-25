-- Journey Connect DB v2.7 - CR-1 crew tag association smoke test
-- Target: PostgreSQL 15+

BEGIN;

DO $$
DECLARE
  v_pk_count integer;
BEGIN
  IF to_regclass('public.crew_tags') IS NULL THEN
    RAISE EXCEPTION 'CR-1 crew_tags table is missing.';
  END IF;

  SELECT count(*)
    INTO v_pk_count
  FROM pg_constraint c
  WHERE c.conrelid = 'public.crew_tags'::regclass
    AND c.contype = 'p';
  IF v_pk_count <> 1 THEN
    RAISE EXCEPTION 'CR-1 crew_tags primary key is missing or ambiguous.';
  END IF;

  IF NOT has_table_privilege('jc_app', 'public.crew_tags', 'SELECT')
     OR NOT has_table_privilege('jc_app', 'public.crew_tags', 'INSERT')
     OR NOT has_table_privilege('jc_app', 'public.crew_tags', 'DELETE') THEN
    RAISE EXCEPTION 'jc_app CR-1 crew_tags privileges are incomplete.';
  END IF;
  IF has_table_privilege('jc_app', 'public.crew_tags', 'UPDATE')
     OR has_table_privilege('jc_app', 'public.crew_tags', 'TRUNCATE') THEN
    RAISE EXCEPTION 'jc_app CR-1 crew_tags privileges are broader than required.';
  END IF;

  IF NOT has_table_privilege('jc_recommendation', 'public.crew_tags', 'SELECT')
     OR has_table_privilege('jc_recommendation', 'public.crew_tags', 'INSERT')
     OR has_table_privilege('jc_recommendation', 'public.crew_tags', 'UPDATE')
     OR has_table_privilege('jc_recommendation', 'public.crew_tags', 'DELETE')
     OR has_table_privilege('jc_recommendation', 'public.crew_tags', 'TRUNCATE') THEN
    RAISE EXCEPTION 'jc_recommendation must remain read-only on CR-1 crew_tags.';
  END IF;

  IF NOT has_table_privilege('jc_admin', 'public.crew_tags', 'SELECT')
     OR has_table_privilege('jc_admin', 'public.crew_tags', 'INSERT')
     OR has_table_privilege('jc_admin', 'public.crew_tags', 'UPDATE')
     OR has_table_privilege('jc_admin', 'public.crew_tags', 'DELETE')
     OR has_table_privilege('jc_admin', 'public.crew_tags', 'TRUNCATE') THEN
    RAISE EXCEPTION 'jc_admin must remain read-only on CR-1 crew_tags.';
  END IF;

  IF has_table_privilege('jc_auth', 'public.crew_tags', 'SELECT')
     OR has_table_privilege('jc_auth', 'public.crew_tags', 'INSERT')
     OR has_table_privilege('jc_auth', 'public.crew_tags', 'UPDATE')
     OR has_table_privilege('jc_auth', 'public.crew_tags', 'DELETE') THEN
    RAISE EXCEPTION 'jc_auth must not access CR-1 crew_tags.';
  END IF;

  IF to_regprocedure('public.enforce_crew_tag_limit()') IS NULL THEN
    RAISE EXCEPTION 'CR-1 crew tag limit function is missing.';
  END IF;
  IF NOT EXISTS (
      SELECT 1
      FROM pg_trigger t
      WHERE t.tgrelid = 'public.crew_tags'::regclass
        AND t.tgname = 'crew_tags_limit_guard'
        AND NOT t.tgisinternal) THEN
    RAISE EXCEPTION 'CR-1 crew tag limit trigger is missing.';
  END IF;
END;
$$;

ROLLBACK;
