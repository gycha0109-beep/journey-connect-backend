-- Journey Connect DB v2.7 extension - DP-6 atomic persistence, roles and access
-- Target: PostgreSQL 15 and 18. Prerequisite: SQL 01..44.
BEGIN;

DO $$
BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'jc_data_quality_writer') THEN
    CREATE ROLE jc_data_quality_writer NOLOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOREPLICATION NOBYPASSRLS;
  END IF;
  IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'jc_data_quality_reader') THEN
    CREATE ROLE jc_data_quality_reader NOLOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOREPLICATION NOBYPASSRLS;
  END IF;
  IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'jc_data_quality_function_owner') THEN
    CREATE ROLE jc_data_quality_function_owner NOLOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOREPLICATION NOBYPASSRLS;
  END IF;
END;
$$;

DO $$
DECLARE v_unsafe text;
BEGIN
  SELECT string_agg(rolname, ', ' ORDER BY rolname) INTO v_unsafe
  FROM pg_roles
  WHERE rolname IN ('jc_data_quality_writer','jc_data_quality_reader','jc_data_quality_function_owner')
    AND (rolsuper OR rolcreaterole OR rolcreatedb OR rolcanlogin OR rolreplication OR rolbypassrls);
  IF v_unsafe IS NOT NULL THEN RAISE EXCEPTION 'Unsafe DP-6 role attributes: %', v_unsafe; END IF;
END;
$$;

CREATE OR REPLACE FUNCTION public.data_quality_snapshot_observation_v1(p_snapshot_id uuid)
RETURNS jsonb LANGUAGE plpgsql STABLE STRICT SECURITY INVOKER
SET search_path = pg_catalog, public, pg_temp
AS $$
DECLARE
  v_snapshot public.data_projection_snapshot_v1%ROWTYPE;
  v_run public.data_projection_run_v1%ROWTYPE;
  v_checkpoint public.data_source_checkpoint_v1%ROWTYPE;
  v_record_count bigint;
  v_subject_count bigint;
  v_source_count bigint;
  v_missing_lineage bigint;
  v_orphan_lineage bigint;
  v_duplicate_lineage bigint;
  v_invalid_p2 bigint := 0;
  v_profile_window_count bigint := 0;
  v_source_authority_mismatch bigint := 0;
  v_source_timestamp_mismatch bigint := 0;
  v_source_range_mismatch bigint := 0;
  v_actual_last_source_event_ref text;
  v_member jsonb;
  v_actual_occurred_at timestamptz;
  v_actual_ingested_at timestamptz;
  v_actual_source_set varchar(64);
  v_actual_checkpoint_definition varchar(64);
  v_actual_content varchar(64);
  v_actual_lineage varchar(64);
  v_record_fingerprints jsonb;
