-- Journey Connect DB v2.7 extension - DP-4.5 Recommendation adapter shadow evidence
-- Target: PostgreSQL 15 and 18
-- Prerequisite: canonical SQL 01..34.
-- Stores append-only shadow/compatibility evidence only. No production Recommendation write is activated.

BEGIN;

DO $$
BEGIN
  IF to_regprocedure('public.gen_random_uuid()') IS NULL THEN
    RAISE EXCEPTION 'DP-4.5 prerequisite gen_random_uuid() is missing.' USING ERRCODE = '42883';
  END IF;
  IF to_regprocedure('public.digest(bytea,text)') IS NULL THEN
    RAISE EXCEPTION 'DP-4.5 prerequisite digest(bytea,text) is missing.' USING ERRCODE = '42883';
  END IF;
  IF to_regprocedure('public.data_event_payload_contains_forbidden_key_v1(jsonb)') IS NULL THEN
    RAISE EXCEPTION 'DP-4.5 prerequisite payload privacy validator is missing.' USING ERRCODE = '42883';
  END IF;
  IF to_regprocedure('public.data_event_type_valid_v1(character varying,character varying)') IS NULL THEN
    RAISE EXCEPTION 'DP-4.5 prerequisite Data event taxonomy validator is missing.' USING ERRCODE = '42883';
  END IF;
  IF to_regprocedure('public.prevent_data_event_append_only_mutation_v1()') IS NULL THEN
    RAISE EXCEPTION 'DP-4.5 prerequisite append-only trigger function is missing.' USING ERRCODE = '42883';
  END IF;
END;
$$;

CREATE OR REPLACE FUNCTION public.data_recommendation_adapter_version_valid_v1(p_value varchar)
RETURNS boolean
LANGUAGE sql
IMMUTABLE
STRICT
SECURITY INVOKER
SET search_path = pg_catalog, pg_temp
AS $$
  SELECT length(p_value) <= 96
     AND p_value ~ '^[a-z0-9]+(?:-[a-z0-9]+)*-v[1-9][0-9]*$';
$$;

CREATE OR REPLACE FUNCTION public.data_recommendation_adapter_failure_code_valid_v1(p_value varchar)
RETURNS boolean
LANGUAGE sql
IMMUTABLE
STRICT
SECURITY INVOKER
SET search_path = pg_catalog, pg_temp
AS $$
  SELECT p_value IN (
    'unsupported_event_type', 'unsupported_schema_version',
    'identity_mapping_required', 'missing_required_reference',
    'missing_exposure_reference', 'payload_unmappable',
    'timestamp_invalid', 'source_fingerprint_mismatch',
    'target_contract_violation', 'privacy_policy_violation',
    'exposure_authority_conflict', 'adapter_invariant_failed',
    'unclassified_mapping_failure', 'dependency_unavailable',
    'temporary_read_failure', 'serialization_failure', 'worker_interrupted'
  );
$$;

CREATE OR REPLACE FUNCTION public.data_recommendation_adapter_failure_class_valid_v1(p_value varchar)
RETURNS boolean
LANGUAGE sql
IMMUTABLE
STRICT
SECURITY INVOKER
SET search_path = pg_catalog, pg_temp
AS $$
  SELECT p_value IN (
    'schema_unsupported', 'source_binding_invalid', 'payload_unmappable',
    'source_hash_mismatch', 'projection_invariant_failed',
    'privacy_policy_violation', 'unclassified_failure',
    'dependency_unavailable', 'temporary_read_failure',
    'serialization_failure', 'worker_interrupted'
  );
$$;

CREATE OR REPLACE FUNCTION public.data_recommendation_adapter_failure_retryable_v1(p_value varchar)
RETURNS boolean
LANGUAGE sql
IMMUTABLE
STRICT
SECURITY INVOKER
SET search_path = pg_catalog, pg_temp
AS $$
  SELECT p_value IN (
    'dependency_unavailable', 'temporary_read_failure',
    'serialization_failure', 'worker_interrupted'
  );
