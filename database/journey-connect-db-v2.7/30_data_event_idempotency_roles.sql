-- Journey Connect DB v2.7 extension - DP-2 atomic idempotency and Data roles
-- Target: PostgreSQL 15 and 18
-- Prerequisite: canonical SQL 01..29.

BEGIN;

DO $$
BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'jc_data_event_writer') THEN
    CREATE ROLE jc_data_event_writer
      NOLOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOREPLICATION NOBYPASSRLS;
  END IF;
  IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'jc_data_event_reader') THEN
    CREATE ROLE jc_data_event_reader
      NOLOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOREPLICATION NOBYPASSRLS;
  END IF;
  IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'jc_data_replay_executor') THEN
    CREATE ROLE jc_data_replay_executor
      NOLOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOREPLICATION NOBYPASSRLS;
  END IF;
END;
$$;

DO $$
DECLARE
  v_unsafe_roles text;
BEGIN
  SELECT string_agg(rolname, ', ' ORDER BY rolname)
    INTO v_unsafe_roles
  FROM pg_roles
  WHERE rolname IN ('jc_data_event_writer', 'jc_data_event_reader', 'jc_data_replay_executor')
    AND (rolsuper OR rolcreaterole OR rolcreatedb OR rolcanlogin OR rolreplication OR rolbypassrls);

  IF v_unsafe_roles IS NOT NULL THEN
    RAISE EXCEPTION 'Unsafe pre-existing DP-2 role attributes: %', v_unsafe_roles;
  END IF;
END;
$$;

REVOKE jc_data_event_writer FROM jc_app, jc_auth, jc_admin, jc_security_owner, jc_recommendation,
  jc_data_event_reader, jc_data_replay_executor;
REVOKE jc_data_event_reader FROM jc_app, jc_auth, jc_admin, jc_security_owner, jc_recommendation,
  jc_data_event_writer, jc_data_replay_executor;
REVOKE jc_data_replay_executor FROM jc_app, jc_auth, jc_admin, jc_security_owner, jc_recommendation,
  jc_data_event_writer, jc_data_event_reader;
REVOKE jc_app, jc_auth, jc_admin, jc_recommendation
  FROM jc_data_event_writer, jc_data_event_reader, jc_data_replay_executor;

CREATE TABLE public.data_event_idempotency_binding_v1 (
  binding_id uuid PRIMARY KEY DEFAULT public.gen_random_uuid(),
  tenant varchar(64) NOT NULL CHECK (tenant = 'journey-connect'),
  producer_version varchar(96) NOT NULL
    CHECK (producer_version ~ '^[a-z0-9]+(?:-[a-z0-9]+)*-v[1-9][0-9]*$'),
  event_family varchar(40) NOT NULL,
  actor_ref varchar(160),
  server_session_ref varchar(160),
  scope_kind varchar(16) GENERATED ALWAYS AS (
    CASE WHEN actor_ref IS NOT NULL THEN 'actor'::varchar ELSE 'session'::varchar END
  ) STORED,
  scope_ref varchar(160) GENERATED ALWAYS AS (COALESCE(actor_ref, server_session_ref)) STORED,
  idempotency_key varchar(128) NOT NULL,
  payload_fingerprint varchar(64) NOT NULL CHECK (payload_fingerprint ~ '^[0-9a-f]{64}$'),
  event_id varchar(160) NOT NULL UNIQUE
    REFERENCES public.data_platform_event_v1(event_id) ON DELETE RESTRICT,
  retention_class varchar(40) NOT NULL DEFAULT 'idempotency_binding_30d'
    CHECK (retention_class = 'idempotency_binding_30d'),
  retention_policy_version varchar(96) NOT NULL DEFAULT 'data-retention-policy-v1'
    CHECK (retention_policy_version = 'data-retention-policy-v1'),
  expires_at timestamptz NOT NULL DEFAULT (CURRENT_TIMESTAMP + interval '30 days'),
  created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT data_event_idempotency_scope_check
    CHECK ((actor_ref IS NOT NULL AND server_session_ref IS NULL)
        OR (actor_ref IS NULL AND server_session_ref IS NOT NULL)),
  CONSTRAINT data_event_idempotency_actor_check
    CHECK (actor_ref IS NULL OR actor_ref ~ '^subject:[A-Za-z0-9][A-Za-z0-9._~-]{0,127}$'),
  CONSTRAINT data_event_idempotency_session_check
    CHECK (server_session_ref IS NULL OR server_session_ref ~ '^session:[A-Za-z0-9][A-Za-z0-9._~-]{0,127}$'),
  CONSTRAINT data_event_idempotency_key_check
    CHECK (idempotency_key ~ '^[A-Za-z0-9][A-Za-z0-9._:~-]{0,127}$'),
  CONSTRAINT data_event_idempotency_retention_check
    CHECK (expires_at = created_at + interval '30 days'),
  CONSTRAINT data_event_idempotency_scope_uq
    UNIQUE (tenant, producer_version, event_family, scope_kind, scope_ref, idempotency_key)
);

