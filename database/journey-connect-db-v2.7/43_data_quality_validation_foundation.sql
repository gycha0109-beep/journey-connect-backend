-- Journey Connect DB v2.7 extension - DP-6 data quality validation foundation
-- Fingerprint domains: data-quality-validation-input-sha256-v1, data-quality-check-evidence-sha256-v1, data-quality-metric-sha256-v1, data-quality-verdict-sha256-v1, data-quality-rebuild-comparison-sha256-v1, data-quality-late-arrival-observation-sha256-v1.
-- Target: PostgreSQL 15 and 18. Prerequisite: SQL 01..42.
-- Validation-only, append-only evidence. No source/projection/snapshot mutation is authorized.
BEGIN;

DO $$
BEGIN
  IF to_regclass('public.data_projection_snapshot_v1') IS NULL
     OR to_regclass('public.data_source_checkpoint_v1') IS NULL
     OR to_regclass('public.data_projection_lineage_v1') IS NULL
     OR to_regprocedure('public.data_projection_fingerprint_v1(varchar,jsonb)') IS NULL
     OR to_regprocedure('public.prevent_data_event_append_only_mutation_v1()') IS NULL THEN
    RAISE EXCEPTION 'DP-6 prerequisite DP-5 objects are missing.' USING ERRCODE = '42P01';
  END IF;
END;
$$;

CREATE OR REPLACE FUNCTION public.data_quality_version_valid_v1(p_value varchar)
RETURNS boolean LANGUAGE sql IMMUTABLE STRICT SECURITY INVOKER
SET search_path = pg_catalog, pg_temp
AS $$
  SELECT length(p_value) <= 96
     AND p_value ~ '^[a-z0-9]+(?:-[a-z0-9]+)*-v[1-9][0-9]*$';
$$;

CREATE OR REPLACE FUNCTION public.data_quality_fingerprint_valid_v1(p_value varchar)
RETURNS boolean LANGUAGE sql IMMUTABLE STRICT SECURITY INVOKER
SET search_path = pg_catalog, pg_temp
AS $$ SELECT p_value ~ '^[0-9a-f]{64}$'; $$;

CREATE OR REPLACE FUNCTION public.data_quality_fingerprint_v1(p_domain varchar, p_value jsonb)
RETURNS varchar LANGUAGE sql IMMUTABLE STRICT SECURITY INVOKER
SET search_path = pg_catalog, public, pg_temp
AS $$ SELECT public.data_projection_fingerprint_v1(p_domain, p_value); $$;