$$;

CREATE OR REPLACE FUNCTION public.data_recommendation_adapter_failure_binding_valid_v1(
  p_failure_code varchar,
  p_failure_class varchar
)
RETURNS boolean
LANGUAGE sql
IMMUTABLE
STRICT
SECURITY INVOKER
SET search_path = pg_catalog, pg_temp
AS $$
  SELECT (p_failure_code, p_failure_class) IN (
    ('unsupported_event_type', 'schema_unsupported'),
    ('unsupported_schema_version', 'schema_unsupported'),
    ('identity_mapping_required', 'source_binding_invalid'),
    ('missing_required_reference', 'source_binding_invalid'),
    ('missing_exposure_reference', 'source_binding_invalid'),
    ('payload_unmappable', 'payload_unmappable'),
    ('timestamp_invalid', 'source_binding_invalid'),
    ('source_fingerprint_mismatch', 'source_hash_mismatch'),
    ('target_contract_violation', 'projection_invariant_failed'),
    ('privacy_policy_violation', 'privacy_policy_violation'),
    ('exposure_authority_conflict', 'source_binding_invalid'),
    ('adapter_invariant_failed', 'projection_invariant_failed'),
    ('unclassified_mapping_failure', 'unclassified_failure'),
    ('dependency_unavailable', 'dependency_unavailable'),
    ('temporary_read_failure', 'temporary_read_failure'),
    ('serialization_failure', 'serialization_failure'),
    ('worker_interrupted', 'worker_interrupted')
  );
$$;

CREATE OR REPLACE FUNCTION public.data_recommendation_adapter_payload_valid_v1(p_value jsonb)
RETURNS boolean
LANGUAGE sql
IMMUTABLE
STRICT
SECURITY INVOKER
SET search_path = pg_catalog, public, pg_temp
AS $$
  SELECT jsonb_typeof(p_value) = 'object'
     AND octet_length(convert_to(p_value::text, 'UTF8')) BETWEEN 2 AND 65536
     AND NOT public.data_event_payload_contains_forbidden_key_v1(p_value);
$$;

CREATE OR REPLACE FUNCTION public.data_recommendation_adapter_logical_identity_hash_v1(
  p_source_event_ref varchar,
  p_source_fingerprint varchar,
  p_adapter_id varchar,
  p_adapter_version varchar,
  p_target_contract_version varchar,
  p_mapping_policy_version varchar
)
RETURNS varchar
LANGUAGE sql
IMMUTABLE
STRICT
SECURITY INVOKER
SET search_path = pg_catalog, public, pg_temp
AS $$
  SELECT encode(public.digest(convert_to(concat_ws(E'\x1f',
    'recommendation-adapter-shadow-identity-v1',
    p_source_event_ref, p_source_fingerprint, p_adapter_id, p_adapter_version,
    p_target_contract_version, p_mapping_policy_version), 'UTF8'), 'sha256'), 'hex')::varchar;
$$;

CREATE OR REPLACE FUNCTION public.data_recommendation_adapter_failure_result_fingerprint_v1(
  p_mapping_status varchar,
  p_failure_code varchar,
  p_failure_class varchar,
  p_retryable boolean,
  p_failure_signature varchar
)
RETURNS varchar
LANGUAGE sql
IMMUTABLE
STRICT
SECURITY INVOKER
SET search_path = pg_catalog, public, pg_temp
AS $$
  SELECT encode(public.digest(convert_to(concat_ws(E'\x1f',
    'recommendation-adapter-failure-result-v1', p_mapping_status,
    p_failure_code, p_failure_class, p_retryable::text, p_failure_signature),
    'UTF8'), 'sha256'), 'hex')::varchar;
$$;

