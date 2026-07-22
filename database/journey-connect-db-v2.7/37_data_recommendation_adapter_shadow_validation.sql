-- Journey Connect DB v2.7 DP-4.5 Recommendation adapter shadow persistence validation
-- Target: PostgreSQL 15 and 18
-- Rollback-only smoke. No production worker, scheduler, Recommendation write or cutover is activated.

BEGIN;

DO $$
DECLARE
  v_missing text;
BEGIN
  SELECT string_agg(name, ', ' ORDER BY name)
    INTO v_missing
  FROM (VALUES
    ('data_recommendation_adapter_run_v1', to_regclass('public.data_recommendation_adapter_run_v1')::text),
    ('data_recommendation_adapter_output_v1', to_regclass('public.data_recommendation_adapter_output_v1')::text),
    ('data_recommendation_adapter_failure_v1', to_regclass('public.data_recommendation_adapter_failure_v1')::text),
    ('data_recommendation_adapter_conflict_observation_v1', to_regclass('public.data_recommendation_adapter_conflict_observation_v1')::text),
    ('data_recommendation_adapter_safe_metrics_v1', to_regclass('public.data_recommendation_adapter_safe_metrics_v1')::text),
    ('persist_recommendation_adapter_shadow_evidence_v1',
      to_regprocedure('public.persist_recommendation_adapter_shadow_evidence_v1(character varying,character varying,character varying,character varying,character varying,character varying,character varying,character varying,character varying,character varying,character varying,timestamp with time zone,timestamp with time zone,character varying,character varying,character varying,character varying,character varying,character varying,timestamp with time zone,jsonb,character varying,character varying,character varying,boolean,character varying)')::text)
  ) AS required(name, object_name)
  WHERE object_name IS NULL;

  IF v_missing IS NOT NULL THEN
    RAISE EXCEPTION 'DP-4.5 required objects missing: %', v_missing;
  END IF;
END;
$$;

DO $$
DECLARE
  v_now timestamptz := clock_timestamp();
  v_new record;
  v_duplicate record;
  v_conflict record;
  v_output_count bigint;
