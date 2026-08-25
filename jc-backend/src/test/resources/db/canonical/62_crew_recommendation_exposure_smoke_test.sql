-- Journey Connect DB v2.7 - PF4 Crew recommendation delivery exposure smoke verifier
-- Target: PostgreSQL 15+

BEGIN;

DO $$
DECLARE
  v_event_oid regclass := to_regclass('public.crew_recommendation_exposure_event');
  v_candidate_oid regclass := to_regclass('public.crew_recommendation_exposure_candidate');
BEGIN
  IF v_event_oid IS NULL OR v_candidate_oid IS NULL THEN
    RAISE EXCEPTION 'PF4 Crew exposure tables are missing.';
  END IF;

  IF NOT EXISTS (
    SELECT 1
    FROM pg_trigger
    WHERE tgrelid = v_event_oid
      AND tgname = 'trg_crew_rec_exp_event_append_only'
      AND NOT tgisinternal
  ) OR NOT EXISTS (
    SELECT 1
    FROM pg_trigger
    WHERE tgrelid = v_candidate_oid
      AND tgname = 'trg_crew_rec_exp_candidate_append_only'
      AND NOT tgisinternal
  ) THEN
    RAISE EXCEPTION 'PF4 append-only triggers are missing.';
  END IF;

  IF NOT EXISTS (
    SELECT 1
    FROM pg_trigger
    WHERE tgrelid = v_event_oid
      AND tgname = 'ctrg_crew_rec_exp_event_exact_candidates'
      AND tgdeferrable
      AND tginitdeferred
  ) OR NOT EXISTS (
    SELECT 1
    FROM pg_trigger
    WHERE tgrelid = v_candidate_oid
      AND tgname = 'ctrg_crew_rec_exp_candidate_exact_candidates'
      AND tgdeferrable
      AND tginitdeferred
  ) THEN
    RAISE EXCEPTION 'PF4 exact candidate-set deferred constraints are missing.';
  END IF;

  IF NOT has_table_privilege('jc_recommendation', v_event_oid, 'SELECT')
     OR NOT has_table_privilege('jc_recommendation', v_event_oid, 'INSERT')
     OR has_table_privilege('jc_recommendation', v_event_oid, 'UPDATE')
     OR has_table_privilege('jc_recommendation', v_event_oid, 'DELETE')
     OR NOT has_table_privilege('jc_recommendation', v_candidate_oid, 'SELECT')
     OR NOT has_table_privilege('jc_recommendation', v_candidate_oid, 'INSERT')
     OR has_table_privilege('jc_recommendation', v_candidate_oid, 'UPDATE')
     OR has_table_privilege('jc_recommendation', v_candidate_oid, 'DELETE') THEN
    RAISE EXCEPTION 'PF4 jc_recommendation least-privilege contract failed.';
  END IF;

  IF NOT has_table_privilege('jc_admin', v_event_oid, 'SELECT')
     OR has_table_privilege('jc_admin', v_event_oid, 'INSERT')
     OR has_table_privilege('jc_admin', v_event_oid, 'UPDATE')
     OR has_table_privilege('jc_admin', v_event_oid, 'DELETE')
     OR NOT has_table_privilege('jc_admin', v_candidate_oid, 'SELECT')
     OR has_table_privilege('jc_admin', v_candidate_oid, 'INSERT')
     OR has_table_privilege('jc_admin', v_candidate_oid, 'UPDATE')
     OR has_table_privilege('jc_admin', v_candidate_oid, 'DELETE') THEN
    RAISE EXCEPTION 'PF4 jc_admin read-only contract failed.';
  END IF;

  IF has_table_privilege('jc_app', v_event_oid, 'SELECT')
     OR has_table_privilege('jc_app', v_event_oid, 'INSERT')
     OR has_table_privilege('jc_app', v_candidate_oid, 'SELECT')
     OR has_table_privilege('jc_app', v_candidate_oid, 'INSERT')
     OR has_table_privilege('jc_auth', v_event_oid, 'SELECT')
     OR has_table_privilege('jc_auth', v_event_oid, 'INSERT')
     OR has_table_privilege('jc_auth', v_candidate_oid, 'SELECT')
     OR has_table_privilege('jc_auth', v_candidate_oid, 'INSERT') THEN
    RAISE EXCEPTION 'PF4 APP/AUTH direct access boundary failed.';
  END IF;

  IF to_regclass('public.recommendation_exposure_event') IS NULL
     OR to_regclass('public.recommendation_exposure_candidate') IS NULL
     OR to_regclass('public.recommendation_p2_experiment_exposure') IS NULL THEN
    RAISE EXCEPTION 'Protected generic/P2 exposure authorities are missing.';
  END IF;

  IF EXISTS (
    SELECT 1
    FROM pg_constraint c
    JOIN pg_class source_table ON source_table.oid = c.conrelid
    JOIN pg_class target_table ON target_table.oid = c.confrelid
    WHERE c.contype = 'f'
      AND source_table.relname IN (
        'crew_recommendation_exposure_event',
        'crew_recommendation_exposure_candidate')
      AND target_table.relname IN (
        'recommendation_exposure_event',
        'recommendation_exposure_candidate',
        'recommendation_p2_experiment_exposure')
  ) THEN
    RAISE EXCEPTION 'PF4 Crew exposure must not reuse generic/P2 exposure authority.';
  END IF;
END;
$$;

ROLLBACK;