BEGIN
  SELECT * INTO v_snapshot FROM public.data_projection_snapshot_v1 WHERE snapshot_id=p_snapshot_id;
  IF NOT FOUND THEN RAISE EXCEPTION 'DP-6 snapshot missing.' USING ERRCODE='23503'; END IF;
  SELECT * INTO v_run FROM public.data_projection_run_v1 WHERE projection_run_id=v_snapshot.projection_run_ref;
  IF NOT FOUND THEN RAISE EXCEPTION 'DP-6 projection run missing.' USING ERRCODE='23503'; END IF;
  SELECT * INTO v_checkpoint FROM public.data_source_checkpoint_v1 WHERE checkpoint_id=v_snapshot.source_checkpoint_ref;
  IF NOT FOUND THEN RAISE EXCEPTION 'DP-6 checkpoint missing.' USING ERRCODE='23503'; END IF;

  FOR v_member IN SELECT value FROM jsonb_array_elements(v_checkpoint.source_members) LOOP
    v_actual_occurred_at:=NULL; v_actual_ingested_at:=NULL;
    IF v_member->>'sourceKind'='canonical_event' THEN
      SELECT e.occurred_at,e.received_at INTO v_actual_occurred_at,v_actual_ingested_at
      FROM public.data_platform_event_v1 e
      WHERE e.event_id=v_member->>'sourceEventRef' AND e.payload_fingerprint=v_member->>'sourceFingerprint';
    ELSIF v_member->>'sourceKind'='adapter_output' THEN
      SELECT o.mapped_occurred_at,o.created_at INTO v_actual_occurred_at,v_actual_ingested_at
      FROM public.data_recommendation_adapter_output_v1 o
      WHERE o.adapter_output_id=(v_member->>'adapterEvidenceRef')::uuid
        AND o.source_event_ref=v_member->>'sourceEventRef'
        AND o.source_fingerprint=v_member->>'sourceFingerprint'
        AND o.mapping_status='mapped_shadow';
    ELSIF v_member->>'sourceKind'='p2_exposure' THEN
      SELECT e.exposed_at,e.created_at INTO v_actual_occurred_at,v_actual_ingested_at
      FROM public.recommendation_p2_experiment_exposure e
      WHERE 'p2_exposure:'||e.exposure_id=v_member->>'sourceEventRef'
        AND e.exposure_fingerprint=v_member->>'sourceFingerprint';
    END IF;
    IF v_actual_occurred_at IS NULL OR v_actual_ingested_at IS NULL THEN
      v_source_authority_mismatch:=v_source_authority_mismatch+1;
    ELSIF v_actual_occurred_at<>(v_member->>'occurredAt')::timestamptz
       OR v_actual_ingested_at<>(v_member->>'ingestedAt')::timestamptz THEN
      v_source_timestamp_mismatch:=v_source_timestamp_mismatch+1;
    END IF;
  END LOOP;

  SELECT count(*) INTO v_source_range_mismatch
  FROM jsonb_array_elements(v_checkpoint.source_members)m
  WHERE (m->>'occurredAt')::timestamptz<v_checkpoint.event_time_from
     OR (m->>'occurredAt')::timestamptz>=v_checkpoint.event_time_to
     OR (m->>'ingestedAt')::timestamptz>v_checkpoint.ingested_at_upper_bound
     OR (m->>'ingestedAt')::timestamptz<(m->>'occurredAt')::timestamptz;
  SELECT m->>'sourceEventRef' INTO v_actual_last_source_event_ref
  FROM jsonb_array_elements(v_checkpoint.source_members)m
  ORDER BY (m->>'occurredAt')::timestamptz DESC,m->>'sourceEventRef' DESC,m->>'sourceFingerprint' DESC LIMIT 1;

  SELECT public.data_projection_fingerprint_v1('data-source-set-sha256-v1',
    jsonb_build_object('sources',jsonb_agg(jsonb_build_object(
      'sourceEventRef',m->>'sourceEventRef','sourceFingerprint',m->>'sourceFingerprint',
      'adapterEvidenceRef',m->'adapterEvidenceRef','occurredAt',m->>'occurredAt','ingestedAt',m->>'ingestedAt')
      ORDER BY m->>'sourceEventRef',m->>'sourceFingerprint',COALESCE(m->>'adapterEvidenceRef',''))))
    INTO v_actual_source_set FROM jsonb_array_elements(v_checkpoint.source_members)m;
  v_actual_checkpoint_definition:=public.data_projection_fingerprint_v1('data-checkpoint-definition-sha256-v1',
    jsonb_build_object('sourceStream',v_checkpoint.source_stream,
      'sourceContractVersion',v_checkpoint.source_contract_version,
      'sourceSchemaVersion',v_checkpoint.source_schema_version,
      'eventTimeFrom',public.data_projection_instant_text_v1(v_checkpoint.event_time_from),
      'eventTimeTo',public.data_projection_instant_text_v1(v_checkpoint.event_time_to),
      'ingestedAtUpperBound',public.data_projection_instant_text_v1(v_checkpoint.ingested_at_upper_bound),
      'sourceEventCount',v_checkpoint.source_event_count,
      'sourceSetFingerprint',v_checkpoint.source_set_fingerprint));

  IF v_snapshot.projection_name='recommendation-profile-input-v1' THEN
    SELECT count(*),count(DISTINCT projection_subject_ref),count(DISTINCT activity_window_days),
      COALESCE(jsonb_agg(projection_record_fingerprint ORDER BY projection_record_fingerprint),'[]'::jsonb)
      INTO v_record_count,v_subject_count,v_profile_window_count,v_record_fingerprints
    FROM public.data_recommendation_profile_input_projection_v1 WHERE snapshot_ref=p_snapshot_id;
    SELECT count(*) INTO v_missing_lineage
    FROM public.data_recommendation_profile_input_projection_v1 r
    WHERE r.snapshot_ref=p_snapshot_id AND NOT EXISTS (
      SELECT 1 FROM public.data_projection_lineage_v1 l
      WHERE l.snapshot_ref=p_snapshot_id AND l.projection_record_ref=r.projection_record_ref);
  ELSE
    SELECT count(*),count(DISTINCT subject_ref),
      COALESCE(jsonb_agg(projection_record_fingerprint ORDER BY projection_record_fingerprint),'[]'::jsonb)
      INTO v_record_count,v_subject_count,v_record_fingerprints
    FROM public.data_experiment_outcome_input_projection_v1 WHERE snapshot_ref=p_snapshot_id;
    SELECT count(*) INTO v_missing_lineage
    FROM public.data_experiment_outcome_input_projection_v1 r
    WHERE r.snapshot_ref=p_snapshot_id AND NOT EXISTS (
      SELECT 1 FROM public.data_projection_lineage_v1 l
      WHERE l.snapshot_ref=p_snapshot_id AND l.projection_record_ref=r.projection_record_ref);
    SELECT count(*) INTO v_invalid_p2
    FROM public.data_experiment_outcome_input_projection_v1 o
    LEFT JOIN public.recommendation_p2_experiment_exposure e ON e.exposure_id=o.exposure_ref
    LEFT JOIN public.recommendation_p2_experiment_assignment a ON a.assignment_id=e.assignment_id
    WHERE o.snapshot_ref=p_snapshot_id AND (
      e.exposure_id IS NULL OR e.run_id<>o.run_ref OR ('user:'||e.user_id::text)<>o.source_user_ref
      OR e.session_id<>replace(o.session_ref,'session:','') OR e.variant<>o.variant_ref
      OR e.exposed_at<>o.exposed_at OR a.experiment_id<>replace(o.experiment_ref,'experiment:','')
      OR a.experiment_version<>o.experiment_version);
  END IF;

  SELECT count(DISTINCT (source_event_ref,source_fingerprint)) INTO v_source_count
  FROM public.data_projection_lineage_v1 WHERE snapshot_ref=p_snapshot_id;
  SELECT count(*) INTO v_orphan_lineage FROM public.data_projection_lineage_v1 l
  WHERE l.snapshot_ref=p_snapshot_id AND NOT EXISTS (
    SELECT 1 FROM public.data_recommendation_profile_input_projection_v1 p
      WHERE p.snapshot_ref=p_snapshot_id AND p.projection_record_ref=l.projection_record_ref
    UNION ALL
    SELECT 1 FROM public.data_experiment_outcome_input_projection_v1 o
      WHERE o.snapshot_ref=p_snapshot_id AND o.projection_record_ref=l.projection_record_ref);
  SELECT COALESCE(sum(c-1),0) INTO v_duplicate_lineage FROM (
    SELECT count(*) c FROM public.data_projection_lineage_v1
    WHERE snapshot_ref=p_snapshot_id
    GROUP BY projection_record_ref,source_event_ref,source_fingerprint HAVING count(*)>1) d;
  SELECT public.data_projection_fingerprint_v1('data-projection-lineage-sha256-v1',
    jsonb_build_object('entries',COALESCE(jsonb_agg(lineage_entry_fingerprint ORDER BY lineage_entry_fingerprint),'[]'::jsonb)))
    INTO v_actual_lineage FROM public.data_projection_lineage_v1 WHERE snapshot_ref=p_snapshot_id;
  v_actual_content:=public.data_projection_fingerprint_v1('data-projection-snapshot-sha256-v1',jsonb_build_object(
    'projectionName',v_snapshot.projection_name,'projectionSchemaVersion',v_snapshot.projection_schema_version,
    'projectionPolicyVersion',v_snapshot.projection_policy_version,'featurePolicyVersion',v_run.feature_policy_version,
    'identityBindingVersion',v_run.identity_binding_version,'targetContractVersion',v_run.target_contract_version,
    'sourceCheckpointRef',v_checkpoint.checkpoint_ref,
    'snapshotAsOf',public.data_projection_instant_text_v1(v_snapshot.snapshot_as_of),
    'recordFingerprints',v_record_fingerprints));

  RETURN jsonb_build_object(
    'snapshotRef',v_snapshot.snapshot_ref,'snapshotId',v_snapshot.snapshot_id,
    'projectionName',v_snapshot.projection_name,'projectionSchemaVersion',v_snapshot.projection_schema_version,
    'projectionPolicyVersion',v_snapshot.projection_policy_version,
    'snapshotAsOf',public.data_projection_instant_text_v1(v_snapshot.snapshot_as_of),
    'snapshotStatus',v_snapshot.snapshot_status,
    'snapshotRecordCount',v_snapshot.record_count,'actualRecordCount',v_record_count,
    'snapshotSubjectCount',v_snapshot.subject_count,'actualSubjectCount',v_subject_count,
    'snapshotSourceCount',v_snapshot.source_event_count,'actualSourceCount',v_source_count,
    'snapshotContentFingerprint',v_snapshot.content_fingerprint,'actualContentFingerprint',v_actual_content,
    'snapshotLineageFingerprint',v_snapshot.lineage_fingerprint,'actualLineageFingerprint',v_actual_lineage,
    'checkpointRef',v_checkpoint.checkpoint_ref,'checkpointSourceCount',v_checkpoint.source_event_count,
    'checkpointMemberCount',jsonb_array_length(v_checkpoint.source_members),
    'checkpointSourceSetFingerprint',v_checkpoint.source_set_fingerprint,
    'actualSourceSetFingerprint',v_actual_source_set,
    'checkpointDefinitionFingerprint',v_checkpoint.checkpoint_definition_fingerprint,
    'actualCheckpointDefinitionFingerprint',v_actual_checkpoint_definition,
    'sourceAuthorityMismatchCount',v_source_authority_mismatch,
    'sourceTimestampMismatchCount',v_source_timestamp_mismatch,
    'sourceRangeMismatchCount',v_source_range_mismatch,
    'lastSourceEventRef',v_checkpoint.last_source_event_ref,
    'actualLastSourceEventRef',v_actual_last_source_event_ref,
    'missingLineageCount',v_missing_lineage,'orphanLineageCount',v_orphan_lineage,
    'duplicateLineageCount',v_duplicate_lineage,'invalidP2ExposureCount',v_invalid_p2,
    'profileWindowCount',v_profile_window_count,'recordFingerprints',v_record_fingerprints);
