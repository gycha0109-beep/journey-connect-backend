-- Journey Connect DB v2.7 - DP-6 rollback-only PostgreSQL 15/18 validation
-- Includes protected DP-5 fixture creation before DP-6 quality validation.
BEGIN;

DO $$
DECLARE
  v_user_id bigint;
  v_profile_fp varchar(64);
  v_profile2_fp varchar(64);
  v_outcome_fp varchar(64);
  v_profile_members jsonb;
  v_profile2_members jsonb;
  v_outcome_members jsonb;
  v_source_set varchar(64);
  v_definition varchar(64);
  v_checkpoint_id uuid;
  v_disp varchar;
  v_records jsonb;
  v_lineage jsonb;
  v_lineage_fp varchar(64);
  v_record_fp varchar(64);
  v_snapshot_fp varchar(64);
  v_run_id uuid;
  v_snapshot_id uuid;
  v_entry jsonb;
  v_record jsonb;
BEGIN
  INSERT INTO public.app_users(email,password_hash,username,display_name)
  VALUES('dp5-smoke@journey.test','hash','dp5_smoke','DP5 Smoke') RETURNING id INTO v_user_id;

  INSERT INTO public.recommendation_snapshot(
    snapshot_id,snapshot_kind,schema_version,canonicalization_version,hash_algorithm,
    content_hash,canonical_payload,payload_json,payload_size_bytes)
  VALUES
    ('dp5-rank','ranking_input_v1','p1.0.0','canonical-json-v1','sha256',
      public.recommendation_snapshot_sha256_hex('ranking_input_v1','p1.0.0',convert_to('{}','UTF8')),convert_to('{}','UTF8'),'{}',2),
    ('dp5-meta','diversity_metadata_v1','p1.0.0','canonical-json-v1','sha256',
      public.recommendation_snapshot_sha256_hex('diversity_metadata_v1','p1.0.0',convert_to('[]','UTF8')),convert_to('[]','UTF8'),'[]',2),
    ('dp5-exp','exploration_metadata_v1','p1.0.0','canonical-json-v1','sha256',
      public.recommendation_snapshot_sha256_hex('exploration_metadata_v1','p1.0.0',convert_to('[]','UTF8')),convert_to('[]','UTF8'),'[]',2),
    ('dp5-result','ranking_result_v1','p1.0.0','canonical-json-v1','sha256',
      public.recommendation_snapshot_sha256_hex('ranking_result_v1','p1.0.0',convert_to('{}','UTF8')),convert_to('{}','UTF8'),'{}',2);

  INSERT INTO public.recommendation_run(
    run_id,request_id,run_mode,run_status,user_id,session_id,context_id,surface,reference_time,
    ranking_snapshot_id,metadata_snapshot_id,exploration_snapshot_id,result_snapshot_id,
    ranking_policy_version,base_integration_policy_version,base_ranking_policy_version,
    score_policy_version,component_policy_versions,diversity_policy_version,exploration_policy_version,
    exploration_seed,ranking_status,ranking_empty_reason,effective_limit,input_count,scored_candidate_count,
    final_ranked_candidate_count,terminal_candidate_count,result_fingerprint,core_build_id,duration_ms)
  VALUES
    ('dp5-baseline','dp5-request-baseline','canary','succeeded',v_user_id,'dp5-session','dp5-context','home','2026-07-19T00:00:00Z',
      'dp5-rank','dp5-meta','dp5-exp','dp5-result','ranking-v3','p0-integration-v1','ranking-policy-v1',
      'ranking-policy-v1','{}','diversity-policy-v1','exploration-policy-v1','p0-deterministic-v1',
      'empty','no_scored_candidates',0,0,0,0,0,repeat('b',64),'java-core-1.0.0',1),
    ('dp5-treatment','dp5-request-treatment','canary','succeeded',v_user_id,'dp5-session','dp5-context','home','2026-07-19T00:00:00Z',
      'dp5-rank','dp5-meta','dp5-exp','dp5-result','p1-policy-bundle-v1:home_feed:empty','p1-integration-v1','ranking-policy-v1',
      'ranking-policy-v2-empty','{}','diversity-policy-home-v2','exploration-policy-v2','p1-deterministic-no-random-v1',
      'empty','no_scored_candidates',0,0,0,0,0,repeat('a',64),'java-core-1.1.0-p1',1);

  INSERT INTO public.recommendation_p1_profile_snapshot(
    profile_snapshot_id,user_id,reference_time,profile_policy_version,feature_vocabulary_version,segment,
    explicit_preference_count,input_event_count,accepted_event_count,ignored_event_count,duplicate_event_count,
    accepted_behavior_weight,signal_count,signals,fingerprint)
  VALUES('dp5-profile',v_user_id,'2026-07-19T00:00:00Z','behavior-profile-policy-v1','feature-vocabulary-v1',
    'empty',0,0,0,0,0,0,0,'[]',repeat('c',64));
  INSERT INTO public.recommendation_p1_policy_assignment(
    assignment_id,baseline_run_id,treatment_run_id,user_id,session_id,profile_snapshot_id,release_id,
    experiment_assignment,segment,selection_reasons,profile_policy_version,feature_vocabulary_version,
    retrieval_policy_version,policy_bundle_version,score_policy_version,diversity_policy_version,
    low_exposure_policy_version,exploration_policy_version)
  VALUES('dp5-p1-assignment','dp5-baseline','dp5-treatment',v_user_id,'dp5-session','dp5-profile',
    'p2-treatment-v1','treatment','empty','["empty_profile"]','behavior-profile-policy-v1',
    'feature-vocabulary-v1','retrieval-policy-v1','p1-policy-bundle-v1:home_feed:empty',
    'ranking-policy-v2-empty','diversity-policy-home-v2','low-exposure-policy-v1','exploration-policy-v2');
  INSERT INTO public.recommendation_p2_experiment_assignment(
    assignment_id,experiment_id,experiment_version,subject_ref,user_id,assignment_unit,variant,bucket,
    assignment_fingerprint,assigned_at,producer_build_id)
  VALUES('dp5-assignment','recommendation-p1','experiment-v1','user:'||v_user_id,v_user_id,'user',
    'treatment',1234,repeat('e',64),'2026-07-19T00:00:00Z','dp5-assignment-build-v1');
  INSERT INTO public.recommendation_p2_experiment_exposure(
    exposure_id,assignment_id,run_id,user_id,session_id,variant,exposed_at,exposure_fingerprint,created_at)
  VALUES('dp5-exposure','dp5-assignment','dp5-treatment',v_user_id,'dp5-session','treatment',
    '2026-07-19T00:00:01Z',repeat('f',64),'2026-07-19T00:00:02Z');

  v_profile_fp := encode(public.digest(convert_to('{"profile":1}','UTF8'),'sha256'),'hex');
  v_profile2_fp := encode(public.digest(convert_to('{"profile":2}','UTF8'),'sha256'),'hex');
  v_outcome_fp := encode(public.digest(convert_to('{"outcome":1}','UTF8'),'sha256'),'hex');
  INSERT INTO public.data_platform_event_v1(
    event_id,contract_version,schema_version,canonicalization_version,fingerprint_version,payload_fingerprint,
    fingerprint_canonical_bytes,producer_version,producer_build_id,event_family,event_type,occurred_at,received_at,
    actor_ref,session_ref,entity_ref,idempotency_key,canonical_payload,expires_at)
  VALUES
    ('event:dp5-profile-1','platform-event-v1','user-behavior-event-v1','platform-event-canonical-json-v1',
      'platform-event-fingerprint-sha256-v1',v_profile_fp,convert_to('{"profile":1}','UTF8'),'dp5-producer-v1',
      'git:'||repeat('1',40),'user_behavior','post_like','2026-07-20T00:00:00Z','2026-07-20T00:00:01Z',
      'subject:dp5-smoke','session:dp5-profile','post:1','dp5-profile-1','{}','2027-07-20T00:00:01Z'),
    ('event:dp5-profile-2','platform-event-v1','user-behavior-event-v1','platform-event-canonical-json-v1',
      'platform-event-fingerprint-sha256-v1',v_profile2_fp,convert_to('{"profile":2}','UTF8'),'dp5-producer-v1',
      'git:'||repeat('1',40),'user_behavior','post_hide','2026-07-21T00:00:00Z','2026-07-21T00:00:01Z',
      'subject:dp5-smoke','session:dp5-profile','post:2','dp5-profile-2','{}','2027-07-21T00:00:01Z'),
    ('event:dp5-outcome-click','platform-event-v1','user-behavior-event-v1','platform-event-canonical-json-v1',
      'platform-event-fingerprint-sha256-v1',v_outcome_fp,convert_to('{"outcome":1}','UTF8'),'dp5-producer-v1',
      'git:'||repeat('1',40),'user_behavior','recommendation_click','2026-07-19T00:00:30Z','2026-07-19T00:00:31Z',
      'subject:dp5-p2','session:dp5-session','post:3','dp5-outcome-1','{}','2027-07-19T00:00:31Z');

  v_profile_members := jsonb_build_array(
    jsonb_build_object('sourceKind','canonical_event','sourceEventRef','event:dp5-profile-1','sourceFingerprint',v_profile_fp,
      'adapterEvidenceRef',NULL,'occurredAt','2026-07-20T00:00:00Z','ingestedAt','2026-07-20T00:00:01Z'));
  SELECT public.data_projection_fingerprint_v1('data-source-set-sha256-v1',jsonb_build_object('sources',jsonb_agg(
    jsonb_build_object('sourceEventRef',m->>'sourceEventRef','sourceFingerprint',m->>'sourceFingerprint',
      'adapterEvidenceRef',m->'adapterEvidenceRef','occurredAt',m->>'occurredAt','ingestedAt',m->>'ingestedAt')
    ORDER BY m->>'sourceEventRef',m->>'sourceFingerprint',COALESCE(m->>'adapterEvidenceRef','')))) INTO v_source_set
  FROM jsonb_array_elements(v_profile_members)m;
  v_definition := public.data_projection_fingerprint_v1('data-checkpoint-definition-sha256-v1',jsonb_build_object(
    'sourceStream','data-platform-event-v1','sourceContractVersion','platform-event-v1','sourceSchemaVersion','user-behavior-event-v1',
    'eventTimeFrom','2026-07-19T00:00:00Z','eventTimeTo','2026-07-22T00:00:00Z',
    'ingestedAtUpperBound','2026-07-22T00:00:00Z','sourceEventCount',1,'sourceSetFingerprint',v_source_set));
  SELECT disposition,checkpoint_id INTO v_disp,v_checkpoint_id FROM public.persist_data_source_checkpoint_v1(
    'checkpoint:dp5-profile','data-platform-event-v1','platform-event-v1','user-behavior-event-v1',
    '2026-07-19T00:00:00Z','2026-07-22T00:00:00Z','2026-07-22T00:00:00Z','event:dp5-profile-1',
    v_profile_members,v_source_set,v_definition);
  IF v_disp<>'NEW' THEN RAISE EXCEPTION 'profile checkpoint NEW failed'; END IF;
  SELECT disposition INTO v_disp FROM public.persist_data_source_checkpoint_v1(
    'checkpoint:ignored-duplicate','data-platform-event-v1','platform-event-v1','user-behavior-event-v1',
    '2026-07-19T00:00:00Z','2026-07-22T00:00:00Z','2026-07-22T00:00:00Z','event:dp5-profile-1',
    v_profile_members,v_source_set,v_definition);
  IF v_disp<>'DUPLICATE' THEN RAISE EXCEPTION 'checkpoint DUPLICATE failed'; END IF;

  v_profile2_members := jsonb_build_array(
    jsonb_build_object('sourceKind','canonical_event','sourceEventRef','event:dp5-profile-2','sourceFingerprint',v_profile2_fp,
      'adapterEvidenceRef',NULL,'occurredAt','2026-07-21T00:00:00Z','ingestedAt','2026-07-21T00:00:01Z'));
  SELECT public.data_projection_fingerprint_v1('data-source-set-sha256-v1',jsonb_build_object('sources',jsonb_agg(
    jsonb_build_object('sourceEventRef',m->>'sourceEventRef','sourceFingerprint',m->>'sourceFingerprint',
      'adapterEvidenceRef',m->'adapterEvidenceRef','occurredAt',m->>'occurredAt','ingestedAt',m->>'ingestedAt')
    ORDER BY m->>'sourceEventRef',m->>'sourceFingerprint',COALESCE(m->>'adapterEvidenceRef','')))) INTO v_source_set
  FROM jsonb_array_elements(v_profile2_members)m;
  v_definition := public.data_projection_fingerprint_v1('data-checkpoint-definition-sha256-v1',jsonb_build_object(
    'sourceStream','data-platform-event-v1','sourceContractVersion','platform-event-v1','sourceSchemaVersion','user-behavior-event-v1',
    'eventTimeFrom','2026-07-19T00:00:00Z','eventTimeTo','2026-07-22T00:00:00Z',
    'ingestedAtUpperBound','2026-07-22T00:00:00Z','sourceEventCount',1,'sourceSetFingerprint',v_source_set));
  SELECT disposition INTO v_disp FROM public.persist_data_source_checkpoint_v1(
    'checkpoint:dp5-profile-conflict','data-platform-event-v1','platform-event-v1','user-behavior-event-v1',
    '2026-07-19T00:00:00Z','2026-07-22T00:00:00Z','2026-07-22T00:00:00Z','event:dp5-profile-2',
    v_profile2_members,v_source_set,v_definition);
  IF v_disp<>'CONFLICT' THEN RAISE EXCEPTION 'checkpoint CONFLICT failed'; END IF;
  -- caller-supplied source timestamps cannot diverge from the authoritative source row.
  BEGIN
    PERFORM public.persist_data_source_checkpoint_v1('checkpoint:timestamp-tamper','data-platform-event-v1',
      'platform-event-v1','user-behavior-event-v1','2026-07-19T00:00:00Z','2026-07-22T00:00:00Z',
      '2026-07-22T00:00:00Z','event:dp5-profile-2',
      jsonb_set(v_profile2_members,'{0,occurredAt}','"2026-07-20T23:59:59Z"'::jsonb),v_source_set,v_definition);
    RAISE EXCEPTION 'source timestamp tamper unexpectedly succeeded';
  EXCEPTION WHEN SQLSTATE '23514' THEN NULL; END;
  -- invalid fingerprints fail closed.
  BEGIN
    PERFORM public.persist_data_source_checkpoint_v1('checkpoint:invalid','data-platform-event-v1','platform-event-v1',
      'user-behavior-event-v1','2026-07-19T00:00:00Z','2026-07-22T00:00:00Z','2026-07-22T00:00:00Z',
      'event:dp5-profile-2',v_profile2_members,repeat('0',64),repeat('0',64));
    RAISE EXCEPTION 'source mismatch unexpectedly succeeded';
  EXCEPTION WHEN SQLSTATE '22023' THEN NULL; END;

  v_entry := jsonb_build_object('projectionRecordRef','profile_record:dp5','sourceEventRef','event:dp5-profile-1',
    'sourceFingerprint',v_profile_fp,'adapterEvidenceRef',NULL,'sourceCheckpointRef','checkpoint:dp5-profile',
    'projectionPolicyVersion','recommendation-profile-projection-policy-v1','mappingPolicyVersion',NULL);
  v_entry := v_entry || jsonb_build_object('sourceKind','canonical_event','lineageEntryFingerprint',
    public.data_projection_fingerprint_v1('data-projection-lineage-entry-sha256-v1',v_entry));
  v_lineage := jsonb_build_array(v_entry);
  v_lineage_fp := public.data_projection_fingerprint_v1('data-projection-lineage-sha256-v1',
    jsonb_build_object('entries',jsonb_build_array(v_entry->>'lineageEntryFingerprint')));
  v_record := jsonb_build_object(
    'recordRef','profile_record:dp5','projectionName','recommendation-profile-input-v1','subjectRef','subject:dp5-smoke',
    'projectionAsOf','2026-07-22T00:00:00Z','sourceCheckpointRef','checkpoint:dp5-profile',
    'profileSchemaVersion','recommendation-profile-input-v1','projectionPolicyVersion','recommendation-profile-projection-policy-v1',
    'activityWindowDays',7,'interactionCounts',jsonb_build_object('post_like',1),'recentRegions','[]'::jsonb,
    'recentContentRefs',jsonb_build_array(jsonb_build_object('reference','post:1','count',1,'lastOccurredAt','2026-07-20T00:00:00Z')),
    'recentTagRefs','[]'::jsonb,'engagementSignals',jsonb_build_object('post_like',1),'negativeSignals','{}'::jsonb,
    'sourceEventCount',1,'sourceLineageFingerprint',v_lineage_fp);
  v_record_fp := public.data_projection_fingerprint_v1('data-projection-record-sha256-v1',v_record-'recordRef');
  v_record := v_record || jsonb_build_object('projectionRecordFingerprint',v_record_fp);
  v_records := jsonb_build_array(v_record);
  v_snapshot_fp := public.data_projection_fingerprint_v1('data-projection-snapshot-sha256-v1',jsonb_build_object(
    'projectionName','recommendation-profile-input-v1','projectionSchemaVersion','recommendation-profile-input-v1',
    'projectionPolicyVersion','recommendation-profile-projection-policy-v1','featurePolicyVersion','recommendation-profile-feature-policy-v1',
    'identityBindingVersion','recommendation-user-subject-binding-v1','targetContractVersion','recommendation-profile-input-v1',
    'sourceCheckpointRef','checkpoint:dp5-profile','snapshotAsOf','2026-07-22T00:00:00Z',
    'recordFingerprints',jsonb_build_array(v_record_fp)));
  SELECT disposition,projection_run_id,snapshot_id INTO v_disp,v_run_id,v_snapshot_id
  FROM public.persist_data_projection_snapshot_v1(
    'projection_run:dp5-profile','snapshot:dp5-profile','recommendation-profile-input-v1',
    'recommendation-profile-input-v1','recommendation-profile-projection-policy-v1','recommendation-profile-feature-policy-v1',
    'checkpoint:dp5-profile','2026-07-22T00:00:00Z','recommendation-user-subject-binding-v1','approved-binding-input',
    repeat('a',64),'journey-connect','recommendation-profile-input-v1','git:'||repeat('1',40),
    v_records,v_lineage,v_snapshot_fp,v_lineage_fp);
  IF v_disp<>'NEW' THEN RAISE EXCEPTION 'profile snapshot NEW failed'; END IF;
  SELECT disposition INTO v_disp FROM public.persist_data_projection_snapshot_v1(
    'projection_run:ignored','snapshot:ignored','recommendation-profile-input-v1','recommendation-profile-input-v1',
    'recommendation-profile-projection-policy-v1','recommendation-profile-feature-policy-v1','checkpoint:dp5-profile',
    '2026-07-22T00:00:00Z','recommendation-user-subject-binding-v1','approved-binding-input',repeat('a',64),
    'journey-connect','recommendation-profile-input-v1','git:'||repeat('2',40),v_records,v_lineage,v_snapshot_fp,v_lineage_fp);
  IF v_disp<>'DUPLICATE' THEN RAISE EXCEPTION 'snapshot DUPLICATE failed'; END IF;
  v_record := jsonb_set(v_record,'{interactionCounts,post_like}','2'::jsonb,true);
  v_record_fp := public.data_projection_fingerprint_v1('data-projection-record-sha256-v1',v_record-'recordRef'-'projectionRecordFingerprint');
  v_record := jsonb_set(v_record,'{projectionRecordFingerprint}',to_jsonb(v_record_fp));
  v_records := jsonb_build_array(v_record);
  v_snapshot_fp := public.data_projection_fingerprint_v1('data-projection-snapshot-sha256-v1',jsonb_build_object(
    'projectionName','recommendation-profile-input-v1','projectionSchemaVersion','recommendation-profile-input-v1',
    'projectionPolicyVersion','recommendation-profile-projection-policy-v1','featurePolicyVersion','recommendation-profile-feature-policy-v1',
    'identityBindingVersion','recommendation-user-subject-binding-v1','targetContractVersion','recommendation-profile-input-v1',
    'sourceCheckpointRef','checkpoint:dp5-profile','snapshotAsOf','2026-07-22T00:00:00Z',
    'recordFingerprints',jsonb_build_array(v_record_fp)));
  SELECT disposition INTO v_disp FROM public.persist_data_projection_snapshot_v1(
    'projection_run:conflict','snapshot:conflict','recommendation-profile-input-v1','recommendation-profile-input-v1',
    'recommendation-profile-projection-policy-v1','recommendation-profile-feature-policy-v1','checkpoint:dp5-profile',
    '2026-07-22T00:00:00Z','recommendation-user-subject-binding-v1','approved-binding-input',repeat('a',64),
    'journey-connect','recommendation-profile-input-v1','git:'||repeat('3',40),v_records,v_lineage,v_snapshot_fp,v_lineage_fp);
  IF v_disp<>'CONFLICT' THEN RAISE EXCEPTION 'snapshot CONFLICT failed'; END IF;
