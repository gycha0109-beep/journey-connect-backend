-- Journey Connect DB v2.7 extension - DP-7 cross-track integration validation foundation
-- Target: PostgreSQL 15 and 18. Prerequisite: SQL 01..47.
-- Validation-only append evidence. No Recommendation, Intelligence or Search runtime activation.
BEGIN;

DO $$
BEGIN
  IF to_regclass('public.data_projection_snapshot_v1') IS NULL
     OR to_regclass('public.data_snapshot_quality_verdict_v1') IS NULL
     OR to_regprocedure('public.data_projection_fingerprint_v1(varchar,jsonb)') IS NULL
     OR to_regprocedure('public.prevent_data_event_append_only_mutation_v1()') IS NULL THEN
    RAISE EXCEPTION 'DP-7 prerequisite DP-5/DP-6 objects are missing.' USING ERRCODE='42P01';
  END IF;
END;
$$;

CREATE OR REPLACE FUNCTION public.data_integration_version_valid_v1(p_value varchar)
RETURNS boolean LANGUAGE sql IMMUTABLE STRICT SECURITY INVOKER
SET search_path=pg_catalog,pg_temp
AS $$ SELECT length(p_value)<=96 AND p_value ~ '^[a-z0-9]+(?:-[a-z0-9]+)*-v[1-9][0-9]*$'; $$;

CREATE OR REPLACE FUNCTION public.data_integration_fingerprint_valid_v1(p_value varchar)
RETURNS boolean LANGUAGE sql IMMUTABLE STRICT SECURITY INVOKER
SET search_path=pg_catalog,pg_temp
AS $$ SELECT p_value ~ '^[0-9a-f]{64}$'; $$;

CREATE OR REPLACE FUNCTION public.data_integration_fingerprint_v1(p_domain varchar,p_value jsonb)
RETURNS varchar LANGUAGE sql IMMUTABLE STRICT SECURITY INVOKER
SET search_path=pg_catalog,public,pg_temp
AS $$ SELECT public.data_projection_fingerprint_v1(p_domain,p_value); $$;

CREATE OR REPLACE FUNCTION public.data_integration_failure_code_valid_v1(p_value varchar)
RETURNS boolean LANGUAGE sql IMMUTABLE STRICT SECURITY INVOKER
SET search_path=pg_catalog,pg_temp
AS $$
  SELECT p_value IN (
    'recommendation_contract_missing','recommendation_schema_unsupported','recommendation_required_field_missing',
    'recommendation_field_semantic_mismatch','recommendation_unit_mismatch','recommendation_identity_namespace_mismatch',
    'recommendation_window_mismatch','recommendation_quality_verdict_invalid','recommendation_lineage_missing',
    'recommendation_fingerprint_mismatch','recommendation_authority_violation','recommendation_metric_semantic_violation',
    'recommendation_production_write_detected','intelligence_contract_missing','intelligence_domain_mapping_missing',
    'intelligence_schema_unsupported','intelligence_required_field_missing','intelligence_field_semantic_mismatch',
    'intelligence_identity_namespace_mismatch','intelligence_quality_semantic_mismatch','intelligence_lineage_missing',
    'intelligence_privacy_violation','intelligence_retention_conflict','intelligence_runtime_activation_detected',
    'intelligence_authority_violation','search_contract_missing','search_schema_unsupported','search_required_field_missing',
    'search_document_identity_mismatch','search_region_semantic_mismatch','search_content_semantic_mismatch',
    'search_tag_semantic_mismatch','search_quality_verdict_invalid','search_lineage_missing','search_fingerprint_mismatch',
    'search_privacy_violation','search_retention_conflict','search_production_index_write_detected','search_cutover_violation',
    'search_authority_violation','cross_track_identity_binding_missing','cross_track_identity_binding_invalid',
    'cross_track_identity_fingerprint_mismatch','cross_track_identity_namespace_mismatch',
    'cross_track_identity_scope_mismatch','cross_track_identity_authority_violation',
    'cross_track_identity_automatic_merge_detected','cross_track_read_authority_violation',
    'cross_track_write_authority_violation','cross_track_validation_authority_violation',
    'cross_track_production_authority_violation','cross_track_object_ownership_conflict',
    'cross_track_privacy_class_mismatch','cross_track_pii_exposure','cross_track_raw_payload_exposure',
    'cross_track_precise_location_exposure','cross_track_lineage_access_violation','cross_track_reidentification_risk',
    'cross_track_retention_conflict','cross_track_deletion_semantic_conflict','quality_verdict_missing',
    'quality_verdict_rejected','quality_verdict_inconclusive','quality_verdict_conflicted','quality_policy_unsupported',
    'quality_verdict_fingerprint_mismatch','quality_snapshot_mismatch','quality_verdict_authority_violation',
    'cross_track_fingerprint_invalid','CROSS_TRACK_INTEGRATION_VERDICT_CONFLICT'
  );
