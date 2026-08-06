-- SR-6F-F non-production manual Search CTR activation foundation smoke test.
-- Run after 08_search_ctr_nonprod_manual_activation_foundation.sql. All fixtures are rolled back.
BEGIN;

DO $$
BEGIN
  IF NOT has_function_privilege(
       'jc_reliability',
       'public.read_search_ctr_projection_head_v1(timestamp with time zone,timestamp with time zone,character varying)',
       'EXECUTE')
     OR NOT has_function_privilege(
       'jc_reliability',
       'public.execute_search_ctr_manual_v1(character varying,timestamp with time zone,timestamp with time zone,character varying,character varying,timestamp with time zone,character varying,character varying,character varying)',
       'EXECUTE')
     OR has_table_privilege('jc_reliability', 'public.search_ctr_projection_snapshot_v1', 'SELECT')
     OR has_table_privilege('jc_reliability', 'public.search_ctr_manual_run_audit_v1', 'SELECT')
     OR has_table_privilege('jc_reliability', 'public.search_ctr_manual_run_audit_v1', 'INSERT') THEN
    RAISE EXCEPTION 'Search CTR manual activation privilege contract failed';
  END IF;

  IF pg_get_function_result(
       'public.read_search_ctr_projection_head_v1(timestamp with time zone,timestamp with time zone,character varying)'::regprocedure
     ) ~* '(user_id|subject_ref|session_id|exposure_id|click_event_id|raw_query)'
     OR pg_get_function_result(
       'public.execute_search_ctr_manual_v1(character varying,timestamp with time zone,timestamp with time zone,character varying,character varying,timestamp with time zone,character varying,character varying,character varying)'::regprocedure
     ) ~* '(user_id|subject_ref|session_id|exposure_id|click_event_id|raw_query)' THEN
    RAISE EXCEPTION 'Search CTR manual activation boundary leaks identity-bearing columns';
  END IF;
END;
$$;

SET ROLE jc_reliability;
DO $$
DECLARE
  v_head record;
  v_result record;
  v_start timestamptz := '2000-01-01T00:00:00Z';
  v_end timestamptz := '2000-01-01T01:00:00Z';
BEGIN
  SELECT * INTO v_head FROM public.read_search_ctr_projection_head_v1(
    v_start, v_end, 'reliability-search-ctr-manual'
  );
  IF v_head.projection_id IS NOT NULL THEN
    RAISE EXCEPTION 'Search CTR manual smoke expected no initial head';
  END IF;

  SELECT * INTO v_result FROM public.execute_search_ctr_manual_v1(
    'search-ctr-manual-run:11111111111111111111111111111111',
    v_start,
    v_end,
    'test',
    'search-ctr-activation-finality-v1',
    v_end + interval '35 minutes',
    'search-ctr:manual-smoke-v1',
    'sr6ff-smoke-v1',
    'reliability-search-ctr-manual'
  );
  IF v_result.write_status <> 'STORED'
     OR v_result.eligible_exposure_count <> 0
     OR v_result.attributed_exposure_count <> 0
     OR v_result.ctr_basis_points IS NOT NULL
     OR v_result.status <> 'PROVISIONAL' THEN
    RAISE EXCEPTION 'Search CTR manual execution mismatch: %', row_to_json(v_result);
  END IF;

  SELECT * INTO v_head FROM public.read_search_ctr_projection_head_v1(
    v_start, v_end, 'reliability-search-ctr-manual'
  );
  IF v_head.projection_id IS DISTINCT FROM v_result.projection_id
     OR v_head.projection_fingerprint IS DISTINCT FROM v_result.projection_fingerprint THEN
    RAISE EXCEPTION 'Search CTR manual head read mismatch';
  END IF;
END;
$$;
RESET ROLE;

DO $$
DECLARE
  v_denied boolean := false;
BEGIN
  IF (SELECT count(*) FROM public.search_ctr_manual_run_audit_v1) <> 1 THEN
    RAISE EXCEPTION 'Search CTR manual run audit must contain exactly one row';
  END IF;
  IF EXISTS (
    SELECT 1 FROM public.search_ctr_manual_run_audit_v1
    WHERE runtime_mode <> 'NONPRODUCTION_MANUAL'
       OR environment <> 'test'
       OR write_status <> 'STORED'
       OR finality_write_attempted
  ) THEN
    RAISE EXCEPTION 'Search CTR manual run audit contract mismatch';
  END IF;

  BEGIN
    UPDATE public.search_ctr_manual_run_audit_v1
    SET environment = 'dev';
  EXCEPTION WHEN SQLSTATE '55000' THEN
    v_denied := true;
  END;
  IF NOT v_denied THEN
    RAISE EXCEPTION 'Search CTR manual run audit UPDATE must be denied';
  END IF;
END;
$$;

SET ROLE jc_reliability;
DO $$
DECLARE
  v_denied boolean := false;
BEGIN
  BEGIN
    PERFORM public.execute_search_ctr_manual_v1(
      'search-ctr-manual-run:22222222222222222222222222222222',
      '2000-01-01T00:00:00Z',
      '2000-01-01T01:00:00Z',
      'production',
      'search-ctr-activation-finality-v1',
      '2000-01-01T01:35:00Z',
      'search-ctr:manual-smoke-denied-v1',
      'sr6ff-smoke-v1',
      'reliability-search-ctr-manual'
    );
  EXCEPTION WHEN insufficient_privilege THEN
    v_denied := true;
  END;
  IF NOT v_denied THEN
    RAISE EXCEPTION 'Search CTR manual production environment must be denied';
  END IF;
END;
$$;
RESET ROLE;

ROLLBACK;
