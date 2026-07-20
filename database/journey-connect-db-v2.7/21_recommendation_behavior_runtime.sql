-- Journey Connect DB v2.5 - P0-7 behavior runtime and atomic post interaction capture

BEGIN;

ALTER TABLE public.recommendation_behavior_event
  DROP CONSTRAINT IF EXISTS recommendation_behavior_event_entity_requirement_check;

ALTER TABLE public.recommendation_behavior_event
  ADD CONSTRAINT recommendation_behavior_event_entity_requirement_check
  CHECK (
    (event_type = 'search'
      AND entity_type IS NULL
      AND entity_key IS NULL
      AND source_entity_id IS NULL)
    OR
    (event_type <> 'search'
      AND entity_type IS NOT NULL
      AND entity_key IS NOT NULL
      AND source_entity_id IS NOT NULL)
  );

CREATE OR REPLACE FUNCTION public.validate_recommendation_behavior_event_binding()
RETURNS trigger
LANGUAGE plpgsql
SECURITY INVOKER
SET search_path = pg_catalog, public, pg_temp
AS $$
DECLARE
  v_user_id bigint;
  v_session_id varchar(128);
BEGIN
  IF NEW.run_id IS NULL THEN
    RETURN NEW;
  END IF;

  IF NEW.entity_type IS NULL OR NEW.source_entity_id IS NULL THEN
    RAISE EXCEPTION 'Run-bound recommendation behavior requires an entity.'
      USING ERRCODE = '23514';
  END IF;

  SELECT user_id, session_id
    INTO v_user_id, v_session_id
  FROM public.recommendation_run
  WHERE run_id = NEW.run_id;

  IF NOT FOUND THEN
    RAISE EXCEPTION 'Recommendation run % does not exist.', NEW.run_id
      USING ERRCODE = '23503';
  END IF;

  IF v_user_id IS DISTINCT FROM NEW.user_id
     OR v_session_id IS DISTINCT FROM NEW.session_id THEN
    RAISE EXCEPTION 'Recommendation behavior event user/session binding does not match run %.', NEW.run_id
      USING ERRCODE = '23514';
  END IF;

  IF NOT EXISTS (
    SELECT 1
    FROM public.recommendation_run_candidate candidate
    WHERE candidate.run_id = NEW.run_id
      AND candidate.entity_type = NEW.entity_type
      AND candidate.source_entity_id = NEW.source_entity_id
  ) THEN
    RAISE EXCEPTION 'Recommendation behavior entity is not a ranked candidate of run %.', NEW.run_id
      USING ERRCODE = '23514';
  END IF;

  RETURN NEW;
END;
$$;

CREATE OR REPLACE FUNCTION public.apply_recommendation_post_interaction(
  p_expected_user_id bigint,
  p_post_id bigint,
  p_action varchar,
  p_event_id varchar,
  p_idempotency_key varchar,
  p_schema_version varchar,
  p_payload_fingerprint varchar,
  p_canonical_payload bytea,
  p_session_id varchar,
  p_run_id varchar,
  p_occurred_at timestamptz,
  p_metadata jsonb
)
RETURNS varchar
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = pg_catalog, public, pg_temp
AS $$
DECLARE
  v_user_id bigint;
  v_changed integer := 0;
  v_existing public.recommendation_behavior_event%ROWTYPE;
