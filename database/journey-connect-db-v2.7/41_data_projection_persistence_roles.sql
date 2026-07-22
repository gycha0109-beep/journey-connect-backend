-- Journey Connect DB v2.7 extension - DP-5 atomic persistence, roles and safe access
-- Target: PostgreSQL 15 and 18. Prerequisite: SQL 01..40.
BEGIN;

DO $$
BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'jc_data_projection_writer') THEN
    CREATE ROLE jc_data_projection_writer NOLOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOREPLICATION NOBYPASSRLS;
  END IF;
  IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'jc_data_projection_reader') THEN
    CREATE ROLE jc_data_projection_reader NOLOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOREPLICATION NOBYPASSRLS;
  END IF;
  IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'jc_data_projection_function_owner') THEN
    CREATE ROLE jc_data_projection_function_owner NOLOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOREPLICATION NOBYPASSRLS;
  END IF;
END;
$$;

DO $$
DECLARE v_unsafe text;
BEGIN
  SELECT string_agg(rolname, ', ' ORDER BY rolname) INTO v_unsafe
  FROM pg_roles
  WHERE rolname IN ('jc_data_projection_writer','jc_data_projection_reader','jc_data_projection_function_owner')
    AND (rolsuper OR rolcreaterole OR rolcreatedb OR rolcanlogin OR rolreplication OR rolbypassrls);
  IF v_unsafe IS NOT NULL THEN
    RAISE EXCEPTION 'Unsafe DP-5 role attributes: %', v_unsafe;
  END IF;
END;
$$;

CREATE OR REPLACE FUNCTION public.persist_data_source_checkpoint_v1(
  p_checkpoint_ref varchar,
  p_source_stream varchar,
  p_source_contract_version varchar,
  p_source_schema_version varchar,
  p_event_time_from timestamptz,
  p_event_time_to timestamptz,
  p_ingested_at_upper_bound timestamptz,
  p_last_source_event_ref varchar,
  p_source_members jsonb,
  p_source_set_fingerprint varchar,
  p_checkpoint_definition_fingerprint varchar
)
RETURNS TABLE(disposition varchar, checkpoint_id uuid, error_code varchar)
LANGUAGE plpgsql SECURITY DEFINER
SET search_path = pg_catalog, public, pg_temp
AS $$
DECLARE
  v_now timestamptz := clock_timestamp();
  v_logical_hash varchar(64);
  v_actual_source_set varchar(64);
  v_actual_definition varchar(64);
  v_existing public.data_source_checkpoint_v1%ROWTYPE;
  v_member jsonb;
  v_count bigint;
  v_id uuid;
