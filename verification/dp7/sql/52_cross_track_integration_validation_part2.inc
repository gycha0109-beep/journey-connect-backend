  RETURN jsonb_build_object(
    'integrationRunRef',p_run_ref,'integrationScope',p_scope,'sourceTrack','Data','targetTrack',p_target_track,
    'sourceContract',v_snapshot.projection_name,'sourceSchemaVersion',v_snapshot.projection_schema_version,
    'targetContract',p_target_contract,'targetSchemaVersion',p_target_schema,'targetContractConfirmed',p_target_confirmed,
    'sourceSnapshotRef',p_snapshot_ref,'sourceQualityVerdictRef',p_quality_verdict_ref::text,
    'mappingPolicyVersion','data-cross-track-mapping-policy-v1',
    'integrationPolicyVersion','data-cross-track-integration-policy-v1',
    'validatorVersion','data-cross-track-integration-validator-v1',
    'validationAsOf',public.data_projection_instant_text_v1(v_snapshot.snapshot_as_of),
    'integrationInputFingerprint',v_input_fp,'integrationMappingFingerprint',v_mapping_fp,
    'contractMatrixFingerprint',v_matrix_fp,'mapping',v_mapping,'identityBinding',v_identity,
    'authorities',v_authorities,'privacy',v_privacy,'retention',v_retention,'checks',v_checks);
END;
$$;

DO $$
DECLARE
  v_checkpoint uuid:=public.gen_random_uuid();
  v_projection_run uuid:=public.gen_random_uuid();
  v_snapshot uuid:=public.gen_random_uuid();
  v_quality_run uuid:=public.gen_random_uuid();
  v_quality uuid:=public.gen_random_uuid();
  v_checkpoint_fp varchar(64):=repeat('1',64);
  v_snapshot_fp varchar(64):=repeat('2',64);
  v_lineage_fp varchar(64):=repeat('3',64);
  v_quality_fp varchar(64):=repeat('4',64);
BEGIN
  INSERT INTO public.data_source_checkpoint_v1(
    checkpoint_id,checkpoint_ref,logical_identity_hash,source_stream,source_contract_version,
    source_schema_version,event_time_from,event_time_to,ingested_at_upper_bound,last_source_event_ref,
    source_event_count,source_members,source_set_fingerprint,checkpoint_definition_fingerprint,expires_at)
  VALUES(v_checkpoint,'checkpoint:dp7-fixture',repeat('5',64),'dp7-fixture','platform-event-v1','platform-event-v1',
    '2026-07-22T00:00:00Z','2026-07-22T01:00:00Z','2026-07-22T01:00:00Z','event:dp7-fixture',1,
    jsonb_build_array(jsonb_build_object('sourceKind','canonical_event','sourceEventRef','event:dp7-fixture',
      'sourceFingerprint',repeat('6',64),'adapterEvidenceRef',NULL,'occurredAt','2026-07-22T00:10:00Z',
      'ingestedAt','2026-07-22T00:11:00Z')),v_checkpoint_fp,repeat('7',64),CURRENT_TIMESTAMP+interval '90 days');
  INSERT INTO public.data_projection_run_v1(
    projection_run_id,projection_run_ref,logical_identity_hash,projection_name,projection_schema_version,
    projection_policy_version,feature_policy_version,source_contract_version,source_checkpoint_ref,
    source_from,source_to,projection_as_of,identity_binding_version,identity_binding_source,
    identity_binding_fingerprint,identity_binding_scope,target_contract_version,producer_build_id,expires_at)
  VALUES(v_projection_run,'projection_run:dp7-fixture',repeat('8',64),'recommendation-profile-input-v1',
    'recommendation-profile-input-v1','data-projection-policy-v1','recommendation-profile-feature-policy-v1',
    'platform-event-v1',v_checkpoint,'2026-07-22T00:00:00Z','2026-07-22T01:00:00Z','2026-07-22T01:00:00Z',
    'cross-track-identity-binding-v1','approved-dp7-fixture',repeat('a',64),'cross-track-integration',
    'recommendation-profile-input-v1','git:1111111111111111111111111111111111111111',CURRENT_TIMESTAMP+interval '90 days');
  INSERT INTO public.data_projection_snapshot_v1(
    snapshot_id,snapshot_ref,projection_run_ref,projection_name,projection_schema_version,
    projection_policy_version,source_checkpoint_ref,snapshot_as_of,record_count,subject_count,
    source_event_count,content_fingerprint,lineage_fingerprint,snapshot_status,expires_at)
  VALUES(v_snapshot,'snapshot:dp7-fixture',v_projection_run,'recommendation-profile-input-v1',
    'recommendation-profile-input-v1','data-projection-policy-v1',v_checkpoint,'2026-07-22T01:00:00Z',1,1,1,
    v_snapshot_fp,v_lineage_fp,'validated',CURRENT_TIMESTAMP+interval '90 days');
  INSERT INTO public.data_quality_validation_run_v1(
    validation_run_id,validation_run_ref,logical_identity_hash,validation_scope,snapshot_ref,projection_name,
    projection_schema_version,projection_policy_version,source_checkpoint_ref,validator_version,
    quality_policy_version,validation_as_of,validation_input_fingerprint,expires_at)
  VALUES(v_quality_run,'quality_run:dp7-fixture',repeat('b',64),'FULL',v_snapshot,'recommendation-profile-input-v1',
    'recommendation-profile-input-v1','data-projection-policy-v1',v_checkpoint,'data-quality-validator-v1',
    'data-quality-policy-v1','2026-07-22T01:00:00Z',repeat('c',64),CURRENT_TIMESTAMP+interval '90 days');
  INSERT INTO public.data_snapshot_quality_verdict_v1(
    snapshot_quality_verdict_id,snapshot_ref,validation_run_ref,quality_policy_version,overall_status,
    blocker_count,error_count,warning_count,passed_check_count,failed_check_count,skipped_required_check_count,
    quality_score,verdict_fingerprint,expires_at)
  VALUES(v_quality,v_snapshot,v_quality_run,'data-quality-policy-v1','VALIDATED',0,0,0,1,0,0,100,v_quality_fp,
    CURRENT_TIMESTAMP+interval '90 days');
  CREATE TEMP TABLE dp7_fixture_refs(snapshot_ref varchar,quality_ref uuid) ON COMMIT DROP;
  INSERT INTO dp7_fixture_refs VALUES('snapshot:dp7-fixture',v_quality);
