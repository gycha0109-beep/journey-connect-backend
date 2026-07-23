    RAISE EXCEPTION 'quality verdict missing unexpectedly succeeded';
  EXCEPTION WHEN foreign_key_violation THEN NULL; END;
  SELECT count(*) INTO v_before FROM public.data_cross_track_integration_run_v1;
  IF v_before<>0 THEN RAISE EXCEPTION 'missing quality verdict persisted evidence'; END IF;

  DECLARE
    v_rejected_snapshot varchar; v_rejected_quality uuid;
  BEGIN
      SELECT snapshot_ref,quality_ref INTO v_rejected_snapshot,v_rejected_quality FROM dp7_rejected_refs;
      v_request:=public.dp7_fixture_request_v1('integration_run:quality-rejected',v_rejected_snapshot,v_rejected_quality,
        'Data','QUALITY_VERDICT_BOUNDARY','data-cross-track-integration-verdict-v1',true,true,false);
      BEGIN PERFORM public.persist_data_cross_track_integration_v1(v_request);
        RAISE EXCEPTION 'quality verdict rejected unexpectedly succeeded';
      EXCEPTION WHEN check_violation THEN NULL; END;
  END;
  v_request:=public.dp7_fixture_request_v1('integration_run:recommendation-profile',v_snapshot,v_quality,
    'Recommendation','DATA_RECOMMENDATION_PROFILE','recommendation-profile-input-v1',true,true,true);
  SELECT * INTO v_result FROM public.persist_data_cross_track_integration_v1(v_request);
  IF v_result.disposition<>'NEW' OR v_result.overall_status<>'CONDITIONALLY_COMPATIBLE' THEN
    RAISE EXCEPTION 'Recommendation conditional NEW failed: %',row_to_json(v_result);
  END IF;
  SELECT * INTO v_result FROM public.persist_data_cross_track_integration_v1(v_request);
  IF v_result.disposition<>'DUPLICATE' OR v_result.overall_status<>'CONDITIONALLY_COMPATIBLE' THEN
    RAISE EXCEPTION 'Recommendation DUPLICATE failed: %',row_to_json(v_result);
  END IF;

  SELECT * INTO v_result FROM public.persist_data_cross_track_integration_v1(
    public.dp7_fixture_request_v1('integration_run:intelligence-missing',v_snapshot,v_quality,
      'Intelligence','DATA_INTELLIGENCE_INPUT','intelligence-input-snapshot-v1',false,false,false));
  IF v_result.disposition<>'NEW' OR v_result.overall_status<>'INCONCLUSIVE' THEN
    RAISE EXCEPTION 'Intelligence missing domain mapping did not remain INCONCLUSIVE.';
  END IF;
  SELECT * INTO v_result FROM public.persist_data_cross_track_integration_v1(
    public.dp7_fixture_request_v1('integration_run:search-missing',v_snapshot,v_quality,
      'Search','DATA_SEARCH_INPUT','search-document-projection-v1',false,false,false));
  IF v_result.disposition<>'NEW' OR v_result.overall_status<>'INCONCLUSIVE' THEN
    RAISE EXCEPTION 'Search missing contract did not remain INCONCLUSIVE.';
  END IF;
  SELECT * INTO v_result FROM public.persist_data_cross_track_integration_v1(
    public.dp7_fixture_request_v1('integration_run:boundary-compatible',v_snapshot,v_quality,
      'Data','IDENTITY_BOUNDARY','data-cross-track-authority-matrix-v1',true,true,false));
  IF v_result.disposition<>'NEW' OR v_result.overall_status<>'COMPATIBLE' THEN
    RAISE EXCEPTION 'Boundary COMPATIBLE fixture failed.';
  END IF;

  SELECT * INTO v_result FROM public.persist_data_cross_track_integration_v1(
    public.dp7_fixture_request_v1('integration_run:recommendation-profile-conflict',v_snapshot,v_quality,
      'Recommendation','DATA_RECOMMENDATION_PROFILE','recommendation-profile-input-v1',true,true,true,
      NULL,'different-semantic-map'));
  IF v_result.disposition<>'CONFLICT' OR v_result.error_code<>'CROSS_TRACK_INTEGRATION_VERDICT_CONFLICT' THEN
    RAISE EXCEPTION 'Integration CONFLICT failed: %',row_to_json(v_result);
  END IF;
  IF (SELECT count(*) FROM public.data_cross_track_integration_verdict_v1 v
      JOIN public.data_cross_track_integration_run_v1 r ON r.integration_run_id=v.integration_run_ref
      WHERE r.integration_run_ref='integration_run:recommendation-profile')<>1 THEN
    RAISE EXCEPTION 'Conflict created a second verdict.';
  END IF;

  EXECUTE $ddl$
    CREATE OR REPLACE FUNCTION public.dp7_force_atomic_failure_v1()
    RETURNS trigger
    LANGUAGE plpgsql
    AS $body$
    BEGIN
      IF EXISTS (
        SELECT 1
        FROM public.data_cross_track_integration_run_v1 r
        WHERE r.integration_run_id=NEW.integration_run_ref
          AND r.integration_run_ref='integration_run:partial-rollback'
      ) THEN
        RAISE EXCEPTION 'dp7 atomic rollback fixture' USING ERRCODE='P0001';
      END IF;
      RETURN NEW;
    END;
    $body$
  $ddl$;
  EXECUTE 'CREATE TRIGGER dp7_force_atomic_failure_v1
    BEFORE INSERT ON public.data_cross_track_contract_mapping_evidence_v1
    FOR EACH ROW EXECUTE FUNCTION public.dp7_force_atomic_failure_v1()';

  SELECT count(*) INTO v_before FROM public.data_cross_track_integration_run_v1;
  v_request:=public.dp7_fixture_request_v1('integration_run:partial-rollback',v_snapshot,v_quality,
    'Data','AUTHORITY_BOUNDARY','data-cross-track-authority-matrix-v1',true,true,false);
  BEGIN
    PERFORM public.persist_data_cross_track_integration_v1(v_request);
    RAISE EXCEPTION 'partial transaction rollback unexpectedly succeeded';
  EXCEPTION WHEN raise_exception THEN
    IF SQLERRM<>'dp7 atomic rollback fixture' THEN RAISE; END IF;
  END;
  IF (SELECT count(*) FROM public.data_cross_track_integration_run_v1)<>v_before THEN
    RAISE EXCEPTION 'Partial DP-7 insert was not rolled back.';
  END IF;
  EXECUTE 'DROP TRIGGER dp7_force_atomic_failure_v1 ON public.data_cross_track_contract_mapping_evidence_v1';
  EXECUTE 'DROP FUNCTION public.dp7_force_atomic_failure_v1()';