CREATE TABLE public.data_recommendation_adapter_run_v1 (
  adapter_run_id uuid PRIMARY KEY DEFAULT public.gen_random_uuid(),
  logical_identity_hash varchar(64) NOT NULL UNIQUE
    CHECK (logical_identity_hash ~ '^[0-9a-f]{64}$'),
  source_event_ref varchar(160) NOT NULL,
  source_fingerprint varchar(64) NOT NULL
    CHECK (source_fingerprint ~ '^[0-9a-f]{64}$'),
  source_contract_version varchar(96) NOT NULL,
  source_schema_version varchar(96) NOT NULL,
  adapter_id varchar(96) NOT NULL,
  adapter_version varchar(96) NOT NULL,
  mapping_policy_version varchar(96) NOT NULL,
  output_fingerprint_version varchar(96) NOT NULL,
  target_contract_version varchar(96) NOT NULL,
  target_schema_version varchar(96) NOT NULL,
  producer_build_id varchar(44) NOT NULL
    CHECK (producer_build_id ~ '^git:[0-9a-f]{40}$'),
  evidence_kind varchar(16) NOT NULL CHECK (evidence_kind IN ('mapped', 'failure')),
  result_fingerprint varchar(64) NOT NULL
    CHECK (result_fingerprint ~ '^[0-9a-f]{64}$'),
  run_status varchar(24) NOT NULL
    CHECK (run_status IN ('mapped_shadow', 'unsupported', 'quarantined')),
  started_at timestamptz NOT NULL,
  completed_at timestamptz NOT NULL,
  retention_class varchar(40) NOT NULL DEFAULT 'adapter_evidence_90d'
    CHECK (retention_class = 'adapter_evidence_90d'),
  retention_policy_version varchar(96) NOT NULL DEFAULT 'data-retention-policy-v1'
    CHECK (retention_policy_version = 'data-retention-policy-v1'),
  expires_at timestamptz NOT NULL DEFAULT (CURRENT_TIMESTAMP + interval '90 days'),
  created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT data_recommendation_adapter_run_source_ref_check
    CHECK (source_event_ref ~ '^recommendation_behavior_event:[A-Za-z0-9][A-Za-z0-9._:-]{0,127}$'),
  CONSTRAINT data_recommendation_adapter_run_source_contract_check
    CHECK (source_contract_version = 'recommendation-behavior-event-v1'),
  CONSTRAINT data_recommendation_adapter_run_source_schema_check
    CHECK (source_schema_version = 'recommendation-behavior-event-v1'),
  CONSTRAINT data_recommendation_adapter_run_adapter_check
    CHECK (adapter_id = 'p0-recommendation-event-adapter-v1'
       AND adapter_version = 'recommendation-p0-event-adapter-v1'
       AND mapping_policy_version = 'recommendation-p0-mapping-policy-v1'
       AND output_fingerprint_version = 'recommendation-p0-adapter-output-sha256-v1'),
  CONSTRAINT data_recommendation_adapter_run_target_check
    CHECK (target_contract_version = 'platform-event-v1'
       AND target_schema_version = 'user-behavior-event-v1'),
  CONSTRAINT data_recommendation_adapter_run_time_check
    CHECK (completed_at >= started_at),
  CONSTRAINT data_recommendation_adapter_run_state_check
    CHECK ((evidence_kind = 'mapped' AND run_status = 'mapped_shadow')
        OR (evidence_kind = 'failure' AND run_status IN ('unsupported', 'quarantined'))),
  CONSTRAINT data_recommendation_adapter_run_retention_check
    CHECK (expires_at >= created_at + interval '90 days')
);

CREATE INDEX data_recommendation_adapter_run_created_idx
  ON public.data_recommendation_adapter_run_v1 (created_at DESC, adapter_run_id);
CREATE INDEX data_recommendation_adapter_run_version_idx
  ON public.data_recommendation_adapter_run_v1
    (adapter_version, target_contract_version, created_at DESC);
CREATE INDEX data_recommendation_adapter_run_status_idx
  ON public.data_recommendation_adapter_run_v1 (run_status, created_at DESC);