BEGIN
  IF p_checkpoint_ref IS NULL OR p_checkpoint_ref !~ '^checkpoint:[A-Za-z0-9][A-Za-z0-9._:~-]{0,149}$'
     OR p_source_stream IS NULL OR p_source_stream !~ '^[A-Za-z0-9][A-Za-z0-9._:-]{0,95}$'
     OR NOT public.data_projection_version_valid_v1(p_source_contract_version)
     OR NOT public.data_projection_version_valid_v1(p_source_schema_version)
     OR p_event_time_from IS NULL OR p_event_time_to IS NULL OR p_event_time_from >= p_event_time_to
     OR p_ingested_at_upper_bound IS NULL OR p_ingested_at_upper_bound < p_event_time_from
     OR p_source_members IS NULL OR jsonb_typeof(p_source_members) <> 'array'
     OR jsonb_array_length(p_source_members) = 0
     OR NOT public.data_projection_fingerprint_valid_v1(p_source_set_fingerprint)
     OR NOT public.data_projection_fingerprint_valid_v1(p_checkpoint_definition_fingerprint) THEN
    RAISE EXCEPTION 'Invalid DP-5 checkpoint input.' USING ERRCODE = '22023';
  END IF;

  SELECT count(*) INTO v_count FROM jsonb_array_elements(p_source_members);
  IF v_count <> (SELECT count(DISTINCT (m->>'sourceEventRef', m->>'sourceFingerprint', COALESCE(m->>'adapterEvidenceRef',''))) FROM jsonb_array_elements(p_source_members) m) THEN
    RAISE EXCEPTION 'Duplicate checkpoint source member.' USING ERRCODE = '23505';
  END IF;

  FOR v_member IN SELECT value FROM jsonb_array_elements(p_source_members) LOOP
    IF v_member->>'sourceKind' NOT IN ('canonical_event','adapter_output','p2_exposure')
       OR v_member->>'sourceEventRef' IS NULL
       OR NOT public.data_projection_fingerprint_valid_v1(v_member->>'sourceFingerprint')
       OR (v_member->>'occurredAt')::timestamptz < p_event_time_from
       OR (v_member->>'occurredAt')::timestamptz >= p_event_time_to
       OR (v_member->>'ingestedAt')::timestamptz > p_ingested_at_upper_bound THEN
      RAISE EXCEPTION 'Invalid checkpoint source member.' USING ERRCODE = '22023';
    END IF;
    IF v_member->>'sourceKind' = 'canonical_event' THEN
      IF NOT EXISTS (SELECT 1 FROM public.data_platform_event_v1 e
                     WHERE e.event_id = v_member->>'sourceEventRef'
                       AND e.payload_fingerprint = v_member->>'sourceFingerprint') THEN
        RAISE EXCEPTION 'Canonical source event missing or fingerprint mismatch.' USING ERRCODE = '23503';
      END IF;
    ELSIF v_member->>'sourceKind' = 'adapter_output' THEN
      IF v_member->>'adapterEvidenceRef' IS NULL OR NOT EXISTS (
        SELECT 1 FROM public.data_recommendation_adapter_output_v1 o
        WHERE o.adapter_output_id = (v_member->>'adapterEvidenceRef')::uuid
          AND o.source_event_ref = v_member->>'sourceEventRef'
          AND o.source_fingerprint = v_member->>'sourceFingerprint'
          AND o.mapping_status = 'mapped_shadow') THEN
        RAISE EXCEPTION 'Adapter output evidence missing, rejected or conflicted.' USING ERRCODE = '23503';
      END IF;
    ELSE
      IF NOT EXISTS (SELECT 1 FROM public.recommendation_p2_experiment_exposure x
                     WHERE 'p2_exposure:' || x.exposure_id = v_member->>'sourceEventRef'
                       AND x.exposure_fingerprint = v_member->>'sourceFingerprint') THEN
        RAISE EXCEPTION 'P2 exposure source missing or fingerprint mismatch.' USING ERRCODE = '23503';
      END IF;
    END IF;
  END LOOP;

  IF p_last_source_event_ref <> (SELECT m->>'sourceEventRef'
      FROM jsonb_array_elements(p_source_members) m
      ORDER BY (m->>'occurredAt')::timestamptz DESC, m->>'sourceEventRef' DESC,
               m->>'sourceFingerprint' DESC LIMIT 1) THEN
    RAISE EXCEPTION 'Last source event does not match stable checkpoint order.' USING ERRCODE = '22023';
  END IF;

  SELECT public.data_projection_fingerprint_v1('data-source-set-sha256-v1',
    jsonb_build_object('sources', jsonb_agg(jsonb_build_object(
      'sourceEventRef', m->>'sourceEventRef',
      'sourceFingerprint', m->>'sourceFingerprint',
      'adapterEvidenceRef', m->'adapterEvidenceRef',
      'occurredAt', m->>'occurredAt',
      'ingestedAt', m->>'ingestedAt')
      ORDER BY m->>'sourceEventRef', m->>'sourceFingerprint', COALESCE(m->>'adapterEvidenceRef',''))))
    INTO v_actual_source_set
  FROM jsonb_array_elements(p_source_members) m;
  IF v_actual_source_set <> p_source_set_fingerprint THEN
    RAISE EXCEPTION 'Source set fingerprint mismatch.' USING ERRCODE = '22023';
  END IF;

  v_actual_definition := public.data_projection_fingerprint_v1('data-checkpoint-definition-sha256-v1',
    jsonb_build_object(
      'sourceStream', p_source_stream,
      'sourceContractVersion', p_source_contract_version,
      'sourceSchemaVersion', p_source_schema_version,
      'eventTimeFrom', public.data_projection_instant_text_v1(p_event_time_from),
      'eventTimeTo', public.data_projection_instant_text_v1(p_event_time_to),
      'ingestedAtUpperBound', public.data_projection_instant_text_v1(p_ingested_at_upper_bound),
      'sourceEventCount', v_count,
      'sourceSetFingerprint', p_source_set_fingerprint));
  IF v_actual_definition <> p_checkpoint_definition_fingerprint THEN
    RAISE EXCEPTION 'Checkpoint definition fingerprint mismatch.' USING ERRCODE = '22023';
  END IF;

  v_logical_hash := public.data_projection_fingerprint_v1('data-checkpoint-logical-identity-v1',
    jsonb_build_object('sourceStream',p_source_stream,'sourceContractVersion',p_source_contract_version,
      'sourceSchemaVersion',p_source_schema_version,
      'eventTimeFrom',public.data_projection_instant_text_v1(p_event_time_from),
      'eventTimeTo',public.data_projection_instant_text_v1(p_event_time_to),
      'ingestedAtUpperBound',public.data_projection_instant_text_v1(p_ingested_at_upper_bound)));
  PERFORM pg_advisory_xact_lock(hashtextextended(v_logical_hash, 0));
  SELECT * INTO v_existing FROM public.data_source_checkpoint_v1 WHERE logical_identity_hash = v_logical_hash;
  IF FOUND THEN
    IF v_existing.checkpoint_definition_fingerprint = p_checkpoint_definition_fingerprint THEN
      RETURN QUERY SELECT 'DUPLICATE'::varchar, v_existing.checkpoint_id, NULL::varchar;
      RETURN;
    END IF;
    INSERT INTO public.data_projection_conflict_observation_v1(
      conflict_kind,logical_identity_hash,existing_evidence_ref,existing_fingerprint,
      attempted_fingerprint,failure_code,expires_at)
    VALUES ('checkpoint',v_logical_hash,v_existing.checkpoint_ref,
      v_existing.checkpoint_definition_fingerprint,p_checkpoint_definition_fingerprint,
      'source_checkpoint_invalid',v_now + interval '90 days');
    RETURN QUERY SELECT 'CONFLICT'::varchar, v_existing.checkpoint_id, 'source_checkpoint_invalid'::varchar;
    RETURN;
  END IF;

  INSERT INTO public.data_source_checkpoint_v1(
    checkpoint_ref,logical_identity_hash,source_stream,source_contract_version,source_schema_version,
    event_time_from,event_time_to,ingested_at_upper_bound,last_source_event_ref,source_event_count,
    source_members,source_set_fingerprint,checkpoint_definition_fingerprint,expires_at)
  VALUES (p_checkpoint_ref,v_logical_hash,p_source_stream,p_source_contract_version,p_source_schema_version,
    p_event_time_from,p_event_time_to,p_ingested_at_upper_bound,p_last_source_event_ref,v_count,
    p_source_members,p_source_set_fingerprint,p_checkpoint_definition_fingerprint,v_now + interval '90 days')
  RETURNING data_source_checkpoint_v1.checkpoint_id INTO v_id;
  RETURN QUERY SELECT 'NEW'::varchar, v_id, NULL::varchar;
