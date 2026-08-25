-- Journey Connect DB v2.7 - PF3 crew recommendation feedback smoke test
-- Target: PostgreSQL 15+

BEGIN;

DO $$
DECLARE
  v_function_oid oid;
  v_owner text;
  v_security_definer boolean;
  v_config text;
BEGIN
  v_function_oid := to_regprocedure(
      'public.record_crew_join_recommendation_feedback(bigint,bigint,timestamp with time zone,bytea)');
  IF v_function_oid IS NULL THEN
    RAISE EXCEPTION 'PF3 crew recommendation feedback function is missing.';
  END IF;

  SELECT role.rolname, proc.prosecdef, array_to_string(proc.proconfig, ',')
    INTO v_owner, v_security_definer, v_config
  FROM pg_proc proc
  JOIN pg_roles role ON role.oid = proc.proowner
  WHERE proc.oid = v_function_oid;

  IF v_owner <> 'jc_recommendation' OR NOT v_security_definer THEN
    RAISE EXCEPTION 'PF3 command must be SECURITY DEFINER owned by jc_recommendation.';
  END IF;
  IF v_config IS NULL OR v_config NOT LIKE '%search_path=pg_catalog, public, pg_temp%' THEN
    RAISE EXCEPTION 'PF3 command search_path is not fixed.';
  END IF;

  IF NOT has_function_privilege(
      'jc_app',
      'public.record_crew_join_recommendation_feedback(bigint,bigint,timestamp with time zone,bytea)',
      'EXECUTE') THEN
    RAISE EXCEPTION 'jc_app must execute the PF3 command bridge.';
  END IF;

  IF has_function_privilege(
      'jc_auth',
      'public.record_crew_join_recommendation_feedback(bigint,bigint,timestamp with time zone,bytea)',
      'EXECUTE')
     OR has_function_privilege(
      'jc_admin',
      'public.record_crew_join_recommendation_feedback(bigint,bigint,timestamp with time zone,bytea)',
      'EXECUTE')
     OR has_function_privilege(
      'jc_security_owner',
      'public.record_crew_join_recommendation_feedback(bigint,bigint,timestamp with time zone,bytea)',
      'EXECUTE') THEN
    RAISE EXCEPTION 'PF3 command execute privilege is broader than jc_app.';
  END IF;

  IF has_table_privilege('jc_app', 'public.recommendation_behavior_event', 'SELECT')
     OR has_table_privilege('jc_app', 'public.recommendation_behavior_event', 'INSERT')
     OR has_table_privilege('jc_app', 'public.recommendation_behavior_event', 'UPDATE')
     OR has_table_privilege('jc_app', 'public.recommendation_behavior_event', 'DELETE')
     OR has_table_privilege('jc_app', 'public.recommendation_behavior_event', 'TRUNCATE') THEN
    RAISE EXCEPTION 'jc_app must not receive direct recommendation behavior table authority.';
  END IF;

  IF NOT has_column_privilege('jc_recommendation', 'public.crews', 'id', 'SELECT')
     OR NOT has_column_privilege('jc_recommendation', 'public.crews', 'region_id', 'SELECT')
     OR NOT has_column_privilege('jc_recommendation', 'public.crew_members', 'crew_id', 'SELECT')
     OR NOT has_column_privilege('jc_recommendation', 'public.crew_members', 'user_id', 'SELECT')
     OR NOT has_column_privilege('jc_recommendation', 'public.crew_members', 'status', 'SELECT') THEN
    RAISE EXCEPTION 'jc_recommendation PF3 read columns are incomplete.';
  END IF;

  IF has_table_privilege('jc_recommendation', 'public.crews', 'SELECT')
     OR has_table_privilege('jc_recommendation', 'public.crew_members', 'SELECT')
     OR has_column_privilege('jc_recommendation', 'public.crew_members', 'reviewed_by', 'SELECT')
     OR has_column_privilege('jc_recommendation', 'public.crew_members', 'reviewed_at', 'SELECT') THEN
    RAISE EXCEPTION 'jc_recommendation crew read authority is broader than PF3 requires.';
  END IF;
END;
$$;

ROLLBACK;