CREATE TABLE public.data_recommendation_adapter_output_v1 (
  adapter_output_id uuid PRIMARY KEY DEFAULT public.gen_random_uuid(),
  adapter_run_ref uuid NOT NULL UNIQUE
    REFERENCES public.data_recommendation_adapter_run_v1(adapter_run_id) ON DELETE RESTRICT,
  source_event_ref varchar(160) NOT NULL,
  source_fingerprint varchar(64) NOT NULL
    CHECK (source_fingerprint ~ '^[0-9a-f]{64}$'),
  compatibility_class varchar(32) NOT NULL
    CHECK (compatibility_class IN ('exact_compatible', 'semantic_compatible')),
  mapped_event_type varchar(80) NOT NULL,
  mapped_actor_ref varchar(160) NOT NULL,
  mapped_session_ref varchar(160) NOT NULL,
  mapped_entity_ref varchar(160),
  mapped_occurred_at timestamptz NOT NULL,
  mapped_payload jsonb NOT NULL,
  output_fingerprint varchar(64) NOT NULL
    CHECK (output_fingerprint ~ '^[0-9a-f]{64}$'),
  mapping_status varchar(24) NOT NULL DEFAULT 'mapped_shadow'
    CHECK (mapping_status = 'mapped_shadow'),
  retention_class varchar(40) NOT NULL DEFAULT 'adapter_evidence_90d'
    CHECK (retention_class = 'adapter_evidence_90d'),
  retention_policy_version varchar(96) NOT NULL DEFAULT 'data-retention-policy-v1'
    CHECK (retention_policy_version = 'data-retention-policy-v1'),
  expires_at timestamptz NOT NULL DEFAULT (CURRENT_TIMESTAMP + interval '90 days'),
  created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT data_recommendation_adapter_output_source_ref_check
    CHECK (source_event_ref ~ '^recommendation_behavior_event:[A-Za-z0-9][A-Za-z0-9._:-]{0,127}$'),
  CONSTRAINT data_recommendation_adapter_output_event_type_check
    CHECK (public.data_event_type_valid_v1('user_behavior', mapped_event_type)),
  CONSTRAINT data_recommendation_adapter_output_actor_check
    CHECK (mapped_actor_ref ~ '^subject:[A-Za-z0-9][A-Za-z0-9._~-]{0,127}$'),
  CONSTRAINT data_recommendation_adapter_output_session_check
    CHECK (mapped_session_ref ~ '^session:[A-Za-z0-9][A-Za-z0-9._~-]{0,127}$'),
  CONSTRAINT data_recommendation_adapter_output_entity_check
    CHECK (mapped_entity_ref IS NULL OR (
      mapped_entity_ref ~ '^[a-z][a-z0-9_]{0,31}:[^[:space:]]{1,128}$'
      AND split_part(mapped_entity_ref, ':', 1) IN (
        'post', 'journey', 'place', 'crew', 'user', 'tag', 'region',
        'itinerary', 'profile', 'search_result'
      )
    )),
  CONSTRAINT data_recommendation_adapter_output_payload_check
    CHECK (public.data_recommendation_adapter_payload_valid_v1(mapped_payload)),
  CONSTRAINT data_recommendation_adapter_output_retention_check
    CHECK (expires_at >= created_at + interval '90 days')
);

CREATE INDEX data_recommendation_adapter_output_compatibility_idx
  ON public.data_recommendation_adapter_output_v1
    (compatibility_class, created_at DESC);
CREATE INDEX data_recommendation_adapter_output_event_type_idx
  ON public.data_recommendation_adapter_output_v1
    (mapped_event_type, created_at DESC);