BEGIN
  v_user_id := public.require_active_user();
  IF v_user_id IS DISTINCT FROM p_expected_user_id THEN
    RAISE EXCEPTION 'Authenticated request user does not match interaction user.'
      USING ERRCODE = '42501';
  END IF;

  IF p_post_id IS NULL OR p_post_id <= 0 THEN
    RAISE EXCEPTION 'Interaction post ID must be positive.' USING ERRCODE = '22023';
  END IF;
  IF p_action NOT IN ('like', 'unlike', 'save', 'unsave') THEN
    RAISE EXCEPTION 'Unsupported post interaction action %.', p_action USING ERRCODE = '22023';
  END IF;
  IF p_event_id IS NULL OR p_event_id !~ '^[A-Za-z0-9][A-Za-z0-9._:-]{0,127}$' THEN
    RAISE EXCEPTION 'Invalid recommendation behavior event ID.' USING ERRCODE = '22023';
  END IF;
  IF p_idempotency_key IS NULL OR char_length(btrim(p_idempotency_key)) NOT BETWEEN 1 AND 160 THEN
    RAISE EXCEPTION 'Invalid recommendation behavior idempotency key.' USING ERRCODE = '22023';
  END IF;
  IF p_schema_version IS NULL OR char_length(btrim(p_schema_version)) NOT BETWEEN 1 AND 64 THEN
    RAISE EXCEPTION 'Invalid recommendation behavior schema version.' USING ERRCODE = '22023';
  END IF;
  IF p_payload_fingerprint IS NULL OR p_payload_fingerprint !~ '^[0-9a-f]{64}$' THEN
    RAISE EXCEPTION 'Invalid recommendation behavior payload fingerprint.' USING ERRCODE = '22023';
  END IF;
  IF p_canonical_payload IS NULL OR octet_length(p_canonical_payload) > 262144 THEN
    RAISE EXCEPTION 'Invalid recommendation behavior canonical payload.' USING ERRCODE = '22023';
  END IF;
  IF p_payload_fingerprint <> public.recommendation_sha256_hex(p_canonical_payload) THEN
    RAISE EXCEPTION 'Recommendation behavior payload fingerprint mismatch.' USING ERRCODE = '23514';
  END IF;
  IF p_session_id IS NULL OR char_length(btrim(p_session_id)) NOT BETWEEN 1 AND 128 THEN
    RAISE EXCEPTION 'Invalid recommendation behavior session ID.' USING ERRCODE = '22023';
  END IF;
  IF p_run_id IS NOT NULL AND p_run_id !~ '^[A-Za-z0-9][A-Za-z0-9._:-]{0,127}$' THEN
    RAISE EXCEPTION 'Invalid recommendation run ID.' USING ERRCODE = '22023';
  END IF;
  IF p_occurred_at IS NULL
     OR p_occurred_at > clock_timestamp() + interval '5 minutes'
     OR p_occurred_at < clock_timestamp() - interval '30 days' THEN
    RAISE EXCEPTION 'Recommendation behavior occurrence time is outside the accepted range.'
      USING ERRCODE = '22023';
  END IF;
  IF p_metadata IS NULL OR jsonb_typeof(p_metadata) <> 'object' THEN
    RAISE EXCEPTION 'Recommendation behavior metadata must be a JSON object.' USING ERRCODE = '22023';
  END IF;

  -- Serialize both unique identities before reading or changing domain state.
  PERFORM pg_advisory_xact_lock(
    hashtextextended('recommendation_behavior_event:event:' || p_event_id, 0)
  );
  PERFORM pg_advisory_xact_lock(
    hashtextextended('recommendation_behavior_event:idempotency:' || p_idempotency_key, 0)
  );

  SELECT * INTO v_existing
  FROM public.recommendation_behavior_event
  WHERE event_id = p_event_id OR idempotency_key = p_idempotency_key
  ORDER BY CASE WHEN event_id = p_event_id THEN 0 ELSE 1 END
  LIMIT 1;

  IF FOUND THEN
    IF v_existing.event_id = p_event_id
       AND v_existing.idempotency_key = p_idempotency_key
       AND v_existing.schema_version = p_schema_version
       AND v_existing.payload_fingerprint = p_payload_fingerprint
       AND v_existing.canonical_payload = p_canonical_payload
       AND v_existing.user_id IS NOT DISTINCT FROM v_user_id
       AND v_existing.session_id = p_session_id
       AND v_existing.run_id IS NOT DISTINCT FROM p_run_id
       AND v_existing.event_type = p_action
       AND v_existing.entity_type = 'post'
       AND v_existing.source_entity_id = p_post_id
       AND date_trunc('microseconds', v_existing.occurred_at)
           = date_trunc('microseconds', p_occurred_at)
       AND v_existing.metadata = p_metadata THEN
      RETURN 'duplicate';
    END IF;
    RETURN 'idempotency_conflict';
  END IF;

  IF p_action IN ('like', 'save')
     AND NOT public.can_user_view_post(v_user_id, p_post_id) THEN
    RAISE EXCEPTION 'Post % is not visible to the request user.', p_post_id
      USING ERRCODE = '42501';
  END IF;

  BEGIN
  IF p_action = 'like' THEN
    INSERT INTO public.post_likes (post_id, user_id)
    VALUES (p_post_id, v_user_id)
    ON CONFLICT DO NOTHING;
    GET DIAGNOSTICS v_changed = ROW_COUNT;
  ELSIF p_action = 'unlike' THEN
    DELETE FROM public.post_likes
    WHERE post_id = p_post_id AND user_id = v_user_id;
    GET DIAGNOSTICS v_changed = ROW_COUNT;
  ELSIF p_action = 'save' THEN
    INSERT INTO public.bookmarks (post_id, user_id)
    VALUES (p_post_id, v_user_id)
    ON CONFLICT DO NOTHING;
    GET DIAGNOSTICS v_changed = ROW_COUNT;
  ELSE
    DELETE FROM public.bookmarks
    WHERE post_id = p_post_id AND user_id = v_user_id;
    GET DIAGNOSTICS v_changed = ROW_COUNT;
  END IF;

  IF v_changed = 0 THEN
    SELECT * INTO v_existing
    FROM public.recommendation_behavior_event
    WHERE event_id = p_event_id OR idempotency_key = p_idempotency_key
    ORDER BY CASE WHEN event_id = p_event_id THEN 0 ELSE 1 END
    LIMIT 1;

    IF FOUND THEN
      IF v_existing.event_id = p_event_id
         AND v_existing.idempotency_key = p_idempotency_key
         AND v_existing.schema_version = p_schema_version
         AND v_existing.payload_fingerprint = p_payload_fingerprint
         AND v_existing.canonical_payload = p_canonical_payload
         AND v_existing.user_id IS NOT DISTINCT FROM v_user_id
         AND v_existing.session_id = p_session_id
         AND v_existing.run_id IS NOT DISTINCT FROM p_run_id
         AND v_existing.event_type = p_action
         AND v_existing.entity_type = 'post'
         AND v_existing.source_entity_id = p_post_id
         AND date_trunc('microseconds', v_existing.occurred_at)
             = date_trunc('microseconds', p_occurred_at)
         AND v_existing.metadata = p_metadata THEN
        RETURN 'duplicate';
      END IF;
      RETURN 'idempotency_conflict';
    END IF;
    RETURN 'no_change';
  END IF;

  INSERT INTO public.recommendation_behavior_event (
      event_id, idempotency_key, schema_version, payload_fingerprint,
      canonical_payload, payload_size_bytes, user_id, session_id, run_id,
      event_type, entity_type, entity_key, source_entity_id, occurred_at, metadata
    ) VALUES (
      p_event_id, p_idempotency_key, p_schema_version, p_payload_fingerprint,
      p_canonical_payload, octet_length(p_canonical_payload), v_user_id, p_session_id, p_run_id,
      p_action, 'post', 'post:' || p_post_id::text, p_post_id,
      date_trunc('microseconds', p_occurred_at), p_metadata
    );
  EXCEPTION WHEN unique_violation THEN
    SELECT * INTO v_existing
    FROM public.recommendation_behavior_event
    WHERE event_id = p_event_id OR idempotency_key = p_idempotency_key
    ORDER BY CASE WHEN event_id = p_event_id THEN 0 ELSE 1 END
    LIMIT 1;

    IF FOUND
       AND v_existing.event_id = p_event_id
       AND v_existing.idempotency_key = p_idempotency_key
       AND v_existing.schema_version = p_schema_version
       AND v_existing.payload_fingerprint = p_payload_fingerprint
       AND v_existing.canonical_payload = p_canonical_payload
       AND v_existing.user_id IS NOT DISTINCT FROM v_user_id
       AND v_existing.session_id = p_session_id
       AND v_existing.run_id IS NOT DISTINCT FROM p_run_id
       AND v_existing.event_type = p_action
       AND v_existing.entity_type = 'post'
       AND v_existing.source_entity_id = p_post_id
       AND date_trunc('microseconds', v_existing.occurred_at)
           = date_trunc('microseconds', p_occurred_at)
       AND v_existing.metadata = p_metadata THEN
      RETURN 'duplicate';
    END IF;
    RETURN 'idempotency_conflict';
  END;

  RETURN 'applied';