BEGIN
  SELECT * INTO v_new
  FROM public.persist_recommendation_adapter_shadow_evidence_v1(
    p_source_event_ref => 'recommendation_behavior_event:dp45-success',
    p_source_fingerprint => repeat('1', 64),
    p_source_contract_version => 'recommendation-behavior-event-v1',
    p_source_schema_version => 'recommendation-behavior-event-v1',
    p_adapter_id => 'p0-recommendation-event-adapter-v1',
    p_adapter_version => 'recommendation-p0-event-adapter-v1',
    p_mapping_policy_version => 'recommendation-p0-mapping-policy-v1',
    p_output_fingerprint_version => 'recommendation-p0-adapter-output-sha256-v1',
    p_target_contract_version => 'platform-event-v1',
    p_target_schema_version => 'user-behavior-event-v1',
    p_producer_build_id => 'git:1111111111111111111111111111111111111111',
    p_started_at => v_now - interval '1 second',
    p_completed_at => v_now,
    p_mapping_status => 'mapped_shadow',
    p_compatibility_class => 'semantic_compatible',
    p_mapped_event_type => 'post_view',
    p_mapped_actor_ref => 'subject:dp45-success',
    p_mapped_session_ref => 'session:dp45-success',
    p_mapped_entity_ref => 'post:45',
    p_mapped_occurred_at => v_now - interval '2 seconds',
    p_mapped_payload => '{"surface":"home"}'::jsonb,
    p_output_fingerprint => repeat('2', 64)
  );

  IF v_new.disposition <> 'NEW' OR v_new.evidence_kind <> 'mapped'
     OR v_new.evidence_id IS NULL OR v_new.error_code IS NOT NULL THEN
    RAISE EXCEPTION 'DP-4.5 mapped NEW failed';
  END IF;

  SELECT * INTO v_duplicate
  FROM public.persist_recommendation_adapter_shadow_evidence_v1(
    p_source_event_ref => 'recommendation_behavior_event:dp45-success',
    p_source_fingerprint => repeat('1', 64),
    p_source_contract_version => 'recommendation-behavior-event-v1',
    p_source_schema_version => 'recommendation-behavior-event-v1',
    p_adapter_id => 'p0-recommendation-event-adapter-v1',
    p_adapter_version => 'recommendation-p0-event-adapter-v1',
    p_mapping_policy_version => 'recommendation-p0-mapping-policy-v1',
    p_output_fingerprint_version => 'recommendation-p0-adapter-output-sha256-v1',
    p_target_contract_version => 'platform-event-v1',
    p_target_schema_version => 'user-behavior-event-v1',
    p_producer_build_id => 'git:9999999999999999999999999999999999999999',
    p_started_at => v_now - interval '1 second',
    p_completed_at => v_now,
    p_mapping_status => 'mapped_shadow',
    p_compatibility_class => 'semantic_compatible',
    p_mapped_event_type => 'post_view',
    p_mapped_actor_ref => 'subject:dp45-success',
    p_mapped_session_ref => 'session:dp45-success',
    p_mapped_entity_ref => 'post:45',
    p_mapped_occurred_at => v_now - interval '2 seconds',
    p_mapped_payload => '{"surface":"home"}'::jsonb,
    p_output_fingerprint => repeat('2', 64)
  );

  IF v_duplicate.disposition <> 'DUPLICATE'
     OR v_duplicate.adapter_run_id IS DISTINCT FROM v_new.adapter_run_id
     OR v_duplicate.evidence_id IS DISTINCT FROM v_new.evidence_id THEN
    RAISE EXCEPTION 'DP-4.5 mapped DUPLICATE did not return existing evidence';
  END IF;

  SELECT * INTO v_conflict
  FROM public.persist_recommendation_adapter_shadow_evidence_v1(
    p_source_event_ref => 'recommendation_behavior_event:dp45-success',
    p_source_fingerprint => repeat('1', 64),
    p_source_contract_version => 'recommendation-behavior-event-v1',
    p_source_schema_version => 'recommendation-behavior-event-v1',
    p_adapter_id => 'p0-recommendation-event-adapter-v1',
    p_adapter_version => 'recommendation-p0-event-adapter-v1',
    p_mapping_policy_version => 'recommendation-p0-mapping-policy-v1',
    p_output_fingerprint_version => 'recommendation-p0-adapter-output-sha256-v1',
    p_target_contract_version => 'platform-event-v1',
    p_target_schema_version => 'user-behavior-event-v1',
    p_producer_build_id => 'git:1111111111111111111111111111111111111111',
    p_started_at => v_now - interval '1 second',
    p_completed_at => v_now,
    p_mapping_status => 'mapped_shadow',
    p_compatibility_class => 'semantic_compatible',
    p_mapped_event_type => 'post_view',
    p_mapped_actor_ref => 'subject:dp45-success',
    p_mapped_session_ref => 'session:dp45-success',
    p_mapped_entity_ref => 'post:45',
    p_mapped_occurred_at => v_now - interval '2 seconds',
    p_mapped_payload => '{"surface":"detail"}'::jsonb,
    p_output_fingerprint => repeat('3', 64)
  );

  IF v_conflict.disposition <> 'CONFLICT'
     OR v_conflict.error_code <> 'ADAPTER_EVIDENCE_CONFLICT'
     OR v_conflict.adapter_run_id IS DISTINCT FROM v_new.adapter_run_id THEN
    RAISE EXCEPTION 'DP-4.5 mapped CONFLICT failed';
  END IF;

  SELECT count(*) INTO v_output_count
  FROM public.data_recommendation_adapter_output_v1
  WHERE source_event_ref = 'recommendation_behavior_event:dp45-success';
  IF v_output_count <> 1 THEN
    RAISE EXCEPTION 'DP-4.5 conflict created an additional mapped output';
  END IF;
END;
$$;

DO $$
DECLARE
  v_now timestamptz := clock_timestamp();
  v_new record;
  v_duplicate record;
  v_conflict record;
