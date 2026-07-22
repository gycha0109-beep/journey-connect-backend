-- Journey Connect DB v2.7 extension - DP-5 projection/snapshot foundation
-- Target: PostgreSQL 15 and 18
-- Prerequisite: canonical SQL 01..37.
-- Shadow-only Data evidence. No Recommendation serving input or production traffic is activated.

BEGIN;

DO $$
BEGIN
  IF to_regprocedure('public.gen_random_uuid()') IS NULL
     OR to_regprocedure('public.digest(bytea,text)') IS NULL
     OR to_regprocedure('public.data_event_canonical_json_v1(jsonb)') IS NULL
     OR to_regprocedure('public.prevent_data_event_append_only_mutation_v1()') IS NULL THEN
    RAISE EXCEPTION 'DP-5 prerequisite Data functions are missing.' USING ERRCODE = '42883';
  END IF;
  IF to_regclass('public.data_platform_event_v1') IS NULL
     OR to_regclass('public.data_recommendation_adapter_output_v1') IS NULL
     OR to_regclass('public.recommendation_p2_experiment_exposure') IS NULL THEN
    RAISE EXCEPTION 'DP-5 prerequisite source authorities are missing.' USING ERRCODE = '42P01';
  END IF;
END;
$$;

CREATE OR REPLACE FUNCTION public.data_projection_version_valid_v1(p_value varchar)
RETURNS boolean LANGUAGE sql IMMUTABLE STRICT SECURITY INVOKER
SET search_path = pg_catalog, pg_temp
AS $$
  SELECT length(p_value) <= 96
     AND p_value ~ '^[a-z0-9]+(?:-[a-z0-9]+)*-v[1-9][0-9]*$';
$$;

CREATE OR REPLACE FUNCTION public.data_projection_fingerprint_valid_v1(p_value varchar)
RETURNS boolean LANGUAGE sql IMMUTABLE STRICT SECURITY INVOKER
SET search_path = pg_catalog, pg_temp
AS $$ SELECT p_value ~ '^[0-9a-f]{64}$'; $$;

CREATE OR REPLACE FUNCTION public.data_projection_fingerprint_v1(p_domain varchar, p_value jsonb)
RETURNS varchar LANGUAGE sql IMMUTABLE STRICT SECURITY INVOKER
SET search_path = pg_catalog, public, pg_temp
AS $$
  SELECT encode(public.digest(convert_to(
    to_json(p_domain)::text || ':' || public.data_event_canonical_json_v1(p_value), 'UTF8'),
    'sha256'), 'hex')::varchar;
$$;

CREATE OR REPLACE FUNCTION public.data_projection_instant_text_v1(p_value timestamptz)
RETURNS text LANGUAGE sql IMMUTABLE STRICT SECURITY INVOKER
SET search_path = pg_catalog, pg_temp
AS $$
  SELECT to_char(p_value AT TIME ZONE 'UTC', 'YYYY-MM-DD"T"HH24:MI:SS') ||
    CASE WHEN mod(extract(microseconds FROM p_value)::bigint, 1000000) = 0 THEN 'Z'
         ELSE '.' || rtrim(to_char(p_value AT TIME ZONE 'UTC', 'US'), '0') || 'Z' END;
$$;

CREATE OR REPLACE FUNCTION public.data_projection_identity_valid_v1(p_value varchar)
RETURNS boolean LANGUAGE sql IMMUTABLE STRICT SECURITY INVOKER
SET search_path = pg_catalog, pg_temp
AS $$
  SELECT p_value ~ '^subject:[A-Za-z0-9][A-Za-z0-9._~-]{0,127}$'
      OR p_value ~ '^user:[1-9][0-9]*$';
$$;

CREATE OR REPLACE FUNCTION public.data_projection_failure_code_valid_v1(p_value varchar)
RETURNS boolean LANGUAGE sql IMMUTABLE STRICT SECURITY INVOKER
SET search_path = pg_catalog, pg_temp
AS $$
  SELECT p_value IN (
    'unsupported_projection_schema','unsupported_source_schema','source_checkpoint_invalid',
    'source_event_missing','source_fingerprint_mismatch','adapter_evidence_missing',
    'adapter_evidence_conflicted','adapter_evidence_rejected','identity_binding_required',
    'identity_binding_invalid','identity_namespace_conflict','exposure_binding_missing',
    'exposure_binding_invalid','outcome_window_violation','projection_invariant_failed',
    'lineage_incomplete','snapshot_fingerprint_conflict','privacy_policy_violation',
    'unclassified_projection_failure','PROJECTION_SNAPSHOT_CONFLICT'
  );