END;
$$;

ALTER FUNCTION public.validate_recommendation_behavior_event_binding()
  OWNER TO jc_security_owner;
ALTER FUNCTION public.apply_recommendation_post_interaction(
  bigint, bigint, varchar, varchar, varchar, varchar, varchar,
  bytea, varchar, varchar, timestamptz, jsonb
) OWNER TO jc_security_owner;

GRANT SELECT, INSERT, DELETE ON public.post_likes, public.bookmarks TO jc_security_owner;
GRANT SELECT ON public.posts, public.follows TO jc_security_owner;
GRANT SELECT ON public.recommendation_run, public.recommendation_run_candidate,
  public.recommendation_behavior_event TO jc_security_owner;
GRANT INSERT ON public.recommendation_behavior_event TO jc_security_owner;
GRANT EXECUTE ON FUNCTION public.recommendation_sha256_hex(bytea) TO jc_security_owner;
GRANT EXECUTE ON FUNCTION public.require_active_user() TO jc_security_owner;
GRANT EXECUTE ON FUNCTION public.can_user_view_post(bigint, bigint) TO jc_security_owner;

REVOKE EXECUTE ON FUNCTION public.apply_recommendation_post_interaction(
  bigint, bigint, varchar, varchar, varchar, varchar, varchar,
  bytea, varchar, varchar, timestamptz, jsonb
) FROM PUBLIC, jc_auth, jc_admin, jc_recommendation, jc_security_owner;
GRANT EXECUTE ON FUNCTION public.apply_recommendation_post_interaction(
  bigint, bigint, varchar, varchar, varchar, varchar, varchar,
  bytea, varchar, varchar, timestamptz, jsonb
) TO jc_app;

COMMIT;
