#!/usr/bin/env bash
set -euo pipefail
: "${DATABASE_URL:?DATABASE_URL is required}"
work="$(mktemp -d)"
trap 'rm -rf "$work"' EXIT

psql "$DATABASE_URL" -v ON_ERROR_STOP=1 <<'SQL'
INSERT INTO public.data_platform_event_v1(
  event_id,contract_version,schema_version,canonicalization_version,fingerprint_version,payload_fingerprint,
  fingerprint_canonical_bytes,producer_version,producer_build_id,event_family,event_type,occurred_at,received_at,
  actor_ref,session_ref,entity_ref,idempotency_key,canonical_payload,expires_at)
SELECT 'event:dp5-concurrency','platform-event-v1','user-behavior-event-v1','platform-event-canonical-json-v1',
  'platform-event-fingerprint-sha256-v1',encode(public.digest(convert_to('{"concurrency":1}','UTF8'),'sha256'),'hex'),
  convert_to('{"concurrency":1}','UTF8'),'dp5-producer-v1','git:'||repeat('1',40),'user_behavior','post_like',
  '2026-07-20T00:00:00Z','2026-07-20T00:00:01Z','subject:dp5-concurrency','session:dp5-concurrency',
  'post:1','dp5-concurrency','{}','2027-07-20T00:00:01Z'
WHERE NOT EXISTS (SELECT 1 FROM public.data_platform_event_v1 WHERE event_id='event:dp5-concurrency');

DO $$
DECLARE m jsonb; sf varchar(64); df varchar(64); d varchar;
BEGIN
  m:=jsonb_build_array(jsonb_build_object('sourceKind','canonical_event','sourceEventRef','event:dp5-concurrency',
    'sourceFingerprint',(SELECT payload_fingerprint FROM public.data_platform_event_v1 WHERE event_id='event:dp5-concurrency'),
    'adapterEvidenceRef',NULL,'occurredAt','2026-07-20T00:00:00Z','ingestedAt','2026-07-20T00:00:01Z'));
  SELECT public.data_projection_fingerprint_v1('data-source-set-sha256-v1',jsonb_build_object('sources',jsonb_agg(
    jsonb_build_object('sourceEventRef',x->>'sourceEventRef','sourceFingerprint',x->>'sourceFingerprint',
      'adapterEvidenceRef',x->'adapterEvidenceRef','occurredAt',x->>'occurredAt','ingestedAt',x->>'ingestedAt')
    ORDER BY x->>'sourceEventRef',x->>'sourceFingerprint',COALESCE(x->>'adapterEvidenceRef','')))) INTO sf
  FROM jsonb_array_elements(m)x;
  df:=public.data_projection_fingerprint_v1('data-checkpoint-definition-sha256-v1',jsonb_build_object(
    'sourceStream','data-platform-event-v1','sourceContractVersion','platform-event-v1','sourceSchemaVersion','user-behavior-event-v1',
    'eventTimeFrom','2026-07-19T00:00:00Z','eventTimeTo','2026-07-22T00:00:00Z','ingestedAtUpperBound','2026-07-22T00:00:00Z',
    'sourceEventCount',1,'sourceSetFingerprint',sf));
  SELECT disposition INTO d FROM public.persist_data_source_checkpoint_v1('checkpoint:dp5-concurrency','data-platform-event-v1',
    'platform-event-v1','user-behavior-event-v1','2026-07-19T00:00:00Z','2026-07-22T00:00:00Z',
    '2026-07-22T00:00:00Z','event:dp5-concurrency',m,sf,df);
  IF d NOT IN ('NEW','DUPLICATE') THEN RAISE EXCEPTION 'checkpoint setup failed'; END IF;
END$$;

