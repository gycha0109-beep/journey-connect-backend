-- Journey Connect DB v2.7 extension - DP-6 rebuild, conflict evidence and safe aggregate views
-- Target: PostgreSQL 15 and 18. Prerequisite: SQL 01..45.
BEGIN;

-- DP-6 fingerprint functions are SECURITY INVOKER. Grant only the immutable
-- canonicalization chain they call; no table write or broad function privilege is added.
GRANT EXECUTE ON FUNCTION public.digest(bytea,text),
  public.data_event_canonical_json_v1(jsonb),
  public.data_projection_fingerprint_v1(varchar,jsonb)
  TO jc_data_quality_function_owner;

CREATE TABLE public.data_quality_rebuild_comparison_v1 (
  rebuild_comparison_id uuid PRIMARY KEY DEFAULT public.gen_random_uuid(),
  validation_run_ref uuid NOT NULL UNIQUE REFERENCES public.data_quality_validation_run_v1(validation_run_id) ON DELETE RESTRICT,
  matched boolean NOT NULL,
  expected_record_count bigint NOT NULL CHECK (expected_record_count >= 0),
  observed_record_count bigint NOT NULL CHECK (observed_record_count >= 0),
  expected_subject_count bigint NOT NULL CHECK (expected_subject_count >= 0),
  observed_subject_count bigint NOT NULL CHECK (observed_subject_count >= 0),
  expected_source_count bigint NOT NULL CHECK (expected_source_count >= 0),
  observed_source_count bigint NOT NULL CHECK (observed_source_count >= 0),
  expected_record_fingerprints jsonb NOT NULL CHECK (jsonb_typeof(expected_record_fingerprints)='array'),
  observed_record_fingerprints jsonb NOT NULL CHECK (jsonb_typeof(observed_record_fingerprints)='array'),
  expected_snapshot_fingerprint varchar(64) NOT NULL CHECK (public.data_quality_fingerprint_valid_v1(expected_snapshot_fingerprint)),
  observed_snapshot_fingerprint varchar(64) NOT NULL CHECK (public.data_quality_fingerprint_valid_v1(observed_snapshot_fingerprint)),
  expected_lineage_fingerprint varchar(64) NOT NULL CHECK (public.data_quality_fingerprint_valid_v1(expected_lineage_fingerprint)),
  observed_lineage_fingerprint varchar(64) NOT NULL CHECK (public.data_quality_fingerprint_valid_v1(observed_lineage_fingerprint)),
  comparison_fingerprint varchar(64) NOT NULL CHECK (public.data_quality_fingerprint_valid_v1(comparison_fingerprint)),
  retention_class varchar(40) NOT NULL DEFAULT 'data_quality_evidence_90d' CHECK (retention_class='data_quality_evidence_90d'),
  retention_policy_version varchar(96) NOT NULL DEFAULT 'data-retention-policy-v1' CHECK (retention_policy_version='data-retention-policy-v1'),
  expires_at timestamptz NOT NULL DEFAULT (CURRENT_TIMESTAMP + interval '90 days'),
  created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT data_quality_rebuild_match_check CHECK (
    matched = (expected_record_count=observed_record_count
      AND expected_subject_count=observed_subject_count
      AND expected_source_count=observed_source_count
      AND expected_record_fingerprints=observed_record_fingerprints
      AND expected_snapshot_fingerprint=observed_snapshot_fingerprint
      AND expected_lineage_fingerprint=observed_lineage_fingerprint)),
  CONSTRAINT data_quality_rebuild_retention_check CHECK (expires_at >= created_at + interval '90 days')
);

CREATE TABLE public.data_quality_validation_conflict_evidence_v1 (
  validation_conflict_id uuid PRIMARY KEY DEFAULT public.gen_random_uuid(),
  logical_identity_hash varchar(64) NOT NULL CHECK (public.data_quality_fingerprint_valid_v1(logical_identity_hash)),
  existing_validation_run_ref uuid NOT NULL REFERENCES public.data_quality_validation_run_v1(validation_run_id) ON DELETE RESTRICT,
  existing_input_fingerprint varchar(64) NOT NULL CHECK (public.data_quality_fingerprint_valid_v1(existing_input_fingerprint)),
  attempted_input_fingerprint varchar(64) NOT NULL CHECK (public.data_quality_fingerprint_valid_v1(attempted_input_fingerprint)),
  existing_verdict_fingerprint varchar(64) NOT NULL CHECK (public.data_quality_fingerprint_valid_v1(existing_verdict_fingerprint)),
  attempted_verdict_fingerprint varchar(64) NOT NULL CHECK (public.data_quality_fingerprint_valid_v1(attempted_verdict_fingerprint)),
  failure_code varchar(96) NOT NULL CHECK (failure_code='QUALITY_VERDICT_CONFLICT'),
  retention_class varchar(40) NOT NULL DEFAULT 'data_quality_evidence_90d' CHECK (retention_class='data_quality_evidence_90d'),
  retention_policy_version varchar(96) NOT NULL DEFAULT 'data-retention-policy-v1' CHECK (retention_policy_version='data-retention-policy-v1'),
  expires_at timestamptz NOT NULL DEFAULT (CURRENT_TIMESTAMP + interval '90 days'),
  created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT data_quality_conflict_retention_check CHECK (expires_at >= created_at + interval '90 days')
);

