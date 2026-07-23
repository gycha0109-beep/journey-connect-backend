     OR v_checks IS NULL OR jsonb_typeof(v_checks)<>'array' OR jsonb_array_length(v_checks)=0
     OR v_authorities IS NULL OR jsonb_typeof(v_authorities)<>'array' OR jsonb_array_length(v_authorities)=0 THEN
    RAISE EXCEPTION 'Invalid DP-7 integration request.' USING ERRCODE='22023';
  END IF;

  IF p_request->>'integrationPolicyVersion'<>'data-cross-track-integration-policy-v1'
     OR p_request->>'mappingPolicyVersion'<>'data-cross-track-mapping-policy-v1'
     OR p_request->>'validatorVersion'<>'data-cross-track-integration-validator-v1'
     OR p_request->>'sourceTrack'<>'Data'
     OR p_request->>'targetTrack' NOT IN ('Recommendation','Intelligence','Search','Data') THEN
    RAISE EXCEPTION 'Unsupported DP-7 policy, validator or track.' USING ERRCODE='22023';
  END IF;

  SELECT * INTO v_snapshot FROM public.data_projection_snapshot_v1
  WHERE snapshot_ref=p_request->>'sourceSnapshotRef';
  IF NOT FOUND THEN
    RAISE EXCEPTION 'DP-7 source snapshot missing.' USING ERRCODE='23503';
  END IF;
  IF v_snapshot.projection_name<>p_request->>'sourceContract'
     OR v_snapshot.projection_schema_version<>p_request->>'sourceSchemaVersion' THEN
    RAISE EXCEPTION 'DP-7 source contract or schema mismatch.' USING ERRCODE='23514';
  END IF;

  SELECT * INTO v_quality FROM public.data_snapshot_quality_verdict_v1
  WHERE snapshot_quality_verdict_id=(p_request->>'sourceQualityVerdictRef')::uuid;
  IF NOT FOUND THEN
    RAISE EXCEPTION 'quality_verdict_missing' USING ERRCODE='23503';
  END IF;
  IF v_quality.snapshot_ref<>v_snapshot.snapshot_id THEN
    RAISE EXCEPTION 'quality_snapshot_mismatch' USING ERRCODE='23514';
  END IF;
  IF v_quality.quality_policy_version<>'data-quality-policy-v1' THEN
    RAISE EXCEPTION 'quality_policy_unsupported' USING ERRCODE='23514';
  END IF;
  IF v_quality.overall_status='REJECTED' THEN
    RAISE EXCEPTION 'quality_verdict_rejected' USING ERRCODE='23514';
  ELSIF v_quality.overall_status='INCONCLUSIVE' THEN
    RAISE EXCEPTION 'quality_verdict_inconclusive' USING ERRCODE='23514';
  ELSIF v_quality.overall_status<>'VALIDATED' THEN
    RAISE EXCEPTION 'quality_verdict_authority_violation' USING ERRCODE='23514';
  END IF;
  IF NOT public.data_integration_fingerprint_valid_v1(v_quality.verdict_fingerprint) THEN
    RAISE EXCEPTION 'quality_verdict_fingerprint_mismatch' USING ERRCODE='23514';
  END IF;
  IF EXISTS (
    SELECT 1 FROM public.data_snapshot_quality_verdict_v1 q
    WHERE q.snapshot_ref=v_snapshot.snapshot_id
      AND q.snapshot_quality_verdict_id<>v_quality.snapshot_quality_verdict_id
      AND (q.overall_status<>v_quality.overall_status OR q.verdict_fingerprint<>v_quality.verdict_fingerprint)
  ) THEN
    RAISE EXCEPTION 'quality_verdict_conflicted' USING ERRCODE='23514';
  END IF;

  IF (p_request->>'validationAsOf')::timestamptz<v_snapshot.snapshot_as_of THEN
    RAISE EXCEPTION 'DP-7 validation time precedes snapshot.' USING ERRCODE='22007';
  END IF;

  IF v_mapping->>'sourceContract'<>p_request->>'sourceContract'
     OR v_mapping->>'sourceSchemaVersion'<>p_request->>'sourceSchemaVersion'
     OR v_mapping->>'targetContract'<>p_request->>'targetContract'
     OR v_mapping->>'targetSchemaVersion'<>p_request->>'targetSchemaVersion'
     OR v_mapping->>'mappingPolicyVersion'<>p_request->>'mappingPolicyVersion' THEN
    RAISE EXCEPTION 'DP-7 mapping identity mismatch.' USING ERRCODE='23514';
  END IF;
  v_mapping_fp:=public.data_cross_track_mapping_fingerprint_v1(v_mapping);
  IF v_mapping_fp<>p_request->>'integrationMappingFingerprint' THEN
    RAISE EXCEPTION 'integration_mapping_fingerprint mismatch.' USING ERRCODE='23514';
  END IF;
  v_matrix_fp:=public.data_cross_track_contract_matrix_fingerprint_v1(v_mapping_fp);
  IF v_matrix_fp<>p_request->>'contractMatrixFingerprint' THEN
    RAISE EXCEPTION 'cross_track_contract_matrix_fingerprint mismatch.' USING ERRCODE='23514';
  END IF;

  IF COALESCE(v_identity->>'bindingFingerprint','')=''
     OR v_identity->>'bindingFingerprint'<>v_identity->>'authoritativeFingerprint'
     OR NOT public.data_integration_fingerprint_valid_v1(v_identity->>'bindingFingerprint')
     OR v_identity->>'bindingScope'<>'cross-track-integration'
     OR v_identity->>'ownerTrack'<>'Data'
     OR COALESCE((v_identity->>'automaticMerge')::boolean,true)
     OR v_identity->>'sourceIdentityRef' !~ '^(subject:[A-Za-z0-9][A-Za-z0-9._~-]{0,127}|user:[1-9][0-9]*)$'
     OR v_identity->>'targetIdentityRef' !~ '^(subject:[A-Za-z0-9][A-Za-z0-9._~-]{0,127}|user:[1-9][0-9]*)$' THEN
    RAISE EXCEPTION 'cross_track_identity_binding_invalid' USING ERRCODE='23514';
  END IF;

  IF EXISTS (SELECT 1 FROM jsonb_array_elements(v_authorities) a
    WHERE COALESCE((a->>'writeAttempted')::boolean,false)
       OR COALESCE((a->>'productionAttempted')::boolean,false)
       OR (COALESCE((a->>'readAttempted')::boolean,false) AND NOT COALESCE((a->>'readAllowed')::boolean,false))
       OR (COALESCE((a->>'validationAttempted')::boolean,false) AND NOT COALESCE((a->>'validationAllowed')::boolean,false))) THEN
    RAISE EXCEPTION 'cross_track_authority_violation' USING ERRCODE='23514';
  END IF;

  IF COALESCE((v_privacy->>'rawPayloadPresent')::boolean,true)
     OR COALESCE((v_privacy->>'piiPresent')::boolean,true)
     OR COALESCE((v_privacy->>'rawTextPresent')::boolean,true)
     OR COALESCE((v_privacy->>'preciseLocationPresent')::boolean,true)
     OR NOT COALESCE((v_privacy->>'aggregateOnly')::boolean,false)
     OR NOT COALESCE((v_privacy->>'lineagePurposeBound')::boolean,false)
     OR COALESCE((v_privacy->>'reidentificationRisk')::boolean,true) THEN
    RAISE EXCEPTION 'cross_track_privacy_violation' USING ERRCODE='23514';
  END IF;

  IF (v_retention->>'sourceRetentionDays')::integer<1
     OR (v_retention->>'targetRetentionDays')::integer<1
     OR (v_retention->>'targetRetentionDays')::integer>(v_retention->>'sourceRetentionDays')::integer
     OR (v_retention->>'integrationEvidenceRetentionDays')::integer<>90
     OR NOT COALESCE((v_retention->>'deletionSemanticsAligned')::boolean,false)
     OR COALESCE((v_retention->>'automaticPurgeEnabled')::boolean,true)
     OR COALESCE((v_retention->>'physicalDeleteEnabled')::boolean,true) THEN
    RAISE EXCEPTION 'cross_track_retention_conflict' USING ERRCODE='23514';
  END IF;

  IF jsonb_array_length(v_checks)<>(SELECT count(DISTINCT c->>'checkCode') FROM jsonb_array_elements(v_checks)c)
     OR jsonb_array_length(v_checks)<>(SELECT count(DISTINCT (c->>'order')::integer) FROM jsonb_array_elements(v_checks)c) THEN
    RAISE EXCEPTION 'Duplicate DP-7 check identity or order.' USING ERRCODE='23505';
  END IF;

  FOREACH v_required_prefix IN ARRAY ARRAY['quality.','identity.','authority.','privacy.','retention.','fingerprint.'] LOOP
    IF NOT EXISTS (SELECT 1 FROM jsonb_array_elements(v_checks)c
      WHERE c->>'checkCode' LIKE v_required_prefix||'%' AND COALESCE((c->>'required')::boolean,false)) THEN
      RAISE EXCEPTION 'Required DP-7 check category missing: %',v_required_prefix USING ERRCODE='23514';
    END IF;
  END LOOP;
  v_required_prefix:=CASE p_request->>'targetTrack'
    WHEN 'Recommendation' THEN 'recommendation.' WHEN 'Intelligence' THEN 'intelligence.'
    WHEN 'Search' THEN 'search.' ELSE NULL END;
  IF v_required_prefix IS NOT NULL AND NOT EXISTS (SELECT 1 FROM jsonb_array_elements(v_checks)c
    WHERE c->>'checkCode' LIKE v_required_prefix||'%' AND COALESCE((c->>'required')::boolean,false)) THEN
    RAISE EXCEPTION 'Required target-track check category missing.' USING ERRCODE='23514';
  END IF;

  FOR v_item IN SELECT value FROM jsonb_array_elements(v_checks)
    ORDER BY (value->>'order')::integer,value->>'checkCode'
  LOOP
    IF (v_item->>'order')::integer<0
       OR v_item->>'checkCode' !~ '^[a-z][a-z0-9_.]{0,95}$'
       OR v_item->>'checkStatus' NOT IN ('PASS','FAIL','SKIPPED','NOT_APPLICABLE')
       OR v_item->>'severity' NOT IN ('INFO','WARNING','ERROR','BLOCKER')
       OR (v_item->>'checkStatus'='PASS' AND COALESCE(v_item->>'observedValue','')='not_executed')
       OR (v_item->>'checkStatus'='FAIL' AND (COALESCE(v_item->>'failureCode','')=''
           OR NOT public.data_integration_failure_code_valid_v1(v_item->>'failureCode')))
       OR (v_item->>'checkStatus' NOT IN ('FAIL','SKIPPED') AND COALESCE(v_item->>'failureCode','')<>'')
       OR public.data_cross_track_check_fingerprint_v1(v_item)<>v_item->>'evidenceFingerprint' THEN
      RAISE EXCEPTION 'Invalid DP-7 check evidence: %',COALESCE(v_item->>'checkCode','<missing>') USING ERRCODE='23514';
    END IF;
  END LOOP;

  IF NOT COALESCE((p_request->>'targetContractConfirmed')::boolean,false)
     AND NOT EXISTS (SELECT 1 FROM jsonb_array_elements(v_checks)c
       WHERE COALESCE((c->>'required')::boolean,false) AND c->>'checkStatus'='SKIPPED'
         AND c->>'checkCode' LIKE v_required_prefix||'%') THEN
    RAISE EXCEPTION 'Missing target contract must remain INCONCLUSIVE.' USING ERRCODE='23514';
  END IF;

  SELECT COALESCE(jsonb_agg(jsonb_build_object(
      'objectName',a->>'objectName','owningTrack',a->>'owningTrack','actorTrack',a->>'actorTrack',