END;
$$;


CREATE OR REPLACE FUNCTION public.data_quality_validation_input_fingerprint_v1(
  p_snapshot_id uuid,p_validation_scope varchar,p_validator_version varchar,p_quality_policy_version varchar)
RETURNS varchar LANGUAGE plpgsql STABLE STRICT SECURITY INVOKER
SET search_path = pg_catalog, public, pg_temp
AS $$
DECLARE
  v_snapshot public.data_projection_snapshot_v1%ROWTYPE;
  v_run public.data_projection_run_v1%ROWTYPE;
  v_checkpoint public.data_source_checkpoint_v1%ROWTYPE;
  v_sources jsonb;
  v_records jsonb;
  v_lineage jsonb;
  v_identity jsonb;
  v_exposure jsonb;
BEGIN
  SELECT * INTO v_snapshot FROM public.data_projection_snapshot_v1 WHERE snapshot_id=p_snapshot_id;
  IF NOT FOUND THEN RAISE EXCEPTION 'DP-6 snapshot missing.' USING ERRCODE='23503'; END IF;
  SELECT * INTO v_run FROM public.data_projection_run_v1 WHERE projection_run_id=v_snapshot.projection_run_ref;
  SELECT * INTO v_checkpoint FROM public.data_source_checkpoint_v1 WHERE checkpoint_id=v_snapshot.source_checkpoint_ref;
  SELECT COALESCE(jsonb_agg(m->>'sourceFingerprint' ORDER BY m->>'sourceFingerprint'),'[]'::jsonb)
    INTO v_sources FROM jsonb_array_elements(v_checkpoint.source_members) m;
  IF v_snapshot.projection_name='recommendation-profile-input-v1' THEN
    SELECT COALESCE(jsonb_agg(projection_record_fingerprint ORDER BY projection_record_fingerprint),'[]'::jsonb)
      INTO v_records FROM public.data_recommendation_profile_input_projection_v1 WHERE snapshot_ref=p_snapshot_id;
  ELSE
    SELECT COALESCE(jsonb_agg(projection_record_fingerprint ORDER BY projection_record_fingerprint),'[]'::jsonb)
      INTO v_records FROM public.data_experiment_outcome_input_projection_v1 WHERE snapshot_ref=p_snapshot_id;
  END IF;
  SELECT COALESCE(jsonb_agg(lineage_entry_fingerprint ORDER BY lineage_entry_fingerprint),'[]'::jsonb)
    INTO v_lineage FROM public.data_projection_lineage_v1 WHERE snapshot_ref=p_snapshot_id;
  v_identity:=jsonb_build_array(v_run.identity_binding_fingerprint);
  IF v_snapshot.projection_name='experiment-outcome-input-v1' THEN
    SELECT COALESCE(jsonb_agg(e.exposure_fingerprint ORDER BY e.exposure_fingerprint),'[]'::jsonb)
      INTO v_exposure FROM public.data_experiment_outcome_input_projection_v1 o
      JOIN public.recommendation_p2_experiment_exposure e ON e.exposure_id=o.exposure_ref
      WHERE o.snapshot_ref=p_snapshot_id;
  ELSE v_exposure:='[]'::jsonb; END IF;
  RETURN public.data_quality_fingerprint_v1('data-quality-validation-input-sha256-v1',jsonb_build_object(
    'snapshotRef',v_snapshot.snapshot_ref,'snapshotFingerprint',v_snapshot.content_fingerprint,
    'snapshotLineageFingerprint',v_snapshot.lineage_fingerprint,
    'checkpointRef',v_checkpoint.checkpoint_ref,'sourceSetFingerprint',v_checkpoint.source_set_fingerprint,
    'projectionSchemaVersion',v_snapshot.projection_schema_version,
    'projectionPolicyVersion',v_snapshot.projection_policy_version,
    'identityBindingVersion',v_run.identity_binding_version,
    'targetContractVersion',v_run.target_contract_version,
    'validatorVersion',p_validator_version,'qualityPolicyVersion',p_quality_policy_version,
    'validationScope',p_validation_scope,'sourceFingerprints',v_sources,'recordFingerprints',v_records,
    'lineageFingerprints',v_lineage,'identityFingerprints',v_identity,'exposureFingerprints',v_exposure));
