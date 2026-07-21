-- Journey Connect DB v2.7 extension - DP-2 canonical Data event store
-- Target: PostgreSQL 15 and 18
-- Prerequisite: canonical SQL 01..28.
-- This migration creates Data-owned append-only event and observation evidence only.

BEGIN;

DO $$
DECLARE
  v_missing text;
BEGIN
  SELECT string_agg(required_object, ', ' ORDER BY required_object)
    INTO v_missing
  FROM (
    VALUES
      ('public.digest(bytea,text)', to_regprocedure('public.digest(bytea,text)')),
      ('public.gen_random_uuid()', to_regprocedure('public.gen_random_uuid()'))
  ) AS required(required_object, object_oid)
  WHERE object_oid IS NULL;

  IF v_missing IS NOT NULL THEN
    RAISE EXCEPTION 'DP-2 prerequisite objects are missing: %', v_missing
      USING ERRCODE = '42883';
  END IF;
END;
$$;

CREATE OR REPLACE FUNCTION public.prevent_data_event_append_only_mutation_v1()
RETURNS trigger
LANGUAGE plpgsql
SECURITY INVOKER
SET search_path = pg_catalog, pg_temp
AS $$
BEGIN
  RAISE EXCEPTION 'Data event evidence table %.% is append-only.', TG_TABLE_SCHEMA, TG_TABLE_NAME
    USING ERRCODE = '55000';
END;
$$;

CREATE OR REPLACE FUNCTION public.data_event_type_valid_v1(
  p_event_family varchar,
  p_event_type varchar
)
RETURNS boolean
LANGUAGE sql
IMMUTABLE
STRICT
SECURITY INVOKER
SET search_path = pg_catalog, pg_temp
AS $$
  SELECT p_event_family = 'user_behavior'
     AND p_event_type IN (
       'post_impression', 'post_view', 'post_dwell', 'post_like', 'post_unlike',
       'post_bookmark', 'post_unbookmark', 'post_share', 'post_hide', 'post_report',
       'search_submit', 'search_result_impression', 'search_result_click',
       'recommendation_impression', 'recommendation_click', 'profile_view',
       'follow', 'unfollow', 'tag_click', 'crew_join', 'crew_leave'
     );
$$;

CREATE OR REPLACE FUNCTION public.data_event_payload_contains_forbidden_key_v1(p_value jsonb)
RETURNS boolean
LANGUAGE plpgsql
IMMUTABLE
STRICT
SECURITY INVOKER
SET search_path = pg_catalog, pg_temp
AS $$
DECLARE
  v_key text;
  v_child jsonb;
  v_normalized_key text;
BEGIN
  IF jsonb_typeof(p_value) = 'object' THEN
    FOR v_key, v_child IN SELECT key, value FROM jsonb_each(p_value) LOOP
      v_normalized_key := regexp_replace(lower(v_key), '[^a-z0-9]', '', 'g');
      IF v_normalized_key ~ '(accesstoken|refreshtoken|authorization|apikey|secret|password|credential|rawidentity|rawaccount|emailaddress|phonenumber)' THEN
        RETURN true;
      END IF;
      IF public.data_event_payload_contains_forbidden_key_v1(v_child) THEN
        RETURN true;
      END IF;
    END LOOP;
  ELSIF jsonb_typeof(p_value) = 'array' THEN
    FOR v_child IN SELECT value FROM jsonb_array_elements(p_value) LOOP
      IF public.data_event_payload_contains_forbidden_key_v1(v_child) THEN
        RETURN true;
      END IF;
    END LOOP;
  END IF;
  RETURN false;
END;
$$;

CREATE OR REPLACE FUNCTION public.data_event_canonical_json_v1(p_value jsonb)
RETURNS text
LANGUAGE plpgsql
IMMUTABLE
STRICT
SECURITY INVOKER
SET search_path = pg_catalog, pg_temp
AS $$
DECLARE
  v_type text;
  v_result text;
  v_number numeric;
