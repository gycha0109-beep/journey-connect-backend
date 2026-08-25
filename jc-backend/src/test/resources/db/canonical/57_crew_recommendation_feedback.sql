-- Journey Connect DB v2.7 - PF3 crew recommendation feedback bridge
-- Target: PostgreSQL 15+
-- Prerequisite: recommendation behavior storage and backend runtime crew authorization

BEGIN;

DO $$
DECLARE
  v_missing text;
BEGIN
  SELECT string_agg(required_object, ', ' ORDER BY required_object)
    INTO v_missing
  FROM (
    VALUES
      ('public.recommendation_behavior_event', to_regclass('public.recommendation_behavior_event')),
      ('public.crews', to_regclass('public.crews')),
      ('public.crew_members', to_regclass('public.crew_members'))
  ) AS required(required_object, object_oid)
  WHERE object_oid IS NULL;

  IF v_missing IS NOT NULL THEN
    RAISE EXCEPTION 'PF3 prerequisite objects are missing: %', v_missing
      USING ERRCODE = '42P01';
  END IF;

  IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'jc_app')
     OR NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'jc_recommendation') THEN
    RAISE EXCEPTION 'PF3 prerequisite runtime roles are missing.';
  END IF;

  IF NOT has_table_privilege('jc_recommendation', 'public.crews', 'SELECT')
     OR NOT has_table_privilege('jc_recommendation', 'public.crew_members', 'SELECT') THEN
    RAISE EXCEPTION 'PF3 requires the baseline jc_recommendation crew read authority from 11_backend_runtime_security_roles.sql.';
  END IF;
END;
$$;

CREATE OR REPLACE FUNCTION public.record_crew_join_recommendation_feedback(
  p_user_id bigint,
  p_crew_id bigint,
  p_occurred_at timestamptz,
  p_canonical_payload bytea
)
RETURNS text
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = pg_catalog, public, pg_temp
AS $$
DECLARE
  v_event_id varchar(128);
  v_session_id varchar(128);
  v_schema_version constant varchar(64) := 'crew-recommendation-feedback-v1';
  v_policy_version constant varchar(64) := 'crew-join-positive-only-v1';
  v_metadata jsonb;
  v_payload jsonb;
  v_expected jsonb;
  v_occurred_text text;
  v_fingerprint varchar(64);
  v_existing public.recommendation_behavior_event%ROWTYPE;
