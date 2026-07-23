-- DP-7 isolated concurrency fixture setup. Test database only.
BEGIN;
CREATE OR REPLACE FUNCTION public.dp7_fixture_request_v1(
  p_run_ref varchar,
  p_snapshot_ref varchar,
  p_quality_verdict_ref uuid,
  p_target_track varchar,
  p_scope varchar,
  p_target_contract varchar,
  p_target_confirmed boolean,
  p_domain_mapping boolean,
  p_conditional boolean,
  p_target_schema varchar DEFAULT NULL,
  p_semantic_marker varchar DEFAULT 'aligned')
RETURNS jsonb LANGUAGE plpgsql STABLE SECURITY DEFINER
SET search_path=pg_catalog,public,pg_temp
AS $$
DECLARE
  v_snapshot public.data_projection_snapshot_v1%ROWTYPE;
  v_quality public.data_snapshot_quality_verdict_v1%ROWTYPE;
  v_mapping jsonb;
  v_identity jsonb;
  v_authorities jsonb;
  v_privacy jsonb;
  v_retention jsonb;
  v_checks jsonb;
  v_mapping_fp varchar(64);
  v_matrix_fp varchar(64);
  v_input_fp varchar(64);
  v_target_prefix varchar;
  v_target_check jsonb;
  v_binding_fp varchar(64):=repeat('a',64);
