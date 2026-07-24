\set ON_ERROR_STOP on
BEGIN;
INSERT INTO rca1b_fixture.scenario_registry(lane,scenario,role,dimension,expected_classification) VALUES
('P1','p1_exact_shared_field_match','BASELINE','QUERY_RESULT_PARITY','MATCH_EXACT'),
('P1','p1_deterministic_derived_match','BASELINE','QUERY_RESULT_PARITY','MATCH_DERIVED'),
('P1','p1_7_day_window_match','BASELINE','QUERY_RESULT_PARITY','MATCH_EXACT'),
('P1','p1_30_day_window_match','BASELINE','QUERY_RESULT_PARITY','MATCH_EXACT'),
('P1','p1_90_day_window_match','BASELINE','QUERY_RESULT_PARITY','MATCH_EXACT'),
('P1','p1_exact_mismatch','EXPECTED_NEGATIVE','QUERY_RESULT_PARITY','RECONCILIATION_INCONCLUSIVE'),
('P1','p1_derived_mismatch','EXPECTED_NEGATIVE','QUERY_RESULT_PARITY','RECONCILIATION_INCONCLUSIVE'),
('P1','p1_null_value','BASELINE','NULL_SEMANTICS_PARITY','MATCH_EXACT'),
('P1','p1_empty_string','BASELINE','NULL_SEMANTICS_PARITY','MATCH_EXACT'),
('P1','p1_numeric_normalization','BASELINE','NUMERIC_NORMALIZATION_PARITY','MATCH_EXACT'),
('P1','p1_timezone_normalization','BASELINE','TIMEZONE_NORMALIZATION_PARITY','MATCH_EXACT'),
('P1','p1_deterministic_row_ordering','BASELINE','ROW_ORDER_PARITY','MATCH_EXACT'),
('P1','p1_duplicate_logical_row','EXPECTED_NEGATIVE','DUPLICATE_ROW_DETECTION','RECONCILIATION_INCONCLUSIVE'),
('P1','p1_row_count_mismatch','EXPECTED_NEGATIVE','SOURCE_ROW_COUNT_PARITY','RECONCILIATION_INCONCLUSIVE'),
('P1','p1_checkpoint_match','BASELINE','CHECKPOINT_PARITY','MATCH_EXACT'),
('P1','p1_checkpoint_mismatch','EXPECTED_NEGATIVE','CHECKPOINT_PARITY','SOURCE_CHECKPOINT_MISMATCH'),
('P1','p1_stale_checkpoint','EXPECTED_NEGATIVE','CHECKPOINT_PARITY','SOURCE_STALE'),
('P1','p1_lineage_match','BASELINE','QUERY_RESULT_PARITY','MATCH_EXACT'),
('P1','p1_lineage_mismatch','EXPECTED_NEGATIVE','QUERY_RESULT_PARITY','LINEAGE_MISMATCH'),
('P1','p1_snapshot_mismatch','EXPECTED_NEGATIVE','SNAPSHOT_ISOLATION_PARITY','RECONCILIATION_INCONCLUSIVE'),
('P1','p1_synthetic_identity_valid','BASELINE','QUERY_RESULT_PARITY','MATCH_EXACT'),
('P1','p1_identity_absent','EXPECTED_NEGATIVE','QUERY_RESULT_PARITY','IDENTITY_MAPPING_REQUIRED'),
('P1','p1_identity_invalid','EXPECTED_NEGATIVE','QUERY_RESULT_PARITY','IDENTITY_MAPPING_REQUIRED'),
('P1','p1_identity_expired','EXPECTED_NEGATIVE','QUERY_RESULT_PARITY','IDENTITY_MAPPING_REQUIRED'),
('P1','p1_identity_deleted','EXPECTED_NEGATIVE','QUERY_RESULT_PARITY','IDENTITY_MAPPING_REQUIRED'),
('P1','p1_identity_mismatched','EXPECTED_NEGATIVE','QUERY_RESULT_PARITY','IDENTITY_SCHEME_MISMATCH'),
('P1','p1_unauthorized_purpose','EXPECTED_NEGATIVE','QUERY_RESULT_PARITY','IDENTITY_MAPPING_REQUIRED'),
('P1','p1_unauthorized_caller','EXPECTED_NEGATIVE','QUERY_RESULT_PARITY','IDENTITY_MAPPING_REQUIRED'),
('P2','p2_valid_authoritative_exposure','BASELINE','EXPOSURE_ROW_UNIQUENESS','MATCH_EXACT'),
('P2','p2_assignment_version_variant_match','BASELINE','ASSIGNMENT_VERSION_JOIN_PARITY','MATCH_EXACT'),
('P2','p2_exact_subject_session_run_exposure_binding','BASELINE','QUERY_RESULT_PARITY','MATCH_EXACT'),
('P2','p2_lower_boundary_inclusive_event','BASELINE','WINDOW_BOUNDARY_SQL_PARITY','MATCH_EXACT'),
('P2','p2_upper_boundary_exclusive_event','BASELINE','WINDOW_BOUNDARY_SQL_PARITY','MATCH_EXACT'),
('P2','p2_click','BASELINE','EVENT_TYPE_FILTER_PARITY','MATCH_EXACT'),
('P2','p2_like','BASELINE','EVENT_TYPE_FILTER_PARITY','MATCH_EXACT'),
('P2','p2_save','BASELINE','EVENT_TYPE_FILTER_PARITY','MATCH_EXACT'),
('P2','p2_share','BASELINE','EVENT_TYPE_FILTER_PARITY','MATCH_EXACT'),
('P2','p2_combined_valid_engagement','BASELINE','EVENT_TYPE_FILTER_PARITY','MATCH_EXACT'),
('P2','p2_bound_fallback','BASELINE','FALLBACK_JOIN_PARITY','MATCH_EXACT'),
('P2','p2_general_exposure_contamination','EXPECTED_NEGATIVE','QUERY_RESULT_PARITY','EXPOSURE_AUTHORITY_MISMATCH'),
('P2','p2_behavior_impression_contamination','EXPECTED_NEGATIVE','EVENT_TYPE_FILTER_PARITY','EXPOSURE_AUTHORITY_MISMATCH'),
('P2','p2_view_contamination','EXPECTED_NEGATIVE','EVENT_TYPE_FILTER_PARITY','PROTECTED_AUTHORITY_DIFFERENCE'),
('P2','p2_hide_contamination','EXPECTED_NEGATIVE','EVENT_TYPE_FILTER_PARITY','PROTECTED_AUTHORITY_DIFFERENCE'),
('P2','p2_report_contamination','EXPECTED_NEGATIVE','EVENT_TYPE_FILTER_PARITY','PROTECTED_AUTHORITY_DIFFERENCE'),
('P2','p2_unbound_fallback','EXPECTED_NEGATIVE','FALLBACK_JOIN_PARITY','FALLBACK_BINDING_MISMATCH'),
('P2','p2_duplicate_exposure','EXPECTED_NEGATIVE','EXPOSURE_ROW_UNIQUENESS','RECONCILIATION_INCONCLUSIVE'),
('P2','p2_duplicate_outcome','EXPECTED_NEGATIVE','OUTCOME_ROW_UNIQUENESS','RECONCILIATION_INCONCLUSIVE'),
('P2','p2_duplicate_observation_key','EXPECTED_GAP','DUPLICATE_OBSERVATION_DETECTION','MIGRATION_REQUIRED'),
('P2','p2_assignment_mismatch','EXPECTED_NEGATIVE','ASSIGNMENT_VERSION_JOIN_PARITY','RECONCILIATION_INCONCLUSIVE'),
('P2','p2_version_mismatch','EXPECTED_NEGATIVE','ASSIGNMENT_VERSION_JOIN_PARITY','RECONCILIATION_INCONCLUSIVE'),
('P2','p2_subject_mismatch','EXPECTED_NEGATIVE','QUERY_RESULT_PARITY','IDENTITY_SCHEME_MISMATCH'),
('P2','p2_session_mismatch','EXPECTED_NEGATIVE','QUERY_RESULT_PARITY','RECONCILIATION_INCONCLUSIVE'),
('P2','p2_run_mismatch','EXPECTED_NEGATIVE','QUERY_RESULT_PARITY','RECONCILIATION_INCONCLUSIVE'),
('P2','p2_exposure_mismatch','EXPECTED_NEGATIVE','QUERY_RESULT_PARITY','EXPOSURE_AUTHORITY_MISMATCH'),
('P2','p2_checkpoint_mismatch','EXPECTED_NEGATIVE','CHECKPOINT_PARITY','SOURCE_CHECKPOINT_MISMATCH'),
('P2','p2_stale_checkpoint','EXPECTED_NEGATIVE','CHECKPOINT_PARITY','SOURCE_STALE'),
('P2','p2_lineage_mismatch','EXPECTED_NEGATIVE','QUERY_RESULT_PARITY','LINEAGE_MISMATCH'),
('P2','p2_stale_unexposed_assignment','EXPECTED_GAP','QUERY_RESULT_PARITY','MIGRATION_REQUIRED'),
('P2','p2_persisted_dedupe_migration_gap','EXPECTED_GAP','DUPLICATE_OBSERVATION_DETECTION','MIGRATION_REQUIRED'),
('P2','p2_identity_absent','EXPECTED_NEGATIVE','QUERY_RESULT_PARITY','IDENTITY_MAPPING_REQUIRED'),
('P2','p2_identity_invalid','EXPECTED_NEGATIVE','QUERY_RESULT_PARITY','IDENTITY_MAPPING_REQUIRED'),
('P2','p2_identity_expired','EXPECTED_NEGATIVE','QUERY_RESULT_PARITY','IDENTITY_MAPPING_REQUIRED'),
('P2','p2_identity_deleted','EXPECTED_NEGATIVE','QUERY_RESULT_PARITY','IDENTITY_MAPPING_REQUIRED'),
('P2','p2_identity_mismatched','EXPECTED_NEGATIVE','QUERY_RESULT_PARITY','IDENTITY_SCHEME_MISMATCH'),
('P2','p2_unauthorized_purpose','EXPECTED_NEGATIVE','QUERY_RESULT_PARITY','IDENTITY_MAPPING_REQUIRED'),
('P2','p2_unauthorized_caller','EXPECTED_NEGATIVE','QUERY_RESULT_PARITY','IDENTITY_MAPPING_REQUIRED')
ON CONFLICT DO NOTHING;
INSERT INTO rca1b_fixture.row_limit_probe(ordinal)
SELECT value FROM generate_series(1,1001) value
ON CONFLICT DO NOTHING;
INSERT INTO public.app_users(email,password_hash,username,display_name)
VALUES ('rca1b-fixture@example.invalid','$2a$10$Rca1bFixtureOnlyHashValue000000000000000000000000000','rca1b_fixture','RCA1B Fixture')
ON CONFLICT DO NOTHING;
INSERT INTO public.regions(name_local,name_ko,name_en,slug,region_type,country_code,timezone,sort_order)
VALUES ('RCA1B Fixture Country','RCA1B Fixture Country','RCA1B Fixture Country','rca1b-fixture-country','country','RZ','UTC',999)
ON CONFLICT (slug) DO NOTHING;
INSERT INTO public.regions(parent_id,name_local,name_ko,name_en,slug,region_type,country_code,timezone,sort_order)
SELECT id,'RCA1B Fixture City','RCA1B Fixture City','RCA1B Fixture City','rca1b-fixture-country-city','city','RZ','UTC',1
FROM public.regions WHERE slug='rca1b-fixture-country'
ON CONFLICT (slug) DO NOTHING;
DO $$
DECLARE
v_user bigint;
v_region bigint;
v_place bigint;
v_post bigint;
BEGIN
SELECT id INTO v_user FROM public.app_users WHERE email='rca1b-fixture@example.invalid';
SELECT id INTO v_region FROM public.regions WHERE slug='rca1b-fixture-country-city';
SELECT id INTO v_place FROM public.places WHERE name_local='RCA1B Fixture Place' AND region_id=v_region;
IF v_place IS NULL THEN
INSERT INTO public.places(region_id,name_local,name_ko,name_en,category,created_by_user_id)
VALUES(v_region,'RCA1B Fixture Place','RCA1B Fixture Place','RCA1B Fixture Place','test',v_user)
RETURNING id INTO v_place;
END IF;
SELECT id INTO v_post FROM public.posts WHERE author_id=v_user AND title='RCA1B Fixture Post';
IF v_post IS NULL THEN
INSERT INTO public.posts(author_id,main_region_id,title,content,visibility,status)
VALUES(v_user,v_region,'RCA1B Fixture Post','Synthetic non-production fixture','public','draft')
RETURNING id INTO v_post;
INSERT INTO public.post_places(post_id,place_id,sort_order) VALUES(v_post,v_place,0);
UPDATE public.posts SET status='published' WHERE id=v_post;
END IF;
END $$;
INSERT INTO public.recommendation_snapshot(snapshot_id,snapshot_kind,schema_version,canonicalization_version,content_hash,canonical_payload,payload_json,payload_size_bytes)
SELECT id,kind,version,'recommendation-canonical-json-v1',public.recommendation_snapshot_sha256_hex(kind,version,payload),payload,jsonb_build_object('fixture',id),octet_length(payload)
FROM (VALUES
('rca1b-ranking-snapshot','ranking_input_v1','ranking-input-v1',convert_to('{"fixture":"ranking"}','UTF8')),
('rca1b-diversity-snapshot','diversity_metadata_v1','diversity-metadata-v1',convert_to('{"fixture":"diversity"}','UTF8')),
('rca1b-exploration-snapshot','exploration_metadata_v1','exploration-metadata-v1',convert_to('{"fixture":"exploration"}','UTF8')),
('rca1b-result-snapshot','ranking_result_v1','ranking-result-v1',convert_to('{"fixture":"result"}','UTF8'))
) AS v(id,kind,version,payload)
ON CONFLICT DO NOTHING;
DO $$
DECLARE
v_user bigint;
v_post bigint;
BEGIN
SELECT id INTO v_user FROM public.app_users WHERE email='rca1b-fixture@example.invalid';
SELECT id INTO v_post FROM public.posts WHERE author_id=v_user AND title='RCA1B Fixture Post';
IF NOT EXISTS (SELECT 1 FROM public.recommendation_run WHERE run_id='rca1b-run-baseline') THEN
INSERT INTO public.recommendation_run(
run_id,request_id,run_mode,run_status,user_id,session_id,context_id,surface,reference_time,
ranking_snapshot_id,metadata_snapshot_id,exploration_snapshot_id,result_snapshot_id,
ranking_policy_version,base_integration_policy_version,base_ranking_policy_version,
score_policy_version,component_policy_versions,diversity_policy_version,exploration_policy_version,
exploration_seed,ranking_status,ranking_empty_reason,requested_limit,effective_limit,input_count,
scored_candidate_count,final_ranked_candidate_count,terminal_candidate_count,result_fingerprint,
core_build_id,duration_ms,fallback_reason)
VALUES(
'rca1b-run-baseline','rca1b-request-baseline','shadow','fallback',v_user,'rca1b-session','rca1b-context','home','2026-07-24T00:00:00Z',
'rca1b-ranking-snapshot','rca1b-diversity-snapshot','rca1b-exploration-snapshot','rca1b-result-snapshot',
'ranking-v3','ranking-integration-v3','ranking-v1','score-v1','{"fixture":"v1"}',
'diversity-v1','exploration-v1','rca1b-seed','ranked',NULL,1,1,1,1,1,0,
repeat('1',64),'rca1b-java-core',1,'fixture_fallback');
INSERT INTO public.recommendation_run_candidate(
run_id,absolute_rank,entity_type,entity_key,source_entity_id,origin,score,score_is_negative_zero,
base_absolute_rank,diversified_absolute_rank,score_policy_version,provenance)
VALUES('rca1b-run-baseline',1,'post','post:'||v_post,v_post,'personalized',0.75,false,1,1,'score-v1','{"fixture":true}');
END IF;
END $$;
DO $$
DECLARE v_user bigint;
BEGIN
SELECT id INTO v_user FROM public.app_users WHERE email='rca1b-fixture@example.invalid';
INSERT INTO public.recommendation_p1_profile_snapshot(
profile_snapshot_id,user_id,reference_time,profile_policy_version,feature_vocabulary_version,segment,
explicit_preference_count,input_event_count,accepted_event_count,ignored_event_count,duplicate_event_count,
accepted_behavior_weight,signal_count,signals,fingerprint)
VALUES('rca1b-p1-profile',v_user,'2026-07-24T00:00:00Z','profile-policy-v1','feature-vocabulary-v1','established',
0,8,8,0,0,1.25,0,'[]',repeat('2',64))
ON CONFLICT DO NOTHING;
INSERT INTO public.recommendation_p2_experiment_assignment(
assignment_id,experiment_id,experiment_version,subject_ref,user_id,assignment_unit,variant,bucket,
assignment_fingerprint,assigned_at,producer_build_id)
VALUES('rca1b-assignment','rca1b-experiment','v1','user:'||v_user,v_user,'user','baseline',1,repeat('3',64),
'2026-07-24T00:00:00Z','git:d07091bff54a3bfdae10d8fb6f3008923d69d455')
ON CONFLICT DO NOTHING;
INSERT INTO public.recommendation_p2_experiment_exposure(
exposure_id,assignment_id,run_id,user_id,session_id,variant,exposed_at,exposure_fingerprint)
VALUES('rca1b-exposure','rca1b-assignment','rca1b-run-baseline',v_user,'rca1b-session','baseline',
'2026-07-24T00:00:00Z',repeat('4',64))
ON CONFLICT DO NOTHING;
END $$;
DO $$
DECLARE
v_user bigint;
v_post bigint;
v_type text;
v_time timestamptz;
v_id text;
v_payload bytea;
BEGIN
SELECT id INTO v_user FROM public.app_users WHERE email='rca1b-fixture@example.invalid';
SELECT id INTO v_post FROM public.posts WHERE author_id=v_user AND title='RCA1B Fixture Post';
FOR v_type,v_time,v_id IN
SELECT * FROM (VALUES
('click','2026-07-24T00:00:00Z'::timestamptz,'rca1b-event-click-lower'),
('like','2026-07-25T00:00:00Z'::timestamptz,'rca1b-event-like'),
('save','2026-07-26T00:00:00Z'::timestamptz,'rca1b-event-save'),
('share','2026-07-27T00:00:00Z'::timestamptz,'rca1b-event-share'),
('click','2026-07-31T00:00:00Z'::timestamptz,'rca1b-event-click-upper'),
('impression','2026-07-24T01:00:00Z'::timestamptz,'rca1b-event-impression'),
('view','2026-07-24T02:00:00Z'::timestamptz,'rca1b-event-view'),
('hide','2026-07-24T03:00:00Z'::timestamptz,'rca1b-event-hide'),
('report','2026-07-24T04:00:00Z'::timestamptz,'rca1b-event-report')
) AS e(event_type,occurred_at,event_id)
LOOP
v_payload := convert_to('{"event":"'||v_type||'"}','UTF8');
INSERT INTO public.recommendation_behavior_event(
event_id,idempotency_key,schema_version,payload_fingerprint,canonical_payload,payload_size_bytes,
user_id,session_id,run_id,event_type,entity_type,entity_key,source_entity_id,occurred_at,metadata)
VALUES(v_id,'idem-'||v_id,'recommendation-behavior-event-v1',public.recommendation_sha256_hex(v_payload),v_payload,octet_length(v_payload),
v_user,'rca1b-session','rca1b-run-baseline',v_type,'post','post:'||v_post,v_post,v_time,'{"fixture":true}')
ON CONFLICT DO NOTHING;
END LOOP;
END $$;
INSERT INTO public.data_source_checkpoint_v1(
checkpoint_id,checkpoint_ref,logical_identity_hash,source_stream,source_contract_version,source_schema_version,
event_time_from,event_time_to,ingested_at_upper_bound,last_source_event_ref,source_event_count,source_members,
source_set_fingerprint,checkpoint_definition_fingerprint,expires_at,created_at)
VALUES(
'11111111-1111-1111-1111-111111111111','checkpoint:rca1b:baseline',repeat('5',64),'rca1b-synthetic-stream',
'rca1b-source-contract-v1','rca1b-source-schema-v1','2026-07-17T00:00:00Z','2026-07-24T00:00:00Z','2026-07-24T00:00:00Z',
'rca1b-event-share',8,'["e1","e2","e3","e4","e5","e6","e7","e8"]',repeat('6',64),repeat('7',64),
'2027-07-24T00:00:00Z','2026-07-24T00:00:00Z')
ON CONFLICT DO NOTHING;
INSERT INTO public.data_projection_run_v1(
projection_run_id,projection_run_ref,logical_identity_hash,projection_name,projection_schema_version,
projection_policy_version,feature_policy_version,source_contract_version,source_checkpoint_ref,source_from,
source_to,projection_as_of,identity_binding_version,identity_binding_source,identity_binding_fingerprint,
identity_binding_scope,target_contract_version,producer_build_id,expires_at,created_at)
VALUES(
'22222222-2222-2222-2222-222222222222','projection_run:rca1b:baseline',repeat('8',64),
'recommendation-profile-input-v1','recommendation-profile-input-v1','recommendation-profile-projection-policy-v1',
'recommendation-feature-policy-v1','rca1b-source-contract-v1','11111111-1111-1111-1111-111111111111',
'2026-07-17T00:00:00Z','2026-07-24T00:00:00Z','2026-07-24T00:00:00Z','synthetic-identity-v1',
'rca1b-fixture','aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa','rca1b-nonproduction',
'recommendation-profile-input-v1','git:d07091bff54a3bfdae10d8fb6f3008923d69d455','2027-07-24T00:00:00Z','2026-07-24T00:00:00Z')
ON CONFLICT DO NOTHING;
INSERT INTO public.data_projection_snapshot_v1(
snapshot_id,snapshot_ref,projection_run_ref,projection_name,projection_schema_version,projection_policy_version,
source_checkpoint_ref,snapshot_as_of,record_count,subject_count,source_event_count,content_fingerprint,
lineage_fingerprint,snapshot_status,expires_at,created_at)
VALUES(
'33333333-3333-3333-3333-333333333333','snapshot:rca1b:baseline','22222222-2222-2222-2222-222222222222',
'recommendation-profile-input-v1','recommendation-profile-input-v1','recommendation-profile-projection-policy-v1',
'11111111-1111-1111-1111-111111111111','2026-07-24T00:00:00Z',4,1,8,repeat('b',64),repeat('c',64),'validated',
'2027-07-24T00:00:00Z','2026-07-24T00:00:00Z')
ON CONFLICT DO NOTHING;
INSERT INTO public.data_recommendation_profile_input_projection_v1(
profile_projection_id,snapshot_ref,projection_record_ref,projection_subject_ref,projection_as_of,source_checkpoint_ref,
profile_schema_version,projection_policy_version,activity_window_days,interaction_counts,recent_regions,recent_content_refs,
recent_tag_refs,engagement_signals,negative_signals,source_event_count,source_lineage_fingerprint,
projection_record_fingerprint,expires_at,created_at)
SELECT gen_random_uuid(),'33333333-3333-3333-3333-333333333333','profile_record:rca1b:'||window_days,
'subject:rca1b-user-1','2026-07-24T00:00:00Z','11111111-1111-1111-1111-111111111111',
'recommendation-profile-input-v1','recommendation-profile-projection-policy-v1',window_days,
'{"segment":"established","total":"8"}', '[]','[]','[]',
'{"acceptedBehaviorWeight":"1.25"}','{}',8,repeat('c',64),
CASE window_days WHEN 7 THEN repeat('d',64) WHEN 30 THEN repeat('e',64) ELSE repeat('f',64) END,
'2027-07-24T00:00:00Z','2026-07-24T00:00:00Z'
FROM (VALUES(7),(30),(90)) w(window_days)
ON CONFLICT DO NOTHING;
DO $$
DECLARE v_user bigint;
BEGIN
SELECT id INTO v_user FROM public.app_users WHERE email='rca1b-fixture@example.invalid';
INSERT INTO public.data_experiment_outcome_input_projection_v1(
outcome_projection_id,snapshot_ref,projection_record_ref,experiment_ref,experiment_version,variant_ref,exposure_ref,
run_ref,source_user_ref,subject_ref,session_ref,exposed_at,outcome_window_seconds,clicked,liked,saved,shared,
fallback_observed,outcome_event_refs,source_checkpoint_ref,source_event_count,source_lineage_fingerprint,
projection_record_fingerprint,expires_at,created_at)
VALUES(gen_random_uuid(),'33333333-3333-3333-3333-333333333333','outcome_record:rca1b:baseline',
'experiment:rca1b-experiment','v1','baseline','rca1b-exposure','rca1b-run-baseline','user:'||v_user,
'subject:rca1b-user-1','session:rca1b-session','2026-07-24T00:00:00Z',604800,true,true,true,true,true,
'["click","like","save","share"]','11111111-1111-1111-1111-111111111111',8,repeat('c',64),repeat('9',64),
'2027-07-24T00:00:00Z','2026-07-24T00:00:00Z')
ON CONFLICT DO NOTHING;
END $$;
INSERT INTO public.data_projection_lineage_v1(
lineage_id,snapshot_ref,projection_record_ref,source_kind,source_event_ref,source_fingerprint,
source_checkpoint_ref,projection_policy_version,lineage_entry_fingerprint,expires_at,created_at)
SELECT gen_random_uuid(),'33333333-3333-3333-3333-333333333333',record_ref,source_kind,event_ref,source_fp,
'11111111-1111-1111-1111-111111111111','recommendation-profile-projection-policy-v1',lineage_fp,
'2027-07-24T00:00:00Z','2026-07-24T00:00:00Z'
FROM (VALUES
('profile_record:rca1b:7','canonical_event','rca1b-event-click-lower',repeat('1',64),repeat('a',64)),
('profile_record:rca1b:30','canonical_event','rca1b-event-like',repeat('2',64),repeat('b',64)),
('profile_record:rca1b:90','canonical_event','rca1b-event-save',repeat('3',64),repeat('c',64)),
('outcome_record:rca1b:baseline','p2_exposure','rca1b-exposure',repeat('4',64),repeat('d',64))
) v(record_ref,source_kind,event_ref,source_fp,lineage_fp)
ON CONFLICT DO NOTHING;
INSERT INTO rca1b_fixture.p1_case_map(
case_id,profile_snapshot_id,snapshot_ref,subject_ref,checkpoint_ref,checkpoint_sequence,checkpoint_at,snapshot_at,lineage_fingerprint)
VALUES('p1-baseline','rca1b-p1-profile','33333333-3333-3333-3333-333333333333','subject:rca1b-user-1',
'checkpoint:rca1b:baseline',8,'2026-07-24T00:00:00Z','2026-07-24T00:00:00Z',repeat('c',64))
ON CONFLICT DO NOTHING;
INSERT INTO rca1b_fixture.p2_case_map(
case_id,assignment_id,exposure_id,snapshot_ref,synthetic_subject_ref,checkpoint_ref,checkpoint_sequence,checkpoint_at,snapshot_at,lineage_fingerprint)
VALUES('p2-baseline','rca1b-assignment','rca1b-exposure','33333333-3333-3333-3333-333333333333','subject:rca1b-user-1',
'checkpoint:rca1b:baseline',8,'2026-07-24T00:00:00Z','2026-07-24T00:00:00Z',repeat('c',64))
ON CONFLICT DO NOTHING;
DO $$
BEGIN
BEGIN
INSERT INTO public.recommendation_p2_experiment_exposure(
exposure_id,assignment_id,run_id,user_id,session_id,variant,exposed_at,exposure_fingerprint)
SELECT 'rca1b-exposure-duplicate','rca1b-assignment','rca1b-run-baseline',user_id,'rca1b-session','baseline',
'2026-07-24T00:00:01Z',repeat('a',64)
FROM public.app_users WHERE email='rca1b-fixture@example.invalid';
RAISE EXCEPTION 'duplicate exposure was not blocked';
EXCEPTION WHEN unique_violation THEN
INSERT INTO rca1b_fixture.seed_assertion VALUES('duplicate_exposure_blocked','BLOCKED','23505') ON CONFLICT DO NOTHING;
END;
BEGIN
INSERT INTO public.data_experiment_outcome_input_projection_v1
SELECT gen_random_uuid(),snapshot_ref,'outcome_record:rca1b:duplicate',experiment_ref,experiment_version,variant_ref,
exposure_ref,run_ref,source_user_ref,subject_ref,session_ref,exposed_at,outcome_window_seconds,clicked,liked,saved,shared,
fallback_observed,outcome_event_refs,source_checkpoint_ref,source_event_count,source_lineage_fingerprint,repeat('8',64),
retention_class,retention_policy_version,expires_at,created_at
FROM public.data_experiment_outcome_input_projection_v1
WHERE projection_record_ref='outcome_record:rca1b:baseline';
RAISE EXCEPTION 'duplicate outcome was not blocked';
EXCEPTION WHEN unique_violation THEN
INSERT INTO rca1b_fixture.seed_assertion VALUES('duplicate_outcome_blocked','BLOCKED','23505') ON CONFLICT DO NOTHING;
END;
BEGIN
INSERT INTO public.data_recommendation_profile_input_projection_v1
SELECT gen_random_uuid(),snapshot_ref,'profile_record:rca1b:duplicate',projection_subject_ref,projection_as_of,source_checkpoint_ref,
profile_schema_version,projection_policy_version,activity_window_days,interaction_counts,recent_regions,recent_content_refs,
recent_tag_refs,engagement_signals,negative_signals,source_event_count,source_lineage_fingerprint,repeat('7',64),
retention_class,retention_policy_version,expires_at,created_at
FROM public.data_recommendation_profile_input_projection_v1
WHERE projection_record_ref='profile_record:rca1b:7';
RAISE EXCEPTION 'duplicate P1 row was not blocked';
EXCEPTION WHEN unique_violation THEN
INSERT INTO rca1b_fixture.seed_assertion VALUES('duplicate_p1_blocked','BLOCKED','23505') ON CONFLICT DO NOTHING;
END;
END $$;
COMMIT;
