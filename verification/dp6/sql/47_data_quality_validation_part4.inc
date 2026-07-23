  v_item:=v_item||jsonb_build_object('evidenceFingerprint',
    public.data_quality_fingerprint_v1('data-quality-check-evidence-sha256-v1',jsonb_build_object(
        'checkCode',v_item->>'checkCode','scope',v_item->>'checkScope','expected',v_item->>'expectedValue',
        'observed',v_item->>'observedValue','difference',v_item->>'differenceValue','severity',v_item->>'severity',
        'status',v_item->>'checkStatus','failure',COALESCE(v_item->>'failureCode',''),
        'reason',COALESCE(v_item->>'reasonCode',''),
        'required',(v_item->>'required')::boolean)));
  v_checks:=jsonb_set(v_checks,'{0}',v_item);
  v_verdict:=jsonb_build_object('overallStatus','REJECTED','blockerCount',1,'errorCount',0,'warningCount',0,
    'passedCheckCount',v_passed-1,'failedCheckCount',1,'skippedRequiredCheckCount',0,
    'qualityScore',round((v_passed-1)::numeric*100/v_passed::numeric,6)::text,
    'qualityPolicyVersion','data-quality-policy-v1');
  v_verdict:=v_verdict||jsonb_build_object('verdictFingerprint',
    public.data_quality_fingerprint_v1('data-quality-verdict-sha256-v1',v_verdict));
  SELECT disposition,error_code INTO v_disp,v_error FROM public.persist_data_quality_validation_v1(
    'quality_run:dp6-conflict','FULL','snapshot:dp5-outcome','data-quality-validator-v1','data-quality-policy-v1',
    '2026-07-22T00:00:00Z',v_input,v_checks,v_metrics,jsonb_build_array(jsonb_build_object(
      'scope','SOURCE_COMPLETENESS','failureCode','quality_threshold_failed','severity','BLOCKER',
      'evidenceReference','quality_check:source_count','evidenceFingerprint',v_item->>'evidenceFingerprint')),
    v_verdict,v_rebuild,'[]'::jsonb);
  IF v_disp<>'CONFLICT' OR v_error<>'QUALITY_VERDICT_CONFLICT' THEN RAISE EXCEPTION 'quality verdict CONFLICT failed'; END IF;

  IF (SELECT count(*) FROM public.data_quality_validation_run_v1 WHERE snapshot_ref=v_snapshot.snapshot_id)<>1
     OR (SELECT count(*) FROM public.data_snapshot_quality_verdict_v1 WHERE snapshot_ref=v_snapshot.snapshot_id)<>1 THEN
    RAISE EXCEPTION 'duplicate/conflict created extra quality evidence';
  END IF;
END;
$$;

-- Fail-closed verdict and zero-denominator constraints.
DO $$
BEGIN
  BEGIN
    INSERT INTO public.data_quality_metric_v1(validation_run_ref,metric_name,numerator,denominator,metric_value,
      metric_unit,policy_threshold,threshold_operator,threshold_result,metric_version,metric_fingerprint)
    SELECT validation_run_id,'invalid_zero_rate',0,0,1,'ratio',1,'GREATER_THAN_OR_EQUAL','PASS',
      'data-quality-metric-v1',repeat('a',64) FROM public.data_quality_validation_run_v1 LIMIT 1;
    RAISE EXCEPTION 'zero denominator PASS unexpectedly succeeded';
  EXCEPTION WHEN check_violation THEN NULL; END;
END;
$$;

-- Append-only controls.
DO $$
BEGIN
  BEGIN UPDATE public.data_source_checkpoint_v1 SET source_event_count=2 WHERE checkpoint_ref='checkpoint:dp5-profile';
    RAISE EXCEPTION 'checkpoint UPDATE unexpectedly succeeded'; EXCEPTION WHEN SQLSTATE '55000' THEN NULL; END;
  BEGIN DELETE FROM public.data_projection_snapshot_v1 WHERE snapshot_ref='snapshot:dp5-profile';
    RAISE EXCEPTION 'snapshot DELETE unexpectedly succeeded'; EXCEPTION WHEN SQLSTATE '55000' THEN NULL; END;
  BEGIN UPDATE public.data_projection_lineage_v1 SET projection_policy_version='changed-v1';
    RAISE EXCEPTION 'lineage UPDATE unexpectedly succeeded'; EXCEPTION WHEN SQLSTATE '55000' THEN NULL; END;
  BEGIN UPDATE public.data_snapshot_quality_verdict_v1 SET overall_status='REJECTED';
    RAISE EXCEPTION 'quality verdict UPDATE unexpectedly succeeded'; EXCEPTION WHEN SQLSTATE '55000' THEN NULL; END;
  BEGIN DELETE FROM public.data_quality_validation_check_result_v1;
    RAISE EXCEPTION 'quality check DELETE unexpectedly succeeded'; EXCEPTION WHEN SQLSTATE '55000' THEN NULL; END;