BEGIN
  SELECT * INTO v_new
  FROM public.persist_recommendation_adapter_shadow_evidence_v1(
    p_source_event_ref => 'recommendation_behavior_event:dp45-failure',
    p_source_fingerprint => repeat('4', 64),
    p_source_contract_version => 'recommendation-behavior-event-v1',
    p_source_schema_version => 'recommendation-behavior-event-v1',
    p_adapter_id => 'p0-recommendation-event-adapter-v1',
    p_adapter_version => 'recommendation-p0-event-adapter-v1',
    p_mapping_policy_version => 'recommendation-p0-mapping-policy-v1',
    p_output_fingerprint_version => 'recommendation-p0-adapter-output-sha256-v1',
    p_target_contract_version => 'platform-event-v1',
    p_target_schema_version => 'user-behavior-event-v1',
    p_producer_build_id => 'git:2222222222222222222222222222222222222222',
    p_started_at => v_now - interval '1 second',
    p_completed_at => v_now,
    p_mapping_status => 'unsupported',
    p_compatibility_class => 'unsupported',
    p_failure_code => 'unsupported_event_type',
    p_failure_class => 'schema_unsupported',
    p_retryable => false,
    p_failure_signature => repeat('5', 64)
  );

  IF v_new.disposition <> 'NEW' OR v_new.evidence_kind <> 'failure'
     OR v_new.evidence_id IS NULL THEN
    RAISE EXCEPTION 'DP-4.5 failure NEW failed';
  END IF;

  SELECT * INTO v_duplicate
  FROM public.persist_recommendation_adapter_shadow_evidence_v1(
    p_source_event_ref => 'recommendation_behavior_event:dp45-failure',
    p_source_fingerprint => repeat('4', 64),
    p_source_contract_version => 'recommendation-behavior-event-v1',
    p_source_schema_version => 'recommendation-behavior-event-v1',
    p_adapter_id => 'p0-recommendation-event-adapter-v1',
    p_adapter_version => 'recommendation-p0-event-adapter-v1',
    p_mapping_policy_version => 'recommendation-p0-mapping-policy-v1',
    p_output_fingerprint_version => 'recommendation-p0-adapter-output-sha256-v1',
    p_target_contract_version => 'platform-event-v1',
    p_target_schema_version => 'user-behavior-event-v1',
    p_producer_build_id => 'git:3333333333333333333333333333333333333333',
    p_started_at => v_now - interval '1 second',
    p_completed_at => v_now,
    p_mapping_status => 'unsupported',
    p_compatibility_class => 'unsupported',
    p_failure_code => 'unsupported_event_type',
    p_failure_class => 'schema_unsupported',
    p_retryable => false,
    p_failure_signature => repeat('5', 64)
  );

  IF v_duplicate.disposition <> 'DUPLICATE'
     OR v_duplicate.evidence_id IS DISTINCT FROM v_new.evidence_id THEN
    RAISE EXCEPTION 'DP-4.5 failure DUPLICATE failed';
  END IF;

  SELECT * INTO v_conflict
  FROM public.persist_recommendation_adapter_shadow_evidence_v1(
    p_source_event_ref => 'recommendation_behavior_event:dp45-failure',
    p_source_fingerprint => repeat('4', 64),
    p_source_contract_version => 'recommendation-behavior-event-v1',
    p_source_schema_version => 'recommendation-behavior-event-v1',
    p_adapter_id => 'p0-recommendation-event-adapter-v1',
    p_adapter_version => 'recommendation-p0-event-adapter-v1',
    p_mapping_policy_version => 'recommendation-p0-mapping-policy-v1',
    p_output_fingerprint_version => 'recommendation-p0-adapter-output-sha256-v1',
    p_target_contract_version => 'platform-event-v1',
    p_target_schema_version => 'user-behavior-event-v1',
    p_producer_build_id => 'git:2222222222222222222222222222222222222222',
    p_started_at => v_now - interval '1 second',
    p_completed_at => v_now,
    p_mapping_status => 'unsupported',
    p_compatibility_class => 'unsupported',
    p_failure_code => 'unsupported_schema_version',
    p_failure_class => 'schema_unsupported',
    p_retryable => false,
    p_failure_signature => repeat('6', 64)
  );

  IF v_conflict.disposition <> 'CONFLICT'
     OR v_conflict.error_code <> 'ADAPTER_EVIDENCE_CONFLICT' THEN
    RAISE EXCEPTION 'DP-4.5 failure CONFLICT failed';
  END IF;