$$;

CREATE TABLE public.data_source_checkpoint_v1 (
  checkpoint_id uuid PRIMARY KEY DEFAULT public.gen_random_uuid(),
  checkpoint_ref varchar(160) NOT NULL UNIQUE,
  logical_identity_hash varchar(64) NOT NULL UNIQUE
    CHECK (public.data_projection_fingerprint_valid_v1(logical_identity_hash)),
  source_stream varchar(96) NOT NULL,
  source_contract_version varchar(96) NOT NULL,
  source_schema_version varchar(96) NOT NULL,
  event_time_from timestamptz NOT NULL,
  event_time_to timestamptz NOT NULL,
  ingested_at_upper_bound timestamptz NOT NULL,
  last_source_event_ref varchar(180) NOT NULL,
  source_event_count bigint NOT NULL CHECK (source_event_count > 0),
  source_members jsonb NOT NULL CHECK (jsonb_typeof(source_members) = 'array'),
  source_set_fingerprint varchar(64) NOT NULL
    CHECK (public.data_projection_fingerprint_valid_v1(source_set_fingerprint)),
  checkpoint_definition_fingerprint varchar(64) NOT NULL
    CHECK (public.data_projection_fingerprint_valid_v1(checkpoint_definition_fingerprint)),
  retention_class varchar(40) NOT NULL DEFAULT 'projection_evidence_90d'
    CHECK (retention_class = 'projection_evidence_90d'),
  retention_policy_version varchar(96) NOT NULL DEFAULT 'data-retention-policy-v1'
    CHECK (retention_policy_version = 'data-retention-policy-v1'),
  expires_at timestamptz NOT NULL DEFAULT (CURRENT_TIMESTAMP + interval '90 days'),
  created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT data_source_checkpoint_ref_check
    CHECK (checkpoint_ref ~ '^checkpoint:[A-Za-z0-9][A-Za-z0-9._:~-]{0,149}$'),
  CONSTRAINT data_source_checkpoint_version_check
    CHECK (public.data_projection_version_valid_v1(source_contract_version)
       AND public.data_projection_version_valid_v1(source_schema_version)),
  CONSTRAINT data_source_checkpoint_range_check
    CHECK (event_time_from < event_time_to AND ingested_at_upper_bound >= event_time_from),
  CONSTRAINT data_source_checkpoint_member_count_check
    CHECK (jsonb_array_length(source_members) = source_event_count),
  CONSTRAINT data_source_checkpoint_retention_check
    CHECK (expires_at >= created_at + interval '90 days')
);

CREATE TABLE public.data_projection_run_v1 (
  projection_run_id uuid PRIMARY KEY DEFAULT public.gen_random_uuid(),
  projection_run_ref varchar(160) NOT NULL UNIQUE,
  logical_identity_hash varchar(64) NOT NULL UNIQUE
    CHECK (public.data_projection_fingerprint_valid_v1(logical_identity_hash)),
  projection_name varchar(96) NOT NULL
    CHECK (projection_name IN ('recommendation-profile-input-v1','experiment-outcome-input-v1')),
  projection_schema_version varchar(96) NOT NULL,
  projection_policy_version varchar(96) NOT NULL,
  feature_policy_version varchar(96) NOT NULL,
  source_contract_version varchar(96) NOT NULL,
  source_checkpoint_ref uuid NOT NULL REFERENCES public.data_source_checkpoint_v1(checkpoint_id) ON DELETE RESTRICT,
  source_from timestamptz NOT NULL,
  source_to timestamptz NOT NULL,
  projection_as_of timestamptz NOT NULL,
  identity_binding_version varchar(96) NOT NULL,
  identity_binding_source varchar(96) NOT NULL,
  identity_binding_fingerprint varchar(64) NOT NULL
    CHECK (public.data_projection_fingerprint_valid_v1(identity_binding_fingerprint)),
  identity_binding_scope varchar(96) NOT NULL,
  target_contract_version varchar(96) NOT NULL,
  producer_build_id varchar(44) NOT NULL CHECK (producer_build_id ~ '^git:[0-9a-f]{40}$'),
  retention_class varchar(40) NOT NULL DEFAULT 'projection_evidence_90d'
    CHECK (retention_class = 'projection_evidence_90d'),
  retention_policy_version varchar(96) NOT NULL DEFAULT 'data-retention-policy-v1'
    CHECK (retention_policy_version = 'data-retention-policy-v1'),
  expires_at timestamptz NOT NULL DEFAULT (CURRENT_TIMESTAMP + interval '90 days'),
  created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT data_projection_run_ref_check
    CHECK (projection_run_ref ~ '^projection_run:[A-Za-z0-9][A-Za-z0-9._:~-]{0,143}$'),
  CONSTRAINT data_projection_run_versions_check CHECK (
    public.data_projection_version_valid_v1(projection_schema_version)
    AND public.data_projection_version_valid_v1(projection_policy_version)
    AND public.data_projection_version_valid_v1(feature_policy_version)
    AND public.data_projection_version_valid_v1(source_contract_version)
    AND public.data_projection_version_valid_v1(identity_binding_version)
    AND public.data_projection_version_valid_v1(target_contract_version)
    AND identity_binding_source ~ '^[A-Za-z0-9][A-Za-z0-9._:-]{0,95}$'
    AND identity_binding_scope ~ '^[A-Za-z0-9][A-Za-z0-9._:-]{0,95}$'),
  CONSTRAINT data_projection_run_range_check
    CHECK (source_from < source_to AND projection_as_of >= source_from),
  CONSTRAINT data_projection_run_retention_check
    CHECK (expires_at >= created_at + interval '90 days')
);