$$;

CREATE TABLE public.data_cross_track_integration_policy_v1 (
  integration_policy_version varchar(96) PRIMARY KEY CHECK (integration_policy_version='data-cross-track-integration-policy-v1'),
  supported_source_contracts jsonb NOT NULL CHECK (jsonb_typeof(supported_source_contracts)='array'),
  supported_target_contracts jsonb NOT NULL CHECK (jsonb_typeof(supported_target_contracts)='array'),
  supported_schema_versions jsonb NOT NULL CHECK (jsonb_typeof(supported_schema_versions)='array'),
  required_checks jsonb NOT NULL CHECK (jsonb_typeof(required_checks)='array' AND jsonb_array_length(required_checks)>0),
  required_quality_policy_versions jsonb NOT NULL CHECK (jsonb_typeof(required_quality_policy_versions)='array'),
  required_quality_verdict varchar(16) NOT NULL CHECK (required_quality_verdict='VALIDATED'),
  identity_namespace_rules jsonb NOT NULL CHECK (jsonb_typeof(identity_namespace_rules)='object'),
  authority_rules jsonb NOT NULL CHECK (jsonb_typeof(authority_rules)='object'),
  privacy_rules jsonb NOT NULL CHECK (jsonb_typeof(privacy_rules)='object'),
  retention_rules jsonb NOT NULL CHECK (jsonb_typeof(retention_rules)='object'),
  lineage_requirements jsonb NOT NULL CHECK (jsonb_typeof(lineage_requirements)='object'),
  mapping_policy_versions jsonb NOT NULL CHECK (jsonb_typeof(mapping_policy_versions)='array'),
  conditional_compatibility_rules jsonb NOT NULL CHECK (jsonb_typeof(conditional_compatibility_rules)='array'),
  inconclusive_rules jsonb NOT NULL CHECK (jsonb_typeof(inconclusive_rules)='array'),
  blocker_failure_codes jsonb NOT NULL CHECK (jsonb_typeof(blocker_failure_codes)='array'),
  policy_fingerprint varchar(64) NOT NULL CHECK (public.data_integration_fingerprint_valid_v1(policy_fingerprint)),
  retention_class varchar(48) NOT NULL DEFAULT 'cross_track_integration_evidence_90d' CHECK (retention_class='cross_track_integration_evidence_90d'),
  retention_policy_version varchar(96) NOT NULL DEFAULT 'data-retention-policy-v1' CHECK (retention_policy_version='data-retention-policy-v1'),
  expires_at timestamptz NOT NULL DEFAULT (CURRENT_TIMESTAMP+interval '90 days'),
  created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CHECK (expires_at>=created_at+interval '90 days')
);

INSERT INTO public.data_cross_track_integration_policy_v1(
  integration_policy_version,supported_source_contracts,supported_target_contracts,supported_schema_versions,
  required_checks,required_quality_policy_versions,required_quality_verdict,identity_namespace_rules,authority_rules,
  privacy_rules,retention_rules,lineage_requirements,mapping_policy_versions,conditional_compatibility_rules,
  inconclusive_rules,blocker_failure_codes,policy_fingerprint)