END;
$$;

DO $$
DECLARE
  v_before bigint;
  v_after bigint;
  v_now timestamptz := clock_timestamp();
BEGIN
  SELECT count(*) INTO v_before FROM public.data_recommendation_adapter_run_v1;
  BEGIN
    PERFORM * FROM public.persist_recommendation_adapter_shadow_evidence_v1(
      p_source_event_ref => 'recommendation_behavior_event:dp45-invalid',
      p_source_fingerprint => repeat('7', 64),
      p_source_contract_version => 'recommendation-behavior-event-v1',
      p_source_schema_version => 'recommendation-behavior-event-v1',
      p_adapter_id => 'p0-recommendation-event-adapter-v1',
      p_adapter_version => 'recommendation-p0-event-adapter-v1',
      p_mapping_policy_version => 'recommendation-p0-mapping-policy-v1',
      p_output_fingerprint_version => 'recommendation-p0-adapter-output-sha256-v1',
      p_target_contract_version => 'platform-event-v1',
      p_target_schema_version => 'user-behavior-event-v1',
      p_producer_build_id => 'git:7777777777777777777777777777777777777777',
      p_started_at => v_now - interval '1 second',
      p_completed_at => v_now,
      p_mapping_status => 'mapped_shadow',
      p_compatibility_class => 'unsupported',
      p_mapped_event_type => 'post_view',
      p_mapped_actor_ref => 'subject:dp45-invalid',
      p_mapped_session_ref => 'session:dp45-invalid',
      p_mapped_occurred_at => v_now,
      p_mapped_payload => '{"authorization":"secret"}'::jsonb,
      p_output_fingerprint => 'INVALID'
    );
    RAISE EXCEPTION 'invalid DP-4.5 evidence unexpectedly persisted';
  EXCEPTION WHEN SQLSTATE '22023' THEN
    NULL;
  END;
  SELECT count(*) INTO v_after FROM public.data_recommendation_adapter_run_v1;
  IF v_after <> v_before THEN
    RAISE EXCEPTION 'DP-4.5 invalid transaction left a partial run';
  END IF;
END;
$$;

DO $$
DECLARE
  v_run uuid;
  v_output uuid;
  v_failure uuid;
  v_conflict uuid;
BEGIN
  SELECT adapter_run_id INTO v_run FROM public.data_recommendation_adapter_run_v1 LIMIT 1;
  SELECT adapter_output_id INTO v_output FROM public.data_recommendation_adapter_output_v1 LIMIT 1;
  SELECT adapter_failure_id INTO v_failure FROM public.data_recommendation_adapter_failure_v1 LIMIT 1;
  SELECT conflict_observation_id INTO v_conflict
  FROM public.data_recommendation_adapter_conflict_observation_v1 LIMIT 1;

  BEGIN
    UPDATE public.data_recommendation_adapter_run_v1
    SET run_status = run_status WHERE adapter_run_id = v_run;
    RAISE EXCEPTION 'adapter run UPDATE unexpectedly succeeded';
  EXCEPTION WHEN SQLSTATE '55000' THEN NULL;
  END;
  BEGIN
    DELETE FROM public.data_recommendation_adapter_output_v1
    WHERE adapter_output_id = v_output;
    RAISE EXCEPTION 'adapter output DELETE unexpectedly succeeded';
  EXCEPTION WHEN SQLSTATE '55000' THEN NULL;
  END;
  BEGIN
    UPDATE public.data_recommendation_adapter_failure_v1
    SET failure_code = failure_code WHERE adapter_failure_id = v_failure;
    RAISE EXCEPTION 'adapter failure UPDATE unexpectedly succeeded';
  EXCEPTION WHEN SQLSTATE '55000' THEN NULL;
  END;
  BEGIN
    DELETE FROM public.data_recommendation_adapter_conflict_observation_v1
    WHERE conflict_observation_id = v_conflict;
    RAISE EXCEPTION 'adapter conflict DELETE unexpectedly succeeded';
  EXCEPTION WHEN SQLSTATE '55000' THEN NULL;
  END;