BEGIN
  SELECT * INTO STRICT v_snapshot FROM public.data_projection_snapshot_v1 WHERE snapshot_ref=p_snapshot_ref;
  SELECT * INTO STRICT v_quality FROM public.data_snapshot_quality_verdict_v1
    WHERE snapshot_quality_verdict_id=p_quality_verdict_ref;
  p_target_schema:=COALESCE(p_target_schema,p_target_contract);
  v_mapping:=jsonb_build_object(
    'sourceContract',v_snapshot.projection_name,'sourceSchemaVersion',v_snapshot.projection_schema_version,
    'targetContract',p_target_contract,'targetSchemaVersion',p_target_schema,
    'mappingPolicyVersion','data-cross-track-mapping-policy-v1',
    'targetContractPresent',p_target_confirmed,'targetAuthorityConfirmed',p_target_confirmed,
    'schemaSupported',true,'requiredFieldsPresent',true,'semanticsCompatible',true,'unitsCompatible',true,
    'domainMappingApproved',p_domain_mapping,'missingRequiredFields','[]'::jsonb,
    'semanticMappings',jsonb_build_object('fixture',p_semantic_marker),'unitMappings','{"window":"days"}'::jsonb);
  v_mapping_fp:=public.data_cross_track_mapping_fingerprint_v1(v_mapping);
  v_matrix_fp:=public.data_cross_track_contract_matrix_fingerprint_v1(v_mapping_fp);
  v_identity:=jsonb_build_object(
    'sourceIdentityRef','user:42','targetIdentityRef','subject:dp7-fixture',
    'bindingVersion','cross-track-identity-binding-v1','bindingSource','approved-dp7-fixture',
    'bindingFingerprint',v_binding_fp,'authoritativeFingerprint',v_binding_fp,
    'bindingScope','cross-track-integration','ownerTrack','Data','automaticMerge',false);
  v_authorities:=jsonb_build_array(
    jsonb_build_object('objectName','canonical event','owningTrack','Data','actorTrack','Data',
      'readAllowed',true,'writeAllowed',false,'validationAllowed',true,'productionAllowed',false,
      'readAttempted',true,'writeAttempted',false,'validationAttempted',true,'productionAttempted',false),
    jsonb_build_object('objectName','target authoritative object','owningTrack',p_target_track,'actorTrack','Data',
      'readAllowed',true,'writeAllowed',false,'validationAllowed',true,'productionAllowed',false,
      'readAttempted',true,'writeAttempted',false,'validationAttempted',true,'productionAttempted',false));
  v_privacy:='{"sourcePrivacyClass":"pseudonymous","targetPrivacyClass":"pseudonymous","rawPayloadPresent":false,"piiPresent":false,"rawTextPresent":false,"preciseLocationPresent":false,"aggregateOnly":true,"lineagePurposeBound":true,"reidentificationRisk":false}'::jsonb;
  v_retention:='{"sourceRetentionDays":90,"targetRetentionDays":90,"integrationEvidenceRetentionDays":90,"deletionSemanticsAligned":true,"automaticPurgeEnabled":false,"physicalDeleteEnabled":false}'::jsonb;

  v_target_prefix:=CASE p_target_track WHEN 'Recommendation' THEN 'recommendation'
    WHEN 'Intelligence' THEN 'intelligence' WHEN 'Search' THEN 'search' ELSE 'data' END;
  v_target_check:=CASE
    WHEN p_target_confirmed THEN jsonb_build_object(
      'order',700,'checkCode',v_target_prefix||'.contract.present','checkScope',p_scope,
      'sourceReference',v_snapshot.projection_name,'targetReference',p_target_contract,
      'expectedValue','present','observedValue','present','severity','INFO','checkStatus','PASS',
      'failureCode','','required',true,'conditionalRequirement',p_conditional)
    ELSE jsonb_build_object(
      'order',700,'checkCode',v_target_prefix||CASE WHEN p_target_track='Intelligence' THEN '.domain_mapping' ELSE '.contract.present' END,
      'checkScope',p_scope,'sourceReference',v_snapshot.projection_name,'targetReference',p_target_contract,
      'expectedValue','approved target contract','observedValue','missing','severity','ERROR','checkStatus','SKIPPED',
      'failureCode',CASE WHEN p_target_track='Intelligence' THEN 'intelligence_domain_mapping_missing' ELSE 'search_contract_missing' END,
      'required',true,'conditionalRequirement',false) END;
  v_checks:=jsonb_build_array(
    jsonb_build_object('order',0,'checkCode','quality.verdict.status','checkScope','QUALITY_VERDICT_BOUNDARY',
      'sourceReference',p_snapshot_ref,'targetReference',p_quality_verdict_ref::text,
      'expectedValue','VALIDATED','observedValue','VALIDATED','severity','INFO','checkStatus','PASS','failureCode','','required',true,'conditionalRequirement',false),
    jsonb_build_object('order',10,'checkCode','identity.binding.valid','checkScope','IDENTITY_BOUNDARY',
      'sourceReference','user:42','targetReference','subject:dp7-fixture','expectedValue','explicit/versioned/scoped',
      'observedValue','explicit/versioned/scoped','severity','INFO','checkStatus','PASS','failureCode','','required',true,'conditionalRequirement',false),
    jsonb_build_object('order',20,'checkCode','authority.production_traffic_control','checkScope','AUTHORITY_BOUNDARY',
      'sourceReference','Data','targetReference',p_target_track,'expectedValue','no production authority',
      'observedValue','no production authority','severity','INFO','checkStatus','PASS','failureCode','','required',true,'conditionalRequirement',false),
    jsonb_build_object('order',120,'checkCode','privacy.boundary','checkScope','PRIVACY_BOUNDARY',
      'sourceReference','pseudonymous','targetReference','pseudonymous','expectedValue','no raw PII/payload/location',
      'observedValue','no raw PII/payload/location','severity','INFO','checkStatus','PASS','failureCode','','required',true,'conditionalRequirement',false),
    jsonb_build_object('order',130,'checkCode','retention.boundary','checkScope','RETENTION_BOUNDARY',
      'sourceReference','90d','targetReference','90d','expectedValue','target <= source; no purge/delete',
      'observedValue','target <= source; no purge/delete','severity','INFO','checkStatus','PASS','failureCode','','required',true,'conditionalRequirement',false),
    jsonb_build_object('order',140,'checkCode','fingerprint.snapshot','checkScope','FINGERPRINT_BOUNDARY',
      'sourceReference',v_snapshot.content_fingerprint,'targetReference',v_snapshot.lineage_fingerprint,
      'expectedValue','lowercase sha256','observedValue','lowercase sha256','severity','INFO','checkStatus','PASS','failureCode','','required',true,'conditionalRequirement',false),
    v_target_check);
  SELECT jsonb_agg(c||jsonb_build_object('evidenceFingerprint',public.data_cross_track_check_fingerprint_v1(c))
    ORDER BY (c->>'order')::integer,c->>'checkCode') INTO v_checks FROM jsonb_array_elements(v_checks)c;
  v_input_fp:=public.data_integration_fingerprint_v1('integration-input-sha256-v1',jsonb_build_object(
    'sourceTrack','Data','targetTrack',p_target_track,'sourceContract',v_snapshot.projection_name,
    'sourceSchemaVersion',v_snapshot.projection_schema_version,'targetContract',p_target_contract,
    'targetSchemaVersion',p_target_schema,'sourceSnapshotFingerprint',v_snapshot.content_fingerprint,
    'sourceLineageFingerprint',v_snapshot.lineage_fingerprint,
    'sourceQualityVerdictFingerprint',v_quality.verdict_fingerprint,
    'mappingPolicyVersion','data-cross-track-mapping-policy-v1',
    'integrationPolicyVersion','data-cross-track-integration-policy-v1',
    'identityBindingFingerprint',v_binding_fp,'mappingFingerprint',v_mapping_fp));
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

CREATE TABLE public.dp7_concurrency_request_v1(request jsonb NOT NULL);
INSERT INTO public.dp7_concurrency_request_v1(request)
SELECT public.dp7_fixture_request_v1(
  'integration_run:concurrent-same-identity',snapshot_ref,quality_ref,
  'Data','FINGERPRINT_BOUNDARY','data-cross-track-contract-matrix-v1',true,true,false)
FROM dp7_fixture_refs;
COMMIT;
