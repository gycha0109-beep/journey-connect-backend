-- Journey Connect DB v2.7 extension - DP-4.5 atomic shadow evidence persistence
-- Target: PostgreSQL 15 and 18
-- Prerequisite: canonical SQL 01..35.

BEGIN;

DO $$
BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'jc_data_adapter_evidence_writer') THEN
    CREATE ROLE jc_data_adapter_evidence_writer
      NOLOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOREPLICATION NOBYPASSRLS;
  END IF;
  IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'jc_data_adapter_evidence_reader') THEN
    CREATE ROLE jc_data_adapter_evidence_reader
      NOLOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOREPLICATION NOBYPASSRLS;
  END IF;
  IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'jc_data_adapter_evidence_function_owner') THEN
    CREATE ROLE jc_data_adapter_evidence_function_owner
      NOLOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOREPLICATION NOBYPASSRLS;
  END IF;
END;
$$;

DO $$
DECLARE
  v_unsafe_roles text;
BEGIN
  SELECT string_agg(rolname, ', ' ORDER BY rolname)
    INTO v_unsafe_roles
  FROM pg_roles
  WHERE rolname IN (
    'jc_data_adapter_evidence_writer',
    'jc_data_adapter_evidence_reader',
    'jc_data_adapter_evidence_function_owner'
  )
    AND (rolsuper OR rolcreaterole OR rolcreatedb OR rolcanlogin OR rolreplication OR rolbypassrls);

  IF v_unsafe_roles IS NOT NULL THEN
    RAISE EXCEPTION 'Unsafe pre-existing DP-4.5 role attributes: %', v_unsafe_roles;
  END IF;
END;
$$;

REVOKE jc_data_adapter_evidence_writer FROM jc_app, jc_auth, jc_admin, jc_security_owner,
  jc_recommendation, jc_data_event_writer, jc_data_event_reader,
  jc_data_retry_processor, jc_data_quarantine_reviewer, jc_data_replay_executor,
  jc_data_adapter_evidence_reader, jc_data_adapter_evidence_function_owner;
REVOKE jc_data_adapter_evidence_reader FROM jc_app, jc_auth, jc_admin, jc_security_owner,
  jc_recommendation, jc_data_event_writer, jc_data_event_reader,
  jc_data_retry_processor, jc_data_quarantine_reviewer, jc_data_replay_executor,
  jc_data_adapter_evidence_writer, jc_data_adapter_evidence_function_owner;
REVOKE jc_data_adapter_evidence_function_owner FROM jc_app, jc_auth, jc_admin,
  jc_recommendation, jc_data_event_writer, jc_data_event_reader,
  jc_data_retry_processor, jc_data_quarantine_reviewer, jc_data_replay_executor,
  jc_data_adapter_evidence_writer, jc_data_adapter_evidence_reader;

CREATE OR REPLACE FUNCTION public.persist_recommendation_adapter_shadow_evidence_v1(
  p_source_event_ref varchar,
  p_source_fingerprint varchar,
  p_source_contract_version varchar,
  p_source_schema_version varchar,
  p_adapter_id varchar,
  p_adapter_version varchar,
  p_mapping_policy_version varchar,
  p_output_fingerprint_version varchar,
  p_target_contract_version varchar,
  p_target_schema_version varchar,
  p_producer_build_id varchar,
  p_started_at timestamptz,
  p_completed_at timestamptz,
  p_mapping_status varchar,
  p_compatibility_class varchar DEFAULT NULL,
  p_mapped_event_type varchar DEFAULT NULL,
  p_mapped_actor_ref varchar DEFAULT NULL,
  p_mapped_session_ref varchar DEFAULT NULL,
  p_mapped_entity_ref varchar DEFAULT NULL,
  p_mapped_occurred_at timestamptz DEFAULT NULL,
  p_mapped_payload jsonb DEFAULT NULL,
  p_output_fingerprint varchar DEFAULT NULL,
  p_failure_code varchar DEFAULT NULL,
  p_failure_class varchar DEFAULT NULL,
  p_retryable boolean DEFAULT NULL,
  p_failure_signature varchar DEFAULT NULL
)
RETURNS TABLE (
  disposition varchar,
  adapter_run_id uuid,
  evidence_kind varchar,
  evidence_id uuid,
  error_code varchar
)
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = pg_catalog, public, pg_temp
AS $$
DECLARE
  v_now timestamptz := clock_timestamp();
  v_logical_hash varchar(64);
  v_result_fingerprint varchar(64);
  v_existing public.data_recommendation_adapter_run_v1%ROWTYPE;
  v_run_id uuid;
  v_evidence_id uuid;
  v_evidence_kind varchar(16);
  v_existing_ref varchar(80);
  v_existing_output_fingerprint varchar(64);