END;
$$;

DO $$
BEGIN
  IF (SELECT rolcanlogin OR rolsuper OR rolcreatedb OR rolcreaterole OR rolreplication OR rolbypassrls
      FROM pg_roles WHERE rolname='jc_data_projection_function_owner') THEN
    RAISE EXCEPTION 'function owner role is unsafe';
  END IF;
  IF has_table_privilege('jc_data_projection_writer','public.data_projection_snapshot_v1','INSERT')
     OR has_table_privilege('jc_data_projection_reader','public.data_projection_snapshot_v1','SELECT')
     OR has_function_privilege('public','public.persist_data_projection_snapshot_v1(varchar,varchar,varchar,varchar,varchar,varchar,varchar,timestamptz,varchar,varchar,varchar,varchar,varchar,varchar,jsonb,jsonb,varchar,varchar)','EXECUTE') THEN
    RAISE EXCEPTION 'least privilege or PUBLIC denial failed';
  END IF;
  IF NOT has_function_privilege('jc_data_projection_writer','public.persist_data_projection_snapshot_v1(varchar,varchar,varchar,varchar,varchar,varchar,varchar,timestamptz,varchar,varchar,varchar,varchar,varchar,varchar,jsonb,jsonb,varchar,varchar)','EXECUTE')
     OR NOT has_table_privilege('jc_data_projection_reader','public.data_projection_safe_metrics_v1','SELECT') THEN
    RAISE EXCEPTION 'approved role grant missing';
  END IF;
  IF (SELECT rolcanlogin OR rolsuper OR rolcreatedb OR rolcreaterole OR rolreplication OR rolbypassrls
      FROM pg_roles WHERE rolname='jc_data_quality_function_owner') THEN
    RAISE EXCEPTION 'quality function owner role is unsafe';
  END IF;
  IF has_table_privilege('jc_data_quality_writer','public.data_quality_validation_run_v1','INSERT')
     OR has_table_privilege('jc_data_quality_reader','public.data_quality_validation_run_v1','SELECT')
     OR has_function_privilege('public','public.persist_data_quality_validation_v1(varchar,varchar,varchar,varchar,varchar,timestamptz,varchar,jsonb,jsonb,jsonb,jsonb,jsonb,jsonb)','EXECUTE') THEN
    RAISE EXCEPTION 'quality least privilege or PUBLIC denial failed';
  END IF;
  IF NOT has_function_privilege('jc_data_quality_writer','public.persist_data_quality_validation_v1(varchar,varchar,varchar,varchar,varchar,timestamptz,varchar,jsonb,jsonb,jsonb,jsonb,jsonb,jsonb)','EXECUTE')
     OR NOT has_table_privilege('jc_data_quality_reader','public.data_quality_safe_metrics_v1','SELECT') THEN
    RAISE EXCEPTION 'approved quality role grant missing';
  END IF;
  IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema='public'
    AND table_name='data_quality_safe_metrics_v1'
    AND column_name IN ('user_id','subject_id','session_id','request_id','source_event_id','projection_record_id','raw_payload','raw_lineage','exposure_id')) THEN
    RAISE EXCEPTION 'safe view exposes prohibited identity dimensions';
  END IF;
  IF EXISTS (SELECT 1 FROM information_schema.routines WHERE routine_schema='public'
             AND routine_name ILIKE '%purge%' AND (routine_name ILIKE '%projection%' OR routine_name ILIKE '%quality%')) THEN
    RAISE EXCEPTION 'automatic projection purge unexpectedly exists';
  END IF;
END;
$$;

ROLLBACK;