END;
$$;

CREATE OR REPLACE FUNCTION public.persist_data_projection_snapshot_v1(
  p_projection_run_ref varchar,
  p_snapshot_ref varchar,
  p_projection_name varchar,
  p_projection_schema_version varchar,
  p_projection_policy_version varchar,
  p_feature_policy_version varchar,
  p_source_checkpoint_ref varchar,
  p_projection_as_of timestamptz,
  p_identity_binding_version varchar,
  p_identity_binding_source varchar,
  p_identity_binding_fingerprint varchar,
  p_identity_binding_scope varchar,
  p_target_contract_version varchar,
  p_producer_build_id varchar,
  p_records jsonb,
  p_lineage jsonb,
  p_snapshot_content_fingerprint varchar,
  p_lineage_fingerprint varchar
)
RETURNS TABLE(disposition varchar, projection_run_id uuid, snapshot_id uuid, error_code varchar)
LANGUAGE plpgsql SECURITY DEFINER
SET search_path = pg_catalog, public, pg_temp
AS $$
DECLARE
  v_now timestamptz := clock_timestamp();
  v_checkpoint public.data_source_checkpoint_v1%ROWTYPE;
  v_logical_hash varchar(64);
  v_actual_content varchar(64);
  v_actual_lineage varchar(64);
  v_existing_run public.data_projection_run_v1%ROWTYPE;
  v_existing_snapshot public.data_projection_snapshot_v1%ROWTYPE;
  v_run_id uuid;
  v_snapshot_id uuid;
  v_record jsonb;
  v_entry jsonb;
  v_record_count bigint;
  v_subject_count bigint;
  v_source_count bigint;
  v_validation_id uuid;
  v_source_member jsonb;
  v_exposure public.recommendation_p2_experiment_exposure%ROWTYPE;
  v_assignment public.recommendation_p2_experiment_assignment%ROWTYPE;
  v_recommendation_run public.recommendation_run%ROWTYPE;