END;
$$;

CREATE OR REPLACE FUNCTION public.persist_data_quality_validation_v1(
  p_validation_run_ref varchar,
  p_validation_scope varchar,
  p_snapshot_ref varchar,
  p_validator_version varchar,
  p_quality_policy_version varchar,
  p_validation_as_of timestamptz,
  p_validation_input_fingerprint varchar,
  p_checks jsonb,
  p_metrics jsonb,
  p_anomalies jsonb,
  p_verdict jsonb,
  p_rebuild jsonb,
  p_late_arrivals jsonb
)
RETURNS TABLE(disposition varchar,validation_run_id uuid,snapshot_quality_verdict_id uuid,error_code varchar)
LANGUAGE plpgsql SECURITY DEFINER
SET search_path = pg_catalog, public, pg_temp
AS $$
DECLARE
  v_now timestamptz:=clock_timestamp();
  v_snapshot public.data_projection_snapshot_v1%ROWTYPE;
  v_checkpoint public.data_source_checkpoint_v1%ROWTYPE;
  v_policy public.data_quality_policy_evidence_v1%ROWTYPE;
  v_existing public.data_quality_validation_run_v1%ROWTYPE;
  v_existing_verdict public.data_snapshot_quality_verdict_v1%ROWTYPE;
  v_run_id uuid;
  v_verdict_id uuid;
  v_logical_hash varchar(64);
  v_actual_input varchar(64);
  v_item jsonb;
  v_actual_fp varchar(64);
  v_required text;
  v_failed bigint;
  v_blockers bigint;
  v_skipped bigint;
  v_metric_failed bigint;
  v_unavailable bigint;
  v_errors bigint;
  v_warnings bigint;
  v_passed bigint;
  v_expected_score numeric;
  v_observation jsonb;
  v_expected_scope text;
  v_expected_threshold numeric;
  v_expected_operator text;
  v_metric_value numeric;
  v_expected_metric_status text;
