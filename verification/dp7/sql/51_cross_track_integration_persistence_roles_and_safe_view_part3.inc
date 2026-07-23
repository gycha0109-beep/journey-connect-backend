      'readAllowed',COALESCE((a->>'readAllowed')::boolean,false),
      'writeAllowed',COALESCE((a->>'writeAllowed')::boolean,false),
      'validationAllowed',COALESCE((a->>'validationAllowed')::boolean,false),
      'productionAllowed',COALESCE((a->>'productionAllowed')::boolean,false),
      'readAttempted',COALESCE((a->>'readAttempted')::boolean,false),
      'writeAttempted',COALESCE((a->>'writeAttempted')::boolean,false),
      'validationAttempted',COALESCE((a->>'validationAttempted')::boolean,false),
      'productionAttempted',COALESCE((a->>'productionAttempted')::boolean,false))
      ORDER BY a->>'objectName'),'[]'::jsonb)
  INTO v_authority_results FROM jsonb_array_elements(v_authorities)a;

  v_input_fp:=public.data_integration_fingerprint_v1('integration-input-sha256-v1',jsonb_build_object(
    'sourceTrack',p_request->>'sourceTrack','targetTrack',p_request->>'targetTrack',
    'sourceContract',p_request->>'sourceContract','sourceSchemaVersion',p_request->>'sourceSchemaVersion',
    'targetContract',p_request->>'targetContract','targetSchemaVersion',p_request->>'targetSchemaVersion',
    'sourceSnapshotFingerprint',v_snapshot.content_fingerprint,
    'sourceLineageFingerprint',v_snapshot.lineage_fingerprint,
    'sourceQualityVerdictFingerprint',v_quality.verdict_fingerprint,
    'mappingPolicyVersion',p_request->>'mappingPolicyVersion',
    'integrationPolicyVersion',p_request->>'integrationPolicyVersion',
    'identityBindingFingerprint',v_identity->>'bindingFingerprint',
    'mappingFingerprint',v_mapping_fp,
    'authorityResults',v_authority_results,
    'privacyResult',jsonb_build_object(
      'sourcePrivacyClass',v_privacy->>'sourcePrivacyClass',
      'targetPrivacyClass',v_privacy->>'targetPrivacyClass',
      'rawPayloadPresent',COALESCE((v_privacy->>'rawPayloadPresent')::boolean,false),
      'piiPresent',COALESCE((v_privacy->>'piiPresent')::boolean,false),
      'rawTextPresent',COALESCE((v_privacy->>'rawTextPresent')::boolean,false),
      'preciseLocationPresent',COALESCE((v_privacy->>'preciseLocationPresent')::boolean,false),
      'aggregateOnly',COALESCE((v_privacy->>'aggregateOnly')::boolean,false),
      'lineagePurposeBound',COALESCE((v_privacy->>'lineagePurposeBound')::boolean,false),
      'reidentificationRisk',COALESCE((v_privacy->>'reidentificationRisk')::boolean,false)),
    'retentionResult',jsonb_build_object(
      'sourceRetentionDays',(v_retention->>'sourceRetentionDays')::integer,
      'targetRetentionDays',(v_retention->>'targetRetentionDays')::integer,
      'integrationEvidenceRetentionDays',(v_retention->>'integrationEvidenceRetentionDays')::integer,
      'deletionSemanticsAligned',COALESCE((v_retention->>'deletionSemanticsAligned')::boolean,false),
      'automaticPurgeEnabled',COALESCE((v_retention->>'automaticPurgeEnabled')::boolean,false),
      'physicalDeleteEnabled',COALESCE((v_retention->>'physicalDeleteEnabled')::boolean,false))));
  IF v_input_fp<>p_request->>'integrationInputFingerprint' THEN
    RAISE EXCEPTION 'integration_input_fingerprint mismatch.' USING ERRCODE='23514';
  END IF;

  SELECT count(*) FILTER(WHERE c->>'checkStatus'='FAIL' AND c->>'severity'='BLOCKER'),
         count(*) FILTER(WHERE c->>'checkStatus'='FAIL' AND c->>'severity'='ERROR'),
         count(*) FILTER(WHERE c->>'severity'='WARNING'),
         count(*) FILTER(WHERE c->>'checkStatus'='PASS'),
         count(*) FILTER(WHERE c->>'checkStatus'='FAIL'),
         count(*) FILTER(WHERE c->>'checkStatus'='SKIPPED' AND COALESCE((c->>'required')::boolean,false)),
         count(*) FILTER(WHERE COALESCE((c->>'conditionalRequirement')::boolean,false))
    INTO v_blockers,v_errors,v_warnings,v_passed,v_failed,v_skipped,v_conditional
  FROM jsonb_array_elements(v_checks)c;

  v_status:=CASE
    WHEN v_blockers>0 OR EXISTS (SELECT 1 FROM jsonb_array_elements(v_checks)c
      WHERE c->>'checkStatus'='FAIL' AND COALESCE((c->>'required')::boolean,false)) THEN 'INCOMPATIBLE'
    WHEN v_skipped>0 OR NOT COALESCE((p_request->>'targetContractConfirmed')::boolean,false) THEN 'INCONCLUSIVE'
    WHEN v_conditional>0 THEN 'CONDITIONALLY_COMPATIBLE'
    ELSE 'COMPATIBLE' END;
  v_verdict_fp:=public.data_cross_track_verdict_fingerprint_v1(v_status,v_checks);

  v_logical_hash:=public.data_integration_fingerprint_v1('integration-input-sha256-v1',jsonb_build_object(
    'sourceSnapshotRef',p_request->>'sourceSnapshotRef','sourceTrack',p_request->>'sourceTrack',
    'targetTrack',p_request->>'targetTrack','sourceContract',p_request->>'sourceContract',
    'targetContract',p_request->>'targetContract','integrationScope',p_request->>'integrationScope',
    'validatorVersion',p_request->>'validatorVersion','integrationPolicyVersion',p_request->>'integrationPolicyVersion'));
  PERFORM pg_advisory_xact_lock(hashtextextended(v_logical_hash,0));

  SELECT * INTO v_existing FROM public.data_cross_track_integration_run_v1
  WHERE logical_identity_hash=v_logical_hash;
  IF FOUND THEN
    SELECT * INTO v_existing_verdict FROM public.data_cross_track_integration_verdict_v1
    WHERE integration_run_ref=v_existing.integration_run_id;
    IF v_existing.integration_input_fingerprint=v_input_fp
       AND v_existing.integration_mapping_fingerprint=v_mapping_fp
       AND v_existing.contract_matrix_fingerprint=v_matrix_fp
       AND v_existing_verdict.verdict_fingerprint=v_verdict_fp THEN
      RETURN QUERY SELECT 'DUPLICATE'::varchar,v_existing.integration_run_id,
        v_existing_verdict.integration_verdict_id,v_existing_verdict.overall_status,NULL::varchar;
      RETURN;
    END IF;
    v_conflict_fp:=public.data_integration_fingerprint_v1('integration-verdict-sha256-v1',jsonb_build_object(
      'logicalIdentityHash',v_logical_hash,'existingInputFingerprint',v_existing.integration_input_fingerprint,
      'incomingInputFingerprint',v_input_fp,'existingVerdictFingerprint',v_existing_verdict.verdict_fingerprint,
      'incomingVerdictFingerprint',v_verdict_fp,'conflictCode','CROSS_TRACK_INTEGRATION_VERDICT_CONFLICT'));
    INSERT INTO public.data_cross_track_integration_conflict_evidence_v1(
      existing_integration_run_ref,logical_identity_hash,existing_input_fingerprint,incoming_input_fingerprint,
      existing_verdict_fingerprint,incoming_verdict_fingerprint,conflict_code,conflict_fingerprint,observed_at,expires_at)
    VALUES(v_existing.integration_run_id,v_logical_hash,v_existing.integration_input_fingerprint,v_input_fp,
      v_existing_verdict.verdict_fingerprint,v_verdict_fp,'CROSS_TRACK_INTEGRATION_VERDICT_CONFLICT',
      v_conflict_fp,v_now,v_now+interval '90 days')
    ON CONFLICT(conflict_fingerprint) DO NOTHING;
    INSERT INTO public.data_cross_track_integration_status_evidence_v1(
      integration_run_ref,integration_status,failure_code,observed_at,expires_at)
    VALUES(v_existing.integration_run_id,'CONFLICTED','CROSS_TRACK_INTEGRATION_VERDICT_CONFLICT',v_now,v_now+interval '90 days');
    RETURN QUERY SELECT 'CONFLICT'::varchar,v_existing.integration_run_id,
      v_existing_verdict.integration_verdict_id,v_existing_verdict.overall_status,
      'CROSS_TRACK_INTEGRATION_VERDICT_CONFLICT'::varchar;
    RETURN;
  END IF;

  INSERT INTO public.data_cross_track_integration_run_v1(
    integration_run_ref,logical_identity_hash,integration_scope,source_track,target_track,
    source_contract,source_schema_version,target_contract,target_schema_version,target_contract_confirmed,
    source_snapshot_ref,source_quality_verdict_ref,mapping_policy_version,integration_policy_version,
    validator_version,validation_as_of,integration_input_fingerprint,integration_mapping_fingerprint,
    contract_matrix_fingerprint,expires_at)
  VALUES(p_request->>'integrationRunRef',v_logical_hash,p_request->>'integrationScope',p_request->>'sourceTrack',
    p_request->>'targetTrack',p_request->>'sourceContract',p_request->>'sourceSchemaVersion',
    p_request->>'targetContract',p_request->>'targetSchemaVersion',
    COALESCE((p_request->>'targetContractConfirmed')::boolean,false),v_snapshot.snapshot_id,
    v_quality.snapshot_quality_verdict_id,p_request->>'mappingPolicyVersion',p_request->>'integrationPolicyVersion',
    p_request->>'validatorVersion',(p_request->>'validationAsOf')::timestamptz,v_input_fp,v_mapping_fp,v_matrix_fp,
    v_now+interval '90 days')
  RETURNING data_cross_track_integration_run_v1.integration_run_id INTO v_run_id;

  INSERT INTO public.data_cross_track_integration_status_evidence_v1(
    integration_run_ref,integration_status,observed_at,expires_at)
  VALUES(v_run_id,'STARTED',v_now,v_now+interval '90 days');

  FOR v_item IN SELECT value FROM jsonb_array_elements(v_checks)
    ORDER BY (value->>'order')::integer,value->>'checkCode'
  LOOP
    INSERT INTO public.data_cross_track_integration_check_result_v1(
      integration_run_ref,check_order,check_code,check_scope,source_reference,target_reference,
      expected_value,observed_value,severity,check_status,failure_code,required_check,
      conditional_requirement,evidence_fingerprint,expires_at)
    VALUES(v_run_id,(v_item->>'order')::integer,v_item->>'checkCode',v_item->>'checkScope',
      NULLIF(v_item->>'sourceReference',''),NULLIF(v_item->>'targetReference',''),
      to_jsonb(COALESCE(v_item->>'expectedValue','')),to_jsonb(COALESCE(v_item->>'observedValue','')),
      v_item->>'severity',v_item->>'checkStatus',NULLIF(v_item->>'failureCode',''),
      COALESCE((v_item->>'required')::boolean,false),
      COALESCE((v_item->>'conditionalRequirement')::boolean,false),v_item->>'evidenceFingerprint',
      v_now+interval '90 days');
    IF v_item->>'checkStatus'='FAIL' AND v_item->>'severity' IN ('WARNING','ERROR','BLOCKER') THEN
      INSERT INTO public.data_cross_track_integration_anomaly_v1(
        integration_run_ref,check_code,failure_code,severity,evidence_fingerprint,expires_at)
      VALUES(v_run_id,v_item->>'checkCode',v_item->>'failureCode',v_item->>'severity',
        v_item->>'evidenceFingerprint',v_now+interval '90 days');
    END IF;
  END LOOP;

  INSERT INTO public.data_cross_track_contract_mapping_evidence_v1(
    integration_run_ref,source_contract,source_schema_version,target_contract,target_schema_version,
    mapping_policy_version,target_contract_present,target_authority_confirmed,schema_supported,
    required_fields_present,semantics_compatible,units_compatible,domain_mapping_approved,
    missing_required_fields,semantic_mappings,unit_mappings,mapping_fingerprint,expires_at)
  VALUES(v_run_id,v_mapping->>'sourceContract',v_mapping->>'sourceSchemaVersion',v_mapping->>'targetContract',
    v_mapping->>'targetSchemaVersion',v_mapping->>'mappingPolicyVersion',
    COALESCE((v_mapping->>'targetContractPresent')::boolean,false),
    COALESCE((v_mapping->>'targetAuthorityConfirmed')::boolean,false),
    COALESCE((v_mapping->>'schemaSupported')::boolean,false),
    COALESCE((v_mapping->>'requiredFieldsPresent')::boolean,false),
    COALESCE((v_mapping->>'semanticsCompatible')::boolean,false),
    COALESCE((v_mapping->>'unitsCompatible')::boolean,false),
    COALESCE((v_mapping->>'domainMappingApproved')::boolean,false),
    COALESCE(v_mapping->'missingRequiredFields','[]'::jsonb),COALESCE(v_mapping->'semanticMappings','{}'::jsonb),
    COALESCE(v_mapping->'unitMappings','{}'::jsonb),v_mapping_fp,v_now+interval '90 days');

  INSERT INTO public.data_cross_track_identity_evidence_v1(
    integration_run_ref,source_identity_namespace,target_identity_namespace,binding_version,binding_source,
    binding_scope,owner_track,automatic_merge,binding_fingerprint,validation_status,expires_at)
  VALUES(v_run_id,v_identity->>'sourceIdentityRef',v_identity->>'targetIdentityRef',v_identity->>'bindingVersion',
    v_identity->>'bindingSource',v_identity->>'bindingScope',v_identity->>'ownerTrack',false,
    v_identity->>'bindingFingerprint','PASS',v_now+interval '90 days');

  FOR v_item IN SELECT value FROM jsonb_array_elements(v_authorities) ORDER BY value->>'objectName'
  LOOP
    INSERT INTO public.data_cross_track_authority_evidence_v1(
      integration_run_ref,object_name,owning_track,actor_track,read_allowed,write_allowed,
      validation_allowed,production_allowed,read_attempted,write_attempted,validation_attempted,
      production_attempted,validation_status,evidence_fingerprint,expires_at)
    VALUES(v_run_id,v_item->>'objectName',v_item->>'owningTrack',v_item->>'actorTrack',