CREATE OR REPLACE FUNCTION public.data_quality_failure_code_valid_v1(p_value varchar)
RETURNS boolean LANGUAGE sql IMMUTABLE STRICT SECURITY INVOKER
SET search_path = pg_catalog, pg_temp
AS $$
  SELECT p_value IN (
    'unsupported_validation_scope','unsupported_validator_version','unsupported_quality_policy_version',
    'source_count_mismatch','source_set_fingerprint_mismatch','source_range_mismatch',
    'source_event_missing','source_event_unexpected','source_event_out_of_range','source_schema_mismatch',
    'source_contract_mismatch','source_timestamp_mismatch','checkpoint_definition_fingerprint_mismatch',
    'projection_record_missing','projection_record_unexpected','projection_source_count_mismatch',
    'projection_window_violation','projection_duplicate_aggregation','projection_aggregation_mismatch',
    'projection_signal_mismatch','invalid_adapter_evidence_included','conflicted_adapter_evidence_included',
    'rejected_adapter_evidence_included','unsupported_adapter_evidence_included','outcome_window_violation',
    'outcome_aggregation_mismatch','snapshot_record_count_mismatch','snapshot_subject_count_mismatch',
    'snapshot_source_count_mismatch','snapshot_content_fingerprint_mismatch',
    'snapshot_lineage_fingerprint_mismatch','snapshot_contract_mismatch','snapshot_checkpoint_mismatch',
    'snapshot_as_of_mismatch','snapshot_status_invalid','lineage_missing','lineage_orphan',
    'lineage_duplicate','lineage_source_missing','lineage_projection_record_missing',
    'lineage_source_fingerprint_mismatch','lineage_adapter_evidence_missing',
    'lineage_adapter_evidence_mismatch','lineage_checkpoint_mismatch',
    'lineage_mapping_policy_version_mismatch','lineage_projection_policy_version_mismatch',
    'lineage_fingerprint_mismatch','identity_binding_missing','identity_binding_invalid',
    'identity_binding_fingerprint_mismatch','identity_binding_scope_mismatch','identity_namespace_conflict',
    'identity_projection_subject_mismatch','exposure_binding_missing','exposure_binding_invalid',
    'exposure_subject_mismatch','exposure_variant_mismatch','exposure_time_mismatch',
    'exposure_experiment_mismatch','general_exposure_used_as_p2','duplicate_exposure_ambiguity',
    'fallback_authority_mismatch','rebuild_record_count_mismatch','rebuild_subject_count_mismatch',
    'rebuild_source_count_mismatch','rebuild_projection_record_mismatch',
    'rebuild_projection_fingerprint_mismatch','rebuild_snapshot_fingerprint_mismatch',
    'rebuild_lineage_fingerprint_mismatch','rebuild_ordering_mismatch',
    'rebuild_profile_aggregation_mismatch','rebuild_outcome_mismatch','non_deterministic_output',
    'quality_threshold_failed','quality_required_check_skipped','quality_evidence_incomplete',
    'quality_verdict_conflict','privacy_policy_violation','unclassified_quality_failure',
    'QUALITY_VERDICT_CONFLICT'
  );
$$;

CREATE TABLE public.data_quality_validation_run_v1 (
  validation_run_id uuid PRIMARY KEY DEFAULT public.gen_random_uuid(),
  validation_run_ref varchar(160) NOT NULL UNIQUE,
  logical_identity_hash varchar(64) NOT NULL UNIQUE
    CHECK (public.data_quality_fingerprint_valid_v1(logical_identity_hash)),
  validation_scope varchar(32) NOT NULL CHECK (validation_scope IN (
    'SOURCE_COMPLETENESS','PROJECTION_COMPLETENESS','SNAPSHOT_CONSISTENCY','LINEAGE_INTEGRITY',
    'IDENTITY_INTEGRITY','EXPOSURE_INTEGRITY','DETERMINISTIC_REBUILD','FULL')),
  snapshot_ref uuid NOT NULL REFERENCES public.data_projection_snapshot_v1(snapshot_id) ON DELETE RESTRICT,
  projection_name varchar(96) NOT NULL CHECK (projection_name IN (
    'recommendation-profile-input-v1','experiment-outcome-input-v1')),
  projection_schema_version varchar(96) NOT NULL,
  projection_policy_version varchar(96) NOT NULL,
  source_checkpoint_ref uuid NOT NULL REFERENCES public.data_source_checkpoint_v1(checkpoint_id) ON DELETE RESTRICT,
  validator_version varchar(96) NOT NULL CHECK (validator_version = 'data-quality-validator-v1'),
  quality_policy_version varchar(96) NOT NULL CHECK (quality_policy_version = 'data-quality-policy-v1'),
  validation_as_of timestamptz NOT NULL,
  validation_input_fingerprint varchar(64) NOT NULL
    CHECK (public.data_quality_fingerprint_valid_v1(validation_input_fingerprint)),
  retention_class varchar(40) NOT NULL DEFAULT 'data_quality_evidence_90d'
    CHECK (retention_class = 'data_quality_evidence_90d'),
  retention_policy_version varchar(96) NOT NULL DEFAULT 'data-retention-policy-v1'
    CHECK (retention_policy_version = 'data-retention-policy-v1'),
  expires_at timestamptz NOT NULL DEFAULT (CURRENT_TIMESTAMP + interval '90 days'),
  created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT data_quality_validation_run_ref_check
    CHECK (validation_run_ref ~ '^quality_run:[A-Za-z0-9][A-Za-z0-9._:~-]{0,147}$'),
  CONSTRAINT data_quality_validation_run_versions_check CHECK (
    public.data_quality_version_valid_v1(projection_schema_version)
    AND public.data_quality_version_valid_v1(projection_policy_version)
    AND public.data_quality_version_valid_v1(validator_version)
    AND public.data_quality_version_valid_v1(quality_policy_version)),
  CONSTRAINT data_quality_validation_run_retention_check
    CHECK (expires_at >= created_at + interval '90 days')
);