CREATE INDEX data_event_idempotency_expiry_idx
  ON public.data_event_idempotency_binding_v1 (expires_at, binding_id);

CREATE TRIGGER data_event_idempotency_binding_append_only_v1
BEFORE UPDATE OR DELETE ON public.data_event_idempotency_binding_v1
FOR EACH ROW EXECUTE FUNCTION public.prevent_data_event_append_only_mutation_v1();

CREATE OR REPLACE FUNCTION public.ingest_data_platform_event_v1(
  p_tenant varchar,
  p_event_id varchar,
  p_contract_version varchar,
  p_schema_version varchar,
  p_canonicalization_version varchar,
  p_fingerprint_version varchar,
  p_payload_fingerprint varchar,
  p_fingerprint_canonical_bytes bytea,
  p_producer_version varchar,
  p_producer_build_id varchar,
  p_event_family varchar,
  p_event_type varchar,
  p_occurred_at timestamptz,
  p_received_at timestamptz,
  p_actor_ref varchar,
  p_session_ref varchar,
  p_entity_ref varchar,
  p_request_ref varchar,
  p_correlation_ref varchar,
  p_causation_ref varchar,
  p_idempotency_key varchar,
  p_canonical_payload jsonb
)
RETURNS TABLE (
  disposition varchar,
  canonical_event_id varchar,
  idempotency_binding_id uuid,
  error_code varchar
)
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = pg_catalog, public, pg_temp
AS $$
DECLARE
  v_scope_kind varchar(16);
  v_scope_ref varchar(160);
  v_existing public.data_event_idempotency_binding_v1%ROWTYPE;
  v_binding_id uuid;
  v_fingerprint_json jsonb;
  v_fingerprint_occurred_at timestamptz;
  v_key_count integer;
  v_unknown_key_count integer;