BEGIN
  SELECT * INTO v_checkpoint FROM public.data_source_checkpoint_v1 WHERE checkpoint_ref = p_source_checkpoint_ref;
  IF NOT FOUND THEN RAISE EXCEPTION 'Source checkpoint missing.' USING ERRCODE = '23503'; END IF;
  IF p_projection_name NOT IN ('recommendation-profile-input-v1','experiment-outcome-input-v1')
     OR p_projection_schema_version <> p_projection_name
     OR NOT public.data_projection_version_valid_v1(p_projection_policy_version)
     OR NOT public.data_projection_version_valid_v1(p_feature_policy_version)
     OR NOT public.data_projection_version_valid_v1(p_identity_binding_version)
     OR NOT public.data_projection_fingerprint_valid_v1(p_identity_binding_fingerprint)
     OR p_target_contract_version <> p_projection_name
     OR p_producer_build_id !~ '^git:[0-9a-f]{40}$'
     OR p_records IS NULL OR jsonb_typeof(p_records) <> 'array' OR jsonb_array_length(p_records) = 0
     OR p_lineage IS NULL OR jsonb_typeof(p_lineage) <> 'array' OR jsonb_array_length(p_lineage) = 0
     OR NOT public.data_projection_fingerprint_valid_v1(p_snapshot_content_fingerprint)
     OR NOT public.data_projection_fingerprint_valid_v1(p_lineage_fingerprint) THEN
    RAISE EXCEPTION 'Invalid DP-5 projection snapshot input.' USING ERRCODE = '22023';
  END IF;

  SELECT public.data_projection_fingerprint_v1('data-projection-lineage-sha256-v1',
      jsonb_build_object('entries', jsonb_agg(e->>'lineageEntryFingerprint' ORDER BY e->>'lineageEntryFingerprint')))
    INTO v_actual_lineage FROM jsonb_array_elements(p_lineage) e;
  IF v_actual_lineage <> p_lineage_fingerprint THEN
    RAISE EXCEPTION 'Lineage fingerprint mismatch.' USING ERRCODE = '22023';
  END IF;

  FOR v_entry IN SELECT value FROM jsonb_array_elements(p_lineage) LOOP
    IF NOT public.data_projection_fingerprint_valid_v1(v_entry->>'sourceFingerprint')
       OR NOT public.data_projection_fingerprint_valid_v1(v_entry->>'lineageEntryFingerprint')
       OR NOT EXISTS (SELECT 1 FROM jsonb_array_elements(v_checkpoint.source_members) m
                      WHERE m->>'sourceEventRef'=v_entry->>'sourceEventRef'
                        AND m->>'sourceFingerprint'=v_entry->>'sourceFingerprint') THEN
      RAISE EXCEPTION 'Lineage source is absent from checkpoint.' USING ERRCODE = '23503';
    END IF;
    SELECT m.value INTO v_source_member FROM jsonb_array_elements(v_checkpoint.source_members) m
      WHERE m->>'sourceEventRef'=v_entry->>'sourceEventRef'
        AND m->>'sourceFingerprint'=v_entry->>'sourceFingerprint' LIMIT 1;
    IF v_entry->>'sourceKind' <> v_source_member->>'sourceKind' THEN
      RAISE EXCEPTION 'Lineage source kind mismatch.' USING ERRCODE = '22023';
    END IF;
    IF v_entry->>'sourceKind'='adapter_output'
       AND COALESCE(v_entry->>'adapterEvidenceRef','') <> COALESCE(v_source_member->>'adapterEvidenceRef','') THEN
      RAISE EXCEPTION 'Adapter lineage evidence mismatch.' USING ERRCODE = '22023';
    END IF;
    IF public.data_projection_fingerprint_v1('data-projection-lineage-entry-sha256-v1',
         v_entry - 'sourceKind' - 'lineageEntryFingerprint') <> v_entry->>'lineageEntryFingerprint' THEN
      RAISE EXCEPTION 'Lineage entry fingerprint mismatch.' USING ERRCODE = '22023';
    END IF;
  END LOOP;

  FOR v_record IN SELECT value FROM jsonb_array_elements(p_records) LOOP
    IF v_record->>'projectionName' <> p_projection_name
       OR NOT public.data_projection_fingerprint_valid_v1(v_record->>'projectionRecordFingerprint')
       OR public.data_projection_fingerprint_v1('data-projection-record-sha256-v1',
            v_record - 'recordRef' - 'projectionRecordFingerprint') <> v_record->>'projectionRecordFingerprint' THEN
      RAISE EXCEPTION 'Projection record fingerprint mismatch.' USING ERRCODE = '22023';
    END IF;
    IF NOT EXISTS (SELECT 1 FROM jsonb_array_elements(p_lineage) e
                   WHERE e->>'projectionRecordRef'=v_record->>'recordRef') THEN
      RAISE EXCEPTION 'Projection record lineage is incomplete.' USING ERRCODE = '23514';
    END IF;
  END LOOP;

  SELECT public.data_projection_fingerprint_v1('data-projection-snapshot-sha256-v1',
    jsonb_build_object(
      'projectionName',p_projection_name,'projectionSchemaVersion',p_projection_schema_version,
      'projectionPolicyVersion',p_projection_policy_version,'featurePolicyVersion',p_feature_policy_version,
      'identityBindingVersion',p_identity_binding_version,'targetContractVersion',p_target_contract_version,
      'sourceCheckpointRef',p_source_checkpoint_ref,
      'snapshotAsOf',public.data_projection_instant_text_v1(p_projection_as_of),
      'recordFingerprints',(SELECT jsonb_agg(r->>'projectionRecordFingerprint' ORDER BY r->>'projectionRecordFingerprint') FROM jsonb_array_elements(p_records) r)))
    INTO v_actual_content;
  IF v_actual_content <> p_snapshot_content_fingerprint THEN
    RAISE EXCEPTION 'Snapshot content fingerprint mismatch.' USING ERRCODE = '22023';
  END IF;

  v_logical_hash := public.data_projection_fingerprint_v1('data-projection-snapshot-logical-identity-v1',
    jsonb_build_object('projectionName',p_projection_name,'projectionSchemaVersion',p_projection_schema_version,
      'projectionPolicyVersion',p_projection_policy_version,'sourceCheckpointRef',p_source_checkpoint_ref,
      'identityBindingVersion',p_identity_binding_version,'targetContractVersion',p_target_contract_version));
  PERFORM pg_advisory_xact_lock(hashtextextended(v_logical_hash, 0));
  SELECT * INTO v_existing_run FROM public.data_projection_run_v1 WHERE logical_identity_hash=v_logical_hash;
  IF FOUND THEN
    SELECT * INTO v_existing_snapshot FROM public.data_projection_snapshot_v1
      WHERE projection_run_ref=v_existing_run.projection_run_id;
    IF v_existing_snapshot.content_fingerprint=p_snapshot_content_fingerprint
       AND v_existing_snapshot.lineage_fingerprint=p_lineage_fingerprint THEN
      RETURN QUERY SELECT 'DUPLICATE'::varchar,v_existing_run.projection_run_id,v_existing_snapshot.snapshot_id,NULL::varchar;
      RETURN;
    END IF;
    INSERT INTO public.data_projection_conflict_observation_v1(
      conflict_kind,logical_identity_hash,existing_evidence_ref,existing_fingerprint,attempted_fingerprint,
      failure_code,expires_at)
    VALUES ('snapshot',v_logical_hash,v_existing_snapshot.snapshot_ref,v_existing_snapshot.content_fingerprint,
      p_snapshot_content_fingerprint,'PROJECTION_SNAPSHOT_CONFLICT',v_now+interval '90 days');
    RETURN QUERY SELECT 'CONFLICT'::varchar,v_existing_run.projection_run_id,v_existing_snapshot.snapshot_id,'PROJECTION_SNAPSHOT_CONFLICT'::varchar;
    RETURN;
  END IF;

  INSERT INTO public.data_projection_run_v1(
    projection_run_ref,logical_identity_hash,projection_name,projection_schema_version,
    projection_policy_version,feature_policy_version,source_contract_version,source_checkpoint_ref,
    source_from,source_to,projection_as_of,identity_binding_version,identity_binding_source,
    identity_binding_fingerprint,identity_binding_scope,target_contract_version,producer_build_id,expires_at)
  VALUES (p_projection_run_ref,v_logical_hash,p_projection_name,p_projection_schema_version,
    p_projection_policy_version,p_feature_policy_version,v_checkpoint.source_contract_version,v_checkpoint.checkpoint_id,
    v_checkpoint.event_time_from,v_checkpoint.event_time_to,p_projection_as_of,p_identity_binding_version,
    p_identity_binding_source,p_identity_binding_fingerprint,p_identity_binding_scope,p_target_contract_version,
    p_producer_build_id,v_now+interval '90 days') RETURNING data_projection_run_v1.projection_run_id INTO v_run_id;
  INSERT INTO public.data_projection_run_status_evidence_v1(projection_run_ref,run_status,observed_at,expires_at)
    VALUES(v_run_id,'started',v_now,v_now+interval '90 days');

  SELECT count(*), count(DISTINCT r->>'subjectRef') INTO v_record_count,v_subject_count FROM jsonb_array_elements(p_records) r;
  SELECT count(DISTINCT e->>'sourceEventRef') INTO v_source_count FROM jsonb_array_elements(p_lineage) e;
  INSERT INTO public.data_projection_snapshot_v1(
    snapshot_ref,projection_run_ref,projection_name,projection_schema_version,projection_policy_version,
    source_checkpoint_ref,snapshot_as_of,record_count,subject_count,source_event_count,
    content_fingerprint,lineage_fingerprint,snapshot_status,expires_at)
  VALUES(p_snapshot_ref,v_run_id,p_projection_name,p_projection_schema_version,p_projection_policy_version,
    v_checkpoint.checkpoint_id,p_projection_as_of,v_record_count,v_subject_count,v_source_count,
    p_snapshot_content_fingerprint,p_lineage_fingerprint,'validated',v_now+interval '90 days')
  RETURNING data_projection_snapshot_v1.snapshot_id INTO v_snapshot_id;

  FOR v_record IN SELECT value FROM jsonb_array_elements(p_records) LOOP
    IF p_projection_name='recommendation-profile-input-v1' THEN
      IF v_record->>'subjectRef' !~ '^subject:[A-Za-z0-9][A-Za-z0-9._~-]{0,127}$'
         OR (v_record->>'activityWindowDays')::integer NOT IN (7,30,90) THEN
        RAISE EXCEPTION 'Profile projection identity/window invalid.' USING ERRCODE='22023';
      END IF;
      INSERT INTO public.data_recommendation_profile_input_projection_v1(
        snapshot_ref,projection_record_ref,projection_subject_ref,projection_as_of,source_checkpoint_ref,
        profile_schema_version,projection_policy_version,activity_window_days,interaction_counts,
        recent_regions,recent_content_refs,recent_tag_refs,engagement_signals,negative_signals,
        source_event_count,source_lineage_fingerprint,projection_record_fingerprint,expires_at)
      VALUES(v_snapshot_id,v_record->>'recordRef',v_record->>'subjectRef',(v_record->>'projectionAsOf')::timestamptz,
        v_checkpoint.checkpoint_id,v_record->>'profileSchemaVersion',v_record->>'projectionPolicyVersion',
        (v_record->>'activityWindowDays')::integer,v_record->'interactionCounts',v_record->'recentRegions',
        v_record->'recentContentRefs',v_record->'recentTagRefs',v_record->'engagementSignals',
        v_record->'negativeSignals',(v_record->>'sourceEventCount')::bigint,
        v_record->>'sourceLineageFingerprint',v_record->>'projectionRecordFingerprint',v_now+interval '90 days');
    ELSE
      SELECT * INTO v_exposure FROM public.recommendation_p2_experiment_exposure WHERE exposure_id=v_record->>'exposureRef';
      IF NOT FOUND THEN RAISE EXCEPTION 'P2 exposure binding missing.' USING ERRCODE='23503'; END IF;
      SELECT * INTO v_assignment FROM public.recommendation_p2_experiment_assignment WHERE assignment_id=v_exposure.assignment_id;
      SELECT * INTO v_recommendation_run FROM public.recommendation_run WHERE run_id=v_exposure.run_id;
      IF v_record->>'experimentRef' <> 'experiment:'||v_assignment.experiment_id
         OR v_record->>'experimentVersion' <> v_assignment.experiment_version
         OR v_record->>'variantRef' <> v_exposure.variant
         OR v_record->>'runRef' <> v_exposure.run_id
         OR v_record->>'sourceUserRef' <> 'user:'||v_exposure.user_id::text
         OR v_record->>'sessionRef' <> 'session:'||v_exposure.session_id
         OR (v_record->>'exposedAt')::timestamptz <> v_exposure.exposed_at
         OR (v_record->>'fallbackObserved')::boolean <> (v_recommendation_run.run_status='fallback')
         OR (v_record->>'outcomeWindowSeconds')::bigint <> 604800 THEN
        RAISE EXCEPTION 'P2 exposure binding invalid.' USING ERRCODE='23514';
      END IF;
      IF EXISTS (
        SELECT 1 FROM jsonb_array_elements_text(v_record->'outcomeEventRefs') ref
        LEFT JOIN LATERAL (
          SELECT (m->>'occurredAt')::timestamptz occurred_at
          FROM jsonb_array_elements(v_checkpoint.source_members) m
          WHERE m->>'sourceEventRef'=ref.value LIMIT 1) event_time ON true
        WHERE event_time.occurred_at IS NULL OR event_time.occurred_at < v_exposure.exposed_at
          OR event_time.occurred_at >= v_exposure.exposed_at + interval '7 days') THEN
        RAISE EXCEPTION 'P2 outcome window violation.' USING ERRCODE='23514';
      END IF;
      INSERT INTO public.data_experiment_outcome_input_projection_v1(
        snapshot_ref,projection_record_ref,experiment_ref,experiment_version,variant_ref,exposure_ref,run_ref,
        source_user_ref,subject_ref,session_ref,exposed_at,outcome_window_seconds,clicked,liked,saved,shared,
        fallback_observed,outcome_event_refs,source_checkpoint_ref,source_event_count,
        source_lineage_fingerprint,projection_record_fingerprint,expires_at)
      VALUES(v_snapshot_id,v_record->>'recordRef',v_record->>'experimentRef',v_record->>'experimentVersion',
        v_record->>'variantRef',v_record->>'exposureRef',v_record->>'runRef',v_record->>'sourceUserRef',
        v_record->>'subjectRef',v_record->>'sessionRef',(v_record->>'exposedAt')::timestamptz,
        (v_record->>'outcomeWindowSeconds')::bigint,(v_record->>'clicked')::boolean,(v_record->>'liked')::boolean,
        (v_record->>'saved')::boolean,(v_record->>'shared')::boolean,(v_record->>'fallbackObserved')::boolean,
        v_record->'outcomeEventRefs',v_checkpoint.checkpoint_id,(v_record->>'sourceEventCount')::bigint,
        v_record->>'sourceLineageFingerprint',v_record->>'projectionRecordFingerprint',v_now+interval '90 days');
    END IF;
  END LOOP;

  FOR v_entry IN SELECT value FROM jsonb_array_elements(p_lineage) LOOP
    INSERT INTO public.data_projection_lineage_v1(
      snapshot_ref,projection_record_ref,source_kind,source_event_ref,source_fingerprint,
      adapter_evidence_ref,source_checkpoint_ref,projection_policy_version,mapping_policy_version,
      lineage_entry_fingerprint,expires_at)
    VALUES(v_snapshot_id,v_entry->>'projectionRecordRef',v_entry->>'sourceKind',v_entry->>'sourceEventRef',
      v_entry->>'sourceFingerprint',CASE WHEN v_entry->>'adapterEvidenceRef' IS NULL THEN NULL ELSE (v_entry->>'adapterEvidenceRef')::uuid END,
      v_checkpoint.checkpoint_id,v_entry->>'projectionPolicyVersion',v_entry->>'mappingPolicyVersion',
      v_entry->>'lineageEntryFingerprint',v_now+interval '90 days');
  END LOOP;

  INSERT INTO public.data_projection_validation_evidence_v1(
    projection_run_ref,snapshot_ref,validation_status,validation_fingerprint,expires_at)
  VALUES(v_run_id,v_snapshot_id,'passed',public.data_projection_fingerprint_v1('data-projection-validation-v1',
    jsonb_build_object('snapshotRef',p_snapshot_ref,'contentFingerprint',p_snapshot_content_fingerprint,
      'lineageFingerprint',p_lineage_fingerprint,'status','passed')),v_now+interval '90 days')
  RETURNING validation_evidence_id INTO v_validation_id;
  INSERT INTO public.data_projection_run_status_evidence_v1(projection_run_ref,run_status,observed_at,expires_at)
    VALUES(v_run_id,'completed',v_now,v_now+interval '90 days'),(v_run_id,'validated',v_now,v_now+interval '90 days');
  RETURN QUERY SELECT 'NEW'::varchar,v_run_id,v_snapshot_id,NULL::varchar;