BEGIN
  IF p_source_event_ref IS NULL
     OR p_source_event_ref !~ '^recommendation_behavior_event:[A-Za-z0-9][A-Za-z0-9._:-]{0,127}$' THEN
    RAISE EXCEPTION 'Invalid Recommendation source event reference.' USING ERRCODE = '22023';
  END IF;
  IF p_source_fingerprint IS NULL OR p_source_fingerprint !~ '^[0-9a-f]{64}$' THEN
    RAISE EXCEPTION 'Invalid source fingerprint.' USING ERRCODE = '22023';
  END IF;
  IF p_source_contract_version <> 'recommendation-behavior-event-v1'
     OR p_source_schema_version <> 'recommendation-behavior-event-v1'
     OR p_adapter_id <> 'p0-recommendation-event-adapter-v1'
     OR p_adapter_version <> 'recommendation-p0-event-adapter-v1'
     OR p_mapping_policy_version <> 'recommendation-p0-mapping-policy-v1'
     OR p_output_fingerprint_version <> 'recommendation-p0-adapter-output-sha256-v1'
     OR p_target_contract_version <> 'platform-event-v1'
     OR p_target_schema_version <> 'user-behavior-event-v1' THEN
    RAISE EXCEPTION 'Unsupported DP-4 adapter version boundary.' USING ERRCODE = '22023';
  END IF;
  IF p_producer_build_id IS NULL OR p_producer_build_id !~ '^git:[0-9a-f]{40}$' THEN
    RAISE EXCEPTION 'Invalid producer build ID.' USING ERRCODE = '22023';
  END IF;
  IF p_started_at IS NULL OR p_completed_at IS NULL OR p_completed_at < p_started_at
     OR p_completed_at > v_now + interval '5 minutes' THEN
    RAISE EXCEPTION 'Invalid adapter run time boundary.' USING ERRCODE = '22023';
  END IF;

  IF p_mapping_status = 'mapped_shadow' THEN
    IF p_compatibility_class NOT IN ('exact_compatible', 'semantic_compatible')
       OR p_mapped_event_type IS NULL
       OR NOT public.data_event_type_valid_v1('user_behavior', p_mapped_event_type)
       OR p_mapped_actor_ref IS NULL
       OR p_mapped_actor_ref !~ '^subject:[A-Za-z0-9][A-Za-z0-9._~-]{0,127}$'
       OR p_mapped_session_ref IS NULL
       OR p_mapped_session_ref !~ '^session:[A-Za-z0-9][A-Za-z0-9._~-]{0,127}$'
       OR (p_mapped_entity_ref IS NOT NULL AND p_mapped_entity_ref !~ '^[a-z][a-z0-9_]{0,31}:[^[:space:]]{1,128}$')
       OR p_mapped_occurred_at IS NULL
       OR p_mapped_occurred_at > p_completed_at + interval '5 minutes'
       OR p_mapped_payload IS NULL
       OR NOT public.data_recommendation_adapter_payload_valid_v1(p_mapped_payload)
       OR p_output_fingerprint IS NULL
       OR p_output_fingerprint !~ '^[0-9a-f]{64}$'
       OR p_failure_code IS NOT NULL OR p_failure_class IS NOT NULL
       OR p_retryable IS NOT NULL OR p_failure_signature IS NOT NULL THEN
      RAISE EXCEPTION 'Mapped shadow evidence shape is invalid.' USING ERRCODE = '22023';
    END IF;
    IF p_mapped_entity_ref IS NOT NULL
       AND split_part(p_mapped_entity_ref, ':', 1) NOT IN (
         'post', 'journey', 'place', 'crew', 'user', 'tag', 'region',
         'itinerary', 'profile', 'search_result'
       ) THEN
      RAISE EXCEPTION 'Mapped entity reference type is invalid.' USING ERRCODE = '22023';
    END IF;
    v_evidence_kind := 'mapped';
    v_result_fingerprint := p_output_fingerprint;
  ELSIF p_mapping_status IN ('unsupported', 'quarantined') THEN
    IF p_compatibility_class <> 'unsupported'
       OR p_mapped_event_type IS NOT NULL OR p_mapped_actor_ref IS NOT NULL
       OR p_mapped_session_ref IS NOT NULL OR p_mapped_entity_ref IS NOT NULL
       OR p_mapped_occurred_at IS NOT NULL OR p_mapped_payload IS NOT NULL
       OR p_output_fingerprint IS NOT NULL
       OR p_failure_code IS NULL
       OR NOT public.data_recommendation_adapter_failure_code_valid_v1(p_failure_code)
       OR p_failure_class IS NULL
       OR NOT public.data_recommendation_adapter_failure_class_valid_v1(p_failure_class)
       OR NOT public.data_recommendation_adapter_failure_binding_valid_v1(p_failure_code, p_failure_class)
       OR p_retryable IS NULL
       OR p_retryable <> public.data_recommendation_adapter_failure_retryable_v1(p_failure_code)
       OR p_failure_signature IS NULL OR p_failure_signature !~ '^[0-9a-f]{64}$' THEN
      RAISE EXCEPTION 'Mapping failure evidence shape is invalid.' USING ERRCODE = '22023';
    END IF;
    v_evidence_kind := 'failure';
    v_result_fingerprint := public.data_recommendation_adapter_failure_result_fingerprint_v1(
      p_mapping_status, p_failure_code, p_failure_class, p_retryable, p_failure_signature);
  ELSE
    RAISE EXCEPTION 'Unsupported adapter mapping status.' USING ERRCODE = '22023';
  END IF;

  v_logical_hash := public.data_recommendation_adapter_logical_identity_hash_v1(
    p_source_event_ref, p_source_fingerprint, p_adapter_id, p_adapter_version,
    p_target_contract_version, p_mapping_policy_version);

  PERFORM pg_advisory_xact_lock(hashtextextended(v_logical_hash, 0));

  SELECT run.* INTO v_existing
  FROM public.data_recommendation_adapter_run_v1 run
  WHERE run.logical_identity_hash = v_logical_hash
  FOR UPDATE;

  IF FOUND THEN
    IF v_existing.evidence_kind = 'mapped' THEN
      SELECT output.adapter_output_id, output.output_fingerprint
        INTO v_evidence_id, v_existing_output_fingerprint
      FROM public.data_recommendation_adapter_output_v1 output
      WHERE output.adapter_run_ref = v_existing.adapter_run_id;
      v_existing_ref := 'output:' || v_evidence_id::text;
    ELSE
      SELECT failure.adapter_failure_id
        INTO v_evidence_id
      FROM public.data_recommendation_adapter_failure_v1 failure
      WHERE failure.adapter_run_ref = v_existing.adapter_run_id;
      v_existing_output_fingerprint := NULL;
      v_existing_ref := 'failure:' || v_evidence_id::text;
    END IF;

    IF v_existing.result_fingerprint = v_result_fingerprint
       AND v_existing.evidence_kind = v_evidence_kind THEN
      INSERT INTO public.data_recommendation_adapter_duplicate_counter_v1 (
        logical_identity_hash, duplicate_count, first_seen_at, last_seen_at, expires_at
      ) VALUES (
        v_logical_hash, 1, v_now, v_now, v_now + interval '90 days'
      )
      ON CONFLICT (logical_identity_hash) DO UPDATE
      SET duplicate_count = CASE
            WHEN public.data_recommendation_adapter_duplicate_counter_v1.duplicate_count = 9223372036854775807
              THEN 9223372036854775807
            ELSE public.data_recommendation_adapter_duplicate_counter_v1.duplicate_count + 1
          END,
          last_seen_at = EXCLUDED.last_seen_at;

      RETURN QUERY SELECT 'DUPLICATE'::varchar, v_existing.adapter_run_id,
        v_existing.evidence_kind, v_evidence_id, NULL::varchar;
      RETURN;
    END IF;

    INSERT INTO public.data_recommendation_adapter_conflict_observation_v1 (
      logical_identity_hash, existing_evidence_ref,
      existing_result_fingerprint, attempted_result_fingerprint,
      existing_output_fingerprint, attempted_output_fingerprint,
      failure_code, expires_at
    ) VALUES (
      v_logical_hash, v_existing_ref,
      v_existing.result_fingerprint, v_result_fingerprint,
      v_existing_output_fingerprint,
      CASE WHEN v_evidence_kind = 'mapped' THEN p_output_fingerprint ELSE NULL END,
      'ADAPTER_EVIDENCE_CONFLICT', v_now + interval '90 days'
    );

    RETURN QUERY SELECT 'CONFLICT'::varchar, v_existing.adapter_run_id,
      v_existing.evidence_kind, v_evidence_id, 'ADAPTER_EVIDENCE_CONFLICT'::varchar;
    RETURN;
  END IF;

  INSERT INTO public.data_recommendation_adapter_run_v1 (
    logical_identity_hash, source_event_ref, source_fingerprint,
    source_contract_version, source_schema_version,
    adapter_id, adapter_version, mapping_policy_version,
    output_fingerprint_version, target_contract_version, target_schema_version,
    producer_build_id, evidence_kind, result_fingerprint, run_status,
    started_at, completed_at, expires_at
  ) VALUES (
    v_logical_hash, p_source_event_ref, p_source_fingerprint,
    p_source_contract_version, p_source_schema_version,
    p_adapter_id, p_adapter_version, p_mapping_policy_version,
    p_output_fingerprint_version, p_target_contract_version, p_target_schema_version,
    p_producer_build_id, v_evidence_kind, v_result_fingerprint, p_mapping_status,
    p_started_at, p_completed_at, v_now + interval '90 days'
  ) RETURNING adapter_run_id INTO v_run_id;

  IF v_evidence_kind = 'mapped' THEN
    INSERT INTO public.data_recommendation_adapter_output_v1 (
      adapter_run_ref, source_event_ref, source_fingerprint,
      compatibility_class, mapped_event_type, mapped_actor_ref,
      mapped_session_ref, mapped_entity_ref, mapped_occurred_at,
      mapped_payload, output_fingerprint, mapping_status, expires_at
    ) VALUES (
      v_run_id, p_source_event_ref, p_source_fingerprint,
      p_compatibility_class, p_mapped_event_type, p_mapped_actor_ref,
      p_mapped_session_ref, p_mapped_entity_ref, p_mapped_occurred_at,
      p_mapped_payload, p_output_fingerprint, p_mapping_status,
      v_now + interval '90 days'
    ) RETURNING adapter_output_id INTO v_evidence_id;
  ELSE
    INSERT INTO public.data_recommendation_adapter_failure_v1 (
      adapter_run_ref, source_event_ref, source_fingerprint,
      failure_code, failure_class, retryable, failure_signature,
      adapter_version, target_contract_version, mapping_status, expires_at
    ) VALUES (
      v_run_id, p_source_event_ref, p_source_fingerprint,
      p_failure_code, p_failure_class, p_retryable, p_failure_signature,
      p_adapter_version, p_target_contract_version, p_mapping_status,
      v_now + interval '90 days'
    ) RETURNING adapter_failure_id INTO v_evidence_id;
  END IF;

  RETURN QUERY SELECT 'NEW'::varchar, v_run_id, v_evidence_kind, v_evidence_id, NULL::varchar;