CREATE OR REPLACE FUNCTION public.dp5_concurrency_attempt(p_suffix text)
RETURNS varchar LANGUAGE plpgsql SECURITY INVOKER SET search_path=pg_catalog,public,pg_temp AS $$
DECLARE e jsonb; l jsonb; lf varchar(64); r jsonb; rf varchar(64); rs jsonb; sf varchar(64); d varchar;
BEGIN
 e:=jsonb_build_object('projectionRecordRef','profile_record:dp5-concurrency','sourceEventRef','event:dp5-concurrency',
   'sourceFingerprint',(SELECT payload_fingerprint FROM public.data_platform_event_v1 WHERE event_id='event:dp5-concurrency'),
   'adapterEvidenceRef',NULL,'sourceCheckpointRef','checkpoint:dp5-concurrency',
   'projectionPolicyVersion','recommendation-profile-projection-policy-v1','mappingPolicyVersion',NULL);
 e:=e||jsonb_build_object('sourceKind','canonical_event','lineageEntryFingerprint',
   public.data_projection_fingerprint_v1('data-projection-lineage-entry-sha256-v1',e));
 l:=jsonb_build_array(e);
 lf:=public.data_projection_fingerprint_v1('data-projection-lineage-sha256-v1',jsonb_build_object('entries',jsonb_build_array(e->>'lineageEntryFingerprint')));
 r:=jsonb_build_object('recordRef','profile_record:dp5-concurrency','projectionName','recommendation-profile-input-v1',
   'subjectRef','subject:dp5-concurrency','projectionAsOf','2026-07-22T00:00:00Z','sourceCheckpointRef','checkpoint:dp5-concurrency',
   'profileSchemaVersion','recommendation-profile-input-v1','projectionPolicyVersion','recommendation-profile-projection-policy-v1',
   'activityWindowDays',7,'interactionCounts',jsonb_build_object('post_like',1),'recentRegions','[]'::jsonb,
   'recentContentRefs','[]'::jsonb,'recentTagRefs','[]'::jsonb,'engagementSignals',jsonb_build_object('post_like',1),
   'negativeSignals','{}'::jsonb,'sourceEventCount',1,'sourceLineageFingerprint',lf);
 rf:=public.data_projection_fingerprint_v1('data-projection-record-sha256-v1',r-'recordRef');
 r:=r||jsonb_build_object('projectionRecordFingerprint',rf); rs:=jsonb_build_array(r);
 sf:=public.data_projection_fingerprint_v1('data-projection-snapshot-sha256-v1',jsonb_build_object(
   'projectionName','recommendation-profile-input-v1','projectionSchemaVersion','recommendation-profile-input-v1',
   'projectionPolicyVersion','recommendation-profile-projection-policy-v1','featurePolicyVersion','recommendation-profile-feature-policy-v1',
   'identityBindingVersion','recommendation-user-subject-binding-v1','targetContractVersion','recommendation-profile-input-v1',
   'sourceCheckpointRef','checkpoint:dp5-concurrency','snapshotAsOf','2026-07-22T00:00:00Z','recordFingerprints',jsonb_build_array(rf)));
 SELECT disposition INTO d FROM public.persist_data_projection_snapshot_v1(
   'projection_run:dp5-concurrency-'||p_suffix,'snapshot:dp5-concurrency-'||p_suffix,'recommendation-profile-input-v1',
   'recommendation-profile-input-v1','recommendation-profile-projection-policy-v1','recommendation-profile-feature-policy-v1',
   'checkpoint:dp5-concurrency','2026-07-22T00:00:00Z','recommendation-user-subject-binding-v1','approved-binding-input',
   repeat('c',64),'journey-connect','recommendation-profile-input-v1','git:'||repeat('1',40),rs,l,sf,lf);
 RETURN d;
END$$;
SQL

(psql "$DATABASE_URL" -At -v ON_ERROR_STOP=1 -c "SELECT public.dp5_concurrency_attempt('a');" >"$work/a" 2>&1) & p1=$!
(psql "$DATABASE_URL" -At -v ON_ERROR_STOP=1 -c "SELECT public.dp5_concurrency_attempt('b');" >"$work/b" 2>&1) & p2=$!
wait "$p1"; wait "$p2"
cat "$work/a" "$work/b"
new_count=$(grep -h '^NEW$' "$work/a" "$work/b" | wc -l | tr -d ' ')
dup_count=$(grep -h '^DUPLICATE$' "$work/a" "$work/b" | wc -l | tr -d ' ')
[[ "$new_count" == 1 && "$dup_count" == 1 ]]
counts=$(psql "$DATABASE_URL" -At -v ON_ERROR_STOP=1 -c "SELECT count(*)||':'||(SELECT count(*) FROM public.data_projection_snapshot_v1 s JOIN public.data_projection_run_v1 r ON r.projection_run_id=s.projection_run_ref WHERE r.logical_identity_hash=(SELECT logical_identity_hash FROM public.data_projection_run_v1 WHERE projection_run_ref LIKE 'projection_run:dp5-concurrency-%' LIMIT 1)) FROM public.data_projection_run_v1 WHERE projection_run_ref LIKE 'projection_run:dp5-concurrency-%';")
echo "DP-5 concurrency persisted counts: $counts"
[[ "$counts" == "1:1" ]]
psql "$DATABASE_URL" -v ON_ERROR_STOP=1 -c "DROP FUNCTION public.dp5_concurrency_attempt(text);" >/dev/null
echo "DP-5 concurrent same identity: exactly one NEW and one DUPLICATE: PASS"