BEGIN
  v_type := jsonb_typeof(p_value);

  IF v_type = 'object' THEN
    SELECT '{' || COALESCE(string_agg(
      to_json(entry.key)::text || ':' || public.data_event_canonical_json_v1(entry.value),
      ',' ORDER BY entry.key COLLATE "C"), '') || '}'
      INTO v_result
    FROM jsonb_each(p_value) AS entry(key, value);
    RETURN v_result;
  ELSIF v_type = 'array' THEN
    SELECT '[' || COALESCE(string_agg(
      public.data_event_canonical_json_v1(element.value),
      ',' ORDER BY element.ordinality), '') || ']'
      INTO v_result
    FROM jsonb_array_elements(p_value) WITH ORDINALITY AS element(value, ordinality);
    RETURN v_result;
  ELSIF v_type = 'string' THEN
    RETURN to_json(p_value #>> '{}')::text;
  ELSIF v_type = 'number' THEN
    v_number := (p_value #>> '{}')::numeric;
    IF v_number = 0 THEN
      RETURN '0';
    END IF;
    RETURN trim_scale(v_number)::text;
  ELSIF v_type = 'boolean' THEN
    RETURN p_value::text;
  ELSIF v_type = 'null' THEN
    RETURN 'null';
  END IF;

  RAISE EXCEPTION 'Unsupported canonical JSON value type: %', v_type
    USING ERRCODE = '22023';
END;
$$;

CREATE TABLE public.data_platform_event_v1 (
  event_id varchar(160) PRIMARY KEY,
  tenant varchar(64) NOT NULL DEFAULT 'journey-connect'
    CHECK (tenant = 'journey-connect'),
  contract_version varchar(96) NOT NULL
    CHECK (contract_version = 'platform-event-v1'),
  schema_version varchar(96) NOT NULL
    CHECK (schema_version = 'user-behavior-event-v1'),
  canonicalization_version varchar(96) NOT NULL
    CHECK (canonicalization_version = 'platform-event-canonical-json-v1'),
  fingerprint_version varchar(96) NOT NULL
    CHECK (fingerprint_version = 'platform-event-fingerprint-sha256-v1'),
  payload_fingerprint varchar(64) NOT NULL
    CHECK (payload_fingerprint ~ '^[0-9a-f]{64}$'),
  fingerprint_canonical_bytes bytea NOT NULL
    CHECK (octet_length(fingerprint_canonical_bytes) BETWEEN 2 AND 1048576),
  producer_version varchar(96) NOT NULL
    CHECK (producer_version ~ '^[a-z0-9]+(?:-[a-z0-9]+)*-v[1-9][0-9]*$'),
  producer_build_id varchar(44) NOT NULL
    CHECK (producer_build_id ~ '^git:[0-9a-f]{40}$'),
  event_family varchar(40) NOT NULL
    CHECK (event_family IN (
      'user_behavior', 'content_lifecycle', 'ai_analysis', 'search_runtime',
      'recommendation_runtime', 'experiment_runtime', 'admin_audit',
      'trip_planner_runtime', 'data_quality'
    )),
  event_type varchar(80) NOT NULL
    CHECK (event_type ~ '^[a-z][a-z0-9]*(?:_[a-z0-9]+)*$'),
  occurred_at timestamptz NOT NULL,
  received_at timestamptz NOT NULL,
  actor_ref varchar(160),
  session_ref varchar(160),
  entity_ref varchar(160),
  request_ref varchar(160),
  correlation_ref varchar(160),
  causation_ref varchar(160),
  idempotency_key varchar(128) NOT NULL,
  canonical_payload jsonb NOT NULL,
  retention_class varchar(40) NOT NULL DEFAULT 'canonical_event_365d'
    CHECK (retention_class = 'canonical_event_365d'),
  retention_policy_version varchar(96) NOT NULL DEFAULT 'data-retention-policy-v1'
    CHECK (retention_policy_version = 'data-retention-policy-v1'),
  expires_at timestamptz NOT NULL,
  created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT data_platform_event_id_format_check
    CHECK (event_id ~ '^event:[A-Za-z0-9][A-Za-z0-9._~-]{0,127}$'),
  CONSTRAINT data_platform_event_family_type_check
    CHECK (public.data_event_type_valid_v1(event_family, event_type)),
  CONSTRAINT data_platform_event_time_order_check
    CHECK (received_at >= occurred_at),
  CONSTRAINT data_platform_event_actor_check
    CHECK (actor_ref IS NULL OR actor_ref ~ '^subject:[A-Za-z0-9][A-Za-z0-9._~-]{0,127}$'),
  CONSTRAINT data_platform_event_session_check
    CHECK (session_ref IS NULL OR session_ref ~ '^session:[A-Za-z0-9][A-Za-z0-9._~-]{0,127}$'),
  CONSTRAINT data_platform_event_scope_check
    CHECK (actor_ref IS NOT NULL OR session_ref IS NOT NULL),
  CONSTRAINT data_platform_event_entity_check
    CHECK (entity_ref IS NULL OR (
      entity_ref ~ '^[a-z][a-z0-9_]{0,31}:[^[:space:]]{1,128}$'
      AND split_part(entity_ref, ':', 1) IN (
        'post', 'journey', 'place', 'crew', 'user', 'tag', 'region',
        'itinerary', 'profile', 'search_result'
      )
    )),
  CONSTRAINT data_platform_event_request_check
    CHECK (request_ref IS NULL OR request_ref ~ '^request:[A-Za-z0-9][A-Za-z0-9._~-]{0,127}$'),
  CONSTRAINT data_platform_event_correlation_check
    CHECK (correlation_ref IS NULL OR correlation_ref ~ '^correlation:[A-Za-z0-9][A-Za-z0-9._~-]{0,127}$'),
  CONSTRAINT data_platform_event_causation_check
    CHECK (causation_ref IS NULL OR causation_ref ~ '^(event|command|request|operation):[A-Za-z0-9][A-Za-z0-9._~-]{0,127}$'),
  CONSTRAINT data_platform_event_idempotency_key_check
    CHECK (idempotency_key ~ '^[A-Za-z0-9][A-Za-z0-9._:~-]{0,127}$'),
  CONSTRAINT data_platform_event_payload_shape_check
    CHECK (jsonb_typeof(canonical_payload) = 'object'),
  CONSTRAINT data_platform_event_payload_safety_check
    CHECK (NOT public.data_event_payload_contains_forbidden_key_v1(canonical_payload)),
  CONSTRAINT data_platform_event_fingerprint_check
    CHECK (payload_fingerprint = encode(public.digest(fingerprint_canonical_bytes, 'sha256'), 'hex')),
  CONSTRAINT data_platform_event_retention_check
    CHECK (expires_at = received_at + interval '365 days')
);

COMMENT ON TABLE public.data_platform_event_v1 IS
  'Data-owned canonical platform-event-v1 evidence. Append-only; no production ingestion is activated by DP-2.';
COMMENT ON COLUMN public.data_platform_event_v1.fingerprint_canonical_bytes IS
  'Exact UTF-8 platform-event-canonical-json-v1 bytes for the SC-approved fingerprint inclusion set.';

CREATE INDEX data_platform_event_occurred_idx
  ON public.data_platform_event_v1 (occurred_at DESC, event_id);
CREATE INDEX data_platform_event_family_type_idx
  ON public.data_platform_event_v1 (event_family, event_type, occurred_at DESC);
CREATE INDEX data_platform_event_actor_idx
  ON public.data_platform_event_v1 (actor_ref, occurred_at DESC)
  WHERE actor_ref IS NOT NULL;
CREATE INDEX data_platform_event_session_idx
  ON public.data_platform_event_v1 (session_ref, occurred_at DESC)
  WHERE session_ref IS NOT NULL;
CREATE INDEX data_platform_event_expiry_idx
  ON public.data_platform_event_v1 (expires_at, event_id);

CREATE TABLE public.data_event_ingest_attempt_v1 (
  attempt_id uuid PRIMARY KEY DEFAULT public.gen_random_uuid(),
  attempted_event_id varchar(160) NOT NULL,
  tenant varchar(64) NOT NULL CHECK (tenant = 'journey-connect'),
  producer_version varchar(96) NOT NULL,
  producer_build_id varchar(44) NOT NULL CHECK (producer_build_id ~ '^git:[0-9a-f]{40}$'),
  event_family varchar(40) NOT NULL,
  actor_ref varchar(160),
  session_ref varchar(160),
  request_ref varchar(160),
  correlation_ref varchar(160),
  observed_fingerprint varchar(64) NOT NULL CHECK (observed_fingerprint ~ '^[0-9a-f]{64}$'),
  ingest_disposition varchar(16) NOT NULL CHECK (ingest_disposition IN ('new', 'duplicate', 'conflict')),
  canonical_event_id varchar(160) NOT NULL REFERENCES public.data_platform_event_v1(event_id) ON DELETE RESTRICT,
  retention_class varchar(40) NOT NULL DEFAULT 'ingest_evidence_90d'
    CHECK (retention_class = 'ingest_evidence_90d'),
  retention_policy_version varchar(96) NOT NULL DEFAULT 'data-retention-policy-v1'
    CHECK (retention_policy_version = 'data-retention-policy-v1'),
  expires_at timestamptz NOT NULL DEFAULT (CURRENT_TIMESTAMP + interval '90 days'),
  created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT data_event_ingest_attempt_event_id_check
    CHECK (attempted_event_id ~ '^event:[A-Za-z0-9][A-Za-z0-9._~-]{0,127}$'),
  CONSTRAINT data_event_ingest_attempt_producer_check
    CHECK (producer_version ~ '^[a-z0-9]+(?:-[a-z0-9]+)*-v[1-9][0-9]*$'),
  CONSTRAINT data_event_ingest_attempt_family_check
    CHECK (event_family = 'user_behavior'),
  CONSTRAINT data_event_ingest_attempt_scope_check CHECK (actor_ref IS NOT NULL OR session_ref IS NOT NULL),
  CONSTRAINT data_event_ingest_attempt_actor_check
    CHECK (actor_ref IS NULL OR actor_ref ~ '^subject:[A-Za-z0-9][A-Za-z0-9._~-]{0,127}$'),
  CONSTRAINT data_event_ingest_attempt_session_check
    CHECK (session_ref IS NULL OR session_ref ~ '^session:[A-Za-z0-9][A-Za-z0-9._~-]{0,127}$'),
  CONSTRAINT data_event_ingest_attempt_request_check
    CHECK (request_ref IS NULL OR request_ref ~ '^request:[A-Za-z0-9][A-Za-z0-9._~-]{0,127}$'),
  CONSTRAINT data_event_ingest_attempt_correlation_check
    CHECK (correlation_ref IS NULL OR correlation_ref ~ '^correlation:[A-Za-z0-9][A-Za-z0-9._~-]{0,127}$'),
  CONSTRAINT data_event_ingest_attempt_retention_check CHECK (expires_at >= created_at + interval '90 days')
);

CREATE INDEX data_event_ingest_attempt_event_idx
  ON public.data_event_ingest_attempt_v1 (canonical_event_id, created_at DESC);
CREATE INDEX data_event_ingest_attempt_expiry_idx
  ON public.data_event_ingest_attempt_v1 (expires_at, attempt_id);

CREATE TABLE public.data_event_duplicate_observation_v1 (
  observation_id uuid PRIMARY KEY DEFAULT public.gen_random_uuid(),
  canonical_event_id varchar(160) NOT NULL REFERENCES public.data_platform_event_v1(event_id) ON DELETE RESTRICT,
  observed_event_id varchar(160) NOT NULL,
  observed_fingerprint varchar(64) NOT NULL CHECK (observed_fingerprint ~ '^[0-9a-f]{64}$'),
  request_ref varchar(160),
  correlation_ref varchar(160),
  producer_build_id varchar(44) NOT NULL CHECK (producer_build_id ~ '^git:[0-9a-f]{40}$'),
  retention_class varchar(40) NOT NULL DEFAULT 'ingest_evidence_90d'
    CHECK (retention_class = 'ingest_evidence_90d'),
  retention_policy_version varchar(96) NOT NULL DEFAULT 'data-retention-policy-v1'
    CHECK (retention_policy_version = 'data-retention-policy-v1'),
  expires_at timestamptz NOT NULL DEFAULT (CURRENT_TIMESTAMP + interval '90 days'),
  created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT data_event_duplicate_event_id_check
    CHECK (observed_event_id ~ '^event:[A-Za-z0-9][A-Za-z0-9._~-]{0,127}$'),
  CONSTRAINT data_event_duplicate_request_check
    CHECK (request_ref IS NULL OR request_ref ~ '^request:[A-Za-z0-9][A-Za-z0-9._~-]{0,127}$'),
  CONSTRAINT data_event_duplicate_correlation_check
    CHECK (correlation_ref IS NULL OR correlation_ref ~ '^correlation:[A-Za-z0-9][A-Za-z0-9._~-]{0,127}$'),
  CONSTRAINT data_event_duplicate_retention_check CHECK (expires_at >= created_at + interval '90 days')
);

CREATE TABLE public.data_event_conflict_observation_v1 (
  observation_id uuid PRIMARY KEY DEFAULT public.gen_random_uuid(),
  canonical_event_id varchar(160) NOT NULL REFERENCES public.data_platform_event_v1(event_id) ON DELETE RESTRICT,
  conflicting_event_id varchar(160) NOT NULL,
  existing_fingerprint varchar(64) NOT NULL CHECK (existing_fingerprint ~ '^[0-9a-f]{64}$'),
  conflicting_fingerprint varchar(64) NOT NULL CHECK (conflicting_fingerprint ~ '^[0-9a-f]{64}$'),
  error_code varchar(64) NOT NULL DEFAULT 'IDEMPOTENCY_CONFLICT'
    CHECK (error_code = 'IDEMPOTENCY_CONFLICT'),
  request_ref varchar(160),
  correlation_ref varchar(160),
  producer_build_id varchar(44) NOT NULL CHECK (producer_build_id ~ '^git:[0-9a-f]{40}$'),
  retention_class varchar(40) NOT NULL DEFAULT 'ingest_evidence_90d'
    CHECK (retention_class = 'ingest_evidence_90d'),
  retention_policy_version varchar(96) NOT NULL DEFAULT 'data-retention-policy-v1'
    CHECK (retention_policy_version = 'data-retention-policy-v1'),
  expires_at timestamptz NOT NULL DEFAULT (CURRENT_TIMESTAMP + interval '90 days'),
  created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT data_event_conflict_event_id_check
    CHECK (conflicting_event_id ~ '^event:[A-Za-z0-9][A-Za-z0-9._~-]{0,127}$'),
  CONSTRAINT data_event_conflict_request_check
    CHECK (request_ref IS NULL OR request_ref ~ '^request:[A-Za-z0-9][A-Za-z0-9._~-]{0,127}$'),
  CONSTRAINT data_event_conflict_correlation_check
    CHECK (correlation_ref IS NULL OR correlation_ref ~ '^correlation:[A-Za-z0-9][A-Za-z0-9._~-]{0,127}$'),
  CONSTRAINT data_event_conflict_distinct_fingerprint_check
    CHECK (existing_fingerprint <> conflicting_fingerprint),
  CONSTRAINT data_event_conflict_retention_check CHECK (expires_at >= created_at + interval '90 days')
);

CREATE INDEX data_event_duplicate_event_idx
  ON public.data_event_duplicate_observation_v1 (canonical_event_id, created_at DESC);
CREATE INDEX data_event_conflict_event_idx
  ON public.data_event_conflict_observation_v1 (canonical_event_id, created_at DESC);

CREATE TRIGGER data_platform_event_append_only_v1
BEFORE UPDATE OR DELETE ON public.data_platform_event_v1
FOR EACH ROW EXECUTE FUNCTION public.prevent_data_event_append_only_mutation_v1();
CREATE TRIGGER data_event_ingest_attempt_append_only_v1
BEFORE UPDATE OR DELETE ON public.data_event_ingest_attempt_v1
FOR EACH ROW EXECUTE FUNCTION public.prevent_data_event_append_only_mutation_v1();
CREATE TRIGGER data_event_duplicate_observation_append_only_v1
BEFORE UPDATE OR DELETE ON public.data_event_duplicate_observation_v1
FOR EACH ROW EXECUTE FUNCTION public.prevent_data_event_append_only_mutation_v1();
CREATE TRIGGER data_event_conflict_observation_append_only_v1
BEFORE UPDATE OR DELETE ON public.data_event_conflict_observation_v1
FOR EACH ROW EXECUTE FUNCTION public.prevent_data_event_append_only_mutation_v1();

REVOKE ALL ON TABLE public.data_platform_event_v1,
  public.data_event_ingest_attempt_v1,
  public.data_event_duplicate_observation_v1,
  public.data_event_conflict_observation_v1
FROM PUBLIC;
REVOKE EXECUTE ON FUNCTION public.prevent_data_event_append_only_mutation_v1() FROM PUBLIC;
REVOKE EXECUTE ON FUNCTION public.data_event_type_valid_v1(varchar, varchar) FROM PUBLIC;
REVOKE EXECUTE ON FUNCTION public.data_event_payload_contains_forbidden_key_v1(jsonb) FROM PUBLIC;
REVOKE EXECUTE ON FUNCTION public.data_event_canonical_json_v1(jsonb) FROM PUBLIC;

COMMIT;