BEGIN
  IF p_tenant IS DISTINCT FROM 'journey-connect' THEN
    RAISE EXCEPTION 'Unsupported Data event tenant.' USING ERRCODE = '22023';
  END IF;
  IF p_contract_version IS DISTINCT FROM 'platform-event-v1'
     OR p_canonicalization_version IS DISTINCT FROM 'platform-event-canonical-json-v1'
     OR p_fingerprint_version IS DISTINCT FROM 'platform-event-fingerprint-sha256-v1' THEN
    RAISE EXCEPTION 'Unsupported Data event contract, canonicalization, or fingerprint version.'
      USING ERRCODE = '22023';
  END IF;
  IF p_schema_version IS DISTINCT FROM 'user-behavior-event-v1'
     OR p_producer_version IS NULL
     OR p_producer_version !~ '^[a-z0-9]+(?:-[a-z0-9]+)*-v[1-9][0-9]*$' THEN
    RAISE EXCEPTION 'Invalid schema or producer version.' USING ERRCODE = '22023';
  END IF;
  IF p_producer_build_id IS NULL OR p_producer_build_id !~ '^git:[0-9a-f]{40}$' THEN
    RAISE EXCEPTION 'Invalid producer build ID.' USING ERRCODE = '22023';
  END IF;
  IF p_event_id IS NULL OR p_event_id !~ '^event:[A-Za-z0-9][A-Za-z0-9._~-]{0,127}$' THEN
    RAISE EXCEPTION 'Invalid canonical event ID.' USING ERRCODE = '22023';
  END IF;
  IF NOT public.data_event_type_valid_v1(p_event_family, p_event_type) THEN
    RAISE EXCEPTION 'Unsupported required event family/type.' USING ERRCODE = '22023';
  END IF;
  IF p_occurred_at IS NULL OR p_received_at IS NULL OR p_received_at < p_occurred_at THEN
    RAISE EXCEPTION 'Invalid event timestamps.' USING ERRCODE = '22023';
  END IF;
  IF p_actor_ref IS NOT NULL AND p_actor_ref !~ '^subject:[A-Za-z0-9][A-Za-z0-9._~-]{0,127}$' THEN
    RAISE EXCEPTION 'Invalid canonical actor identity namespace.' USING ERRCODE = '22023';
  END IF;
  IF p_session_ref IS NOT NULL AND p_session_ref !~ '^session:[A-Za-z0-9][A-Za-z0-9._~-]{0,127}$' THEN
    RAISE EXCEPTION 'Invalid server session reference.' USING ERRCODE = '22023';
  END IF;
  IF p_actor_ref IS NULL AND p_session_ref IS NULL THEN
    RAISE EXCEPTION 'Canonical event requires an actor or approved server session.' USING ERRCODE = '22023';
  END IF;
  IF p_entity_ref IS NOT NULL AND (
       p_entity_ref !~ '^[a-z][a-z0-9_]{0,31}:[^[:space:]]{1,128}$'
       OR split_part(p_entity_ref, ':', 1) NOT IN (
         'post', 'journey', 'place', 'crew', 'user', 'tag', 'region',
         'itinerary', 'profile', 'search_result'
       )
     ) THEN
    RAISE EXCEPTION 'Invalid or unregistered entity reference.' USING ERRCODE = '22023';
  END IF;
  IF p_request_ref IS NOT NULL
     AND p_request_ref !~ '^request:[A-Za-z0-9][A-Za-z0-9._~-]{0,127}$' THEN
    RAISE EXCEPTION 'Invalid request reference.' USING ERRCODE = '22023';
  END IF;
  IF p_correlation_ref IS NOT NULL
     AND p_correlation_ref !~ '^correlation:[A-Za-z0-9][A-Za-z0-9._~-]{0,127}$' THEN
    RAISE EXCEPTION 'Invalid correlation reference.' USING ERRCODE = '22023';
  END IF;
  IF p_causation_ref IS NOT NULL
     AND p_causation_ref !~ '^(event|command|request|operation):[A-Za-z0-9][A-Za-z0-9._~-]{0,127}$' THEN
    RAISE EXCEPTION 'Invalid causation reference.' USING ERRCODE = '22023';
  END IF;
  IF p_idempotency_key IS NULL
     OR p_idempotency_key !~ '^[A-Za-z0-9][A-Za-z0-9._:~-]{0,127}$' THEN
    RAISE EXCEPTION 'Invalid idempotency key.' USING ERRCODE = '22023';
  END IF;
  IF p_payload_fingerprint IS NULL OR p_payload_fingerprint !~ '^[0-9a-f]{64}$' THEN
    RAISE EXCEPTION 'Invalid lowercase hexadecimal fingerprint.' USING ERRCODE = '22023';
  END IF;
  IF p_fingerprint_canonical_bytes IS NULL
     OR octet_length(p_fingerprint_canonical_bytes) NOT BETWEEN 2 AND 1048576 THEN
    RAISE EXCEPTION 'Invalid canonical fingerprint bytes.' USING ERRCODE = '22023';
  END IF;
  IF p_payload_fingerprint <> encode(public.digest(p_fingerprint_canonical_bytes, 'sha256'), 'hex') THEN
    RAISE EXCEPTION 'Fingerprint does not match canonical bytes.' USING ERRCODE = '23514';
  END IF;
  IF p_canonical_payload IS NULL OR jsonb_typeof(p_canonical_payload) <> 'object'
     OR public.data_event_payload_contains_forbidden_key_v1(p_canonical_payload) THEN
    RAISE EXCEPTION 'Canonical payload is invalid or contains a forbidden field.' USING ERRCODE = '22023';
  END IF;

  BEGIN
    v_fingerprint_json := convert_from(p_fingerprint_canonical_bytes, 'UTF8')::jsonb;
  EXCEPTION WHEN OTHERS THEN
    RAISE EXCEPTION 'Fingerprint canonical bytes must be valid UTF-8 JSON.' USING ERRCODE = '22023';
  END;

  IF jsonb_typeof(v_fingerprint_json) <> 'object' THEN
    RAISE EXCEPTION 'Fingerprint canonical input must be a JSON object.' USING ERRCODE = '22023';
  END IF;
  IF convert_from(p_fingerprint_canonical_bytes, 'UTF8')
       IS DISTINCT FROM public.data_event_canonical_json_v1(v_fingerprint_json) THEN
    RAISE EXCEPTION 'Fingerprint bytes are not platform-event-canonical-json-v1 canonical bytes.'
      USING ERRCODE = '23514';
  END IF;

  SELECT count(*), count(*) FILTER (WHERE key <> ALL (ARRAY[
      'actorRef', 'canonicalizationVersion', 'causationId', 'contractVersion',
      'entityRef', 'eventFamily', 'eventType', 'occurredAt', 'payload',
      'schemaVersion', 'sessionRef'
    ]))
    INTO v_key_count, v_unknown_key_count
  FROM jsonb_object_keys(v_fingerprint_json) AS keys(key);

  IF v_key_count <> 11 OR v_unknown_key_count <> 0 THEN
    RAISE EXCEPTION 'Fingerprint canonical input has an unapproved inclusion set.' USING ERRCODE = '23514';
  END IF;

  IF COALESCE(v_fingerprint_json ->> 'occurredAt', '')
       !~ '^[0-9]{4}-[0-9]{2}-[0-9]{2}T[0-9]{2}:[0-9]{2}:[0-9]{2}(?:\.[0-9]{1,9})?Z$' THEN
    RAISE EXCEPTION 'Fingerprint occurredAt must use canonical UTC Z form.' USING ERRCODE = '22023';
  END IF;
  BEGIN
    v_fingerprint_occurred_at := (v_fingerprint_json ->> 'occurredAt')::timestamptz;
  EXCEPTION WHEN OTHERS THEN
    RAISE EXCEPTION 'Fingerprint occurredAt is invalid.' USING ERRCODE = '22023';
  END;

  IF (v_fingerprint_json ->> 'actorRef') IS DISTINCT FROM p_actor_ref
     OR (v_fingerprint_json ->> 'canonicalizationVersion') IS DISTINCT FROM p_canonicalization_version
     OR (v_fingerprint_json ->> 'causationId') IS DISTINCT FROM p_causation_ref
     OR (v_fingerprint_json ->> 'contractVersion') IS DISTINCT FROM p_contract_version
     OR (v_fingerprint_json ->> 'entityRef') IS DISTINCT FROM p_entity_ref
     OR (v_fingerprint_json ->> 'eventFamily') IS DISTINCT FROM p_event_family
     OR (v_fingerprint_json ->> 'eventType') IS DISTINCT FROM p_event_type
     OR v_fingerprint_occurred_at IS DISTINCT FROM p_occurred_at
     OR (v_fingerprint_json -> 'payload') IS DISTINCT FROM p_canonical_payload
     OR (v_fingerprint_json ->> 'schemaVersion') IS DISTINCT FROM p_schema_version
     OR (v_fingerprint_json ->> 'sessionRef') IS DISTINCT FROM p_session_ref THEN
    RAISE EXCEPTION 'Fingerprint canonical input does not match the approved event fields.'
      USING ERRCODE = '23514';
  END IF;

  IF p_actor_ref IS NOT NULL THEN
    v_scope_kind := 'actor';
    v_scope_ref := p_actor_ref;
  ELSE
    v_scope_kind := 'session';
    v_scope_ref := p_session_ref;
  END IF;

  PERFORM pg_advisory_xact_lock(hashtextextended(
    concat_ws(E'\x1f', p_tenant, p_producer_version, p_event_family,
      v_scope_kind, v_scope_ref, p_idempotency_key), 0));

  SELECT * INTO v_existing
  FROM public.data_event_idempotency_binding_v1 binding
  WHERE binding.tenant = p_tenant
    AND binding.producer_version = p_producer_version
    AND binding.event_family = p_event_family
    AND binding.scope_kind = v_scope_kind
    AND binding.scope_ref = v_scope_ref
    AND binding.idempotency_key = p_idempotency_key;

  IF FOUND THEN
    IF v_existing.payload_fingerprint = p_payload_fingerprint THEN
      INSERT INTO public.data_event_duplicate_observation_v1 (
        canonical_event_id, observed_event_id, observed_fingerprint,
        request_ref, correlation_ref, producer_build_id
      ) VALUES (
        v_existing.event_id, p_event_id, p_payload_fingerprint,
        p_request_ref, p_correlation_ref, p_producer_build_id
      );
      INSERT INTO public.data_event_ingest_attempt_v1 (
        attempted_event_id, tenant, producer_version, producer_build_id,
        event_family, actor_ref, session_ref, request_ref, correlation_ref,
        observed_fingerprint, ingest_disposition, canonical_event_id
      ) VALUES (
        p_event_id, p_tenant, p_producer_version, p_producer_build_id,
        p_event_family, p_actor_ref, p_session_ref, p_request_ref, p_correlation_ref,
        p_payload_fingerprint, 'duplicate', v_existing.event_id
      );
      RETURN QUERY SELECT 'DUPLICATE'::varchar, v_existing.event_id,
        v_existing.binding_id, NULL::varchar;
      RETURN;
    END IF;

    INSERT INTO public.data_event_conflict_observation_v1 (
      canonical_event_id, conflicting_event_id, existing_fingerprint,
      conflicting_fingerprint, request_ref, correlation_ref, producer_build_id
    ) VALUES (
      v_existing.event_id, p_event_id, v_existing.payload_fingerprint,
      p_payload_fingerprint, p_request_ref, p_correlation_ref, p_producer_build_id
    );
    INSERT INTO public.data_event_ingest_attempt_v1 (
      attempted_event_id, tenant, producer_version, producer_build_id,
      event_family, actor_ref, session_ref, request_ref, correlation_ref,
      observed_fingerprint, ingest_disposition, canonical_event_id
    ) VALUES (
      p_event_id, p_tenant, p_producer_version, p_producer_build_id,
      p_event_family, p_actor_ref, p_session_ref, p_request_ref, p_correlation_ref,
      p_payload_fingerprint, 'conflict', v_existing.event_id
    );
    RETURN QUERY SELECT 'CONFLICT'::varchar, v_existing.event_id,
      v_existing.binding_id, 'IDEMPOTENCY_CONFLICT'::varchar;
    RETURN;
  END IF;

  INSERT INTO public.data_platform_event_v1 (
    event_id, tenant, contract_version, schema_version, canonicalization_version,
    fingerprint_version, payload_fingerprint, fingerprint_canonical_bytes,
    producer_version, producer_build_id, event_family, event_type,
    occurred_at, received_at, actor_ref, session_ref, entity_ref,
    request_ref, correlation_ref, causation_ref, idempotency_key,
    canonical_payload, expires_at
  ) VALUES (
    p_event_id, p_tenant, p_contract_version, p_schema_version, p_canonicalization_version,
    p_fingerprint_version, p_payload_fingerprint, p_fingerprint_canonical_bytes,
    p_producer_version, p_producer_build_id, p_event_family, p_event_type,
    p_occurred_at, p_received_at, p_actor_ref, p_session_ref, p_entity_ref,
    p_request_ref, p_correlation_ref, p_causation_ref, p_idempotency_key,
    p_canonical_payload, p_received_at + interval '365 days'
  );

  INSERT INTO public.data_event_idempotency_binding_v1 (
    tenant, producer_version, event_family, actor_ref, server_session_ref,
    idempotency_key, payload_fingerprint, event_id
  ) VALUES (
    p_tenant, p_producer_version, p_event_family,
    CASE WHEN p_actor_ref IS NOT NULL THEN p_actor_ref ELSE NULL END,
    CASE WHEN p_actor_ref IS NULL THEN p_session_ref ELSE NULL END,
    p_idempotency_key, p_payload_fingerprint, p_event_id
  ) RETURNING binding_id INTO v_binding_id;

  INSERT INTO public.data_event_ingest_attempt_v1 (
    attempted_event_id, tenant, producer_version, producer_build_id,
    event_family, actor_ref, session_ref, request_ref, correlation_ref,
    observed_fingerprint, ingest_disposition, canonical_event_id
  ) VALUES (
    p_event_id, p_tenant, p_producer_version, p_producer_build_id,
    p_event_family, p_actor_ref, p_session_ref, p_request_ref, p_correlation_ref,
    p_payload_fingerprint, 'new', p_event_id
  );

  RETURN QUERY SELECT 'NEW'::varchar, p_event_id, v_binding_id, NULL::varchar;