END;
$$;

CREATE OR REPLACE VIEW public.data_recommendation_adapter_safe_metrics_v1
WITH (security_barrier = true)
AS
WITH base_metrics AS (
  SELECT run.adapter_version, run.target_contract_version,
         'run_count'::varchar AS metric_name, NULL::varchar AS dimension_value,
         count(*)::bigint AS metric_value, NULL::timestamptz AS metric_timestamp
  FROM public.data_recommendation_adapter_run_v1 run
  GROUP BY run.adapter_version, run.target_contract_version
  UNION ALL
  SELECT run.adapter_version, run.target_contract_version,
         'success_count', NULL, count(*)::bigint, NULL::timestamptz
  FROM public.data_recommendation_adapter_run_v1 run
  WHERE run.evidence_kind = 'mapped'
  GROUP BY run.adapter_version, run.target_contract_version
  UNION ALL
  SELECT run.adapter_version, run.target_contract_version,
         'failure_count', NULL, count(*)::bigint, NULL::timestamptz
  FROM public.data_recommendation_adapter_run_v1 run
  WHERE run.evidence_kind = 'failure'
  GROUP BY run.adapter_version, run.target_contract_version
  UNION ALL
  SELECT run.adapter_version, run.target_contract_version,
         'new_count', NULL, count(*)::bigint, NULL::timestamptz
  FROM public.data_recommendation_adapter_run_v1 run
  GROUP BY run.adapter_version, run.target_contract_version
  UNION ALL
  SELECT run.adapter_version, run.target_contract_version,
         'duplicate_count', NULL, COALESCE(sum(counter.duplicate_count), 0)::bigint, NULL::timestamptz
  FROM public.data_recommendation_adapter_run_v1 run
  LEFT JOIN public.data_recommendation_adapter_duplicate_counter_v1 counter
    ON counter.logical_identity_hash = run.logical_identity_hash
  GROUP BY run.adapter_version, run.target_contract_version
  UNION ALL
  SELECT run.adapter_version, run.target_contract_version,
         'conflict_count', NULL, count(conflict.conflict_observation_id)::bigint, NULL::timestamptz
  FROM public.data_recommendation_adapter_run_v1 run
  LEFT JOIN public.data_recommendation_adapter_conflict_observation_v1 conflict
    ON conflict.logical_identity_hash = run.logical_identity_hash
  GROUP BY run.adapter_version, run.target_contract_version
  UNION ALL
  SELECT run.adapter_version, run.target_contract_version,
         'oldest_run_age_seconds', NULL,
         floor(extract(epoch FROM (CURRENT_TIMESTAMP - min(run.created_at))))::bigint,
         min(run.created_at)
  FROM public.data_recommendation_adapter_run_v1 run
  GROUP BY run.adapter_version, run.target_contract_version
  UNION ALL
  SELECT run.adapter_version, run.target_contract_version,
         'latest_processed_at', NULL, NULL::bigint, max(run.completed_at)
  FROM public.data_recommendation_adapter_run_v1 run
  GROUP BY run.adapter_version, run.target_contract_version
),
dimension_metrics AS (
  SELECT run.adapter_version, run.target_contract_version,
         'compatibility_class_count'::varchar AS metric_name,
         output.compatibility_class AS dimension_value,
         count(*)::bigint AS metric_value, NULL::timestamptz AS metric_timestamp
  FROM public.data_recommendation_adapter_run_v1 run
  JOIN public.data_recommendation_adapter_output_v1 output
    ON output.adapter_run_ref = run.adapter_run_id
  GROUP BY run.adapter_version, run.target_contract_version, output.compatibility_class
  UNION ALL
  SELECT run.adapter_version, run.target_contract_version,
         'failure_code_count', failure.failure_code,
         count(*)::bigint, NULL::timestamptz
  FROM public.data_recommendation_adapter_run_v1 run
  JOIN public.data_recommendation_adapter_failure_v1 failure
    ON failure.adapter_run_ref = run.adapter_run_id
  GROUP BY run.adapter_version, run.target_contract_version, failure.failure_code
)
SELECT metric_name, adapter_version, target_contract_version,
       dimension_value, metric_value, metric_timestamp
