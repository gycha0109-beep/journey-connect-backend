      COALESCE((v_item->>'readAllowed')::boolean,false),COALESCE((v_item->>'writeAllowed')::boolean,false),
      COALESCE((v_item->>'validationAllowed')::boolean,false),COALESCE((v_item->>'productionAllowed')::boolean,false),
      COALESCE((v_item->>'readAttempted')::boolean,false),COALESCE((v_item->>'writeAttempted')::boolean,false),
      COALESCE((v_item->>'validationAttempted')::boolean,false),COALESCE((v_item->>'productionAttempted')::boolean,false),
      'PASS',public.data_integration_fingerprint_v1('integration-check-evidence-sha256-v1',
        jsonb_build_object('scope','AUTHORITY_BOUNDARY','object',v_item->>'objectName','result','PASS')),
      v_now+interval '90 days');
  END LOOP;

  INSERT INTO public.data_cross_track_privacy_evidence_v1(
    integration_run_ref,source_privacy_class,target_privacy_class,raw_payload_present,pii_present,
    raw_text_present,precise_location_present,aggregate_only,lineage_purpose_bound,reidentification_risk,
    validation_status,evidence_fingerprint,expires_at)
  VALUES(v_run_id,v_privacy->>'sourcePrivacyClass',v_privacy->>'targetPrivacyClass',false,false,false,false,true,true,false,
    'PASS',public.data_integration_fingerprint_v1('integration-check-evidence-sha256-v1',
      jsonb_build_object('scope','PRIVACY_BOUNDARY','result','PASS','sourceClass',v_privacy->>'sourcePrivacyClass',
      'targetClass',v_privacy->>'targetPrivacyClass')),v_now+interval '90 days');

  INSERT INTO public.data_cross_track_retention_evidence_v1(
    integration_run_ref,source_retention_days,target_retention_days,integration_evidence_retention_days,
    deletion_semantics_aligned,automatic_purge_enabled,physical_delete_enabled,validation_status,
    evidence_fingerprint,expires_at)
  VALUES(v_run_id,(v_retention->>'sourceRetentionDays')::integer,(v_retention->>'targetRetentionDays')::integer,
    90,true,false,false,'PASS',public.data_integration_fingerprint_v1('integration-check-evidence-sha256-v1',
      jsonb_build_object('scope','RETENTION_BOUNDARY','result','PASS','sourceDays',(v_retention->>'sourceRetentionDays')::integer,
      'targetDays',(v_retention->>'targetRetentionDays')::integer,'integrationDays',90)),v_now+interval '90 days');

  INSERT INTO public.data_cross_track_quality_verdict_binding_v1(
    integration_run_ref,source_snapshot_ref,source_quality_verdict_ref,quality_policy_version,quality_status,
    quality_verdict_fingerprint,binding_fingerprint,expires_at)
  VALUES(v_run_id,v_snapshot.snapshot_id,v_quality.snapshot_quality_verdict_id,v_quality.quality_policy_version,
    v_quality.overall_status,v_quality.verdict_fingerprint,
    public.data_integration_fingerprint_v1('integration-check-evidence-sha256-v1',jsonb_build_object(
      'scope','QUALITY_VERDICT_BOUNDARY','snapshotFingerprint',v_snapshot.content_fingerprint,
      'qualityVerdictFingerprint',v_quality.verdict_fingerprint,'result','PASS')),v_now+interval '90 days');

  INSERT INTO public.data_cross_track_integration_verdict_v1(
    integration_run_ref,source_snapshot_ref,source_quality_verdict_ref,source_track,target_track,
    integration_scope,integration_policy_version,overall_status,blocker_count,error_count,warning_count,
    passed_check_count,failed_check_count,skipped_required_check_count,conditional_requirement_count,
    verdict_fingerprint,expires_at)
  VALUES(v_run_id,v_snapshot.snapshot_id,v_quality.snapshot_quality_verdict_id,p_request->>'sourceTrack',
    p_request->>'targetTrack',p_request->>'integrationScope',p_request->>'integrationPolicyVersion',v_status,
    v_blockers,v_errors,v_warnings,v_passed,v_failed,v_skipped,v_conditional,v_verdict_fp,v_now+interval '90 days')
  RETURNING data_cross_track_integration_verdict_v1.integration_verdict_id INTO v_verdict_id;

  IF v_status IN ('COMPATIBLE','CONDITIONALLY_COMPATIBLE') THEN
    INSERT INTO public.data_cross_track_integration_status_evidence_v1(
      integration_run_ref,integration_status,observed_at,expires_at)
    VALUES(v_run_id,'COMPLETED',v_now,v_now+interval '90 days');
  ELSE
    SELECT NULLIF(c->>'failureCode','') INTO v_terminal_failure
    FROM jsonb_array_elements(v_checks)c
    WHERE c->>'checkStatus' IN ('FAIL','SKIPPED') AND NULLIF(c->>'failureCode','') IS NOT NULL
    ORDER BY (c->>'order')::integer,c->>'checkCode' LIMIT 1;
    IF v_terminal_failure IS NULL THEN
      RAISE EXCEPTION 'Non-compatible verdict requires a stable failure code.' USING ERRCODE='23514';
    END IF;
    INSERT INTO public.data_cross_track_integration_status_evidence_v1(
      integration_run_ref,integration_status,failure_code,observed_at,expires_at)
    VALUES(v_run_id,'FAILED',v_terminal_failure,v_now,v_now+interval '90 days');
  END IF;

  RETURN QUERY SELECT 'NEW'::varchar,v_run_id,v_verdict_id,v_status,NULL::varchar;