END;
$$;

CREATE VIEW public.data_platform_event_reader_v1
WITH (security_barrier = true)
AS
SELECT event_id, tenant, contract_version, schema_version, canonicalization_version,
       fingerprint_version, payload_fingerprint, producer_version, producer_build_id,
       event_family, event_type, occurred_at, received_at, actor_ref, session_ref,
       entity_ref, request_ref, correlation_ref, causation_ref, canonical_payload,
       retention_class, retention_policy_version, expires_at, created_at
FROM public.data_platform_event_v1;

CREATE VIEW public.data_event_observation_reader_v1
WITH (security_barrier = true)
AS
SELECT attempt_id AS observation_id, ingest_disposition AS observation_type,
       attempted_event_id, canonical_event_id, observed_fingerprint,
       request_ref, correlation_ref, producer_build_id,
       retention_class, retention_policy_version, expires_at, created_at
FROM public.data_event_ingest_attempt_v1;

GRANT jc_security_owner TO CURRENT_USER;
GRANT USAGE, CREATE ON SCHEMA public TO jc_security_owner;
GRANT USAGE ON SCHEMA public TO jc_data_event_writer, jc_data_event_reader, jc_data_replay_executor;
GRANT EXECUTE ON FUNCTION public.digest(bytea, text) TO jc_security_owner;
GRANT EXECUTE ON FUNCTION public.gen_random_uuid() TO jc_security_owner;