CREATE TABLE public.data_quality_validation_status_evidence_v1 (
  validation_status_evidence_id uuid PRIMARY KEY DEFAULT public.gen_random_uuid(),
  validation_run_ref uuid NOT NULL REFERENCES public.data_quality_validation_run_v1(validation_run_id) ON DELETE RESTRICT,
  validation_status varchar(16) NOT NULL CHECK (validation_status IN ('STARTED','COMPLETED','FAILED','CONFLICTED')),
  failure_code varchar(96),
  observed_at timestamptz NOT NULL,
  retention_class varchar(40) NOT NULL DEFAULT 'data_quality_evidence_90d'
    CHECK (retention_class = 'data_quality_evidence_90d'),
  retention_policy_version varchar(96) NOT NULL DEFAULT 'data-retention-policy-v1'
    CHECK (retention_policy_version = 'data-retention-policy-v1'),
  expires_at timestamptz NOT NULL DEFAULT (CURRENT_TIMESTAMP + interval '90 days'),
  created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT data_quality_validation_status_failure_check CHECK (
    (validation_status IN ('FAILED','CONFLICTED') AND failure_code IS NOT NULL
      AND public.data_quality_failure_code_valid_v1(failure_code))
    OR (validation_status IN ('STARTED','COMPLETED') AND failure_code IS NULL)),
  CONSTRAINT data_quality_validation_status_retention_check
    CHECK (expires_at >= created_at + interval '90 days')
);

CREATE TABLE public.data_quality_validation_check_result_v1 (
  validation_check_id uuid PRIMARY KEY DEFAULT public.gen_random_uuid(),
  validation_run_ref uuid NOT NULL REFERENCES public.data_quality_validation_run_v1(validation_run_id) ON DELETE RESTRICT,
  check_code varchar(96) NOT NULL CHECK (check_code ~ '^[a-z][a-z0-9_.]{0,95}$'),
  check_scope varchar(32) NOT NULL CHECK (check_scope IN (
    'SOURCE_COMPLETENESS','PROJECTION_COMPLETENESS','SNAPSHOT_CONSISTENCY','LINEAGE_INTEGRITY',
    'IDENTITY_INTEGRITY','EXPOSURE_INTEGRITY','DETERMINISTIC_REBUILD')),
  expected_value jsonb NOT NULL,
  observed_value jsonb NOT NULL,
  difference_value jsonb NOT NULL,
  severity varchar(8) NOT NULL CHECK (severity IN ('INFO','WARNING','ERROR','BLOCKER')),
  check_status varchar(16) NOT NULL CHECK (check_status IN ('PASS','FAIL','SKIPPED','NOT_APPLICABLE')),
  failure_code varchar(96),
  reason_code varchar(96),
  required_check boolean NOT NULL,
  evidence_fingerprint varchar(64) NOT NULL
    CHECK (public.data_quality_fingerprint_valid_v1(evidence_fingerprint)),
  retention_class varchar(40) NOT NULL DEFAULT 'data_quality_evidence_90d'
    CHECK (retention_class = 'data_quality_evidence_90d'),
  retention_policy_version varchar(96) NOT NULL DEFAULT 'data-retention-policy-v1'
    CHECK (retention_policy_version = 'data-retention-policy-v1'),
  expires_at timestamptz NOT NULL DEFAULT (CURRENT_TIMESTAMP + interval '90 days'),
  created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT data_quality_check_failure_check CHECK (
    (check_status = 'FAIL' AND failure_code IS NOT NULL
      AND public.data_quality_failure_code_valid_v1(failure_code))
    OR (check_status <> 'FAIL' AND failure_code IS NULL)),
  CONSTRAINT data_quality_check_reason_check CHECK (
    (check_status IN ('SKIPPED','NOT_APPLICABLE') AND reason_code IS NOT NULL
      AND reason_code ~ '^[a-z][a-z0-9_]{0,95}$')
    OR (check_status IN ('PASS','FAIL') AND reason_code IS NULL)),
  CONSTRAINT data_quality_check_unexecuted_not_pass_check
    CHECK (check_status <> 'PASS' OR observed_value <> '"not_executed"'::jsonb),
  CONSTRAINT data_quality_check_retention_check
    CHECK (expires_at >= created_at + interval '90 days'),
  UNIQUE (validation_run_ref, check_code)
);