END;
$$;

CREATE OR REPLACE VIEW public.data_cross_track_integration_safe_metrics_v1
AS
WITH status_by_run AS (
  SELECT integration_run_ref,
    bool_or(integration_status='COMPLETED') AS completed,
    bool_or(integration_status='FAILED') AS failed,
    bool_or(integration_status='CONFLICTED') AS conflicted
  FROM public.data_cross_track_integration_status_evidence_v1
  GROUP BY integration_run_ref
), failures AS (
  SELECT integration_run_ref,failure_code,severity,count(*) AS failure_count
  FROM public.data_cross_track_integration_check_result_v1
  WHERE check_status='FAIL' AND failure_code IS NOT NULL
  GROUP BY integration_run_ref,failure_code,severity
), aggregate_rows AS (
  SELECT r.source_track,r.target_track,r.integration_scope,v.overall_status,
    COALESCE(f.failure_code,'none') AS failure_code,COALESCE(f.severity,'INFO') AS severity,
    count(DISTINCT r.integration_run_id) AS integration_run_count,
    count(DISTINCT r.integration_run_id) FILTER(WHERE s.completed) AS completed_count,
    count(DISTINCT r.integration_run_id) FILTER(WHERE s.failed) AS failed_count,
    count(DISTINCT r.integration_run_id) FILTER(WHERE s.conflicted) AS conflicted_count,
    COALESCE(sum(f.failure_count),0) AS failure_count,
    count(DISTINCT r.integration_run_id) FILTER(WHERE f.failure_code LIKE 'quality_%') AS quality_verdict_block_count,
    count(DISTINCT r.integration_run_id) FILTER(WHERE f.failure_code LIKE 'cross_track_identity_%') AS identity_violation_count,
    count(DISTINCT r.integration_run_id) FILTER(WHERE f.failure_code LIKE 'cross_track_%authority%') AS authority_violation_count,
    count(DISTINCT r.integration_run_id) FILTER(WHERE f.failure_code IN (
      'cross_track_privacy_class_mismatch','cross_track_pii_exposure','cross_track_raw_payload_exposure',
      'cross_track_precise_location_exposure','cross_track_lineage_access_violation','cross_track_reidentification_risk')) AS privacy_violation_count,
    count(DISTINCT r.integration_run_id) FILTER(WHERE f.failure_code IN (
      'cross_track_retention_conflict','cross_track_deletion_semantic_conflict','intelligence_retention_conflict','search_retention_conflict')) AS retention_conflict_count,
    count(DISTINCT r.integration_run_id) FILTER(WHERE f.failure_code LIKE '%fingerprint%') AS fingerprint_mismatch_count,
    CASE WHEN v.overall_status='INCONCLUSIVE' THEN
      extract(epoch FROM (CURRENT_TIMESTAMP-min(r.created_at)))::bigint ELSE NULL END AS oldest_inconclusive_age_seconds,
    max(r.validation_as_of) FILTER(WHERE v.overall_status='COMPATIBLE') AS latest_compatible_validation_time
  FROM public.data_cross_track_integration_run_v1 r
  JOIN public.data_cross_track_integration_verdict_v1 v ON v.integration_run_ref=r.integration_run_id
  LEFT JOIN status_by_run s ON s.integration_run_ref=r.integration_run_id
  LEFT JOIN failures f ON f.integration_run_ref=r.integration_run_id
  GROUP BY r.source_track,r.target_track,r.integration_scope,v.overall_status,
    COALESCE(f.failure_code,'none'),COALESCE(f.severity,'INFO')
)
SELECT * FROM aggregate_rows;