CREATE TABLE public.data_projection_run_status_evidence_v1 (
  run_status_evidence_id uuid PRIMARY KEY DEFAULT public.gen_random_uuid(),
  projection_run_ref uuid NOT NULL REFERENCES public.data_projection_run_v1(projection_run_id) ON DELETE RESTRICT,
  run_status varchar(16) NOT NULL
    CHECK (run_status IN ('started','completed','failed','conflicted','validated','rejected')),
  failure_code varchar(96),
  validation_ref varchar(160),
  observed_at timestamptz NOT NULL,
  retention_class varchar(40) NOT NULL DEFAULT 'projection_evidence_90d'
    CHECK (retention_class = 'projection_evidence_90d'),
  retention_policy_version varchar(96) NOT NULL DEFAULT 'data-retention-policy-v1'
    CHECK (retention_policy_version = 'data-retention-policy-v1'),
  expires_at timestamptz NOT NULL DEFAULT (CURRENT_TIMESTAMP + interval '90 days'),
  created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT data_projection_run_status_failure_check CHECK (
    (run_status IN ('failed','conflicted','rejected') AND failure_code IS NOT NULL
      AND public.data_projection_failure_code_valid_v1(failure_code))
    OR (run_status IN ('started','completed','validated') AND failure_code IS NULL)),
  CONSTRAINT data_projection_run_status_retention_check
    CHECK (expires_at >= created_at + interval '90 days')
);

CREATE TABLE public.data_projection_snapshot_v1 (
  snapshot_id uuid PRIMARY KEY DEFAULT public.gen_random_uuid(),
  snapshot_ref varchar(160) NOT NULL UNIQUE,
  projection_run_ref uuid NOT NULL UNIQUE REFERENCES public.data_projection_run_v1(projection_run_id) ON DELETE RESTRICT,
  projection_name varchar(96) NOT NULL,
  projection_schema_version varchar(96) NOT NULL,
  projection_policy_version varchar(96) NOT NULL,
  source_checkpoint_ref uuid NOT NULL REFERENCES public.data_source_checkpoint_v1(checkpoint_id) ON DELETE RESTRICT,
  snapshot_as_of timestamptz NOT NULL,
  record_count bigint NOT NULL CHECK (record_count > 0),
  subject_count bigint NOT NULL CHECK (subject_count > 0),
  source_event_count bigint NOT NULL CHECK (source_event_count > 0),
  content_fingerprint varchar(64) NOT NULL
    CHECK (public.data_projection_fingerprint_valid_v1(content_fingerprint)),
  lineage_fingerprint varchar(64) NOT NULL
    CHECK (public.data_projection_fingerprint_valid_v1(lineage_fingerprint)),
  snapshot_status varchar(16) NOT NULL CHECK (snapshot_status IN ('created','validated','rejected')),
  retention_class varchar(40) NOT NULL DEFAULT 'projection_evidence_90d'
    CHECK (retention_class = 'projection_evidence_90d'),
  retention_policy_version varchar(96) NOT NULL DEFAULT 'data-retention-policy-v1'
    CHECK (retention_policy_version = 'data-retention-policy-v1'),
  expires_at timestamptz NOT NULL DEFAULT (CURRENT_TIMESTAMP + interval '90 days'),
  created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT data_projection_snapshot_ref_check
    CHECK (snapshot_ref ~ '^snapshot:[A-Za-z0-9][A-Za-z0-9._:~-]{0,149}$'),
  CONSTRAINT data_projection_snapshot_forbidden_status_check
    CHECK (snapshot_status NOT IN ('production_ready','cutover_ready','active','serving','authoritative')),
  CONSTRAINT data_projection_snapshot_retention_check
    CHECK (expires_at >= created_at + interval '90 days')
);