ALTER TABLE public.data_platform_event_v1 OWNER TO jc_security_owner;
ALTER TABLE public.data_event_ingest_attempt_v1 OWNER TO jc_security_owner;
ALTER TABLE public.data_event_duplicate_observation_v1 OWNER TO jc_security_owner;
ALTER TABLE public.data_event_conflict_observation_v1 OWNER TO jc_security_owner;
ALTER TABLE public.data_event_idempotency_binding_v1 OWNER TO jc_security_owner;
ALTER VIEW public.data_platform_event_reader_v1 OWNER TO jc_security_owner;
ALTER VIEW public.data_event_observation_reader_v1 OWNER TO jc_security_owner;
ALTER FUNCTION public.prevent_data_event_append_only_mutation_v1() OWNER TO jc_security_owner;
ALTER FUNCTION public.data_event_type_valid_v1(varchar, varchar) OWNER TO jc_security_owner;
ALTER FUNCTION public.data_event_payload_contains_forbidden_key_v1(jsonb) OWNER TO jc_security_owner;
ALTER FUNCTION public.data_event_canonical_json_v1(jsonb) OWNER TO jc_security_owner;
ALTER FUNCTION public.ingest_data_platform_event_v1(
  varchar, varchar, varchar, varchar, varchar, varchar, varchar, bytea,
  varchar, varchar, varchar, varchar, timestamptz, timestamptz, varchar,
  varchar, varchar, varchar, varchar, varchar, varchar, jsonb
) OWNER TO jc_security_owner;