END;
$$;

CREATE OR REPLACE VIEW public.data_projection_safe_metrics_v1 WITH (security_barrier=true) AS
SELECT 'run_count'::varchar metric_name,r.projection_name,r.projection_schema_version,r.projection_policy_version,
       NULL::varchar dimension_value,count(*)::bigint metric_value,NULL::timestamptz metric_timestamp
FROM public.data_projection_run_v1 r GROUP BY r.projection_name,r.projection_schema_version,r.projection_policy_version
UNION ALL
SELECT 'run_status_count',r.projection_name,r.projection_schema_version,r.projection_policy_version,s.run_status,
       count(*)::bigint,NULL::timestamptz
FROM public.data_projection_run_v1 r JOIN public.data_projection_run_status_evidence_v1 s ON s.projection_run_ref=r.projection_run_id
GROUP BY r.projection_name,r.projection_schema_version,r.projection_policy_version,s.run_status
UNION ALL
SELECT 'snapshot_status_count',s.projection_name,s.projection_schema_version,s.projection_policy_version,s.snapshot_status,
       count(*)::bigint,NULL::timestamptz
FROM public.data_projection_snapshot_v1 s GROUP BY s.projection_name,s.projection_schema_version,s.projection_policy_version,s.snapshot_status
UNION ALL
SELECT 'snapshot_record_count',s.projection_name,s.projection_schema_version,s.projection_policy_version,NULL,
       sum(s.record_count)::bigint,NULL::timestamptz