CREATE TABLE public.data_projection_lineage_v1 (
  lineage_id uuid PRIMARY KEY DEFAULT public.gen_random_uuid(),
  snapshot_ref uuid NOT NULL REFERENCES public.data_projection_snapshot_v1(snapshot_id) ON DELETE RESTRICT,
  projection_record_ref varchar(160) NOT NULL,
  source_kind varchar(24) NOT NULL CHECK (source_kind IN ('canonical_event','adapter_output','p2_exposure')),
  source_event_ref varchar(180) NOT NULL,
  source_fingerprint varchar(64) NOT NULL
    CHECK (public.data_projection_fingerprint_valid_v1(source_fingerprint)),
  adapter_evidence_ref uuid,
  source_checkpoint_ref uuid NOT NULL REFERENCES public.data_source_checkpoint_v1(checkpoint_id) ON DELETE RESTRICT,
  projection_policy_version varchar(96) NOT NULL,
  mapping_policy_version varchar(96),
  lineage_entry_fingerprint varchar(64) NOT NULL UNIQUE
    CHECK (public.data_projection_fingerprint_valid_v1(lineage_entry_fingerprint)),
  retention_class varchar(40) NOT NULL DEFAULT 'projection_evidence_90d'
    CHECK (retention_class = 'projection_evidence_90d'),
  retention_policy_version varchar(96) NOT NULL DEFAULT 'data-retention-policy-v1'
    CHECK (retention_policy_version = 'data-retention-policy-v1'),
  expires_at timestamptz NOT NULL DEFAULT (CURRENT_TIMESTAMP + interval '90 days'),
  created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT data_projection_lineage_adapter_check CHECK (
    (source_kind = 'adapter_output' AND adapter_evidence_ref IS NOT NULL AND mapping_policy_version IS NOT NULL)
    OR (source_kind <> 'adapter_output' AND adapter_evidence_ref IS NULL AND mapping_policy_version IS NULL)),
  CONSTRAINT data_projection_lineage_retention_check
    CHECK (expires_at >= created_at + interval '90 days'),
  UNIQUE (snapshot_ref, projection_record_ref, source_event_ref, source_fingerprint)
);

CREATE TABLE public.data_projection_validation_evidence_v1 (
  validation_evidence_id uuid PRIMARY KEY DEFAULT public.gen_random_uuid(),
  projection_run_ref uuid NOT NULL REFERENCES public.data_projection_run_v1(projection_run_id) ON DELETE RESTRICT,
  snapshot_ref uuid REFERENCES public.data_projection_snapshot_v1(snapshot_id) ON DELETE RESTRICT,
  validation_status varchar(16) NOT NULL CHECK (validation_status IN ('passed','failed')),
  failure_code varchar(96),
  validation_fingerprint varchar(64) NOT NULL
    CHECK (public.data_projection_fingerprint_valid_v1(validation_fingerprint)),
  retention_class varchar(40) NOT NULL DEFAULT 'projection_evidence_90d'
    CHECK (retention_class = 'projection_evidence_90d'),
  retention_policy_version varchar(96) NOT NULL DEFAULT 'data-retention-policy-v1'
    CHECK (retention_policy_version = 'data-retention-policy-v1'),
  expires_at timestamptz NOT NULL DEFAULT (CURRENT_TIMESTAMP + interval '90 days'),
  created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT data_projection_validation_failure_check CHECK (
    (validation_status = 'passed' AND failure_code IS NULL)
    OR (validation_status = 'failed' AND failure_code IS NOT NULL
        AND public.data_projection_failure_code_valid_v1(failure_code))),
  CONSTRAINT data_projection_validation_retention_check
    CHECK (expires_at >= created_at + interval '90 days')
);