REVOKE ALL ON TABLE public.data_platform_event_v1,
  public.data_event_ingest_attempt_v1,
  public.data_event_duplicate_observation_v1,
  public.data_event_conflict_observation_v1,
  public.data_event_idempotency_binding_v1
FROM PUBLIC, jc_app, jc_auth, jc_admin, jc_recommendation,
  jc_data_event_writer, jc_data_event_reader, jc_data_replay_executor;
REVOKE ALL ON TABLE public.data_platform_event_reader_v1,
  public.data_event_observation_reader_v1
FROM PUBLIC, jc_app, jc_auth, jc_admin, jc_recommendation,
  jc_data_event_writer, jc_data_event_reader, jc_data_replay_executor;

REVOKE EXECUTE ON FUNCTION public.prevent_data_event_append_only_mutation_v1()
  FROM PUBLIC, jc_app, jc_auth, jc_admin, jc_recommendation,
    jc_data_event_writer, jc_data_event_reader, jc_data_replay_executor;
REVOKE EXECUTE ON FUNCTION public.data_event_type_valid_v1(varchar, varchar)
  FROM PUBLIC, jc_app, jc_auth, jc_admin, jc_recommendation,
    jc_data_event_writer, jc_data_event_reader, jc_data_replay_executor;
REVOKE EXECUTE ON FUNCTION public.data_event_payload_contains_forbidden_key_v1(jsonb)
  FROM PUBLIC, jc_app, jc_auth, jc_admin, jc_recommendation,
    jc_data_event_writer, jc_data_event_reader, jc_data_replay_executor;
