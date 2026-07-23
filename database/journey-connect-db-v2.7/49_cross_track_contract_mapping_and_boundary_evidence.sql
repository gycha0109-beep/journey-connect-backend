-- Journey Connect DB v2.7 extension - DP-7 contract mapping and boundary evidence
-- Target: PostgreSQL 15 and 18. Prerequisite: SQL 01..48.
BEGIN;

CREATE TABLE public.data_cross_track_contract_mapping_evidence_v1 (
  mapping_evidence_id uuid PRIMARY KEY DEFAULT public.gen_random_uuid(),
  integration_run_ref uuid NOT NULL UNIQUE REFERENCES public.data_cross_track_integration_run_v1(integration_run_id) ON DELETE RESTRICT,
  source_contract varchar(96) NOT NULL,
  source_schema_version varchar(96) NOT NULL,
  target_contract varchar(96) NOT NULL,
  target_schema_version varchar(96) NOT NULL,
  mapping_policy_version varchar(96) NOT NULL CHECK (mapping_policy_version='data-cross-track-mapping-policy-v1'),
  target_contract_present boolean NOT NULL,
  target_authority_confirmed boolean NOT NULL,
  schema_supported boolean NOT NULL,
  required_fields_present boolean NOT NULL,
  semantics_compatible boolean NOT NULL,
  units_compatible boolean NOT NULL,
  domain_mapping_approved boolean NOT NULL,
  missing_required_fields jsonb NOT NULL CHECK (jsonb_typeof(missing_required_fields)='array'),
  semantic_mappings jsonb NOT NULL CHECK (jsonb_typeof(semantic_mappings)='object'),
  unit_mappings jsonb NOT NULL CHECK (jsonb_typeof(unit_mappings)='object'),
  mapping_fingerprint varchar(64) NOT NULL CHECK (public.data_integration_fingerprint_valid_v1(mapping_fingerprint)),
  retention_class varchar(48) NOT NULL DEFAULT 'cross_track_integration_evidence_90d' CHECK (retention_class='cross_track_integration_evidence_90d'),
  retention_policy_version varchar(96) NOT NULL DEFAULT 'data-retention-policy-v1' CHECK (retention_policy_version='data-retention-policy-v1'),
  expires_at timestamptz NOT NULL DEFAULT (CURRENT_TIMESTAMP+interval '90 days'),
  created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CHECK (expires_at>=created_at+interval '90 days')
);

CREATE TABLE public.data_cross_track_identity_evidence_v1 (
  identity_evidence_id uuid PRIMARY KEY DEFAULT public.gen_random_uuid(),
  integration_run_ref uuid NOT NULL UNIQUE REFERENCES public.data_cross_track_integration_run_v1(integration_run_id) ON DELETE RESTRICT,
  source_identity_namespace varchar(180) NOT NULL,
  target_identity_namespace varchar(180) NOT NULL,
  binding_version varchar(96) NOT NULL,
  binding_source varchar(96) NOT NULL,
  binding_scope varchar(96) NOT NULL CHECK (binding_scope='cross-track-integration'),
  owner_track varchar(32) NOT NULL CHECK (owner_track='Data'),
  automatic_merge boolean NOT NULL CHECK (automatic_merge=false),
  binding_fingerprint varchar(64) NOT NULL CHECK (public.data_integration_fingerprint_valid_v1(binding_fingerprint)),
  validation_status varchar(16) NOT NULL CHECK (validation_status IN ('PASS','FAIL','SKIPPED','NOT_APPLICABLE')),
  failure_code varchar(96),
  retention_class varchar(48) NOT NULL DEFAULT 'cross_track_integration_evidence_90d' CHECK (retention_class='cross_track_integration_evidence_90d'),
  retention_policy_version varchar(96) NOT NULL DEFAULT 'data-retention-policy-v1' CHECK (retention_policy_version='data-retention-policy-v1'),
  expires_at timestamptz NOT NULL DEFAULT (CURRENT_TIMESTAMP+interval '90 days'),
  created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CHECK ((validation_status='FAIL' AND failure_code IS NOT NULL AND public.data_integration_failure_code_valid_v1(failure_code)) OR (validation_status<>'FAIL' AND failure_code IS NULL)),
  CHECK (source_identity_namespace ~ '^(subject:[A-Za-z0-9][A-Za-z0-9._~-]{0,127}|user:[1-9][0-9]*)$'),
  CHECK (target_identity_namespace ~ '^(subject:[A-Za-z0-9][A-Za-z0-9._~-]{0,127}|user:[1-9][0-9]*)$'),
  CHECK (expires_at>=created_at+interval '90 days')
);

