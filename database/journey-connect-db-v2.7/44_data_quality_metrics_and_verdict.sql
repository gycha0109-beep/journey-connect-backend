-- Journey Connect DB v2.7 extension - DP-6 metrics, verdicts and late-arrival evidence
-- Target: PostgreSQL 15 and 18. Prerequisite: SQL 01..43.
BEGIN;

CREATE TABLE public.data_quality_policy_evidence_v1 (
  quality_policy_evidence_id uuid PRIMARY KEY DEFAULT public.gen_random_uuid(),
  quality_policy_version varchar(96) NOT NULL UNIQUE CHECK (quality_policy_version = 'data-quality-policy-v1'),
  required_checks jsonb NOT NULL CHECK (jsonb_typeof(required_checks) = 'array' AND jsonb_array_length(required_checks) > 0),
  required_metrics jsonb NOT NULL CHECK (jsonb_typeof(required_metrics) = 'array' AND jsonb_array_length(required_metrics) > 0),
  blocker_failure_codes jsonb NOT NULL CHECK (jsonb_typeof(blocker_failure_codes) = 'array'),
  thresholds jsonb NOT NULL CHECK (jsonb_typeof(thresholds) = 'object'),
  late_arrival_tolerance_seconds bigint NOT NULL CHECK (late_arrival_tolerance_seconds >= 0),
  duplicate_tolerance numeric(20,12) NOT NULL CHECK (duplicate_tolerance >= 0),
  zero_denominator_policy varchar(32) NOT NULL
    CHECK (zero_denominator_policy IN ('NOT_APPLICABLE','UNDEFINED','POLICY_DEFINED_ZERO_CASE')),
  policy_fingerprint varchar(64) NOT NULL CHECK (public.data_quality_fingerprint_valid_v1(policy_fingerprint)),
  retention_class varchar(40) NOT NULL DEFAULT 'data_quality_evidence_90d'
    CHECK (retention_class = 'data_quality_evidence_90d'),
  retention_policy_version varchar(96) NOT NULL DEFAULT 'data-retention-policy-v1'
    CHECK (retention_policy_version = 'data-retention-policy-v1'),
  expires_at timestamptz NOT NULL DEFAULT (CURRENT_TIMESTAMP + interval '90 days'),
  created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT data_quality_policy_retention_check CHECK (expires_at >= created_at + interval '90 days')
);

INSERT INTO public.data_quality_policy_evidence_v1(
  quality_policy_version,required_checks,required_metrics,blocker_failure_codes,thresholds,
  late_arrival_tolerance_seconds,duplicate_tolerance,zero_denominator_policy,policy_fingerprint)
VALUES (
  'data-quality-policy-v1',
  '["source.count","source.set_fingerprint","source.range","source.membership","source.contract","source.timestamp","source.checkpoint_fingerprint","projection.records","projection.source_count","projection.aggregation","projection.adapter_evidence","snapshot.record_count","snapshot.subject_count","snapshot.source_count","snapshot.content_fingerprint","snapshot.lineage_fingerprint","snapshot.contract","snapshot.as_of","snapshot.status","lineage.completeness","lineage.orphan","lineage.duplicate","lineage.source","lineage.adapter_evidence","lineage.binding","lineage.fingerprint","identity.binding","exposure.binding","rebuild.record_count","rebuild.subject_count","rebuild.source_count","rebuild.records","rebuild.snapshot_fingerprint","rebuild.lineage_fingerprint","rebuild.ordering"]'::jsonb,
  '["source_completeness_rate","projection_coverage_rate","lineage_completeness_rate","lineage_orphan_rate","duplicate_source_rate","duplicate_lineage_rate","snapshot_record_reconciliation_rate","snapshot_subject_reconciliation_rate","snapshot_source_reconciliation_rate","fingerprint_match_rate","identity_binding_valid_rate","exposure_binding_valid_rate","late_arrival_rate","conflict_rate","rebuild_match_rate"]'::jsonb,
  '["privacy_policy_violation","identity_namespace_conflict","general_exposure_used_as_p2","snapshot_content_fingerprint_mismatch","snapshot_lineage_fingerprint_mismatch","non_deterministic_output"]'::jsonb,
  '{"source_completeness_rate":{"operator":"GREATER_THAN_OR_EQUAL","value":"1"},"projection_coverage_rate":{"operator":"GREATER_THAN_OR_EQUAL","value":"1"},"lineage_completeness_rate":{"operator":"GREATER_THAN_OR_EQUAL","value":"1"},"lineage_orphan_rate":{"operator":"LESS_THAN_OR_EQUAL","value":"0"},"duplicate_source_rate":{"operator":"LESS_THAN_OR_EQUAL","value":"0"},"duplicate_lineage_rate":{"operator":"LESS_THAN_OR_EQUAL","value":"0"},"snapshot_record_reconciliation_rate":{"operator":"GREATER_THAN_OR_EQUAL","value":"1"},"snapshot_subject_reconciliation_rate":{"operator":"GREATER_THAN_OR_EQUAL","value":"1"},"snapshot_source_reconciliation_rate":{"operator":"GREATER_THAN_OR_EQUAL","value":"1"},"fingerprint_match_rate":{"operator":"GREATER_THAN_OR_EQUAL","value":"1"},"identity_binding_valid_rate":{"operator":"GREATER_THAN_OR_EQUAL","value":"1"},"exposure_binding_valid_rate":{"operator":"GREATER_THAN_OR_EQUAL","value":"1"},"late_arrival_rate":{"operator":"LESS_THAN_OR_EQUAL","value":"0"},"conflict_rate":{"operator":"LESS_THAN_OR_EQUAL","value":"0"},"rebuild_match_rate":{"operator":"GREATER_THAN_OR_EQUAL","value":"1"}}'::jsonb,
  0,0,'NOT_APPLICABLE',
  public.data_quality_fingerprint_v1('data-quality-policy-sha256-v1', jsonb_build_object(
    'qualityPolicyVersion','data-quality-policy-v1','sourceCompleteness','1','projectionCoverage','1',
    'lineageCompleteness','1','orphanLineage','0','duplicateLineage','0','rebuildMatch','1',
    'zeroDenominatorPolicy','NOT_APPLICABLE'))
);