FROM base_metrics
UNION ALL
SELECT metric_name, adapter_version, target_contract_version,
       dimension_value, metric_value, metric_timestamp
FROM dimension_metrics;

REVOKE ALL ON TABLE public.data_recommendation_adapter_run_v1 FROM PUBLIC,
  jc_data_adapter_evidence_writer, jc_data_adapter_evidence_reader;
REVOKE ALL ON TABLE public.data_recommendation_adapter_output_v1 FROM PUBLIC,
  jc_data_adapter_evidence_writer, jc_data_adapter_evidence_reader;
REVOKE ALL ON TABLE public.data_recommendation_adapter_failure_v1 FROM PUBLIC,
  jc_data_adapter_evidence_writer, jc_data_adapter_evidence_reader;
REVOKE ALL ON TABLE public.data_recommendation_adapter_conflict_observation_v1 FROM PUBLIC,
  jc_data_adapter_evidence_writer, jc_data_adapter_evidence_reader;
REVOKE ALL ON TABLE public.data_recommendation_adapter_duplicate_counter_v1 FROM PUBLIC,
  jc_data_adapter_evidence_writer, jc_data_adapter_evidence_reader;
REVOKE ALL ON TABLE public.data_recommendation_adapter_safe_metrics_v1 FROM PUBLIC,
  jc_data_adapter_evidence_writer;