BEGIN
  SELECT * INTO v_snapshot FROM public.data_projection_snapshot_v1 WHERE snapshot_ref=p_snapshot_ref;
  IF NOT FOUND THEN RAISE EXCEPTION 'Snapshot missing for DP-6 FULL validation.' USING ERRCODE='23503'; END IF;
  SELECT * INTO v_checkpoint FROM public.data_source_checkpoint_v1 WHERE checkpoint_id=v_snapshot.source_checkpoint_ref;
  SELECT * INTO v_policy FROM public.data_quality_policy_evidence_v1 WHERE quality_policy_version=p_quality_policy_version;
  IF NOT FOUND OR p_quality_policy_version<>'data-quality-policy-v1' THEN
    RAISE EXCEPTION 'Unsupported quality policy.' USING ERRCODE='22023';
  END IF;
  IF p_validation_scope<>'FULL' OR p_validator_version<>'data-quality-validator-v1'
     OR p_validation_as_of<>v_snapshot.snapshot_as_of
     OR p_checks IS NULL OR jsonb_typeof(p_checks)<>'array' OR jsonb_array_length(p_checks)=0
     OR p_metrics IS NULL OR jsonb_typeof(p_metrics)<>'array' OR jsonb_array_length(p_metrics)=0
     OR p_anomalies IS NULL OR jsonb_typeof(p_anomalies)<>'array'
     OR p_verdict IS NULL OR jsonb_typeof(p_verdict)<>'object'
     OR p_rebuild IS NULL OR jsonb_typeof(p_rebuild)<>'object'
     OR p_late_arrivals IS NULL OR jsonb_typeof(p_late_arrivals)<>'array'
     OR NOT public.data_quality_fingerprint_valid_v1(p_validation_input_fingerprint) THEN
    RAISE EXCEPTION 'Invalid DP-6 validation input.' USING ERRCODE='22023';
  END IF;

  v_actual_input:=public.data_quality_validation_input_fingerprint_v1(
    v_snapshot.snapshot_id,p_validation_scope,p_validator_version,p_quality_policy_version);
  IF v_actual_input<>p_validation_input_fingerprint THEN
    RAISE EXCEPTION 'Validation input fingerprint mismatch.' USING ERRCODE='23514';
  END IF;
  v_observation:=public.data_quality_snapshot_observation_v1(v_snapshot.snapshot_id);

  IF jsonb_array_length(p_checks)<>(SELECT count(DISTINCT c->>'checkCode') FROM jsonb_array_elements(p_checks)c)
     OR jsonb_array_length(p_metrics)<>(SELECT count(DISTINCT m->>'metricName') FROM jsonb_array_elements(p_metrics)m) THEN
    RAISE EXCEPTION 'Duplicate DP-6 check or metric identity.' USING ERRCODE='23505';
  END IF;

  FOR v_required IN SELECT jsonb_array_elements_text(v_policy.required_checks) LOOP
    IF NOT EXISTS (SELECT 1 FROM jsonb_array_elements(p_checks) c WHERE c->>'checkCode'=v_required) THEN
      RAISE EXCEPTION 'Required quality check missing: %',v_required USING ERRCODE='23514';
    END IF;
  END LOOP;
  FOR v_required IN SELECT jsonb_array_elements_text(v_policy.required_metrics) LOOP
    IF NOT EXISTS (SELECT 1 FROM jsonb_array_elements(p_metrics) m WHERE m->>'metricName'=v_required) THEN
      RAISE EXCEPTION 'Required quality metric missing: %',v_required USING ERRCODE='23514';
    END IF;
  END LOOP;

  FOR v_item IN SELECT value FROM jsonb_array_elements(p_checks) LOOP
    v_actual_fp:=public.data_quality_fingerprint_v1('data-quality-check-evidence-sha256-v1',
      jsonb_build_object('checkCode',v_item->>'checkCode','scope',v_item->>'checkScope',
        'expected',v_item->>'expectedValue','observed',v_item->>'observedValue',
        'difference',v_item->>'differenceValue','severity',v_item->>'severity',
        'status',v_item->>'checkStatus','failure',COALESCE(v_item->>'failureCode',''),
        'reason',COALESCE(v_item->>'reasonCode',''),
        'required',(v_item->>'required')::boolean));
    v_expected_scope:=CASE split_part(v_item->>'checkCode','.',1)
      WHEN 'source' THEN 'SOURCE_COMPLETENESS' WHEN 'projection' THEN 'PROJECTION_COMPLETENESS'
      WHEN 'snapshot' THEN 'SNAPSHOT_CONSISTENCY' WHEN 'lineage' THEN 'LINEAGE_INTEGRITY'
      WHEN 'identity' THEN 'IDENTITY_INTEGRITY' WHEN 'exposure' THEN 'EXPOSURE_INTEGRITY'
      WHEN 'rebuild' THEN 'DETERMINISTIC_REBUILD' ELSE NULL END;
    IF v_actual_fp<>v_item->>'evidenceFingerprint'
       OR v_expected_scope IS NULL OR v_item->>'checkScope'<>v_expected_scope
       OR v_item->>'checkStatus' NOT IN ('PASS','FAIL','SKIPPED','NOT_APPLICABLE')
       OR v_item->>'severity' NOT IN ('INFO','WARNING','ERROR','BLOCKER')
       OR (v_item->>'checkStatus'='PASS' AND v_item->>'observedValue'='not_executed')
       OR (v_item->>'checkStatus'='FAIL' AND (COALESCE(v_item->>'failureCode','')=''
            OR NOT public.data_quality_failure_code_valid_v1(v_item->>'failureCode')))
       OR (v_item->>'checkStatus'<>'FAIL' AND COALESCE(v_item->>'failureCode','')<>'')
       OR (v_item->>'checkStatus' IN ('SKIPPED','NOT_APPLICABLE') AND COALESCE(v_item->>'reasonCode','')='')
       OR (v_item->>'checkStatus' IN ('PASS','FAIL') AND COALESCE(v_item->>'reasonCode','')<>'') THEN
      RAISE EXCEPTION 'Invalid DP-6 check evidence.' USING ERRCODE='23514';
    END IF;
  END LOOP;
  FOR v_item IN SELECT value FROM jsonb_array_elements(p_metrics) LOOP
    v_actual_fp:=public.data_quality_fingerprint_v1('data-quality-metric-sha256-v1',
      jsonb_build_object('metricName',v_item->>'metricName','numerator',(v_item->>'numerator')::bigint,
        'denominator',(v_item->>'denominator')::bigint,'metricValue',COALESCE(v_item->>'metricValue',''),
        'metricUnit',v_item->>'metricUnit','threshold',(v_item->>'policyThreshold')::numeric,
        'operator',v_item->>'thresholdOperator',
        'thresholdResult',v_item->>'thresholdResult','metricVersion',v_item->>'metricVersion')); 
    v_expected_threshold:=(v_policy.thresholds->(v_item->>'metricName')->>'value')::numeric;
    v_expected_operator:=v_policy.thresholds->(v_item->>'metricName')->>'operator';
    IF (v_item->>'denominator')::bigint=0 THEN
      v_metric_value:=NULL;
      v_expected_metric_status:=v_policy.zero_denominator_policy;
    ELSE
      v_metric_value:=round((v_item->>'numerator')::numeric/(v_item->>'denominator')::numeric,12);
      v_expected_metric_status:=CASE v_expected_operator
        WHEN 'GREATER_THAN_OR_EQUAL' THEN CASE WHEN v_metric_value>=v_expected_threshold THEN 'PASS' ELSE 'FAIL' END
        WHEN 'LESS_THAN_OR_EQUAL' THEN CASE WHEN v_metric_value<=v_expected_threshold THEN 'PASS' ELSE 'FAIL' END
        WHEN 'EQUAL' THEN CASE WHEN v_metric_value=v_expected_threshold THEN 'PASS' ELSE 'FAIL' END
        ELSE NULL END;
    END IF;
    IF v_actual_fp<>v_item->>'metricFingerprint'
       OR v_item->>'metricUnit'<>'ratio' OR v_item->>'metricVersion'<>'data-quality-metric-v1'
       OR (v_item->>'denominator')::bigint<0 OR (v_item->>'numerator')::bigint<0
       OR (v_item->>'numerator')::bigint>(v_item->>'denominator')::bigint
       OR v_expected_threshold IS NULL OR v_expected_operator IS NULL
       OR (v_item->>'policyThreshold')::numeric<>v_expected_threshold
       OR v_item->>'thresholdOperator'<>v_expected_operator
       OR v_item->>'thresholdResult'<>v_expected_metric_status
       OR ((v_item->>'denominator')::bigint=0 AND COALESCE(v_item->>'metricValue','')<>'')
       OR ((v_item->>'denominator')::bigint>0 AND (v_item->>'metricValue')::numeric<>v_metric_value) THEN
      RAISE EXCEPTION 'Invalid DP-6 metric evidence.' USING ERRCODE='23514';
    END IF;
  END LOOP;
  FOR v_item IN SELECT value FROM jsonb_array_elements(p_anomalies) LOOP
    IF v_item->>'severity' NOT IN ('WARNING','ERROR','BLOCKER')
       OR NOT public.data_quality_failure_code_valid_v1(v_item->>'failureCode')
       OR NOT public.data_quality_fingerprint_valid_v1(v_item->>'evidenceFingerprint')
       OR NOT EXISTS (SELECT 1 FROM jsonb_array_elements(p_checks)c
         WHERE c->>'checkStatus'='FAIL' AND c->>'failureCode'=v_item->>'failureCode'
           AND c->>'severity'=v_item->>'severity'
           AND c->>'evidenceFingerprint'=v_item->>'evidenceFingerprint') THEN
      RAISE EXCEPTION 'Invalid DP-6 anomaly evidence.' USING ERRCODE='23514';
    END IF;
  END LOOP;

  v_actual_fp:=public.data_quality_fingerprint_v1('data-quality-verdict-sha256-v1',p_verdict-'verdictFingerprint');
  IF v_actual_fp<>p_verdict->>'verdictFingerprint'
     OR p_verdict->>'overallStatus' NOT IN ('VALIDATED','REJECTED','INCONCLUSIVE') THEN
    RAISE EXCEPTION 'Invalid DP-6 verdict evidence.' USING ERRCODE='23514';
  END IF;
  v_actual_fp:=public.data_quality_fingerprint_v1('data-quality-rebuild-comparison-sha256-v1',p_rebuild-'comparisonFingerprint');
  IF v_actual_fp<>p_rebuild->>'comparisonFingerprint' THEN
    RAISE EXCEPTION 'Invalid DP-6 rebuild evidence.' USING ERRCODE='23514';
  END IF;
  FOR v_item IN SELECT value FROM jsonb_array_elements(p_late_arrivals) LOOP
    v_actual_fp:=public.data_quality_fingerprint_v1('data-quality-late-arrival-observation-sha256-v1',
      jsonb_build_object('sourceEventRef',v_item->>'sourceEventRef',
        'affectedCheckpointRef',v_item->>'affectedCheckpointRef',
        'affectedSnapshotRef',v_item->>'affectedSnapshotRef','eventTime',v_item->>'eventTime',
        'ingestedAt',v_item->>'ingestedAt','latenessSeconds',(v_item->>'latenessDurationSeconds')::bigint,
        'policyClass',v_item->>'policyClass')); 
    IF v_actual_fp<>v_item->>'observationFingerprint' THEN
      RAISE EXCEPTION 'Invalid DP-6 late-arrival evidence.' USING ERRCODE='23514';
    END IF;
  END LOOP;

  SELECT count(*) FILTER(WHERE c->>'checkStatus'='FAIL'),
         count(*) FILTER(WHERE c->>'checkStatus'='FAIL' AND c->>'severity'='BLOCKER'),
         count(*) FILTER(WHERE c->>'checkStatus'='FAIL' AND c->>'severity'='ERROR'),
         count(*) FILTER(WHERE c->>'checkStatus'='FAIL' AND c->>'severity'='WARNING'),
         count(*) FILTER(WHERE c->>'checkStatus'='PASS'),
         count(*) FILTER(WHERE c->>'checkStatus'='SKIPPED' AND COALESCE((c->>'required')::boolean,false)),
         count(*) FILTER(WHERE c->>'checkStatus'='NOT_APPLICABLE' AND COALESCE((c->>'required')::boolean,false)
           AND NOT (c->>'checkCode'='exposure.binding' AND v_snapshot.projection_name='recommendation-profile-input-v1'))
    INTO v_failed,v_blockers,v_errors,v_warnings,v_passed,v_skipped,v_unavailable
  FROM jsonb_array_elements(p_checks)c;
  SELECT count(*) FILTER(WHERE m->>'thresholdResult'='FAIL') INTO v_metric_failed FROM jsonb_array_elements(p_metrics)m;
  v_expected_score:=CASE WHEN v_passed+v_failed+v_skipped=0 THEN 0
    ELSE round(v_passed::numeric*100/(v_passed+v_failed+v_skipped)::numeric,6) END;
  IF (p_verdict->>'blockerCount')::bigint<>v_blockers
     OR (p_verdict->>'errorCount')::bigint<>v_errors
     OR (p_verdict->>'warningCount')::bigint<>v_warnings
     OR (p_verdict->>'passedCheckCount')::bigint<>v_passed
     OR (p_verdict->>'failedCheckCount')::bigint<>v_failed
     OR (p_verdict->>'skippedRequiredCheckCount')::bigint<>v_skipped
     OR (p_verdict->>'qualityScore')::numeric<>v_expected_score
     OR (p_verdict->>'overallStatus'='VALIDATED' AND (v_failed>0 OR v_skipped>0 OR v_unavailable>0 OR v_metric_failed>0))
     OR (p_verdict->>'overallStatus'='REJECTED' AND v_failed=0 AND v_metric_failed=0)
     OR (p_verdict->>'overallStatus'='INCONCLUSIVE' AND (v_failed>0 OR v_blockers>0 OR (v_skipped=0 AND v_unavailable=0))) THEN
    RAISE EXCEPTION 'DP-6 verdict does not match checks and metrics.' USING ERRCODE='23514';
  END IF;

  IF p_verdict->>'overallStatus'='VALIDATED' AND (
       (v_observation->>'checkpointSourceCount')::bigint<>(v_observation->>'checkpointMemberCount')::bigint
       OR v_observation->>'checkpointSourceSetFingerprint'<>v_observation->>'actualSourceSetFingerprint'
       OR v_observation->>'checkpointDefinitionFingerprint'<>v_observation->>'actualCheckpointDefinitionFingerprint'
       OR (v_observation->>'sourceAuthorityMismatchCount')::bigint<>0
       OR (v_observation->>'sourceTimestampMismatchCount')::bigint<>0
       OR (v_observation->>'sourceRangeMismatchCount')::bigint<>0
       OR v_observation->>'lastSourceEventRef'<>v_observation->>'actualLastSourceEventRef'
       OR v_observation->>'snapshotStatus' NOT IN ('created','validated')
       OR (v_observation->>'snapshotRecordCount')::bigint<>(v_observation->>'actualRecordCount')::bigint
       OR (v_observation->>'snapshotSubjectCount')::bigint<>(v_observation->>'actualSubjectCount')::bigint
       OR (v_observation->>'snapshotSourceCount')::bigint<>(v_observation->>'actualSourceCount')::bigint
       OR v_observation->>'snapshotContentFingerprint'<>v_observation->>'actualContentFingerprint'
       OR v_observation->>'snapshotLineageFingerprint'<>v_observation->>'actualLineageFingerprint'
       OR (v_observation->>'missingLineageCount')::bigint<>0
       OR (v_observation->>'orphanLineageCount')::bigint<>0
       OR (v_observation->>'duplicateLineageCount')::bigint<>0
       OR (v_observation->>'invalidP2ExposureCount')::bigint<>0
       OR (v_snapshot.projection_name='recommendation-profile-input-v1'
           AND (v_observation->>'profileWindowCount')::bigint<>3)
       OR NOT (p_rebuild->>'matched')::boolean
       OR (p_rebuild->>'observedRecordCount')::bigint<>(v_observation->>'actualRecordCount')::bigint
       OR (p_rebuild->>'observedSubjectCount')::bigint<>(v_observation->>'actualSubjectCount')::bigint
       OR (p_rebuild->>'observedSourceCount')::bigint<>(v_observation->>'actualSourceCount')::bigint
       OR p_rebuild->'observedRecordFingerprints'<>v_observation->'recordFingerprints'
       OR p_rebuild->>'observedSnapshotFingerprint'<>v_observation->>'actualContentFingerprint'
       OR p_rebuild->>'observedLineageFingerprint'<>v_observation->>'actualLineageFingerprint') THEN
    RAISE EXCEPTION 'VALIDATED verdict conflicts with authoritative DP-6 observation.' USING ERRCODE='23514';
  END IF;

  v_logical_hash:=public.data_quality_fingerprint_v1('data-quality-validation-logical-identity-v1',jsonb_build_object(
    'snapshotRef',p_snapshot_ref,'validationScope',p_validation_scope,'validatorVersion',p_validator_version,
    'qualityPolicyVersion',p_quality_policy_version));
  PERFORM pg_advisory_xact_lock(hashtextextended(v_logical_hash,0));
  SELECT * INTO v_existing FROM public.data_quality_validation_run_v1 WHERE logical_identity_hash=v_logical_hash;
  IF FOUND THEN
    SELECT * INTO v_existing_verdict FROM public.data_snapshot_quality_verdict_v1
    WHERE validation_run_ref=v_existing.validation_run_id;
    IF v_existing.validation_input_fingerprint=p_validation_input_fingerprint
       AND v_existing_verdict.verdict_fingerprint=p_verdict->>'verdictFingerprint' THEN
      RETURN QUERY SELECT 'DUPLICATE'::varchar,v_existing.validation_run_id,
        v_existing_verdict.snapshot_quality_verdict_id,NULL::varchar;
      RETURN;
    END IF;
    EXECUTE 'INSERT INTO public.data_quality_validation_conflict_evidence_v1(
      logical_identity_hash,existing_validation_run_ref,existing_input_fingerprint,attempted_input_fingerprint,
      existing_verdict_fingerprint,attempted_verdict_fingerprint,failure_code,expires_at)
      VALUES($1,$2,$3,$4,$5,$6,''QUALITY_VERDICT_CONFLICT'',$7)'
      USING v_logical_hash,v_existing.validation_run_id,v_existing.validation_input_fingerprint,
        p_validation_input_fingerprint,v_existing_verdict.verdict_fingerprint,p_verdict->>'verdictFingerprint',
        v_now+interval '90 days';
    INSERT INTO public.data_quality_validation_status_evidence_v1(
      validation_run_ref,validation_status,failure_code,observed_at,expires_at)
    VALUES(v_existing.validation_run_id,'CONFLICTED','QUALITY_VERDICT_CONFLICT',v_now,v_now+interval '90 days');
    RETURN QUERY SELECT 'CONFLICT'::varchar,v_existing.validation_run_id,
      v_existing_verdict.snapshot_quality_verdict_id,'QUALITY_VERDICT_CONFLICT'::varchar;
    RETURN;
  END IF;

  INSERT INTO public.data_quality_validation_run_v1(
    validation_run_ref,logical_identity_hash,validation_scope,snapshot_ref,projection_name,
    projection_schema_version,projection_policy_version,source_checkpoint_ref,validator_version,
    quality_policy_version,validation_as_of,validation_input_fingerprint,expires_at)
  VALUES(p_validation_run_ref,v_logical_hash,p_validation_scope,v_snapshot.snapshot_id,v_snapshot.projection_name,
    v_snapshot.projection_schema_version,v_snapshot.projection_policy_version,v_checkpoint.checkpoint_id,
    p_validator_version,p_quality_policy_version,p_validation_as_of,p_validation_input_fingerprint,v_now+interval '90 days')
  RETURNING data_quality_validation_run_v1.validation_run_id INTO v_run_id;
  INSERT INTO public.data_quality_validation_status_evidence_v1(
    validation_run_ref,validation_status,observed_at,expires_at)
  VALUES(v_run_id,'STARTED',v_now,v_now+interval '90 days');

  FOR v_item IN SELECT value FROM jsonb_array_elements(p_checks) LOOP
    INSERT INTO public.data_quality_validation_check_result_v1(
      validation_run_ref,check_code,check_scope,expected_value,observed_value,difference_value,severity,
      check_status,failure_code,reason_code,required_check,evidence_fingerprint,expires_at)
    VALUES(v_run_id,v_item->>'checkCode',v_item->>'checkScope',to_jsonb(v_item->>'expectedValue'),
      to_jsonb(v_item->>'observedValue'),to_jsonb(v_item->>'differenceValue'),v_item->>'severity',
      v_item->>'checkStatus',NULLIF(v_item->>'failureCode',''),NULLIF(v_item->>'reasonCode',''),
      (v_item->>'required')::boolean,v_item->>'evidenceFingerprint',v_now+interval '90 days');
  END LOOP;
  FOR v_item IN SELECT value FROM jsonb_array_elements(p_metrics) LOOP
    INSERT INTO public.data_quality_metric_v1(
      validation_run_ref,metric_name,numerator,denominator,metric_value,metric_unit,policy_threshold,
      threshold_operator,threshold_result,metric_version,metric_fingerprint,expires_at)
    VALUES(v_run_id,v_item->>'metricName',(v_item->>'numerator')::bigint,(v_item->>'denominator')::bigint,
      NULLIF(v_item->>'metricValue','')::numeric,v_item->>'metricUnit',(v_item->>'policyThreshold')::numeric,
      v_item->>'thresholdOperator',v_item->>'thresholdResult',v_item->>'metricVersion',
      v_item->>'metricFingerprint',v_now+interval '90 days');
  END LOOP;
  FOR v_item IN SELECT value FROM jsonb_array_elements(p_anomalies) LOOP
    INSERT INTO public.data_quality_anomaly_evidence_v1(
      validation_run_ref,anomaly_scope,failure_code,severity,evidence_reference,evidence_fingerprint,expires_at)
    VALUES(v_run_id,v_item->>'scope',v_item->>'failureCode',v_item->>'severity',v_item->>'evidenceReference',
      v_item->>'evidenceFingerprint',v_now+interval '90 days');
  END LOOP;
  INSERT INTO public.data_snapshot_quality_verdict_v1(
    snapshot_ref,validation_run_ref,quality_policy_version,overall_status,blocker_count,error_count,
    warning_count,passed_check_count,failed_check_count,skipped_required_check_count,quality_score,
    verdict_fingerprint,expires_at)
  VALUES(v_snapshot.snapshot_id,v_run_id,p_quality_policy_version,p_verdict->>'overallStatus',
    (p_verdict->>'blockerCount')::bigint,(p_verdict->>'errorCount')::bigint,
    (p_verdict->>'warningCount')::bigint,(p_verdict->>'passedCheckCount')::bigint,
    (p_verdict->>'failedCheckCount')::bigint,(p_verdict->>'skippedRequiredCheckCount')::bigint,
    (p_verdict->>'qualityScore')::numeric,p_verdict->>'verdictFingerprint',v_now+interval '90 days')
  RETURNING data_snapshot_quality_verdict_v1.snapshot_quality_verdict_id INTO v_verdict_id;

  EXECUTE 'INSERT INTO public.data_quality_rebuild_comparison_v1(
    validation_run_ref,matched,expected_record_count,observed_record_count,expected_subject_count,
    observed_subject_count,expected_source_count,observed_source_count,expected_record_fingerprints,
    observed_record_fingerprints,expected_snapshot_fingerprint,observed_snapshot_fingerprint,
    expected_lineage_fingerprint,observed_lineage_fingerprint,comparison_fingerprint,expires_at)
    VALUES($1,$2,$3,$4,$5,$6,$7,$8,$9,$10,$11,$12,$13,$14,$15,$16)'
  USING v_run_id,(p_rebuild->>'matched')::boolean,(p_rebuild->>'expectedRecordCount')::bigint,
    (p_rebuild->>'observedRecordCount')::bigint,(p_rebuild->>'expectedSubjectCount')::bigint,
    (p_rebuild->>'observedSubjectCount')::bigint,(p_rebuild->>'expectedSourceCount')::bigint,
    (p_rebuild->>'observedSourceCount')::bigint,p_rebuild->'expectedRecordFingerprints',
    p_rebuild->'observedRecordFingerprints',p_rebuild->>'expectedSnapshotFingerprint',
    p_rebuild->>'observedSnapshotFingerprint',p_rebuild->>'expectedLineageFingerprint',
    p_rebuild->>'observedLineageFingerprint',p_rebuild->>'comparisonFingerprint',v_now+interval '90 days';
  FOR v_item IN SELECT value FROM jsonb_array_elements(p_late_arrivals) LOOP
    INSERT INTO public.data_quality_late_arrival_observation_v1(
      source_event_ref,affected_checkpoint_ref,affected_snapshot_ref,event_time,ingested_at,
      lateness_duration_seconds,policy_class,observation_fingerprint,expires_at)
    VALUES(v_item->>'sourceEventRef',v_checkpoint.checkpoint_id,v_snapshot.snapshot_id,
      (v_item->>'eventTime')::timestamptz,(v_item->>'ingestedAt')::timestamptz,
      (v_item->>'latenessDurationSeconds')::bigint,v_item->>'policyClass',
      v_item->>'observationFingerprint',v_now+interval '90 days');
  END LOOP;
  INSERT INTO public.data_quality_validation_status_evidence_v1(
    validation_run_ref,validation_status,observed_at,expires_at)
  VALUES(v_run_id,'COMPLETED',v_now,v_now+interval '90 days');
  RETURN QUERY SELECT 'NEW'::varchar,v_run_id,v_verdict_id,NULL::varchar;