CREATE TABLE public.data_cross_track_authority_evidence_v1 (
  authority_evidence_id uuid PRIMARY KEY DEFAULT public.gen_random_uuid(),
  integration_run_ref uuid NOT NULL REFERENCES public.data_cross_track_integration_run_v1(integration_run_id) ON DELETE RESTRICT,
  object_name varchar(96) NOT NULL,
  owning_track varchar(32) NOT NULL,
  actor_track varchar(32) NOT NULL,
  read_allowed boolean NOT NULL,
  write_allowed boolean NOT NULL,
  validation_allowed boolean NOT NULL,
  production_allowed boolean NOT NULL,
  read_attempted boolean NOT NULL,
  write_attempted boolean NOT NULL,
  validation_attempted boolean NOT NULL,
  production_attempted boolean NOT NULL,
  validation_status varchar(16) NOT NULL CHECK (validation_status IN ('PASS','FAIL','SKIPPED','NOT_APPLICABLE')),
  failure_code varchar(96),
  evidence_fingerprint varchar(64) NOT NULL CHECK (public.data_integration_fingerprint_valid_v1(evidence_fingerprint)),
  retention_class varchar(48) NOT NULL DEFAULT 'cross_track_integration_evidence_90d' CHECK (retention_class='cross_track_integration_evidence_90d'),
  retention_policy_version varchar(96) NOT NULL DEFAULT 'data-retention-policy-v1' CHECK (retention_policy_version='data-retention-policy-v1'),
  expires_at timestamptz NOT NULL DEFAULT (CURRENT_TIMESTAMP+interval '90 days'),
  created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CHECK ((validation_status='FAIL' AND failure_code IS NOT NULL AND public.data_integration_failure_code_valid_v1(failure_code)) OR (validation_status<>'FAIL' AND failure_code IS NULL)),
  CHECK (expires_at>=created_at+interval '90 days'),
  UNIQUE(integration_run_ref,object_name)
);

CREATE TABLE public.data_cross_track_privacy_evidence_v1 (
  privacy_evidence_id uuid PRIMARY KEY DEFAULT public.gen_random_uuid(),
  integration_run_ref uuid NOT NULL UNIQUE REFERENCES public.data_cross_track_integration_run_v1(integration_run_id) ON DELETE RESTRICT,
  source_privacy_class varchar(32) NOT NULL,
  target_privacy_class varchar(32) NOT NULL,
  raw_payload_present boolean NOT NULL,
  pii_present boolean NOT NULL,
  raw_text_present boolean NOT NULL,
  precise_location_present boolean NOT NULL,
  aggregate_only boolean NOT NULL,
  lineage_purpose_bound boolean NOT NULL,
  reidentification_risk boolean NOT NULL,
  validation_status varchar(16) NOT NULL CHECK (validation_status IN ('PASS','FAIL','SKIPPED','NOT_APPLICABLE')),
  failure_code varchar(96),
  evidence_fingerprint varchar(64) NOT NULL CHECK (public.data_integration_fingerprint_valid_v1(evidence_fingerprint)),
  retention_class varchar(48) NOT NULL DEFAULT 'cross_track_integration_evidence_90d' CHECK (retention_class='cross_track_integration_evidence_90d'),
  retention_policy_version varchar(96) NOT NULL DEFAULT 'data-retention-policy-v1' CHECK (retention_policy_version='data-retention-policy-v1'),
  expires_at timestamptz NOT NULL DEFAULT (CURRENT_TIMESTAMP+interval '90 days'),
  created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CHECK ((validation_status='FAIL' AND failure_code IS NOT NULL AND public.data_integration_failure_code_valid_v1(failure_code)) OR (validation_status<>'FAIL' AND failure_code IS NULL)),
  CHECK (expires_at>=created_at+interval '90 days')
);