CREATE TABLE public.data_quality_metric_v1 (
  quality_metric_id uuid PRIMARY KEY DEFAULT public.gen_random_uuid(),
  validation_run_ref uuid NOT NULL REFERENCES public.data_quality_validation_run_v1(validation_run_id) ON DELETE RESTRICT,
  metric_name varchar(96) NOT NULL CHECK (metric_name ~ '^[a-z][a-z0-9_]{0,95}$'),
  numerator bigint NOT NULL CHECK (numerator >= 0),
  denominator bigint NOT NULL CHECK (denominator >= 0),
  metric_value numeric(30,12),
  metric_unit varchar(32) NOT NULL CHECK (metric_unit IN ('ratio','count','seconds')),
  policy_threshold numeric(30,12) NOT NULL,
  threshold_operator varchar(32) NOT NULL CHECK (threshold_operator IN ('GREATER_THAN_OR_EQUAL','LESS_THAN_OR_EQUAL','EQUAL')),
  threshold_result varchar(32) NOT NULL CHECK (threshold_result IN ('PASS','FAIL','NOT_APPLICABLE','UNDEFINED','POLICY_DEFINED_ZERO_CASE')),
  metric_version varchar(96) NOT NULL CHECK (metric_version = 'data-quality-metric-v1'),
  metric_fingerprint varchar(64) NOT NULL CHECK (public.data_quality_fingerprint_valid_v1(metric_fingerprint)),
  retention_class varchar(40) NOT NULL DEFAULT 'data_quality_evidence_90d' CHECK (retention_class = 'data_quality_evidence_90d'),
  retention_policy_version varchar(96) NOT NULL DEFAULT 'data-retention-policy-v1' CHECK (retention_policy_version = 'data-retention-policy-v1'),
  expires_at timestamptz NOT NULL DEFAULT (CURRENT_TIMESTAMP + interval '90 days'),
  created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT data_quality_metric_ratio_bounds_check CHECK (
    metric_unit <> 'ratio' OR numerator <= denominator),
  CONSTRAINT data_quality_metric_zero_denominator_check CHECK (
    (denominator = 0 AND metric_value IS NULL AND threshold_result IN ('NOT_APPLICABLE','UNDEFINED','POLICY_DEFINED_ZERO_CASE'))
    OR (denominator > 0 AND metric_value IS NOT NULL AND threshold_result IN ('PASS','FAIL'))),
  CONSTRAINT data_quality_metric_fraction_check CHECK (
    denominator = 0 OR metric_unit <> 'ratio'
    OR metric_value = round(numerator::numeric / denominator::numeric, 12)),
  CONSTRAINT data_quality_metric_retention_check CHECK (expires_at >= created_at + interval '90 days'),
  UNIQUE(validation_run_ref,metric_name)
);