CREATE TABLE public.data_recommendation_adapter_failure_v1 (
  adapter_failure_id uuid PRIMARY KEY DEFAULT public.gen_random_uuid(),
  adapter_run_ref uuid NOT NULL UNIQUE
    REFERENCES public.data_recommendation_adapter_run_v1(adapter_run_id) ON DELETE RESTRICT,
  source_event_ref varchar(160) NOT NULL,
  source_fingerprint varchar(64) NOT NULL
    CHECK (source_fingerprint ~ '^[0-9a-f]{64}$'),
  failure_code varchar(64) NOT NULL,
  failure_class varchar(64) NOT NULL,
  retryable boolean NOT NULL,
  failure_signature varchar(64) NOT NULL
    CHECK (failure_signature ~ '^[0-9a-f]{64}$'),
  adapter_version varchar(96) NOT NULL,
  target_contract_version varchar(96) NOT NULL,
  mapping_status varchar(24) NOT NULL
    CHECK (mapping_status IN ('unsupported', 'quarantined')),
  retention_class varchar(40) NOT NULL DEFAULT 'adapter_evidence_90d'
    CHECK (retention_class = 'adapter_evidence_90d'),
  retention_policy_version varchar(96) NOT NULL DEFAULT 'data-retention-policy-v1'
    CHECK (retention_policy_version = 'data-retention-policy-v1'),
  expires_at timestamptz NOT NULL DEFAULT (CURRENT_TIMESTAMP + interval '90 days'),
  created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT data_recommendation_adapter_failure_source_ref_check
    CHECK (source_event_ref ~ '^recommendation_behavior_event:[A-Za-z0-9][A-Za-z0-9._:-]{0,127}$'),
  CONSTRAINT data_recommendation_adapter_failure_code_check
    CHECK (public.data_recommendation_adapter_failure_code_valid_v1(failure_code)),
  CONSTRAINT data_recommendation_adapter_failure_class_check
    CHECK (public.data_recommendation_adapter_failure_class_valid_v1(failure_class)),
  CONSTRAINT data_recommendation_adapter_failure_binding_check
    CHECK (public.data_recommendation_adapter_failure_binding_valid_v1(failure_code, failure_class)),
  CONSTRAINT data_recommendation_adapter_failure_retryable_check
    CHECK (retryable = public.data_recommendation_adapter_failure_retryable_v1(failure_code)),
  CONSTRAINT data_recommendation_adapter_failure_version_check
    CHECK (adapter_version = 'recommendation-p0-event-adapter-v1'
       AND target_contract_version = 'platform-event-v1'),
  CONSTRAINT data_recommendation_adapter_failure_retention_check
    CHECK (expires_at >= created_at + interval '90 days')
);

CREATE INDEX data_recommendation_adapter_failure_code_idx
  ON public.data_recommendation_adapter_failure_v1 (failure_code, created_at DESC);
CREATE INDEX data_recommendation_adapter_failure_class_idx
  ON public.data_recommendation_adapter_failure_v1 (failure_class, created_at DESC);

CREATE TABLE public.data_recommendation_adapter_conflict_observation_v1 (
  conflict_observation_id uuid PRIMARY KEY DEFAULT public.gen_random_uuid(),
  logical_identity_hash varchar(64) NOT NULL
    CHECK (logical_identity_hash ~ '^[0-9a-f]{64}$'),
  existing_evidence_ref varchar(80) NOT NULL,
  existing_result_fingerprint varchar(64) NOT NULL
    CHECK (existing_result_fingerprint ~ '^[0-9a-f]{64}$'),
  attempted_result_fingerprint varchar(64) NOT NULL
    CHECK (attempted_result_fingerprint ~ '^[0-9a-f]{64}$'),
  existing_output_fingerprint varchar(64),
  attempted_output_fingerprint varchar(64),
  failure_code varchar(64) NOT NULL DEFAULT 'ADAPTER_EVIDENCE_CONFLICT'
    CHECK (failure_code = 'ADAPTER_EVIDENCE_CONFLICT'),
  retention_class varchar(40) NOT NULL DEFAULT 'adapter_evidence_90d'
    CHECK (retention_class = 'adapter_evidence_90d'),
  retention_policy_version varchar(96) NOT NULL DEFAULT 'data-retention-policy-v1'
    CHECK (retention_policy_version = 'data-retention-policy-v1'),
  expires_at timestamptz NOT NULL DEFAULT (CURRENT_TIMESTAMP + interval '90 days'),
  created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT data_recommendation_adapter_conflict_ref_check
    CHECK (existing_evidence_ref ~ '^(output|failure):[0-9a-f-]{36}$'),
  CONSTRAINT data_recommendation_adapter_conflict_output_fingerprint_check
    CHECK ((existing_output_fingerprint IS NULL OR existing_output_fingerprint ~ '^[0-9a-f]{64}$')
       AND (attempted_output_fingerprint IS NULL OR attempted_output_fingerprint ~ '^[0-9a-f]{64}$')),
  CONSTRAINT data_recommendation_adapter_conflict_difference_check
    CHECK (existing_result_fingerprint <> attempted_result_fingerprint),
  CONSTRAINT data_recommendation_adapter_conflict_retention_check
    CHECK (expires_at >= created_at + interval '90 days')
);

