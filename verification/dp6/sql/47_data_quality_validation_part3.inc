  v_record_fp := public.data_projection_fingerprint_v1('data-projection-record-sha256-v1',v_record-'recordRef');
  v_record := v_record || jsonb_build_object('projectionRecordFingerprint',v_record_fp);
  v_records := jsonb_build_array(v_record);
  v_snapshot_fp := public.data_projection_fingerprint_v1('data-projection-snapshot-sha256-v1',jsonb_build_object(
    'projectionName','experiment-outcome-input-v1','projectionSchemaVersion','experiment-outcome-input-v1',
    'projectionPolicyVersion','experiment-outcome-projection-policy-v1','featurePolicyVersion','experiment-outcome-feature-policy-v1',
    'identityBindingVersion','recommendation-user-subject-binding-v1','targetContractVersion','experiment-outcome-input-v1',
    'sourceCheckpointRef','checkpoint:dp5-outcome','snapshotAsOf','2026-07-22T00:00:00Z',
    'recordFingerprints',jsonb_build_array(v_record_fp)));
  SELECT disposition INTO v_disp FROM public.persist_data_projection_snapshot_v1(
    'projection_run:dp5-outcome','snapshot:dp5-outcome','experiment-outcome-input-v1','experiment-outcome-input-v1',
    'experiment-outcome-projection-policy-v1','experiment-outcome-feature-policy-v1','checkpoint:dp5-outcome',
    '2026-07-22T00:00:00Z','recommendation-user-subject-binding-v1','approved-binding-input',repeat('b',64),
    'journey-connect','experiment-outcome-input-v1','git:'||repeat('1',40),v_records,v_lineage,v_snapshot_fp,v_lineage_fp);
  IF v_disp<>'NEW' THEN RAISE EXCEPTION 'outcome snapshot NEW failed'; END IF;

  -- General exposure cannot substitute for protected P2 authority.
  BEGIN
    v_record := jsonb_set(v_record,'{exposureRef}','"general-exposure"');
    v_record_fp := public.data_projection_fingerprint_v1('data-projection-record-sha256-v1',v_record-'recordRef'-'projectionRecordFingerprint');
    v_record := jsonb_set(v_record,'{projectionRecordFingerprint}',to_jsonb(v_record_fp));
    PERFORM public.persist_data_projection_snapshot_v1(
      'projection_run:invalid-exposure','snapshot:invalid-exposure','experiment-outcome-input-v1','experiment-outcome-input-v1',
      'experiment-outcome-projection-policy-v2','experiment-outcome-feature-policy-v1','checkpoint:dp5-outcome',
      '2026-07-22T00:00:00Z','recommendation-user-subject-binding-v1','approved-binding-input',repeat('b',64),
      'journey-connect','experiment-outcome-input-v1','git:'||repeat('1',40),jsonb_build_array(v_record),v_lineage,
      repeat('0',64),v_lineage_fp);
    RAISE EXCEPTION 'general exposure unexpectedly succeeded';
  EXCEPTION WHEN SQLSTATE '22023' OR SQLSTATE '23503' OR SQLSTATE '23514' THEN NULL; END;
END;
$$;


-- DP-6 valid FULL validation, DUPLICATE and CONFLICT.
DO $$
DECLARE
  v_snapshot public.data_projection_snapshot_v1%ROWTYPE;
  v_policy public.data_quality_policy_evidence_v1%ROWTYPE;
  v_input varchar(64);
  v_checks jsonb:='[]'::jsonb;
  v_metrics jsonb:='[]'::jsonb;
  v_item jsonb;
  v_code text;
  v_scope text;
  v_status text;
  v_reason text;
  v_threshold numeric;
  v_operator text;
  v_numerator bigint;
  v_value numeric;
  v_verdict jsonb;
  v_rebuild jsonb;
  v_record_fps jsonb;
  v_disp varchar;
  v_run uuid;
  v_verdict_id uuid;
  v_error varchar;
  v_passed bigint;