FROM public.data_projection_snapshot_v1 s GROUP BY s.projection_name,s.projection_schema_version,s.projection_policy_version
UNION ALL
SELECT 'snapshot_subject_count',s.projection_name,s.projection_schema_version,s.projection_policy_version,NULL,
       sum(s.subject_count)::bigint,NULL::timestamptz
FROM public.data_projection_snapshot_v1 s GROUP BY s.projection_name,s.projection_schema_version,s.projection_policy_version
UNION ALL
SELECT 'snapshot_source_event_count',s.projection_name,s.projection_schema_version,s.projection_policy_version,NULL,
       sum(s.source_event_count)::bigint,NULL::timestamptz
FROM public.data_projection_snapshot_v1 s GROUP BY s.projection_name,s.projection_schema_version,s.projection_policy_version
UNION ALL
SELECT 'failure_code_count',r.projection_name,r.projection_schema_version,r.projection_policy_version,v.failure_code,
       count(*)::bigint,NULL::timestamptz
FROM public.data_projection_run_v1 r JOIN public.data_projection_validation_evidence_v1 v ON v.projection_run_ref=r.projection_run_id
WHERE v.failure_code IS NOT NULL GROUP BY r.projection_name,r.projection_schema_version,r.projection_policy_version,v.failure_code
UNION ALL
SELECT 'conflict_count','all','all','all',c.conflict_kind,count(*)::bigint,NULL::timestamptz
FROM public.data_projection_conflict_observation_v1 c GROUP BY c.conflict_kind
UNION ALL
SELECT 'oldest_unvalidated_snapshot_age_seconds',s.projection_name,s.projection_schema_version,s.projection_policy_version,NULL,
       floor(extract(epoch FROM (CURRENT_TIMESTAMP-min(s.created_at))))::bigint,min(s.created_at)