CREATE INDEX data_recommendation_adapter_conflict_identity_idx
  ON public.data_recommendation_adapter_conflict_observation_v1
    (logical_identity_hash, created_at DESC);

CREATE TABLE public.data_recommendation_adapter_duplicate_counter_v1 (
  logical_identity_hash varchar(64) PRIMARY KEY
    CHECK (logical_identity_hash ~ '^[0-9a-f]{64}$'),
  duplicate_count bigint NOT NULL DEFAULT 0 CHECK (duplicate_count >= 0),
  first_seen_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
  last_seen_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
  expires_at timestamptz NOT NULL DEFAULT (CURRENT_TIMESTAMP + interval '90 days'),
  CONSTRAINT data_recommendation_adapter_duplicate_time_check
    CHECK (last_seen_at >= first_seen_at),
  CONSTRAINT data_recommendation_adapter_duplicate_retention_check
    CHECK (expires_at = first_seen_at + interval '90 days')
);

CREATE TRIGGER data_recommendation_adapter_run_append_only
BEFORE UPDATE OR DELETE ON public.data_recommendation_adapter_run_v1
FOR EACH ROW EXECUTE FUNCTION public.prevent_data_event_append_only_mutation_v1();

CREATE TRIGGER data_recommendation_adapter_output_append_only
BEFORE UPDATE OR DELETE ON public.data_recommendation_adapter_output_v1
FOR EACH ROW EXECUTE FUNCTION public.prevent_data_event_append_only_mutation_v1();

CREATE TRIGGER data_recommendation_adapter_failure_append_only
BEFORE UPDATE OR DELETE ON public.data_recommendation_adapter_failure_v1
FOR EACH ROW EXECUTE FUNCTION public.prevent_data_event_append_only_mutation_v1();

CREATE TRIGGER data_recommendation_adapter_conflict_append_only
BEFORE UPDATE OR DELETE ON public.data_recommendation_adapter_conflict_observation_v1
FOR EACH ROW EXECUTE FUNCTION public.prevent_data_event_append_only_mutation_v1();

COMMENT ON TABLE public.data_recommendation_adapter_run_v1 IS
  'DP-4.5 append-only Recommendation P0 to Data shadow adapter run evidence; not a production Recommendation input.';
COMMENT ON TABLE public.data_recommendation_adapter_output_v1 IS
  'DP-4.5 append-only mapped shadow output evidence containing only privacy-validated DP-4 mapped payload.';
COMMENT ON TABLE public.data_recommendation_adapter_failure_v1 IS
  'DP-4.5 append-only deterministic mapping failure evidence with bounded stable codes.';
COMMENT ON TABLE public.data_recommendation_adapter_conflict_observation_v1 IS
  'DP-4.5 append-only conflict evidence; existing successful or failed evidence remains unchanged.';
COMMENT ON TABLE public.data_recommendation_adapter_duplicate_counter_v1 IS
  'Bounded current-state duplicate counter; not historical evidence and writable only by the approved persistence function owner.';

COMMIT;
