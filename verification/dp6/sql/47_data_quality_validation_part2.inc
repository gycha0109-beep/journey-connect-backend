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