CREATE TABLE public.data_cross_track_retention_evidence_v1 (
  retention_evidence_id uuid PRIMARY KEY DEFAULT public.gen_random_uuid(),
  integration_run_ref uuid NOT NULL UNIQUE REFERENCES public.data_cross_track_integration_run_v1(integration_run_id) ON DELETE RESTRICT,
  source_retention_days integer NOT NULL CHECK (source_retention_days>0),
  target_retention_days integer NOT NULL CHECK (target_retention_days>0),
  integration_evidence_retention_days integer NOT NULL CHECK (integration_evidence_retention_days=90),
  deletion_semantics_aligned boolean NOT NULL,
  automatic_purge_enabled boolean NOT NULL CHECK (automatic_purge_enabled=false),
  physical_delete_enabled boolean NOT NULL CHECK (physical_delete_enabled=false),
  validation_status varchar(16) NOT NULL CHECK (validation_status IN ('PASS','FAIL','SKIPPED','NOT_APPLICABLE')),
  failure_code varchar(96),
  evidence_fingerprint varchar(64) NOT NULL CHECK (public.data_integration_fingerprint_valid_v1(evidence_fingerprint)),
  retention_class varchar(48) NOT NULL DEFAULT 'cross_track_integration_evidence_90d' CHECK (retention_class='cross_track_integration_evidence_90d'),
  retention_policy_version varchar(96) NOT NULL DEFAULT 'data-retention-policy-v1' CHECK (retention_policy_version='data-retention-policy-v1'),
  expires_at timestamptz NOT NULL DEFAULT (CURRENT_TIMESTAMP+interval '90 days'),
  created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CHECK ((validation_status='FAIL' AND failure_code IS NOT NULL AND public.data_integration_failure_code_valid_v1(failure_code)) OR (validation_status<>'FAIL' AND failure_code IS NULL)),
  CHECK (expires_at>=created_at+interval '90 days')
);

CREATE TRIGGER data_cross_track_mapping_append_only BEFORE UPDATE OR DELETE ON public.data_cross_track_contract_mapping_evidence_v1 FOR EACH ROW EXECUTE FUNCTION public.prevent_data_event_append_only_mutation_v1();
CREATE TRIGGER data_cross_track_identity_append_only BEFORE UPDATE OR DELETE ON public.data_cross_track_identity_evidence_v1 FOR EACH ROW EXECUTE FUNCTION public.prevent_data_event_append_only_mutation_v1();
CREATE TRIGGER data_cross_track_authority_append_only BEFORE UPDATE OR DELETE ON public.data_cross_track_authority_evidence_v1 FOR EACH ROW EXECUTE FUNCTION public.prevent_data_event_append_only_mutation_v1();
CREATE TRIGGER data_cross_track_privacy_append_only BEFORE UPDATE OR DELETE ON public.data_cross_track_privacy_evidence_v1 FOR EACH ROW EXECUTE FUNCTION public.prevent_data_event_append_only_mutation_v1();
CREATE TRIGGER data_cross_track_retention_append_only BEFORE UPDATE OR DELETE ON public.data_cross_track_retention_evidence_v1 FOR EACH ROW EXECUTE FUNCTION public.prevent_data_event_append_only_mutation_v1();

COMMIT;