GRANT USAGE ON SCHEMA public TO jc_data_adapter_evidence_writer,
  jc_data_adapter_evidence_reader, jc_data_adapter_evidence_function_owner;
GRANT SELECT, INSERT ON TABLE public.data_recommendation_adapter_run_v1,
  public.data_recommendation_adapter_output_v1,
  public.data_recommendation_adapter_failure_v1,
  public.data_recommendation_adapter_conflict_observation_v1
  TO jc_data_adapter_evidence_function_owner;
GRANT SELECT, INSERT, UPDATE ON TABLE public.data_recommendation_adapter_duplicate_counter_v1
  TO jc_data_adapter_evidence_function_owner;
GRANT SELECT ON TABLE public.data_recommendation_adapter_safe_metrics_v1
  TO jc_data_adapter_evidence_reader;

ALTER FUNCTION public.persist_recommendation_adapter_shadow_evidence_v1(
  varchar, varchar, varchar, varchar, varchar, varchar, varchar, varchar,
  varchar, varchar, varchar, timestamptz, timestamptz, varchar,
  varchar, varchar, varchar, varchar, varchar, timestamptz, jsonb, varchar,
  varchar, varchar, boolean, varchar
) OWNER TO jc_data_adapter_evidence_function_owner;

REVOKE ALL ON FUNCTION public.persist_recommendation_adapter_shadow_evidence_v1(
  varchar, varchar, varchar, varchar, varchar, varchar, varchar, varchar,
  varchar, varchar, varchar, timestamptz, timestamptz, varchar,
  varchar, varchar, varchar, varchar, varchar, timestamptz, jsonb, varchar,
  varchar, varchar, boolean, varchar
) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION public.persist_recommendation_adapter_shadow_evidence_v1(
  varchar, varchar, varchar, varchar, varchar, varchar, varchar, varchar,
  varchar, varchar, varchar, timestamptz, timestamptz, varchar,
  varchar, varchar, varchar, varchar, varchar, timestamptz, jsonb, varchar,
  varchar, varchar, boolean, varchar
) TO jc_data_adapter_evidence_writer;

COMMENT ON FUNCTION public.persist_recommendation_adapter_shadow_evidence_v1(
  varchar, varchar, varchar, varchar, varchar, varchar, varchar, varchar,
  varchar, varchar, varchar, timestamptz, timestamptz, varchar,
  varchar, varchar, varchar, varchar, varchar, timestamptz, jsonb, varchar,
  varchar, varchar, boolean, varchar
) IS 'Atomic DP-4.5 NEW/DUPLICATE/CONFLICT persistence boundary for Recommendation P0 to Data shadow evidence only.';
COMMENT ON VIEW public.data_recommendation_adapter_safe_metrics_v1 IS
  'Identity-free aggregate DP-4.5 metrics. Contains no actor, session, request, payload, fingerprint, token or unrestricted error dimensions.';

COMMIT;