REVOKE ALL ON ALL TABLES IN SCHEMA public FROM jc_data_integration_writer,jc_data_integration_reader;
REVOKE ALL ON public.data_cross_track_integration_policy_v1,
  public.data_cross_track_integration_run_v1,public.data_cross_track_integration_status_evidence_v1,
  public.data_cross_track_integration_check_result_v1,public.data_cross_track_integration_anomaly_v1,
  public.data_cross_track_contract_mapping_evidence_v1,public.data_cross_track_identity_evidence_v1,
  public.data_cross_track_authority_evidence_v1,public.data_cross_track_privacy_evidence_v1,
  public.data_cross_track_retention_evidence_v1,public.data_cross_track_quality_verdict_binding_v1,
  public.data_cross_track_integration_verdict_v1,public.data_cross_track_integration_conflict_evidence_v1
  FROM PUBLIC;
REVOKE ALL ON FUNCTION public.persist_data_cross_track_integration_v1(jsonb) FROM PUBLIC;
REVOKE ALL ON FUNCTION public.data_integration_version_valid_v1(varchar) FROM PUBLIC;
REVOKE ALL ON FUNCTION public.data_integration_fingerprint_valid_v1(varchar) FROM PUBLIC;
REVOKE ALL ON FUNCTION public.data_integration_fingerprint_v1(varchar,jsonb) FROM PUBLIC;
REVOKE ALL ON FUNCTION public.data_integration_failure_code_valid_v1(varchar) FROM PUBLIC;
REVOKE ALL ON FUNCTION public.data_cross_track_mapping_fingerprint_v1(jsonb) FROM PUBLIC;
REVOKE ALL ON FUNCTION public.data_cross_track_check_fingerprint_v1(jsonb) FROM PUBLIC;
REVOKE ALL ON FUNCTION public.data_cross_track_contract_matrix_fingerprint_v1(varchar) FROM PUBLIC;
REVOKE ALL ON FUNCTION public.data_cross_track_verdict_fingerprint_v1(varchar,jsonb) FROM PUBLIC;
REVOKE ALL ON public.data_cross_track_integration_safe_metrics_v1 FROM PUBLIC;

GRANT USAGE ON SCHEMA public TO jc_data_integration_writer,jc_data_integration_reader,jc_data_integration_function_owner;
GRANT SELECT ON public.data_projection_snapshot_v1,public.data_snapshot_quality_verdict_v1,
  public.data_cross_track_integration_policy_v1 TO jc_data_integration_function_owner;
GRANT SELECT,INSERT ON public.data_cross_track_integration_run_v1,
  public.data_cross_track_integration_status_evidence_v1,public.data_cross_track_integration_check_result_v1,
  public.data_cross_track_integration_anomaly_v1,public.data_cross_track_contract_mapping_evidence_v1,
  public.data_cross_track_identity_evidence_v1,public.data_cross_track_authority_evidence_v1,
  public.data_cross_track_privacy_evidence_v1,public.data_cross_track_retention_evidence_v1,
  public.data_cross_track_quality_verdict_binding_v1,public.data_cross_track_integration_verdict_v1,
  public.data_cross_track_integration_conflict_evidence_v1 TO jc_data_integration_function_owner;

ALTER FUNCTION public.data_integration_version_valid_v1(varchar) OWNER TO jc_data_integration_function_owner;
ALTER FUNCTION public.data_integration_fingerprint_valid_v1(varchar) OWNER TO jc_data_integration_function_owner;
ALTER FUNCTION public.data_integration_fingerprint_v1(varchar,jsonb) OWNER TO jc_data_integration_function_owner;
ALTER FUNCTION public.data_integration_failure_code_valid_v1(varchar) OWNER TO jc_data_integration_function_owner;
ALTER FUNCTION public.persist_data_cross_track_integration_v1(jsonb) OWNER TO jc_data_integration_function_owner;
ALTER FUNCTION public.data_cross_track_mapping_fingerprint_v1(jsonb) OWNER TO jc_data_integration_function_owner;
ALTER FUNCTION public.data_cross_track_check_fingerprint_v1(jsonb) OWNER TO jc_data_integration_function_owner;
ALTER FUNCTION public.data_cross_track_contract_matrix_fingerprint_v1(varchar) OWNER TO jc_data_integration_function_owner;
ALTER FUNCTION public.data_cross_track_verdict_fingerprint_v1(varchar,jsonb) OWNER TO jc_data_integration_function_owner;
ALTER VIEW public.data_cross_track_integration_safe_metrics_v1 OWNER TO jc_data_integration_function_owner;

GRANT EXECUTE ON FUNCTION public.persist_data_cross_track_integration_v1(jsonb) TO jc_data_integration_writer;
GRANT SELECT ON public.data_cross_track_integration_safe_metrics_v1 TO jc_data_integration_reader;

COMMIT;
