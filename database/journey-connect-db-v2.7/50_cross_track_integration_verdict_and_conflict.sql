-- Journey Connect DB v2.7 extension - DP-7 quality binding, verdict and conflict evidence
-- Target: PostgreSQL 15 and 18. Prerequisite: SQL 01..49.
BEGIN;

CREATE TABLE public.data_cross_track_quality_verdict_binding_v1 (
  quality_binding_id uuid PRIMARY KEY DEFAULT public.gen_random_uuid(),
  integration_run_ref uuid NOT NULL UNIQUE REFERENCES public.data_cross_track_integration_run_v1(integration_run_id) ON DELETE RESTRICT,
  source_snapshot_ref uuid NOT NULL REFERENCES public.data_projection_snapshot_v1(snapshot_id) ON DELETE RESTRICT,
  source_quality_verdict_ref uuid NOT NULL REFERENCES public.data_snapshot_quality_verdict_v1(snapshot_quality_verdict_id) ON DELETE RESTRICT,
  quality_policy_version varchar(96) NOT NULL CHECK (quality_policy_version='data-quality-policy-v1'),
  quality_status varchar(16) NOT NULL CHECK (quality_status='VALIDATED'),
  quality_verdict_fingerprint varchar(64) NOT NULL CHECK (public.data_integration_fingerprint_valid_v1(quality_verdict_fingerprint)),
  binding_fingerprint varchar(64) NOT NULL CHECK (public.data_integration_fingerprint_valid_v1(binding_fingerprint)),
  retention_class varchar(48) NOT NULL DEFAULT 'cross_track_integration_evidence_90d' CHECK (retention_class='cross_track_integration_evidence_90d'),
  retention_policy_version varchar(96) NOT NULL DEFAULT 'data-retention-policy-v1' CHECK (retention_policy_version='data-retention-policy-v1'),
  expires_at timestamptz NOT NULL DEFAULT (CURRENT_TIMESTAMP+interval '90 days'),
  created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CHECK (expires_at>=created_at+interval '90 days')
);

CREATE TABLE public.data_cross_track_integration_verdict_v1 (
  integration_verdict_id uuid PRIMARY KEY DEFAULT public.gen_random_uuid(),
  integration_run_ref uuid NOT NULL UNIQUE REFERENCES public.data_cross_track_integration_run_v1(integration_run_id) ON DELETE RESTRICT,
  source_snapshot_ref uuid NOT NULL REFERENCES public.data_projection_snapshot_v1(snapshot_id) ON DELETE RESTRICT,
  source_quality_verdict_ref uuid NOT NULL REFERENCES public.data_snapshot_quality_verdict_v1(snapshot_quality_verdict_id) ON DELETE RESTRICT,
  source_track varchar(32) NOT NULL CHECK (source_track='Data'),
  target_track varchar(32) NOT NULL CHECK (target_track IN ('Recommendation','Intelligence','Search','Data')),
  integration_scope varchar(64) NOT NULL,
  integration_policy_version varchar(96) NOT NULL CHECK (integration_policy_version='data-cross-track-integration-policy-v1'),
  overall_status varchar(32) NOT NULL CHECK (overall_status IN ('COMPATIBLE','INCOMPATIBLE','CONDITIONALLY_COMPATIBLE','INCONCLUSIVE')),
  blocker_count bigint NOT NULL CHECK (blocker_count>=0),
  error_count bigint NOT NULL CHECK (error_count>=0),
  warning_count bigint NOT NULL CHECK (warning_count>=0),
  passed_check_count bigint NOT NULL CHECK (passed_check_count>=0),
  failed_check_count bigint NOT NULL CHECK (failed_check_count>=0),
  skipped_required_check_count bigint NOT NULL CHECK (skipped_required_check_count>=0),
  conditional_requirement_count bigint NOT NULL CHECK (conditional_requirement_count>=0),
  verdict_fingerprint varchar(64) NOT NULL CHECK (public.data_integration_fingerprint_valid_v1(verdict_fingerprint)),
  retention_class varchar(48) NOT NULL DEFAULT 'cross_track_integration_evidence_90d' CHECK (retention_class='cross_track_integration_evidence_90d'),
  retention_policy_version varchar(96) NOT NULL DEFAULT 'data-retention-policy-v1' CHECK (retention_policy_version='data-retention-policy-v1'),
  expires_at timestamptz NOT NULL DEFAULT (CURRENT_TIMESTAMP+interval '90 days'),
  created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CHECK (overall_status<>'COMPATIBLE' OR (blocker_count=0 AND failed_check_count=0 AND skipped_required_check_count=0 AND conditional_requirement_count=0)),
  CHECK (overall_status<>'CONDITIONALLY_COMPATIBLE' OR (blocker_count=0 AND failed_check_count=0 AND skipped_required_check_count=0 AND conditional_requirement_count>0)),
  CHECK (overall_status<>'INCOMPATIBLE' OR (blocker_count>0 OR failed_check_count>0)),
  CHECK (overall_status<>'INCONCLUSIVE' OR skipped_required_check_count>0),
  CHECK (expires_at>=created_at+interval '90 days')
);

