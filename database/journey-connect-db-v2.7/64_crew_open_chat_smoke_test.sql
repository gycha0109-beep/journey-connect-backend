-- Journey Connect DB v2.7 - PF6 Crew open chat smoke verifier
-- Target: PostgreSQL 15+

BEGIN;

DO $$
BEGIN
  IF NOT EXISTS (
    SELECT 1
    FROM information_schema.columns
    WHERE table_schema = 'public'
      AND table_name = 'crews'
      AND column_name = 'open_chat_url'
      AND data_type = 'character varying'
      AND character_maximum_length = 500
      AND is_nullable = 'YES'
  ) THEN
    RAISE EXCEPTION 'PF6 crews.open_chat_url contract is missing or invalid.';
  END IF;

  IF NOT has_column_privilege('jc_app', 'public.crews', 'open_chat_url', 'SELECT')
     OR NOT has_column_privilege('jc_app', 'public.crews', 'open_chat_url', 'UPDATE') THEN
    RAISE EXCEPTION 'PF6 APP open-chat read/update authority is missing.';
  END IF;

  IF has_table_privilege('jc_recommendation', 'public.crews', 'SELECT') THEN
    RAISE EXCEPTION 'PF6 recommendation role must not retain table-wide Crew SELECT.';
  END IF;

  IF has_column_privilege('jc_recommendation', 'public.crews', 'open_chat_url', 'SELECT')
     OR has_column_privilege('jc_recommendation', 'public.crews', 'open_chat_url', 'UPDATE') THEN
    RAISE EXCEPTION 'PF6 recommendation role must not access open_chat_url.';
  END IF;

  IF NOT has_column_privilege('jc_recommendation', 'public.crews', 'id', 'SELECT')
     OR NOT has_column_privilege('jc_recommendation', 'public.crews', 'owner_id', 'SELECT')
     OR NOT has_column_privilege('jc_recommendation', 'public.crews', 'region_id', 'SELECT')
     OR NOT has_column_privilege('jc_recommendation', 'public.crews', 'travel_date', 'SELECT')
     OR NOT has_column_privilege('jc_recommendation', 'public.crews', 'capacity', 'SELECT')
     OR NOT has_column_privilege('jc_recommendation', 'public.crews', 'recruiting', 'SELECT')
     OR NOT has_column_privilege('jc_recommendation', 'public.crews', 'created_at', 'SELECT') THEN
    RAISE EXCEPTION 'PF6 recommendation Crew fact projection is incomplete.';
  END IF;

  IF has_column_privilege('jc_auth', 'public.crews', 'open_chat_url', 'SELECT')
     OR has_column_privilege('jc_auth', 'public.crews', 'open_chat_url', 'UPDATE')
     OR has_column_privilege('jc_admin', 'public.crews', 'open_chat_url', 'UPDATE')
     OR has_column_privilege('jc_recommendation', 'public.crews', 'open_chat_url', 'INSERT') THEN
    RAISE EXCEPTION 'PF6 unrelated role boundary failed.';
  END IF;

  IF to_regclass('public.crew_recommendation_exposure_event') IS NULL
     OR to_regclass('public.crew_recommendation_exposure_candidate') IS NULL
     OR to_regclass('public.recommendation_exposure_event') IS NULL
     OR to_regclass('public.recommendation_exposure_candidate') IS NULL THEN
    RAISE EXCEPTION 'PF6 must preserve Crew and generic recommendation exposure authorities.';
  END IF;
END;
$$;

ROLLBACK;