FROM public.data_projection_snapshot_v1 s WHERE s.snapshot_status<>'validated'
GROUP BY s.projection_name,s.projection_schema_version,s.projection_policy_version
UNION ALL
SELECT 'latest_validated_snapshot_time',s.projection_name,s.projection_schema_version,s.projection_policy_version,NULL,
       NULL::bigint,max(s.created_at)
FROM public.data_projection_snapshot_v1 s WHERE s.snapshot_status='validated'
GROUP BY s.projection_name,s.projection_schema_version,s.projection_policy_version;

REVOKE ALL ON FUNCTION public.persist_data_source_checkpoint_v1(varchar,varchar,varchar,varchar,timestamptz,timestamptz,timestamptz,varchar,jsonb,varchar,varchar) FROM PUBLIC;
REVOKE ALL ON FUNCTION public.persist_data_projection_snapshot_v1(varchar,varchar,varchar,varchar,varchar,varchar,varchar,timestamptz,varchar,varchar,varchar,varchar,varchar,varchar,jsonb,jsonb,varchar,varchar) FROM PUBLIC;
REVOKE ALL ON TABLE public.data_source_checkpoint_v1,public.data_projection_run_v1,
  public.data_projection_run_status_evidence_v1,public.data_projection_snapshot_v1,
  public.data_projection_lineage_v1,public.data_projection_validation_evidence_v1,
  public.data_projection_conflict_observation_v1,public.data_recommendation_profile_input_projection_v1,
  public.data_experiment_outcome_input_projection_v1,public.data_projection_safe_metrics_v1
  FROM PUBLIC,jc_data_projection_writer,jc_data_projection_reader;

