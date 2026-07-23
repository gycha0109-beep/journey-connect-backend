-- Journey Connect DB v2.7 extension - DP-7 PostgreSQL 15/18 rollback-only validation
-- Prerequisite: SQL 01..51. This script leaves no committed fixture data.
BEGIN;

DO $$
DECLARE v_count integer;
BEGIN
  SELECT count(*) INTO v_count FROM pg_class c JOIN pg_namespace n ON n.oid=c.relnamespace
  WHERE n.nspname='public' AND c.relname IN (
    'data_cross_track_integration_run_v1','data_cross_track_integration_check_result_v1',
    'data_cross_track_contract_mapping_evidence_v1','data_cross_track_identity_evidence_v1',
    'data_cross_track_authority_evidence_v1','data_cross_track_privacy_evidence_v1',
    'data_cross_track_retention_evidence_v1','data_cross_track_quality_verdict_binding_v1',
    'data_cross_track_integration_verdict_v1','data_cross_track_integration_conflict_evidence_v1',
    'data_cross_track_integration_safe_metrics_v1');
  IF v_count<>11 THEN RAISE EXCEPTION 'DP-7 object inventory incomplete: %',v_count; END IF;
END;
$$;

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