CREATE TABLE public.data_projection_conflict_observation_v1 (
  conflict_observation_id uuid PRIMARY KEY DEFAULT public.gen_random_uuid(),
  conflict_kind varchar(24) NOT NULL CHECK (conflict_kind IN ('checkpoint','snapshot')),
  logical_identity_hash varchar(64) NOT NULL
    CHECK (public.data_projection_fingerprint_valid_v1(logical_identity_hash)),
  existing_evidence_ref varchar(180) NOT NULL,
  existing_fingerprint varchar(64) NOT NULL
    CHECK (public.data_projection_fingerprint_valid_v1(existing_fingerprint)),
  attempted_fingerprint varchar(64) NOT NULL
    CHECK (public.data_projection_fingerprint_valid_v1(attempted_fingerprint)),
  failure_code varchar(96) NOT NULL
    CHECK (failure_code IN ('source_checkpoint_invalid','PROJECTION_SNAPSHOT_CONFLICT')),
  retention_class varchar(40) NOT NULL DEFAULT 'projection_evidence_90d'
    CHECK (retention_class = 'projection_evidence_90d'),
  retention_policy_version varchar(96) NOT NULL DEFAULT 'data-retention-policy-v1'
    CHECK (retention_policy_version = 'data-retention-policy-v1'),
  expires_at timestamptz NOT NULL DEFAULT (CURRENT_TIMESTAMP + interval '90 days'),
  created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT data_projection_conflict_retention_check
    CHECK (expires_at >= created_at + interval '90 days')
);

CREATE INDEX data_source_checkpoint_created_idx ON public.data_source_checkpoint_v1(created_at DESC, checkpoint_id);
CREATE INDEX data_projection_run_name_idx ON public.data_projection_run_v1(projection_name, projection_schema_version, created_at DESC);
CREATE INDEX data_projection_status_idx ON public.data_projection_run_status_evidence_v1(run_status, observed_at DESC);
CREATE INDEX data_projection_snapshot_status_idx ON public.data_projection_snapshot_v1(snapshot_status, created_at DESC);
CREATE INDEX data_projection_lineage_snapshot_idx ON public.data_projection_lineage_v1(snapshot_ref, projection_record_ref);
CREATE INDEX data_projection_validation_failure_idx ON public.data_projection_validation_evidence_v1(failure_code, created_at DESC) WHERE failure_code IS NOT NULL;
CREATE INDEX data_projection_conflict_created_idx ON public.data_projection_conflict_observation_v1(conflict_kind, created_at DESC);

CREATE TRIGGER data_source_checkpoint_append_only BEFORE UPDATE OR DELETE ON public.data_source_checkpoint_v1 FOR EACH ROW EXECUTE FUNCTION public.prevent_data_event_append_only_mutation_v1();
CREATE TRIGGER data_projection_run_append_only BEFORE UPDATE OR DELETE ON public.data_projection_run_v1 FOR EACH ROW EXECUTE FUNCTION public.prevent_data_event_append_only_mutation_v1();
CREATE TRIGGER data_projection_run_status_append_only BEFORE UPDATE OR DELETE ON public.data_projection_run_status_evidence_v1 FOR EACH ROW EXECUTE FUNCTION public.prevent_data_event_append_only_mutation_v1();
CREATE TRIGGER data_projection_snapshot_append_only BEFORE UPDATE OR DELETE ON public.data_projection_snapshot_v1 FOR EACH ROW EXECUTE FUNCTION public.prevent_data_event_append_only_mutation_v1();
CREATE TRIGGER data_projection_lineage_append_only BEFORE UPDATE OR DELETE ON public.data_projection_lineage_v1 FOR EACH ROW EXECUTE FUNCTION public.prevent_data_event_append_only_mutation_v1();
CREATE TRIGGER data_projection_validation_append_only BEFORE UPDATE OR DELETE ON public.data_projection_validation_evidence_v1 FOR EACH ROW EXECUTE FUNCTION public.prevent_data_event_append_only_mutation_v1();
CREATE TRIGGER data_projection_conflict_append_only BEFORE UPDATE OR DELETE ON public.data_projection_conflict_observation_v1 FOR EACH ROW EXECUTE FUNCTION public.prevent_data_event_append_only_mutation_v1();

COMMIT;