GRANT USAGE ON SCHEMA public TO jc_data_projection_writer,jc_data_projection_reader,jc_data_projection_function_owner;
GRANT SELECT,INSERT ON public.data_source_checkpoint_v1,public.data_projection_run_v1,
  public.data_projection_run_status_evidence_v1,public.data_projection_snapshot_v1,
  public.data_projection_lineage_v1,public.data_projection_validation_evidence_v1,
  public.data_projection_conflict_observation_v1,public.data_recommendation_profile_input_projection_v1,
  public.data_experiment_outcome_input_projection_v1 TO jc_data_projection_function_owner;
GRANT SELECT ON public.data_platform_event_v1,public.data_recommendation_adapter_output_v1,
  public.recommendation_p2_experiment_assignment,public.recommendation_p2_experiment_exposure,
  public.recommendation_run TO jc_data_projection_function_owner;
GRANT EXECUTE ON FUNCTION public.gen_random_uuid(),public.digest(bytea,text),
  public.data_event_canonical_json_v1(jsonb),public.data_projection_version_valid_v1(varchar),
  public.data_projection_fingerprint_valid_v1(varchar),public.data_projection_fingerprint_v1(varchar,jsonb),
  public.data_projection_instant_text_v1(timestamptz)
  TO jc_data_projection_function_owner;
ALTER FUNCTION public.persist_data_source_checkpoint_v1(varchar,varchar,varchar,varchar,timestamptz,timestamptz,timestamptz,varchar,jsonb,varchar,varchar) OWNER TO jc_data_projection_function_owner;
ALTER FUNCTION public.persist_data_projection_snapshot_v1(varchar,varchar,varchar,varchar,varchar,varchar,varchar,timestamptz,varchar,varchar,varchar,varchar,varchar,varchar,jsonb,jsonb,varchar,varchar) OWNER TO jc_data_projection_function_owner;
GRANT EXECUTE ON FUNCTION public.persist_data_source_checkpoint_v1(varchar,varchar,varchar,varchar,timestamptz,timestamptz,timestamptz,varchar,jsonb,varchar,varchar) TO jc_data_projection_writer;
GRANT EXECUTE ON FUNCTION public.persist_data_projection_snapshot_v1(varchar,varchar,varchar,varchar,varchar,varchar,varchar,timestamptz,varchar,varchar,varchar,varchar,varchar,varchar,jsonb,jsonb,varchar,varchar) TO jc_data_projection_writer;
GRANT SELECT ON public.data_projection_safe_metrics_v1 TO jc_data_projection_reader;

COMMIT;