VALUES(
  'data-cross-track-integration-policy-v1',
  '["recommendation-profile-input-v1","experiment-outcome-input-v1"]',
  '["recommendation-profile-input-v1","recommendation-evaluation-dataset-v1","intelligence-input-snapshot-v1","search-document-projection-v1"]',
  '["recommendation-profile-input-v1","experiment-outcome-input-v1","recommendation-evaluation-dataset-v1","intelligence-input-snapshot-v1","search-document-projection-v1"]',
  '["quality.verdict","target.contract","schema","semantics","units","identity","authority","privacy","retention","fingerprint"]',
  '["data-quality-policy-v1"]','VALIDATED',
  '{"supported":["subject:<opaque-id>","user:<numeric-id>"],"automaticMerge":false}',
  '{"dp7Authority":"integration-evidence-only","productionAuthority":false}',
  '{"rawPayload":false,"rawPii":false,"rawText":false,"preciseLocation":false}',
  '{"integrationEvidenceDays":90,"automaticPurge":false,"physicalDelete":false}',
  '{"required":true,"rawLineageReaderAccess":false}',
  '["data-cross-track-mapping-policy-v1"]',
  '["recommendation_authority_preserved"]',
  '["target_contract_missing","required_check_skipped","required_evidence_missing"]',
  '["authority_violation","privacy_violation","identity_ambiguity","fingerprint_mismatch"]',
  public.data_integration_fingerprint_v1('data-cross-track-integration-policy-sha256-v1',
    '{"integrationPolicyVersion":"data-cross-track-integration-policy-v1","requiredQualityVerdict":"VALIDATED","integrationEvidenceDays":90}'::jsonb)
);

CREATE TABLE public.data_cross_track_integration_run_v1 (
  integration_run_id uuid PRIMARY KEY DEFAULT public.gen_random_uuid(),
  integration_run_ref varchar(180) NOT NULL UNIQUE,
  logical_identity_hash varchar(64) NOT NULL UNIQUE CHECK (public.data_integration_fingerprint_valid_v1(logical_identity_hash)),
  integration_scope varchar(64) NOT NULL CHECK (integration_scope IN (
    'DATA_RECOMMENDATION_PROFILE','DATA_RECOMMENDATION_EXPERIMENT_OUTCOME','DATA_INTELLIGENCE_INPUT','DATA_SEARCH_INPUT',
    'IDENTITY_BOUNDARY','AUTHORITY_BOUNDARY','PRIVACY_BOUNDARY','RETENTION_BOUNDARY','FINGERPRINT_BOUNDARY','QUALITY_VERDICT_BOUNDARY','FULL')),
  source_track varchar(32) NOT NULL CHECK (source_track='Data'),
  target_track varchar(32) NOT NULL CHECK (target_track IN ('Recommendation','Intelligence','Search','Data')),
  source_contract varchar(96) NOT NULL,
  source_schema_version varchar(96) NOT NULL,
  target_contract varchar(96) NOT NULL,
  target_schema_version varchar(96) NOT NULL,
  target_contract_confirmed boolean NOT NULL,
  source_snapshot_ref uuid NOT NULL REFERENCES public.data_projection_snapshot_v1(snapshot_id) ON DELETE RESTRICT,
  source_quality_verdict_ref uuid NOT NULL REFERENCES public.data_snapshot_quality_verdict_v1(snapshot_quality_verdict_id) ON DELETE RESTRICT,
  mapping_policy_version varchar(96) NOT NULL CHECK (mapping_policy_version='data-cross-track-mapping-policy-v1'),
  integration_policy_version varchar(96) NOT NULL REFERENCES public.data_cross_track_integration_policy_v1(integration_policy_version) ON DELETE RESTRICT,
  validator_version varchar(96) NOT NULL CHECK (validator_version='data-cross-track-integration-validator-v1'),
  validation_as_of timestamptz NOT NULL,
  integration_input_fingerprint varchar(64) NOT NULL CHECK (public.data_integration_fingerprint_valid_v1(integration_input_fingerprint)),
  integration_mapping_fingerprint varchar(64) NOT NULL CHECK (public.data_integration_fingerprint_valid_v1(integration_mapping_fingerprint)),
  contract_matrix_fingerprint varchar(64) NOT NULL CHECK (public.data_integration_fingerprint_valid_v1(contract_matrix_fingerprint)),
  retention_class varchar(48) NOT NULL DEFAULT 'cross_track_integration_evidence_90d' CHECK (retention_class='cross_track_integration_evidence_90d'),
  retention_policy_version varchar(96) NOT NULL DEFAULT 'data-retention-policy-v1' CHECK (retention_policy_version='data-retention-policy-v1'),
  expires_at timestamptz NOT NULL DEFAULT (CURRENT_TIMESTAMP+interval '90 days'),
  created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CHECK (public.data_integration_version_valid_v1(source_contract) AND public.data_integration_version_valid_v1(source_schema_version)
    AND public.data_integration_version_valid_v1(target_contract) AND public.data_integration_version_valid_v1(target_schema_version)),
  CHECK (expires_at>=created_at+interval '90 days')
);