END;
$$;

DO $$
DECLARE
  v_function regprocedure := to_regprocedure(
    'public.persist_recommendation_adapter_shadow_evidence_v1(character varying,character varying,character varying,character varying,character varying,character varying,character varying,character varying,character varying,character varying,character varying,timestamp with time zone,timestamp with time zone,character varying,character varying,character varying,character varying,character varying,character varying,timestamp with time zone,jsonb,character varying,character varying,character varying,boolean,character varying)');
  v_owner text;
  v_config text[];
BEGIN
  IF NOT has_function_privilege('jc_data_adapter_evidence_writer', v_function, 'EXECUTE') THEN
    RAISE EXCEPTION 'DP-4.5 writer execute grant missing';
  END IF;
  IF has_table_privilege('jc_data_adapter_evidence_writer',
      'public.data_recommendation_adapter_run_v1', 'INSERT')
     OR has_table_privilege('jc_data_adapter_evidence_writer',
      'public.data_recommendation_adapter_run_v1', 'UPDATE')
     OR has_table_privilege('jc_data_adapter_evidence_writer',
      'public.data_recommendation_adapter_run_v1', 'DELETE')
     OR has_table_privilege('jc_data_adapter_evidence_writer',
      'public.data_recommendation_adapter_run_v1', 'TRUNCATE')
     OR has_table_privilege('jc_data_adapter_evidence_writer',
      'public.data_recommendation_adapter_output_v1', 'INSERT') THEN
    RAISE EXCEPTION 'DP-4.5 writer has direct evidence mutation privilege';
  END IF;
  IF NOT has_table_privilege('jc_data_adapter_evidence_reader',
      'public.data_recommendation_adapter_safe_metrics_v1', 'SELECT')
     OR has_table_privilege('jc_data_adapter_evidence_reader',
      'public.data_recommendation_adapter_run_v1', 'SELECT')
     OR has_table_privilege('jc_data_adapter_evidence_reader',
      'public.data_recommendation_adapter_run_v1', 'INSERT')
     OR has_table_privilege('jc_data_adapter_evidence_reader',
      'public.data_recommendation_adapter_run_v1', 'UPDATE')
     OR has_table_privilege('jc_data_adapter_evidence_reader',
      'public.data_recommendation_adapter_run_v1', 'DELETE') THEN
    RAISE EXCEPTION 'DP-4.5 reader privilege boundary invalid';
  END IF;

  SELECT pg_get_userbyid(proowner), proconfig
    INTO v_owner, v_config
  FROM pg_proc WHERE oid = v_function;
  IF v_owner <> 'jc_data_adapter_evidence_function_owner' THEN
    RAISE EXCEPTION 'DP-4.5 function owner is not the approved NOLOGIN role';
  END IF;
  IF NOT EXISTS (
    SELECT 1 FROM pg_roles
    WHERE rolname = 'jc_data_adapter_evidence_function_owner'
      AND NOT rolcanlogin AND NOT rolsuper AND NOT rolcreaterole
      AND NOT rolcreatedb AND NOT rolreplication AND NOT rolbypassrls
  ) THEN
    RAISE EXCEPTION 'DP-4.5 function owner attributes are unsafe';
  END IF;
  IF v_config IS NULL
     OR NOT (v_config @> ARRAY['search_path=pg_catalog, public, pg_temp']::text[]) THEN
    RAISE EXCEPTION 'DP-4.5 function fixed search_path missing';
  END IF;

  IF EXISTS (
    SELECT 1
    FROM pg_proc proc
    CROSS JOIN LATERAL aclexplode(COALESCE(proc.proacl, acldefault('f', proc.proowner))) acl
    WHERE proc.oid = v_function AND acl.grantee = 0 AND acl.privilege_type = 'EXECUTE'
  ) THEN
    RAISE EXCEPTION 'PUBLIC can execute DP-4.5 persistence function';
  END IF;
END;
$$;

SET LOCAL ROLE jc_data_adapter_evidence_reader;
SELECT count(*) FROM public.data_recommendation_adapter_safe_metrics_v1;
RESET ROLE;