CREATE TABLE public.data_cross_track_integration_conflict_evidence_v1 (
  integration_conflict_id uuid PRIMARY KEY DEFAULT public.gen_random_uuid(),
  existing_integration_run_ref uuid NOT NULL REFERENCES public.data_cross_track_integration_run_v1(integration_run_id) ON DELETE RESTRICT,
  logical_identity_hash varchar(64) NOT NULL CHECK (public.data_integration_fingerprint_valid_v1(logical_identity_hash)),
  existing_input_fingerprint varchar(64) NOT NULL CHECK (public.data_integration_fingerprint_valid_v1(existing_input_fingerprint)),
  incoming_input_fingerprint varchar(64) NOT NULL CHECK (public.data_integration_fingerprint_valid_v1(incoming_input_fingerprint)),
  existing_verdict_fingerprint varchar(64) NOT NULL CHECK (public.data_integration_fingerprint_valid_v1(existing_verdict_fingerprint)),
  incoming_verdict_fingerprint varchar(64) NOT NULL CHECK (public.data_integration_fingerprint_valid_v1(incoming_verdict_fingerprint)),
  conflict_code varchar(64) NOT NULL CHECK (conflict_code='CROSS_TRACK_INTEGRATION_VERDICT_CONFLICT'),
  conflict_fingerprint varchar(64) NOT NULL UNIQUE CHECK (public.data_integration_fingerprint_valid_v1(conflict_fingerprint)),
  observed_at timestamptz NOT NULL,
  retention_class varchar(48) NOT NULL DEFAULT 'cross_track_integration_evidence_90d' CHECK (retention_class='cross_track_integration_evidence_90d'),
  retention_policy_version varchar(96) NOT NULL DEFAULT 'data-retention-policy-v1' CHECK (retention_policy_version='data-retention-policy-v1'),
  expires_at timestamptz NOT NULL DEFAULT (CURRENT_TIMESTAMP+interval '90 days'),
  created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CHECK (existing_input_fingerprint<>incoming_input_fingerprint OR existing_verdict_fingerprint<>incoming_verdict_fingerprint),
  CHECK (expires_at>=created_at+interval '90 days')
);

CREATE INDEX data_cross_track_verdict_status_idx ON public.data_cross_track_integration_verdict_v1(target_track,integration_scope,overall_status,created_at DESC);
CREATE INDEX data_cross_track_conflict_idx ON public.data_cross_track_integration_conflict_evidence_v1(conflict_code,observed_at DESC);

CREATE TRIGGER data_cross_track_quality_binding_append_only BEFORE UPDATE OR DELETE ON public.data_cross_track_quality_verdict_binding_v1 FOR EACH ROW EXECUTE FUNCTION public.prevent_data_event_append_only_mutation_v1();
CREATE TRIGGER data_cross_track_verdict_append_only BEFORE UPDATE OR DELETE ON public.data_cross_track_integration_verdict_v1 FOR EACH ROW EXECUTE FUNCTION public.prevent_data_event_append_only_mutation_v1();
CREATE TRIGGER data_cross_track_conflict_append_only BEFORE UPDATE OR DELETE ON public.data_cross_track_integration_conflict_evidence_v1 FOR EACH ROW EXECUTE FUNCTION public.prevent_data_event_append_only_mutation_v1();

COMMIT;
