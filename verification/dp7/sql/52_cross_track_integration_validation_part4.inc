  BEGIN PERFORM public.persist_data_cross_track_integration_v1(v_request);
    RAISE EXCEPTION 'authority violation unexpectedly succeeded';
  EXCEPTION WHEN check_violation THEN NULL; END;

  v_request:=public.dp7_fixture_request_v1('integration_run:identity',v_snapshot,v_quality,
    'Data','IDENTITY_BOUNDARY','data-cross-track-authority-matrix-v1',true,true,false);
  v_request:=jsonb_set(v_request,'{identityBinding,authoritativeFingerprint}',to_jsonb(repeat('b',64)));
  BEGIN PERFORM public.persist_data_cross_track_integration_v1(v_request);
    RAISE EXCEPTION 'identity fingerprint mismatch unexpectedly succeeded';
  EXCEPTION WHEN check_violation THEN NULL; END;

  v_request:=public.dp7_fixture_request_v1('integration_run:search-invalid-document',v_snapshot,v_quality,
    'Search','FINGERPRINT_BOUNDARY','search-document-projection-v1',true,true,false);
  v_request:=jsonb_set(v_request,'{checks,6,checkStatus}','"FAIL"'::jsonb);
  v_request:=jsonb_set(v_request,'{checks,6,severity}','"BLOCKER"'::jsonb);
  v_request:=jsonb_set(v_request,'{checks,6,failureCode}','"search_document_identity_mismatch"'::jsonb);
  v_request:=jsonb_set(v_request,'{checks,6,observedValue}','"subject record is not post document"'::jsonb);
  v_request:=jsonb_set(v_request,'{checks,6,evidenceFingerprint}',
    to_jsonb(public.data_cross_track_check_fingerprint_v1(v_request->'checks'->6)));
  SELECT * INTO v_result FROM public.persist_data_cross_track_integration_v1(v_request);
  IF v_result.overall_status<>'INCOMPATIBLE' THEN RAISE EXCEPTION 'invalid Search document mapping was not blocked'; END IF;
END;
$$;

DO $$
DECLARE v_snapshot varchar; v_quality uuid; v_run uuid;
BEGIN
  SELECT snapshot_ref,quality_ref INTO v_snapshot,v_quality FROM dp7_fixture_refs;
  SELECT integration_run_id INTO v_run FROM public.data_cross_track_integration_run_v1
    WHERE integration_run_ref='integration_run:recommendation-profile';
  BEGIN
    UPDATE public.data_cross_track_integration_run_v1 SET target_track='Search' WHERE integration_run_id=v_run;
    RAISE EXCEPTION 'append-only UPDATE unexpectedly succeeded';
  EXCEPTION WHEN object_not_in_prerequisite_state THEN NULL;
  END;
  BEGIN
    DELETE FROM public.data_cross_track_integration_check_result_v1 WHERE integration_run_ref=v_run;
    RAISE EXCEPTION 'append-only DELETE unexpectedly succeeded';
  EXCEPTION WHEN object_not_in_prerequisite_state THEN NULL;
  END;
END;
$$;

GRANT EXECUTE ON FUNCTION public.dp7_fixture_request_v1(varchar,varchar,uuid,varchar,varchar,varchar,boolean,boolean,boolean,varchar,varchar)
  TO jc_data_integration_writer;
GRANT SELECT ON TABLE dp7_fixture_refs TO jc_data_integration_writer;

SET ROLE jc_data_integration_writer;
DO $$
DECLARE v_snapshot varchar; v_quality uuid; v_result record; v_request jsonb;
BEGIN
  SELECT snapshot_ref,quality_ref INTO v_snapshot,v_quality FROM dp7_fixture_refs;
  v_request:=public.dp7_fixture_request_v1('integration_run:writer-execute',v_snapshot,v_quality,
    'Data','FINGERPRINT_BOUNDARY','data-cross-track-contract-matrix-v1',true,true,false);
  SELECT * INTO v_result FROM public.persist_data_cross_track_integration_v1(v_request);
  IF v_result.disposition<>'NEW' THEN RAISE EXCEPTION 'writer function execute failed'; END IF;
  BEGIN
    INSERT INTO public.data_cross_track_integration_status_evidence_v1(
      integration_run_ref,integration_status,observed_at)
    VALUES(v_result.integration_run_id,'COMPLETED',CURRENT_TIMESTAMP);
    RAISE EXCEPTION 'writer direct table write unexpectedly succeeded';
  EXCEPTION WHEN insufficient_privilege THEN NULL; END;