CREATE TABLE public.data_quality_anomaly_evidence_v1 (
  quality_anomaly_id uuid PRIMARY KEY DEFAULT public.gen_random_uuid(),
  validation_run_ref uuid NOT NULL REFERENCES public.data_quality_validation_run_v1(validation_run_id) ON DELETE RESTRICT,
  anomaly_scope varchar(32) NOT NULL,
  failure_code varchar(96) NOT NULL CHECK (public.data_quality_failure_code_valid_v1(failure_code)),
  severity varchar(8) NOT NULL CHECK (severity IN ('WARNING','ERROR','BLOCKER')),
  evidence_reference varchar(180) NOT NULL,
  evidence_fingerprint varchar(64) NOT NULL
    CHECK (public.data_quality_fingerprint_valid_v1(evidence_fingerprint)),
  retention_class varchar(40) NOT NULL DEFAULT 'data_quality_evidence_90d'
    CHECK (retention_class = 'data_quality_evidence_90d'),
  retention_policy_version varchar(96) NOT NULL DEFAULT 'data-retention-policy-v1'
    CHECK (retention_policy_version = 'data-retention-policy-v1'),
  expires_at timestamptz NOT NULL DEFAULT (CURRENT_TIMESTAMP + interval '90 days'),
  created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT data_quality_anomaly_retention_check
    CHECK (expires_at >= created_at + interval '90 days')
);

CREATE INDEX data_quality_validation_run_scope_idx
  ON public.data_quality_validation_run_v1(validation_scope, quality_policy_version, created_at DESC);
CREATE INDEX data_quality_validation_status_idx
  ON public.data_quality_validation_status_evidence_v1(validation_status, observed_at DESC);
CREATE INDEX data_quality_check_failure_idx
  ON public.data_quality_validation_check_result_v1(failure_code, severity, created_at DESC)
  WHERE failure_code IS NOT NULL;
CREATE INDEX data_quality_anomaly_failure_idx
  ON public.data_quality_anomaly_evidence_v1(failure_code, severity, created_at DESC);

CREATE TRIGGER data_quality_validation_run_append_only BEFORE UPDATE OR DELETE
  ON public.data_quality_validation_run_v1 FOR EACH ROW EXECUTE FUNCTION public.prevent_data_event_append_only_mutation_v1();
CREATE TRIGGER data_quality_validation_status_append_only BEFORE UPDATE OR DELETE
  ON public.data_quality_validation_status_evidence_v1 FOR EACH ROW EXECUTE FUNCTION public.prevent_data_event_append_only_mutation_v1();
CREATE TRIGGER data_quality_validation_check_append_only BEFORE UPDATE OR DELETE
  ON public.data_quality_validation_check_result_v1 FOR EACH ROW EXECUTE FUNCTION public.prevent_data_event_append_only_mutation_v1();
CREATE TRIGGER data_quality_anomaly_append_only BEFORE UPDATE OR DELETE
  ON public.data_quality_anomaly_evidence_v1 FOR EACH ROW EXECUTE FUNCTION public.prevent_data_event_append_only_mutation_v1();

COMMIT;