CREATE TABLE public.data_snapshot_quality_verdict_v1 (
  snapshot_quality_verdict_id uuid PRIMARY KEY DEFAULT public.gen_random_uuid(),
  snapshot_ref uuid NOT NULL REFERENCES public.data_projection_snapshot_v1(snapshot_id) ON DELETE RESTRICT,
  validation_run_ref uuid NOT NULL UNIQUE REFERENCES public.data_quality_validation_run_v1(validation_run_id) ON DELETE RESTRICT,
  quality_policy_version varchar(96) NOT NULL CHECK (quality_policy_version = 'data-quality-policy-v1'),
  overall_status varchar(16) NOT NULL CHECK (overall_status IN ('VALIDATED','REJECTED','INCONCLUSIVE')),
  blocker_count bigint NOT NULL CHECK (blocker_count >= 0),
  error_count bigint NOT NULL CHECK (error_count >= 0),
  warning_count bigint NOT NULL CHECK (warning_count >= 0),
  passed_check_count bigint NOT NULL CHECK (passed_check_count >= 0),
  failed_check_count bigint NOT NULL CHECK (failed_check_count >= 0),
  skipped_required_check_count bigint NOT NULL CHECK (skipped_required_check_count >= 0),
  quality_score numeric(9,6) NOT NULL CHECK (quality_score BETWEEN 0 AND 100),
  verdict_fingerprint varchar(64) NOT NULL CHECK (public.data_quality_fingerprint_valid_v1(verdict_fingerprint)),
  retention_class varchar(40) NOT NULL DEFAULT 'data_quality_evidence_90d' CHECK (retention_class = 'data_quality_evidence_90d'),
  retention_policy_version varchar(96) NOT NULL DEFAULT 'data-retention-policy-v1' CHECK (retention_policy_version = 'data-retention-policy-v1'),
  expires_at timestamptz NOT NULL DEFAULT (CURRENT_TIMESTAMP + interval '90 days'),
  created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT data_quality_verdict_semantics_check CHECK (
    (overall_status = 'VALIDATED' AND blocker_count = 0 AND failed_check_count = 0 AND skipped_required_check_count = 0)
    OR (overall_status = 'REJECTED' AND (blocker_count > 0 OR failed_check_count > 0))
    OR (overall_status = 'INCONCLUSIVE' AND blocker_count = 0 AND failed_check_count = 0)),
  CONSTRAINT data_quality_verdict_forbidden_check CHECK (overall_status NOT IN ('PRODUCTION_READY','SERVING_READY','CUTOVER_READY','ACTIVE','AUTHORITATIVE')),
  CONSTRAINT data_quality_verdict_retention_check CHECK (expires_at >= created_at + interval '90 days')
);

CREATE TABLE public.data_quality_late_arrival_observation_v1 (
  late_event_observation_id uuid PRIMARY KEY DEFAULT public.gen_random_uuid(),
  source_event_ref varchar(180) NOT NULL,
  affected_checkpoint_ref uuid NOT NULL REFERENCES public.data_source_checkpoint_v1(checkpoint_id) ON DELETE RESTRICT,
  affected_snapshot_ref uuid NOT NULL REFERENCES public.data_projection_snapshot_v1(snapshot_id) ON DELETE RESTRICT,
  event_time timestamptz NOT NULL,
  ingested_at timestamptz NOT NULL,
  lateness_duration_seconds bigint NOT NULL CHECK (lateness_duration_seconds >= 0),
  policy_class varchar(32) NOT NULL CHECK (policy_class IN ('WITHIN_TOLERANCE','REBUILD_RECOMMENDED','REBUILD_REQUIRED','IGNORED_BY_POLICY')),
  observation_fingerprint varchar(64) NOT NULL UNIQUE CHECK (public.data_quality_fingerprint_valid_v1(observation_fingerprint)),
  retention_class varchar(40) NOT NULL DEFAULT 'data_quality_evidence_90d' CHECK (retention_class = 'data_quality_evidence_90d'),
  retention_policy_version varchar(96) NOT NULL DEFAULT 'data-retention-policy-v1' CHECK (retention_policy_version = 'data-retention-policy-v1'),
  expires_at timestamptz NOT NULL DEFAULT (CURRENT_TIMESTAMP + interval '90 days'),
  created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT data_quality_late_arrival_time_check CHECK (ingested_at >= event_time),
  CONSTRAINT data_quality_late_arrival_retention_check CHECK (expires_at >= created_at + interval '90 days')
);

CREATE INDEX data_quality_metric_name_idx ON public.data_quality_metric_v1(metric_name,metric_version,created_at DESC);
CREATE INDEX data_quality_verdict_status_idx ON public.data_snapshot_quality_verdict_v1(overall_status,quality_policy_version,created_at DESC);
CREATE INDEX data_quality_late_arrival_idx ON public.data_quality_late_arrival_observation_v1(policy_class,created_at DESC);

CREATE TRIGGER data_quality_policy_append_only BEFORE UPDATE OR DELETE ON public.data_quality_policy_evidence_v1 FOR EACH ROW EXECUTE FUNCTION public.prevent_data_event_append_only_mutation_v1();
CREATE TRIGGER data_quality_metric_append_only BEFORE UPDATE OR DELETE ON public.data_quality_metric_v1 FOR EACH ROW EXECUTE FUNCTION public.prevent_data_event_append_only_mutation_v1();
CREATE TRIGGER data_quality_verdict_append_only BEFORE UPDATE OR DELETE ON public.data_snapshot_quality_verdict_v1 FOR EACH ROW EXECUTE FUNCTION public.prevent_data_event_append_only_mutation_v1();
CREATE TRIGGER data_quality_late_arrival_append_only BEFORE UPDATE OR DELETE ON public.data_quality_late_arrival_observation_v1 FOR EACH ROW EXECUTE FUNCTION public.prevent_data_event_append_only_mutation_v1();

COMMIT;