SET LOCAL ROLE jc_data_adapter_evidence_writer;
DO $$
BEGIN
  BEGIN
    UPDATE public.data_recommendation_adapter_run_v1 SET run_status = run_status WHERE false;
    RAISE EXCEPTION 'writer direct UPDATE unexpectedly succeeded';
  EXCEPTION WHEN insufficient_privilege THEN NULL;
  END;
END;
$$;
RESET ROLE;

DO $$
BEGIN
  IF EXISTS (
    SELECT 1 FROM information_schema.columns
    WHERE table_schema = 'public'
      AND table_name = 'data_recommendation_adapter_safe_metrics_v1'
      AND column_name IN (
        'actor_ref', 'user_id', 'session_ref', 'request_ref', 'mapped_payload',
        'source_fingerprint', 'idempotency_key', 'error_message', 'stack_trace', 'token'
      )
  ) THEN
    RAISE EXCEPTION 'DP-4.5 safe view exposes a forbidden dimension';
  END IF;
  IF NOT EXISTS (
    SELECT 1 FROM public.data_recommendation_adapter_safe_metrics_v1
    WHERE metric_name = 'run_count' AND metric_value >= 2
  ) OR NOT EXISTS (
    SELECT 1 FROM public.data_recommendation_adapter_safe_metrics_v1
    WHERE metric_name = 'duplicate_count' AND metric_value >= 2
  ) OR NOT EXISTS (
    SELECT 1 FROM public.data_recommendation_adapter_safe_metrics_v1
    WHERE metric_name = 'conflict_count' AND metric_value >= 2
  ) THEN
    RAISE EXCEPTION 'DP-4.5 safe aggregate metrics incomplete';
  END IF;

  IF EXISTS (
    SELECT 1 FROM pg_proc
    WHERE pronamespace = 'public'::regnamespace
      AND proname ~ '(purge|delete|erase).*recommendation_adapter'
  ) THEN
    RAISE EXCEPTION 'DP-4.5 automatic purge/delete executor must remain absent';
  END IF;
END;
$$;

DO $$
BEGIN
  IF EXISTS (
    SELECT 1
    FROM pg_class relation
    CROSS JOIN LATERAL aclexplode(COALESCE(relation.relacl, acldefault('r', relation.relowner))) acl
    WHERE relation.oid IN (
      'public.data_recommendation_adapter_run_v1'::regclass,
      'public.data_recommendation_adapter_output_v1'::regclass,
      'public.data_recommendation_adapter_failure_v1'::regclass,
      'public.data_recommendation_adapter_conflict_observation_v1'::regclass,
      'public.data_recommendation_adapter_safe_metrics_v1'::regclass
    ) AND acl.grantee = 0
  ) THEN
    RAISE EXCEPTION 'PUBLIC has DP-4.5 table or safe-view privilege';
  END IF;

  IF EXISTS (
    SELECT 1 FROM public.data_recommendation_adapter_run_v1
    WHERE retention_class <> 'adapter_evidence_90d'
       OR retention_policy_version <> 'data-retention-policy-v1'
       OR expires_at < created_at + interval '90 days'
  ) OR EXISTS (
    SELECT 1 FROM public.data_recommendation_adapter_output_v1
    WHERE retention_class <> 'adapter_evidence_90d'
       OR retention_policy_version <> 'data-retention-policy-v1'
       OR expires_at < created_at + interval '90 days'
  ) OR EXISTS (
    SELECT 1 FROM public.data_recommendation_adapter_failure_v1
    WHERE retention_class <> 'adapter_evidence_90d'
       OR retention_policy_version <> 'data-retention-policy-v1'
       OR expires_at < created_at + interval '90 days'
  ) OR EXISTS (
    SELECT 1 FROM public.data_recommendation_adapter_conflict_observation_v1
    WHERE retention_class <> 'adapter_evidence_90d'
       OR retention_policy_version <> 'data-retention-policy-v1'
       OR expires_at < created_at + interval '90 days'
  ) THEN
    RAISE EXCEPTION 'DP-4.5 retention metadata invalid';
  END IF;
END;
$$;

ROLLBACK;