CREATE TABLE public.data_cross_track_integration_status_evidence_v1 (
  integration_status_evidence_id uuid PRIMARY KEY DEFAULT public.gen_random_uuid(),
  integration_run_ref uuid NOT NULL REFERENCES public.data_cross_track_integration_run_v1(integration_run_id) ON DELETE RESTRICT,
  integration_status varchar(16) NOT NULL CHECK (integration_status IN ('STARTED','COMPLETED','FAILED','CONFLICTED')),
  failure_code varchar(96),
  observed_at timestamptz NOT NULL,
  retention_class varchar(48) NOT NULL DEFAULT 'cross_track_integration_evidence_90d' CHECK (retention_class='cross_track_integration_evidence_90d'),
  retention_policy_version varchar(96) NOT NULL DEFAULT 'data-retention-policy-v1' CHECK (retention_policy_version='data-retention-policy-v1'),
  expires_at timestamptz NOT NULL DEFAULT (CURRENT_TIMESTAMP+interval '90 days'),
  created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CHECK ((integration_status IN ('FAILED','CONFLICTED') AND failure_code IS NOT NULL AND public.data_integration_failure_code_valid_v1(failure_code))
    OR (integration_status IN ('STARTED','COMPLETED') AND failure_code IS NULL)),
  CHECK (expires_at>=created_at+interval '90 days')
);

CREATE TABLE public.data_cross_track_integration_check_result_v1 (
  integration_check_id uuid PRIMARY KEY DEFAULT public.gen_random_uuid(),
  integration_run_ref uuid NOT NULL REFERENCES public.data_cross_track_integration_run_v1(integration_run_id) ON DELETE RESTRICT,
  check_order integer NOT NULL CHECK (check_order>=0),
  check_code varchar(96) NOT NULL CHECK (check_code ~ '^[a-z][a-z0-9_.]{0,95}$'),
  check_scope varchar(64) NOT NULL,
  source_reference varchar(180),
  target_reference varchar(180),
  expected_value jsonb NOT NULL,
  observed_value jsonb NOT NULL,
  severity varchar(8) NOT NULL CHECK (severity IN ('INFO','WARNING','ERROR','BLOCKER')),
  check_status varchar(16) NOT NULL CHECK (check_status IN ('PASS','FAIL','SKIPPED','NOT_APPLICABLE')),
  failure_code varchar(96),
  required_check boolean NOT NULL,
  conditional_requirement boolean NOT NULL,
  evidence_fingerprint varchar(64) NOT NULL CHECK (public.data_integration_fingerprint_valid_v1(evidence_fingerprint)),
  retention_class varchar(48) NOT NULL DEFAULT 'cross_track_integration_evidence_90d' CHECK (retention_class='cross_track_integration_evidence_90d'),
  retention_policy_version varchar(96) NOT NULL DEFAULT 'data-retention-policy-v1' CHECK (retention_policy_version='data-retention-policy-v1'),
  expires_at timestamptz NOT NULL DEFAULT (CURRENT_TIMESTAMP+interval '90 days'),
  created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CHECK ((check_status='FAIL' AND failure_code IS NOT NULL AND public.data_integration_failure_code_valid_v1(failure_code))
    OR (check_status IN ('PASS','NOT_APPLICABLE') AND failure_code IS NULL)
    OR (check_status='SKIPPED' AND (failure_code IS NULL OR public.data_integration_failure_code_valid_v1(failure_code)))),
  CHECK (check_status<>'PASS' OR observed_value<>'"not_executed"'::jsonb),
  CHECK (expires_at>=created_at+interval '90 days'),
  UNIQUE(integration_run_ref,check_code)
);