END;
$$;

DO $$
DECLARE
  v_user_id bigint;
  v_outcome_fp varchar(64);
  v_members jsonb;
  v_source_set varchar(64);
  v_definition varchar(64);
  v_entry1 jsonb;
  v_entry2 jsonb;
  v_lineage jsonb;
  v_lineage_fp varchar(64);
  v_record jsonb;
  v_record_fp varchar(64);
  v_records jsonb;
  v_snapshot_fp varchar(64);
  v_disp varchar;
BEGIN
  SELECT id INTO v_user_id FROM public.app_users WHERE username='dp5_smoke';
  SELECT payload_fingerprint INTO v_outcome_fp FROM public.data_platform_event_v1 WHERE event_id='event:dp5-outcome-click';
  v_members := jsonb_build_array(
    jsonb_build_object('sourceKind','p2_exposure','sourceEventRef','p2_exposure:dp5-exposure','sourceFingerprint',repeat('f',64),
      'adapterEvidenceRef',NULL,'occurredAt','2026-07-19T00:00:01Z','ingestedAt','2026-07-19T00:00:02Z'),
    jsonb_build_object('sourceKind','canonical_event','sourceEventRef','event:dp5-outcome-click','sourceFingerprint',v_outcome_fp,
      'adapterEvidenceRef',NULL,'occurredAt','2026-07-19T00:00:30Z','ingestedAt','2026-07-19T00:00:31Z'));
  SELECT public.data_projection_fingerprint_v1('data-source-set-sha256-v1',jsonb_build_object('sources',jsonb_agg(
    jsonb_build_object('sourceEventRef',m->>'sourceEventRef','sourceFingerprint',m->>'sourceFingerprint',
      'adapterEvidenceRef',m->'adapterEvidenceRef','occurredAt',m->>'occurredAt','ingestedAt',m->>'ingestedAt')
    ORDER BY m->>'sourceEventRef',m->>'sourceFingerprint',COALESCE(m->>'adapterEvidenceRef','')))) INTO v_source_set
  FROM jsonb_array_elements(v_members)m;
  v_definition := public.data_projection_fingerprint_v1('data-checkpoint-definition-sha256-v1',jsonb_build_object(
    'sourceStream','data-platform-event-v1','sourceContractVersion','platform-event-v1','sourceSchemaVersion','user-behavior-event-v1',
    'eventTimeFrom','2026-07-19T00:00:00Z','eventTimeTo','2026-07-27T00:00:00Z',
    'ingestedAtUpperBound','2026-07-27T00:00:00Z','sourceEventCount',2,'sourceSetFingerprint',v_source_set));
  SELECT disposition INTO v_disp FROM public.persist_data_source_checkpoint_v1(
    'checkpoint:dp5-outcome','data-platform-event-v1','platform-event-v1','user-behavior-event-v1',
    '2026-07-19T00:00:00Z','2026-07-27T00:00:00Z','2026-07-27T00:00:00Z','event:dp5-outcome-click',
    v_members,v_source_set,v_definition);
  IF v_disp<>'NEW' THEN RAISE EXCEPTION 'outcome checkpoint NEW failed'; END IF;
  v_entry1 := jsonb_build_object('projectionRecordRef','outcome_record:dp5','sourceEventRef','p2_exposure:dp5-exposure',
    'sourceFingerprint',repeat('f',64),'adapterEvidenceRef',NULL,'sourceCheckpointRef','checkpoint:dp5-outcome',
    'projectionPolicyVersion','experiment-outcome-projection-policy-v1','mappingPolicyVersion',NULL);
  v_entry1 := v_entry1 || jsonb_build_object('sourceKind','p2_exposure','lineageEntryFingerprint',
    public.data_projection_fingerprint_v1('data-projection-lineage-entry-sha256-v1',v_entry1));
  v_entry2 := jsonb_build_object('projectionRecordRef','outcome_record:dp5','sourceEventRef','event:dp5-outcome-click',
    'sourceFingerprint',v_outcome_fp,'adapterEvidenceRef',NULL,'sourceCheckpointRef','checkpoint:dp5-outcome',
    'projectionPolicyVersion','experiment-outcome-projection-policy-v1','mappingPolicyVersion',NULL);
  v_entry2 := v_entry2 || jsonb_build_object('sourceKind','canonical_event','lineageEntryFingerprint',
    public.data_projection_fingerprint_v1('data-projection-lineage-entry-sha256-v1',v_entry2));
  v_lineage := jsonb_build_array(v_entry1,v_entry2);
  SELECT public.data_projection_fingerprint_v1('data-projection-lineage-sha256-v1',
    jsonb_build_object('entries',jsonb_agg(e->>'lineageEntryFingerprint' ORDER BY e->>'lineageEntryFingerprint')))
    INTO v_lineage_fp FROM jsonb_array_elements(v_lineage)e;
  v_record := jsonb_build_object(
    'recordRef','outcome_record:dp5','projectionName','experiment-outcome-input-v1',
    'experimentRef','experiment:recommendation-p1','experimentVersion','experiment-v1','variantRef','treatment',
    'exposureRef','dp5-exposure','runRef','dp5-treatment','sourceUserRef','user:'||v_user_id,
    'subjectRef','subject:dp5-p2','sessionRef','session:dp5-session','exposedAt','2026-07-19T00:00:01Z',
    'outcomeWindowSeconds',604800,'clicked',true,'liked',false,'saved',false,'shared',false,
    'fallbackObserved',false,'outcomeEventRefs',jsonb_build_array('event:dp5-outcome-click'),
    'sourceCheckpointRef','checkpoint:dp5-outcome','sourceEventCount',2,'sourceLineageFingerprint',v_lineage_fp);
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
  SELECT * INTO v_snapshot FROM public.data_projection_snapshot_v1 WHERE snapshot_ref='snapshot:dp5-profile';
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
    IF v_code='exposure.binding' THEN v_status:='NOT_APPLICABLE';v_reason:='profile_projection';
    ELSE v_status:='PASS';v_reason:=NULL; END IF;
    v_item:=jsonb_build_object(
      'checkCode',v_code,'checkScope',v_scope,'expectedValue',CASE WHEN v_status='PASS' THEN 'exact' ELSE 'not_applicable' END,
      'observedValue',CASE WHEN v_status='PASS' THEN 'exact' ELSE 'not_applicable' END,'differenceValue','0',
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
  FROM public.data_recommendation_profile_input_projection_v1 WHERE snapshot_ref=v_snapshot.snapshot_id;
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
  FROM public.persist_data_quality_validation_v1('quality_run:dp6-valid','FULL','snapshot:dp5-profile',
    'data-quality-validator-v1','data-quality-policy-v1','2026-07-22T00:00:00Z',v_input,
    v_checks,v_metrics,'[]'::jsonb,v_verdict,v_rebuild,'[]'::jsonb);
  IF v_disp<>'NEW' OR v_error IS NOT NULL THEN RAISE EXCEPTION 'valid FULL validation NEW failed'; END IF;
  SELECT disposition INTO v_disp FROM public.persist_data_quality_validation_v1('quality_run:ignored-duplicate',
    'FULL','snapshot:dp5-profile','data-quality-validator-v1','data-quality-policy-v1','2026-07-22T00:00:00Z',v_input,
    v_checks,v_metrics,'[]'::jsonb,v_verdict,v_rebuild,'[]'::jsonb);
  IF v_disp<>'DUPLICATE' THEN RAISE EXCEPTION 'same validation DUPLICATE failed'; END IF;

  -- Same logical identity with a different valid verdict is a conflict, never a second verdict.
  v_item:=v_checks->0;
  v_item:=jsonb_set(v_item,'{checkStatus}','"FAIL"')||jsonb_build_object(
    'severity','BLOCKER','failureCode','quality_threshold_failed','reasonCode','');
  v_item:=v_item-'evidenceFingerprint';
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
    'quality_run:dp6-conflict','FULL','snapshot:dp5-profile','data-quality-validator-v1','data-quality-policy-v1',
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