END;
$$;

REVOKE ALL ON FUNCTION public.data_quality_snapshot_observation_v1(uuid) FROM PUBLIC;
REVOKE ALL ON FUNCTION public.data_quality_validation_input_fingerprint_v1(uuid,varchar,varchar,varchar) FROM PUBLIC;
REVOKE ALL ON FUNCTION public.persist_data_quality_validation_v1(varchar,varchar,varchar,varchar,varchar,timestamptz,varchar,jsonb,jsonb,jsonb,jsonb,jsonb,jsonb) FROM PUBLIC;
REVOKE ALL ON TABLE public.data_quality_validation_run_v1,public.data_quality_validation_status_evidence_v1,
  public.data_quality_validation_check_result_v1,public.data_quality_anomaly_evidence_v1,
  public.data_quality_policy_evidence_v1,public.data_quality_metric_v1,
  public.data_snapshot_quality_verdict_v1,public.data_quality_late_arrival_observation_v1
  FROM PUBLIC,jc_data_quality_writer,jc_data_quality_reader;

GRANT USAGE ON SCHEMA public TO jc_data_quality_writer,jc_data_quality_reader,jc_data_quality_function_owner;
GRANT SELECT,INSERT ON public.data_quality_validation_run_v1,public.data_quality_validation_status_evidence_v1,
  public.data_quality_validation_check_result_v1,public.data_quality_anomaly_evidence_v1,
  public.data_quality_metric_v1,public.data_snapshot_quality_verdict_v1,
  public.data_quality_late_arrival_observation_v1 TO jc_data_quality_function_owner;
