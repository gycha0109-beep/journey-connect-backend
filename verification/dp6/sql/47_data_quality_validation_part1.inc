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