CREATE TABLE public.data_cross_track_integration_anomaly_v1 (
  integration_anomaly_id uuid PRIMARY KEY DEFAULT public.gen_random_uuid(),
  integration_run_ref uuid NOT NULL REFERENCES public.data_cross_track_integration_run_v1(integration_run_id) ON DELETE RESTRICT,
  check_code varchar(96) NOT NULL,
  failure_code varchar(96) NOT NULL CHECK (public.data_integration_failure_code_valid_v1(failure_code)),
  severity varchar(8) NOT NULL CHECK (severity IN ('WARNING','ERROR','BLOCKER')),
  evidence_fingerprint varchar(64) NOT NULL CHECK (public.data_integration_fingerprint_valid_v1(evidence_fingerprint)),
  retention_class varchar(48) NOT NULL DEFAULT 'cross_track_integration_evidence_90d' CHECK (retention_class='cross_track_integration_evidence_90d'),
  retention_policy_version varchar(96) NOT NULL DEFAULT 'data-retention-policy-v1' CHECK (retention_policy_version='data-retention-policy-v1'),
  expires_at timestamptz NOT NULL DEFAULT (CURRENT_TIMESTAMP+interval '90 days'),
  created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CHECK (expires_at>=created_at+interval '90 days')
);

CREATE INDEX data_cross_track_run_scope_idx ON public.data_cross_track_integration_run_v1(target_track,integration_scope,created_at DESC);
CREATE INDEX data_cross_track_check_failure_idx ON public.data_cross_track_integration_check_result_v1(failure_code,severity,created_at DESC) WHERE failure_code IS NOT NULL;
CREATE INDEX data_cross_track_status_idx ON public.data_cross_track_integration_status_evidence_v1(integration_status,observed_at DESC);

CREATE TRIGGER data_cross_track_policy_append_only BEFORE UPDATE OR DELETE ON public.data_cross_track_integration_policy_v1 FOR EACH ROW EXECUTE FUNCTION public.prevent_data_event_append_only_mutation_v1();
CREATE TRIGGER data_cross_track_run_append_only BEFORE UPDATE OR DELETE ON public.data_cross_track_integration_run_v1 FOR EACH ROW EXECUTE FUNCTION public.prevent_data_event_append_only_mutation_v1();
CREATE TRIGGER data_cross_track_status_append_only BEFORE UPDATE OR DELETE ON public.data_cross_track_integration_status_evidence_v1 FOR EACH ROW EXECUTE FUNCTION public.prevent_data_event_append_only_mutation_v1();
CREATE TRIGGER data_cross_track_check_append_only BEFORE UPDATE OR DELETE ON public.data_cross_track_integration_check_result_v1 FOR EACH ROW EXECUTE FUNCTION public.prevent_data_event_append_only_mutation_v1();
CREATE TRIGGER data_cross_track_anomaly_append_only BEFORE UPDATE OR DELETE ON public.data_cross_track_integration_anomaly_v1 FOR EACH ROW EXECUTE FUNCTION public.prevent_data_event_append_only_mutation_v1();

COMMIT;