GRANT SELECT ON public.data_quality_policy_evidence_v1,public.data_projection_snapshot_v1,
  public.data_projection_run_v1,public.data_source_checkpoint_v1,public.data_projection_lineage_v1,
  public.data_platform_event_v1,public.data_recommendation_adapter_output_v1,
  public.data_recommendation_profile_input_projection_v1,public.data_experiment_outcome_input_projection_v1,
  public.recommendation_p2_experiment_assignment,public.recommendation_p2_experiment_exposure
  TO jc_data_quality_function_owner;
GRANT EXECUTE ON FUNCTION public.gen_random_uuid(),public.data_projection_instant_text_v1(timestamptz),
  public.data_quality_version_valid_v1(varchar),public.data_quality_fingerprint_valid_v1(varchar),
  public.data_quality_fingerprint_v1(varchar,jsonb),public.data_quality_failure_code_valid_v1(varchar),
  public.data_quality_snapshot_observation_v1(uuid),
  public.data_quality_validation_input_fingerprint_v1(uuid,varchar,varchar,varchar)
  TO jc_data_quality_function_owner;
ALTER FUNCTION public.data_quality_snapshot_observation_v1(uuid) OWNER TO jc_data_quality_function_owner;
ALTER FUNCTION public.data_quality_validation_input_fingerprint_v1(uuid,varchar,varchar,varchar) OWNER TO jc_data_quality_function_owner;
ALTER FUNCTION public.persist_data_quality_validation_v1(varchar,varchar,varchar,varchar,varchar,timestamptz,varchar,jsonb,jsonb,jsonb,jsonb,jsonb,jsonb) OWNER TO jc_data_quality_function_owner;
GRANT EXECUTE ON FUNCTION public.persist_data_quality_validation_v1(varchar,varchar,varchar,varchar,varchar,timestamptz,varchar,jsonb,jsonb,jsonb,jsonb,jsonb,jsonb) TO jc_data_quality_writer;

COMMIT;