END;
$$;

DO $$
DECLARE
  v_checkpoint uuid;
  v_projection_run uuid:=public.gen_random_uuid();
  v_snapshot uuid:=public.gen_random_uuid();
  v_quality_run uuid:=public.gen_random_uuid();
  v_quality uuid:=public.gen_random_uuid();
BEGIN
  SELECT checkpoint_id INTO v_checkpoint FROM public.data_source_checkpoint_v1 WHERE checkpoint_ref='checkpoint:dp7-fixture';
  INSERT INTO public.data_projection_run_v1(
    projection_run_id,projection_run_ref,logical_identity_hash,projection_name,projection_schema_version,
    projection_policy_version,feature_policy_version,source_contract_version,source_checkpoint_ref,
    source_from,source_to,projection_as_of,identity_binding_version,identity_binding_source,
    identity_binding_fingerprint,identity_binding_scope,target_contract_version,producer_build_id,expires_at)
  VALUES(v_projection_run,'projection_run:dp7-rejected',repeat('d',64),'recommendation-profile-input-v1',
    'recommendation-profile-input-v1','data-projection-policy-v1','recommendation-profile-feature-policy-v1',
    'platform-event-v1',v_checkpoint,'2026-07-22T00:00:00Z','2026-07-22T01:00:00Z','2026-07-22T01:00:00Z',
    'cross-track-identity-binding-v1','approved-dp7-fixture',repeat('a',64),'cross-track-integration',
    'recommendation-profile-input-v1','git:2222222222222222222222222222222222222222',CURRENT_TIMESTAMP+interval '90 days');
  INSERT INTO public.data_projection_snapshot_v1(
    snapshot_id,snapshot_ref,projection_run_ref,projection_name,projection_schema_version,projection_policy_version,
    source_checkpoint_ref,snapshot_as_of,record_count,subject_count,source_event_count,content_fingerprint,
    lineage_fingerprint,snapshot_status,expires_at)
  VALUES(v_snapshot,'snapshot:dp7-rejected',v_projection_run,'recommendation-profile-input-v1',
    'recommendation-profile-input-v1','data-projection-policy-v1',v_checkpoint,'2026-07-22T01:00:00Z',1,1,1,
    repeat('e',64),repeat('f',64),'rejected',CURRENT_TIMESTAMP+interval '90 days');
  INSERT INTO public.data_quality_validation_run_v1(
    validation_run_id,validation_run_ref,logical_identity_hash,validation_scope,snapshot_ref,projection_name,
    projection_schema_version,projection_policy_version,source_checkpoint_ref,validator_version,
    quality_policy_version,validation_as_of,validation_input_fingerprint,expires_at)
  VALUES(v_quality_run,'quality_run:dp7-rejected',repeat('9',64),'FULL',v_snapshot,'recommendation-profile-input-v1',
    'recommendation-profile-input-v1','data-projection-policy-v1',v_checkpoint,'data-quality-validator-v1',
    'data-quality-policy-v1','2026-07-22T01:00:00Z',repeat('0',64),CURRENT_TIMESTAMP+interval '90 days');
  INSERT INTO public.data_snapshot_quality_verdict_v1(
    snapshot_quality_verdict_id,snapshot_ref,validation_run_ref,quality_policy_version,overall_status,blocker_count,
    error_count,warning_count,passed_check_count,failed_check_count,skipped_required_check_count,quality_score,
    verdict_fingerprint,expires_at)
  VALUES(v_quality,v_snapshot,v_quality_run,'data-quality-policy-v1','REJECTED',1,1,0,0,1,0,0,repeat('1',64),
    CURRENT_TIMESTAMP+interval '90 days');
  CREATE TEMP TABLE dp7_rejected_refs(snapshot_ref varchar,quality_ref uuid) ON COMMIT DROP;
  INSERT INTO dp7_rejected_refs VALUES('snapshot:dp7-rejected',v_quality);
END;
$$;

DO $$
DECLARE
  v_snapshot varchar; v_quality uuid; v_result record; v_before bigint; v_request jsonb;
BEGIN
  SELECT snapshot_ref,quality_ref INTO v_snapshot,v_quality FROM dp7_fixture_refs;
  v_request:=public.dp7_fixture_request_v1('integration_run:quality-missing',v_snapshot,v_quality,
    'Data','QUALITY_VERDICT_BOUNDARY','data-cross-track-integration-verdict-v1',true,true,false);
  v_request:=jsonb_set(v_request,'{sourceQualityVerdictRef}',to_jsonb(public.gen_random_uuid()::text));
  BEGIN PERFORM public.persist_data_cross_track_integration_v1(v_request);