CREATE INDEX data_quality_rebuild_match_idx ON public.data_quality_rebuild_comparison_v1(matched,created_at DESC);
CREATE INDEX data_quality_conflict_created_idx ON public.data_quality_validation_conflict_evidence_v1(created_at DESC);
CREATE TRIGGER data_quality_rebuild_append_only BEFORE UPDATE OR DELETE ON public.data_quality_rebuild_comparison_v1 FOR EACH ROW EXECUTE FUNCTION public.prevent_data_event_append_only_mutation_v1();
CREATE TRIGGER data_quality_conflict_append_only BEFORE UPDATE OR DELETE ON public.data_quality_validation_conflict_evidence_v1 FOR EACH ROW EXECUTE FUNCTION public.prevent_data_event_append_only_mutation_v1();

CREATE VIEW public.data_quality_safe_metrics_v1 WITH (security_barrier=true) AS
SELECT 'validation_run_count'::varchar AS metric_name,r.validation_scope::varchar AS dimension_a,
       r.quality_policy_version::varchar AS dimension_b,NULL::varchar AS dimension_c,
       NULL::varchar AS status,count(*)::numeric AS metric_value,NULL::timestamptz AS observed_time
FROM public.data_quality_validation_run_v1 r
GROUP BY r.validation_scope,r.quality_policy_version
UNION ALL
SELECT 'validation_status_count',r.validation_scope,r.quality_policy_version,NULL,s.validation_status,
       count(*)::numeric,NULL::timestamptz
FROM public.data_quality_validation_run_v1 r JOIN public.data_quality_validation_status_evidence_v1 s
  ON s.validation_run_ref=r.validation_run_id
GROUP BY r.validation_scope,r.quality_policy_version,s.validation_status
UNION ALL
SELECT 'snapshot_verdict_count',r.validation_scope,v.quality_policy_version,NULL,v.overall_status,
       count(*)::numeric,NULL::timestamptz
FROM public.data_snapshot_quality_verdict_v1 v JOIN public.data_quality_validation_run_v1 r
  ON r.validation_run_id=v.validation_run_ref
GROUP BY r.validation_scope,v.quality_policy_version,v.overall_status
UNION ALL
SELECT 'failure_code_count',c.check_scope,r.quality_policy_version,c.failure_code,c.severity,
       count(*)::numeric,NULL::timestamptz
FROM public.data_quality_validation_check_result_v1 c JOIN public.data_quality_validation_run_v1 r
  ON r.validation_run_id=c.validation_run_ref
WHERE c.failure_code IS NOT NULL
GROUP BY c.check_scope,r.quality_policy_version,c.failure_code,c.severity
UNION ALL
SELECT m.metric_name,r.validation_scope,r.quality_policy_version,m.threshold_result,
       NULL,avg(m.metric_value),NULL::timestamptz
FROM public.data_quality_metric_v1 m JOIN public.data_quality_validation_run_v1 r
  ON r.validation_run_id=m.validation_run_ref
GROUP BY m.metric_name,r.validation_scope,r.quality_policy_version,m.threshold_result
UNION ALL
SELECT 'late_arrival_policy_count','FULL',r.quality_policy_version,l.policy_class,NULL,
       count(*)::numeric,NULL::timestamptz
FROM public.data_quality_late_arrival_observation_v1 l
JOIN public.data_projection_snapshot_v1 s ON s.snapshot_id=l.affected_snapshot_ref
JOIN public.data_quality_validation_run_v1 r ON r.snapshot_ref=s.snapshot_id
GROUP BY r.quality_policy_version,l.policy_class
UNION ALL
SELECT 'conflict_count','FULL',r.quality_policy_version,'QUALITY_VERDICT_CONFLICT',NULL,
       count(*)::numeric,NULL::timestamptz
FROM public.data_quality_validation_conflict_evidence_v1 c
JOIN public.data_quality_validation_run_v1 r ON r.validation_run_id=c.existing_validation_run_ref
GROUP BY r.quality_policy_version
UNION ALL
SELECT 'oldest_inconclusive_snapshot_age_seconds','FULL',v.quality_policy_version,NULL,'INCONCLUSIVE',
       floor(extract(epoch FROM (CURRENT_TIMESTAMP-min(v.created_at))))::numeric,min(v.created_at)
FROM public.data_snapshot_quality_verdict_v1 v WHERE v.overall_status='INCONCLUSIVE'
GROUP BY v.quality_policy_version
UNION ALL
SELECT 'latest_validated_snapshot_time','FULL',v.quality_policy_version,NULL,'VALIDATED',
       NULL::numeric,max(s.snapshot_as_of)
FROM public.data_snapshot_quality_verdict_v1 v
JOIN public.data_projection_snapshot_v1 s ON s.snapshot_id=v.snapshot_ref
WHERE v.overall_status='VALIDATED'
GROUP BY v.quality_policy_version;

REVOKE ALL ON TABLE public.data_quality_rebuild_comparison_v1,
  public.data_quality_validation_conflict_evidence_v1,public.data_quality_safe_metrics_v1
  FROM PUBLIC,jc_data_quality_writer,jc_data_quality_reader;
GRANT SELECT,INSERT ON public.data_quality_rebuild_comparison_v1,
  public.data_quality_validation_conflict_evidence_v1 TO jc_data_quality_function_owner;
GRANT SELECT ON public.data_quality_safe_metrics_v1 TO jc_data_quality_reader;

COMMIT;