BEGIN
  IF p_user_id IS NULL OR p_user_id <= 0 OR p_crew_id IS NULL OR p_crew_id <= 0 THEN
    RAISE EXCEPTION 'Crew recommendation feedback IDs must be positive.'
      USING ERRCODE = '22023';
  END IF;
  IF p_occurred_at IS NULL OR p_canonical_payload IS NULL OR octet_length(p_canonical_payload) = 0 THEN
    RAISE EXCEPTION 'Crew recommendation feedback timestamp and payload are required.'
      USING ERRCODE = '22023';
  END IF;

  IF NOT EXISTS (
    SELECT 1
    FROM public.crew_members cm
    WHERE cm.crew_id = p_crew_id
      AND cm.user_id = p_user_id
      AND cm.status = 'APPROVED'
  ) THEN
    RAISE EXCEPTION 'Crew recommendation feedback requires an APPROVED membership.'
      USING ERRCODE = '23514';
  END IF;

  v_event_id := ('crew-join-v1:' || p_user_id::text || ':' || p_crew_id::text)::varchar(128);
  v_session_id := ('crew-feedback-v1:' || p_user_id::text)::varchar(128);
  v_metadata := jsonb_build_object(
      'feedbackPolicyVersion', v_policy_version,
      'signal', 'approved_join');

  v_payload := convert_from(p_canonical_payload, 'UTF8')::jsonb;
  IF jsonb_typeof(v_payload) <> 'object' THEN
    RAISE EXCEPTION 'Crew recommendation canonical payload must be a JSON object.'
      USING ERRCODE = '22023';
  END IF;

  v_occurred_text := v_payload ->> 'occurredAt';
  IF v_occurred_text IS NULL OR v_occurred_text::timestamptz IS DISTINCT FROM p_occurred_at THEN
    RAISE EXCEPTION 'Crew recommendation canonical payload timestamp mismatch.'
      USING ERRCODE = '23514';
  END IF;

  v_expected := jsonb_build_object(
      'eventId', v_event_id,
      'idempotencyKey', v_event_id,
      'schemaVersion', v_schema_version,
      'userId', p_user_id,
      'sessionId', v_session_id,
      'eventType', 'crew_join',
      'entityType', 'crew',
      'entityKey', 'crew:' || p_crew_id::text,
      'sourceEntityId', p_crew_id,
      'occurredAt', v_occurred_text,
      'metadata', v_metadata);
  IF v_payload IS DISTINCT FROM v_expected THEN
    RAISE EXCEPTION 'Crew recommendation canonical payload contract mismatch.'
      USING ERRCODE = '23514';
  END IF;

  -- Match RecommendationBehaviorStore lock namespaces so this cross-role command
  -- serializes with the existing recommendation writer for the same identities.
  PERFORM pg_advisory_xact_lock(hashtextextended(
      'recommendation_behavior_event:event:' || v_event_id, 0));
  PERFORM pg_advisory_xact_lock(hashtextextended(
      'recommendation_behavior_event:idempotency:' || v_event_id, 0));

  v_fingerprint := public.recommendation_sha256_hex(p_canonical_payload);

  SELECT b.*
    INTO v_existing
  FROM public.recommendation_behavior_event b
  WHERE b.event_id = v_event_id OR b.idempotency_key = v_event_id
  ORDER BY CASE WHEN b.event_id = v_event_id THEN 0 ELSE 1 END
  LIMIT 1;

  IF FOUND THEN
    -- The v1 feedback policy intentionally contributes at most one positive crew signal
    -- per user+crew pair. A later cancel/rejoin/re-approval is therefore a duplicate,
    -- not an idempotency conflict, as long as the stored domain identity is the same.
    IF v_existing.event_id IS DISTINCT FROM v_event_id
       OR v_existing.idempotency_key IS DISTINCT FROM v_event_id
       OR v_existing.schema_version IS DISTINCT FROM v_schema_version
       OR v_existing.user_id IS DISTINCT FROM p_user_id
       OR v_existing.session_id IS DISTINCT FROM v_session_id
       OR v_existing.run_id IS NOT NULL
       OR v_existing.event_type IS DISTINCT FROM 'crew_join'
       OR v_existing.entity_type IS DISTINCT FROM 'crew'
       OR v_existing.entity_key IS DISTINCT FROM ('crew:' || p_crew_id::text)
       OR v_existing.source_entity_id IS DISTINCT FROM p_crew_id
       OR v_existing.metadata IS DISTINCT FROM v_metadata THEN
      RAISE EXCEPTION 'Crew recommendation feedback identity conflict for %.', v_event_id
        USING ERRCODE = '23505';
    END IF;
    RETURN 'duplicate';
  END IF;

  INSERT INTO public.recommendation_behavior_event (
      event_id,
      idempotency_key,
      schema_version,
      payload_fingerprint,
      canonical_payload,
      payload_size_bytes,
      user_id,
      session_id,
      run_id,
      event_type,
      entity_type,
      entity_key,
      source_entity_id,
      occurred_at,
      metadata)
  VALUES (
      v_event_id,
      v_event_id,
      v_schema_version,
      v_fingerprint,
      p_canonical_payload,
      octet_length(p_canonical_payload),
      p_user_id,
      v_session_id,
      NULL,
      'crew_join',
      'crew',
      'crew:' || p_crew_id::text,
      p_crew_id,
      p_occurred_at,
      v_metadata);

  RETURN 'stored';
END;
$$;

-- The function executes with existing recommendation storage authority while callers
-- remain inside the APP transaction. jc_app receives only this command entry point,
-- never direct recommendation behavior table privileges.
GRANT jc_recommendation TO CURRENT_USER;
GRANT CREATE ON SCHEMA public TO jc_recommendation;
ALTER FUNCTION public.record_crew_join_recommendation_feedback(bigint, bigint, timestamptz, bytea)
OWNER TO jc_recommendation;
REVOKE CREATE ON SCHEMA public FROM jc_recommendation;
REVOKE jc_recommendation FROM CURRENT_USER;

REVOKE ALL ON FUNCTION public.record_crew_join_recommendation_feedback(bigint, bigint, timestamptz, bytea)
FROM PUBLIC, jc_app, jc_auth, jc_admin, jc_security_owner;
GRANT EXECUTE ON FUNCTION public.record_crew_join_recommendation_feedback(bigint, bigint, timestamptz, bytea)
TO jc_app;

-- Reassert the direct-table boundary after installing the command bridge.
REVOKE SELECT, INSERT, UPDATE, DELETE, TRUNCATE
ON public.recommendation_behavior_event
FROM jc_app, jc_auth;
REVOKE INSERT, UPDATE, DELETE, TRUNCATE
ON public.recommendation_behavior_event
FROM jc_admin;

COMMIT;