BEGIN
  SELECT * INTO v_snapshot FROM public.data_projection_snapshot_v1 WHERE snapshot_ref='snapshot:dp5-outcome';
  SELECT * INTO v_policy FROM public.data_quality_policy_evidence_v1 WHERE quality_policy_version='data-quality-policy-v1';
  v_input:=public.data_quality_validation_input_fingerprint_v1(v_snapshot.snapshot_id,'FULL','data-quality-validator-v1','data-quality-policy-v1');

  FOR v_code IN SELECT jsonb_array_elements_text(v_policy.required_checks) LOOP
    v_scope:=CASE split_part(v_code,'.',1)
      WHEN 'source' THEN 'SOURCE_COMPLETENESS'
      WHEN 'projection' THEN 'PROJECTION_COMPLETENESS'
      WHEN 'snapshot' THEN 'SNAPSHOT_CONSISTENCY'
      WHEN 'lineage' THEN 'LINEAGE_INTEGRITY'
      WHEN 'identity' THEN 'IDENTITY_INTEGRITY'
      WHEN 'exposure' THEN 'EXPOSURE_INTEGRITY'
      ELSE 'DETERMINISTIC_REBUILD' END;
    v_status:='PASS';v_reason:=NULL;
    v_item:=jsonb_build_object(
      'checkCode',v_code,'checkScope',v_scope,'expectedValue','exact',
      'observedValue','exact','differenceValue','0',
      'severity','INFO','checkStatus',v_status,'failureCode','','reasonCode',COALESCE(v_reason,''),'required',true);
    v_item:=v_item||jsonb_build_object('evidenceFingerprint',
      public.data_quality_fingerprint_v1('data-quality-check-evidence-sha256-v1',jsonb_build_object(
        'checkCode',v_item->>'checkCode','scope',v_item->>'checkScope','expected',v_item->>'expectedValue',
        'observed',v_item->>'observedValue','difference',v_item->>'differenceValue','severity',v_item->>'severity',
        'status',v_item->>'checkStatus','failure',COALESCE(v_item->>'failureCode',''),
        'reason',COALESCE(v_item->>'reasonCode',''),
        'required',(v_item->>'required')::boolean)));
    v_checks:=v_checks||jsonb_build_array(v_item);
  END LOOP;

  FOR v_code IN SELECT jsonb_array_elements_text(v_policy.required_metrics) LOOP
    v_threshold:=COALESCE((v_policy.thresholds->v_code->>'value')::numeric,0);
    v_operator:=COALESCE(v_policy.thresholds->v_code->>'operator','GREATER_THAN_OR_EQUAL');
    IF v_code IN ('lineage_orphan_rate','duplicate_source_rate','duplicate_lineage_rate','late_arrival_rate','conflict_rate')
      THEN v_numerator:=0;v_value:=0;
    ELSE v_numerator:=1;v_value:=1; END IF;
    v_item:=jsonb_build_object('metricName',v_code,'numerator',v_numerator,'denominator',1,
      'metricValue',v_value::text,'metricUnit','ratio','policyThreshold',v_threshold::text,
      'thresholdOperator',v_operator,'thresholdResult','PASS','metricVersion','data-quality-metric-v1');
    v_item:=v_item||jsonb_build_object('metricFingerprint',
      public.data_quality_fingerprint_v1('data-quality-metric-sha256-v1',jsonb_build_object(
        'metricName',v_item->>'metricName','numerator',(v_item->>'numerator')::bigint,
        'denominator',(v_item->>'denominator')::bigint,'metricValue',v_item->>'metricValue',
        'metricUnit',v_item->>'metricUnit','threshold',(v_item->>'policyThreshold')::numeric,
        'operator',v_item->>'thresholdOperator',
        'thresholdResult',v_item->>'thresholdResult','metricVersion',v_item->>'metricVersion')));
    v_metrics:=v_metrics||jsonb_build_array(v_item);
  END LOOP;

  SELECT jsonb_agg(projection_record_fingerprint ORDER BY projection_record_fingerprint) INTO v_record_fps
  FROM public.data_experiment_outcome_input_projection_v1 WHERE snapshot_ref=v_snapshot.snapshot_id;
  v_rebuild:=jsonb_build_object('matched',true,'expectedRecordCount',v_snapshot.record_count,
    'observedRecordCount',v_snapshot.record_count,'expectedSubjectCount',v_snapshot.subject_count,
    'observedSubjectCount',v_snapshot.subject_count,'expectedSourceCount',v_snapshot.source_event_count,
    'observedSourceCount',v_snapshot.source_event_count,'expectedRecordFingerprints',v_record_fps,
    'observedRecordFingerprints',v_record_fps,'expectedSnapshotFingerprint',v_snapshot.content_fingerprint,
    'observedSnapshotFingerprint',v_snapshot.content_fingerprint,'expectedLineageFingerprint',v_snapshot.lineage_fingerprint,
    'observedLineageFingerprint',v_snapshot.lineage_fingerprint);
  v_rebuild:=v_rebuild||jsonb_build_object('comparisonFingerprint',
    public.data_quality_fingerprint_v1('data-quality-rebuild-comparison-sha256-v1',v_rebuild));
  SELECT count(*) INTO v_passed FROM jsonb_array_elements(v_checks)c WHERE c->>'checkStatus'='PASS';
  v_verdict:=jsonb_build_object('overallStatus','VALIDATED','blockerCount',0,'errorCount',0,'warningCount',0,
    'passedCheckCount',v_passed,'failedCheckCount',0,'skippedRequiredCheckCount',0,'qualityScore','100',
    'qualityPolicyVersion','data-quality-policy-v1');
  v_verdict:=v_verdict||jsonb_build_object('verdictFingerprint',
    public.data_quality_fingerprint_v1('data-quality-verdict-sha256-v1',v_verdict));

  SELECT disposition,validation_run_id,snapshot_quality_verdict_id,error_code
    INTO v_disp,v_run,v_verdict_id,v_error
  FROM public.persist_data_quality_validation_v1('quality_run:dp6-valid','FULL','snapshot:dp5-outcome',
    'data-quality-validator-v1','data-quality-policy-v1','2026-07-22T00:00:00Z',v_input,
    v_checks,v_metrics,'[]'::jsonb,v_verdict,v_rebuild,'[]'::jsonb);
  IF v_disp<>'NEW' OR v_error IS NOT NULL THEN RAISE EXCEPTION 'valid FULL validation NEW failed'; END IF;
  SELECT disposition INTO v_disp FROM public.persist_data_quality_validation_v1('quality_run:ignored-duplicate',
    'FULL','snapshot:dp5-outcome','data-quality-validator-v1','data-quality-policy-v1','2026-07-22T00:00:00Z',v_input,
    v_checks,v_metrics,'[]'::jsonb,v_verdict,v_rebuild,'[]'::jsonb);
  IF v_disp<>'DUPLICATE' THEN RAISE EXCEPTION 'same validation DUPLICATE failed'; END IF;

  -- Same logical identity with a different valid verdict is a conflict, never a second verdict.
  v_item:=v_checks->0;
  v_item:=jsonb_set(v_item,'{checkStatus}','"FAIL"')||jsonb_build_object(
    'severity','BLOCKER','failureCode','quality_threshold_failed','reasonCode','');
  v_item:=v_item-'evidenceFingerprint';