REVOKE EXECUTE ON FUNCTION public.data_event_canonical_json_v1(jsonb)
  FROM PUBLIC, jc_app, jc_auth, jc_admin, jc_recommendation,
    jc_data_event_writer, jc_data_event_reader, jc_data_replay_executor;
REVOKE EXECUTE ON FUNCTION public.ingest_data_platform_event_v1(
  varchar, varchar, varchar, varchar, varchar, varchar, varchar, bytea,
  varchar, varchar, varchar, varchar, timestamptz, timestamptz, varchar,
  varchar, varchar, varchar, varchar, varchar, varchar, jsonb
) FROM PUBLIC, jc_app, jc_auth, jc_admin, jc_recommendation,
  jc_data_event_reader, jc_data_replay_executor;

GRANT EXECUTE ON FUNCTION public.ingest_data_platform_event_v1(
  varchar, varchar, varchar, varchar, varchar, varchar, varchar, bytea,
  varchar, varchar, varchar, varchar, timestamptz, timestamptz, varchar,
  varchar, varchar, varchar, varchar, varchar, varchar, jsonb
) TO jc_data_event_writer;
GRANT SELECT ON public.data_platform_event_reader_v1,
  public.data_event_observation_reader_v1 TO jc_data_event_reader;

REVOKE CREATE ON SCHEMA public FROM jc_security_owner;
REVOKE jc_security_owner FROM CURRENT_USER;

COMMIT;