END;
$$;

DO $$
DECLARE v_snapshot varchar; v_quality uuid; v_request jsonb; v_result record;
BEGIN
  SELECT snapshot_ref,quality_ref INTO v_snapshot,v_quality FROM dp7_fixture_refs;
  v_request:=public.dp7_fixture_request_v1('integration_run:unsupported-schema',v_snapshot,v_quality,
    'Recommendation','FINGERPRINT_BOUNDARY','recommendation-profile-input-v1',true,true,false,
    'recommendation-profile-input-v2');
  v_request:=jsonb_set(v_request,'{checks,6,checkStatus}','"FAIL"'::jsonb);
  v_request:=jsonb_set(v_request,'{checks,6,severity}','"ERROR"'::jsonb);
  v_request:=jsonb_set(v_request,'{checks,6,failureCode}','"recommendation_schema_unsupported"'::jsonb);
  v_request:=jsonb_set(v_request,'{checks,6,observedValue}','"unsupported"'::jsonb);
  v_request:=jsonb_set(v_request,'{checks,6,evidenceFingerprint}',to_jsonb(public.data_cross_track_check_fingerprint_v1(v_request->'checks'->6)));
  SELECT * INTO v_result FROM public.persist_data_cross_track_integration_v1(v_request);
  IF v_result.overall_status<>'INCOMPATIBLE' THEN RAISE EXCEPTION 'Unsupported schema was not INCOMPATIBLE.'; END IF;
END;
$$;

DO $$
DECLARE v_snapshot varchar; v_quality uuid; v_request jsonb; v_before bigint; v_result record;
BEGIN
  SELECT snapshot_ref,quality_ref INTO v_snapshot,v_quality FROM dp7_fixture_refs;
  SELECT count(*) INTO v_before FROM public.data_cross_track_integration_run_v1;
  v_request:=public.dp7_fixture_request_v1('integration_run:fingerprint-mismatch',v_snapshot,v_quality,
    'Data','IDENTITY_BOUNDARY','data-cross-track-authority-matrix-v1',true,true,false);
  v_request:=jsonb_set(v_request,'{integrationInputFingerprint}',to_jsonb(repeat('f',64)));
  BEGIN
    PERFORM public.persist_data_cross_track_integration_v1(v_request);
    RAISE EXCEPTION 'fingerprint mismatch unexpectedly succeeded';
  EXCEPTION WHEN check_violation THEN NULL;
  END;
  IF (SELECT count(*) FROM public.data_cross_track_integration_run_v1)<>v_before THEN
    RAISE EXCEPTION 'Fingerprint mismatch persisted evidence.';
  END IF;

  v_request:=public.dp7_fixture_request_v1('integration_run:privacy',v_snapshot,v_quality,
    'Data','PRIVACY_BOUNDARY','data-cross-track-privacy-retention-matrix-v1',true,true,false);
  v_request:=jsonb_set(v_request,'{privacy,piiPresent}','true'::jsonb);
  BEGIN PERFORM public.persist_data_cross_track_integration_v1(v_request);
    RAISE EXCEPTION 'privacy violation unexpectedly succeeded';
  EXCEPTION WHEN check_violation THEN NULL; END;

  v_request:=public.dp7_fixture_request_v1('integration_run:retention',v_snapshot,v_quality,
    'Data','RETENTION_BOUNDARY','data-cross-track-privacy-retention-matrix-v1',true,true,false);
  v_request:=jsonb_set(v_request,'{retention,targetRetentionDays}','180'::jsonb);
  BEGIN PERFORM public.persist_data_cross_track_integration_v1(v_request);
    RAISE EXCEPTION 'retention violation unexpectedly succeeded';
  EXCEPTION WHEN check_violation THEN NULL; END;

  v_request:=public.dp7_fixture_request_v1('integration_run:authority',v_snapshot,v_quality,
    'Data','AUTHORITY_BOUNDARY','data-cross-track-authority-matrix-v1',true,true,false);
  v_request:=jsonb_set(v_request,'{authorities,0,writeAttempted}','true'::jsonb);