END;
$$;
RESET ROLE;

SET ROLE jc_data_integration_reader;
DO $$
DECLARE v_count bigint;
BEGIN
  SELECT count(*) INTO v_count FROM public.data_cross_track_integration_safe_metrics_v1;
  IF v_count<1 THEN RAISE EXCEPTION 'reader safe view returned no rows'; END IF;
  BEGIN
    PERFORM count(*) FROM public.data_cross_track_integration_run_v1;
    RAISE EXCEPTION 'reader raw evidence access unexpectedly succeeded';
  EXCEPTION WHEN insufficient_privilege THEN NULL; END;
END;
$$;
RESET ROLE;

DO $$
DECLARE v_unsafe text; v_forbidden text;
BEGIN
  SELECT string_agg(rolname,',') INTO v_unsafe FROM pg_roles
  WHERE rolname='jc_data_integration_function_owner'
    AND (rolsuper OR rolcreaterole OR rolcreatedb OR rolcanlogin OR rolreplication OR rolbypassrls);
  IF v_unsafe IS NOT NULL THEN RAISE EXCEPTION 'function owner role is unsafe'; END IF;
  IF NOT EXISTS (SELECT 1 FROM pg_proc p JOIN pg_namespace n ON n.oid=p.pronamespace
    WHERE n.nspname='public' AND p.proname='persist_data_cross_track_integration_v1'
      AND p.prosecdef AND p.proconfig @> ARRAY['search_path=pg_catalog, public, pg_temp']) THEN
    RAISE EXCEPTION 'DP-7 SECURITY DEFINER or fixed search_path missing';
  END IF;
  IF has_function_privilege('public','public.persist_data_cross_track_integration_v1(jsonb)','EXECUTE') THEN
    RAISE EXCEPTION 'PUBLIC function execute not revoked';
  END IF;
  IF has_table_privilege('public','public.data_cross_track_integration_safe_metrics_v1','SELECT') THEN
    RAISE EXCEPTION 'PUBLIC safe view access not revoked';
  END IF;
  SELECT string_agg(column_name,',' ORDER BY ordinal_position) INTO v_forbidden
  FROM information_schema.columns
  WHERE table_schema='public' AND table_name='data_cross_track_integration_safe_metrics_v1'
    AND column_name IN ('user_id','subject_id','source_event_id','snapshot_ref','exposure_id','raw_payload',
      'raw_lineage','identity_binding','fingerprint','stack_trace','token','error_message');
  IF v_forbidden IS NOT NULL THEN RAISE EXCEPTION 'safe view exposes forbidden columns: %',v_forbidden; END IF;
  IF EXISTS (SELECT 1 FROM pg_proc p JOIN pg_namespace n ON n.oid=p.pronamespace
    WHERE n.nspname='public' AND p.proname LIKE '%purge%cross%track%') THEN
    RAISE EXCEPTION 'automatic purge function detected';
  END IF;
END;
$$;

DO $$
DECLARE v_before_recommendation bigint; v_after_recommendation bigint; v_before_search bigint; v_after_search bigint;
BEGIN
  SELECT count(*) INTO v_before_recommendation FROM public.recommendation_run;
  SELECT count(*) INTO v_before_search FROM public.search_document_projection_v1;
  SELECT count(*) INTO v_after_recommendation FROM public.recommendation_run;
  SELECT count(*) INTO v_after_search FROM public.search_document_projection_v1;
  IF v_before_recommendation<>v_after_recommendation THEN RAISE EXCEPTION 'Recommendation write detected'; END IF;
  IF v_before_search<>v_after_search THEN RAISE EXCEPTION 'Search projection write detected'; END IF;
END;
$$;

SELECT 'DP-7 PostgreSQL integration validation PASS' AS dp7_validation_status;
ROLLBACK;
