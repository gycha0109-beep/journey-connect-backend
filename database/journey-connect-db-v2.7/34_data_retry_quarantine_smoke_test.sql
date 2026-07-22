-- Journey Connect DB v2.7 DP-3 retry/quarantine smoke and contract validation
-- Target: PostgreSQL 15 and 18
-- This test is rollback-only and activates no production scheduler or replay path.

BEGIN;

DO $$
DECLARE
  v_now timestamptz := clock_timestamp();
  v_bytes bytea := convert_to('{}', 'UTF8');
  v_fingerprint varchar(64);
  v_index integer;
BEGIN
  v_fingerprint := encode(public.digest(v_bytes, 'sha256'), 'hex');
  FOR v_index IN 1..8 LOOP
    INSERT INTO public.data_platform_event_v1 (
      event_id, tenant, contract_version, schema_version, canonicalization_version,
      fingerprint_version, payload_fingerprint, fingerprint_canonical_bytes,
      producer_version, producer_build_id, event_family, event_type,
      occurred_at, received_at, actor_ref, session_ref, entity_ref,
      request_ref, correlation_ref, causation_ref, idempotency_key,
      canonical_payload, expires_at
    ) VALUES (
      'event:dp3-smoke-' || v_index, 'journey-connect', 'platform-event-v1',
      'user-behavior-event-v1', 'platform-event-canonical-json-v1',
      'platform-event-fingerprint-sha256-v1', v_fingerprint, v_bytes,
      'dp3-smoke-producer-v1', 'git:0000000000000000000000000000000000000000',
      'user_behavior', 'post_view', v_now, v_now,
      'subject:dp3-smoke-' || v_index, NULL, 'post:' || v_index,
      'request:dp3-smoke-' || v_index, 'correlation:dp3-smoke', NULL,
      'dp3-smoke-key-' || v_index, '{}'::jsonb, v_now + interval '365 days'
    );
  END LOOP;
END;
$$;

DO $$
DECLARE
  v_work uuid;
  v_claim record;
  v_heartbeat record;
  v_complete record;
  v_second record;
BEGIN
  v_work := public.register_data_projection_work_v1(
    'work:success', 'event:dp3-smoke-1', 'recommendation_profile',
    'recommendation-profile-input-v1', clock_timestamp());
  SELECT * INTO v_claim FROM public.claim_data_projection_work_v1('worker:success', 1);
  IF v_claim.work_id IS DISTINCT FROM v_work OR v_claim.attempt_number <> 1 THEN
    RAISE EXCEPTION 'DP-3 success claim failed';
  END IF;
  SELECT * INTO v_heartbeat FROM public.heartbeat_data_projection_work_v1(
    v_work, 'worker:success', v_claim.claim_token);
  IF NOT v_heartbeat.accepted THEN
    RAISE EXCEPTION 'DP-3 owner heartbeat failed';
  END IF;
  SELECT * INTO v_complete FROM public.complete_data_projection_work_v1(
    v_work, 'worker:success', v_claim.claim_token,
    'git:1111111111111111111111111111111111111111');
  IF NOT v_complete.accepted OR v_complete.processing_outcome <> 'retry_succeeded' THEN
    RAISE EXCEPTION 'DP-3 success completion failed';
  END IF;
  SELECT * INTO v_second FROM public.complete_data_projection_work_v1(
    v_work, 'worker:success', v_claim.claim_token,
    'git:1111111111111111111111111111111111111111');
  IF v_second.accepted OR v_second.error_code <> 'EVENT_CLAIM_STALE' THEN
    RAISE EXCEPTION 'DP-3 duplicate success was not rejected';
  END IF;
  IF (SELECT count(*) FROM public.data_projection_processing_attempt_v1
      WHERE work_id = v_work AND outcome = 'retry_succeeded') <> 1 THEN
    RAISE EXCEPTION 'DP-3 success evidence is not exactly once';
  END IF;
END;
$$;

DO $$
DECLARE
  v_work uuid;
  v_first record;
  v_second record;
  v_stale record;
  v_foreign_heartbeat record;
  v_complete record;
  v_now timestamptz;
BEGIN
  v_work := public.register_data_projection_work_v1(
    'work:lease-expiry', 'event:dp3-smoke-2', 'recommendation_profile',
    'recommendation-profile-input-v1', clock_timestamp());
  SELECT * INTO v_first FROM public.claim_data_projection_work_v1('worker:first', 1);
  UPDATE public.data_projection_work_state_v1
  SET claimed_at = statement_timestamp() - interval '61 seconds',
      lease_expires_at = statement_timestamp() - interval '1 second',
      updated_at = clock_timestamp()
  WHERE work_id = v_work;
  SELECT * INTO v_second FROM public.claim_data_projection_work_v1('worker:second', 1);
  IF v_second.work_id IS DISTINCT FROM v_work
     OR v_second.claim_token IS NOT DISTINCT FROM v_first.claim_token THEN
    RAISE EXCEPTION 'DP-3 expired lease was not reclaimed';
  END IF;
  SELECT * INTO v_stale FROM public.complete_data_projection_work_v1(
    v_work, 'worker:first', v_first.claim_token,
    'git:2222222222222222222222222222222222222222');
  IF v_stale.accepted OR v_stale.error_code <> 'EVENT_CLAIM_STALE' THEN
    RAISE EXCEPTION 'DP-3 stale completion was not rejected';
  END IF;
  SELECT * INTO v_foreign_heartbeat FROM public.heartbeat_data_projection_work_v1(
    v_work, 'worker:first', v_second.claim_token);
  IF v_foreign_heartbeat.accepted OR v_foreign_heartbeat.error_code <> 'EVENT_CLAIM_STALE' THEN
    RAISE EXCEPTION 'DP-3 foreign heartbeat was not rejected';
  END IF;
  SELECT * INTO v_complete FROM public.complete_data_projection_work_v1(
    v_work, 'worker:second', v_second.claim_token,
    'git:2222222222222222222222222222222222222222');
  IF NOT v_complete.accepted THEN
    RAISE EXCEPTION 'DP-3 reclaimed completion failed';
  END IF;
END;
$$;

DO $$
DECLARE
  v_work uuid;
  v_claim record;
  v_fail record;
BEGIN
  v_work := public.register_data_projection_work_v1(
    'work:retry', 'event:dp3-smoke-3', 'search_projection',
    'search-projection-v1', clock_timestamp());
  SELECT * INTO v_claim FROM public.claim_data_projection_work_v1('worker:retry', 1);
  SELECT * INTO v_fail FROM public.fail_data_projection_work_v1(
    v_work, 'worker:retry', v_claim.claim_token,
    'dependency_unavailable', 'DEPENDENCY_UNAVAILABLE',
    encode(public.digest(convert_to('retry-1', 'UTF8'), 'sha256'), 'hex'),
    'git:3333333333333333333333333333333333333333');
  IF NOT v_fail.accepted OR v_fail.processing_outcome <> 'retry_scheduled'
     OR v_fail.next_ready_at IS NULL THEN
    RAISE EXCEPTION 'DP-3 retry scheduling failed';
  END IF;
  IF (SELECT attempt_number FROM public.data_projection_work_state_v1 WHERE work_id = v_work) <> 2 THEN
    RAISE EXCEPTION 'DP-3 retry attempt number did not advance';
  END IF;
END;
$$;

DO $$
DECLARE
  v_work uuid;
  v_claim record;
  v_fail record;
  v_review uuid;
BEGIN
  v_work := public.register_data_projection_work_v1(
    'work:immediate-quarantine', 'event:dp3-smoke-4', 'content_projection',
    'content-projection-v1', clock_timestamp());
  SELECT * INTO v_claim FROM public.claim_data_projection_work_v1('worker:quarantine', 1);
  SELECT * INTO v_fail FROM public.fail_data_projection_work_v1(
    v_work, 'worker:quarantine', v_claim.claim_token,
    'schema_unsupported', 'SCHEMA_UNSUPPORTED',
    encode(public.digest(convert_to('schema', 'UTF8'), 'sha256'), 'hex'),
    'git:4444444444444444444444444444444444444444');
  IF NOT v_fail.accepted OR v_fail.processing_outcome <> 'quarantined'
     OR v_fail.error_code <> 'EVENT_QUARANTINED' THEN
    RAISE EXCEPTION 'DP-3 immediate quarantine failed';
  END IF;
  v_review := public.record_data_quarantine_review_v1(
    v_fail.quarantine_evidence_id, 'reviewed_retain', 'subject:reviewer-1',
    'operation:review-retain', 'quarantine-review-policy-v1');
  IF v_review IS NULL THEN
    RAISE EXCEPTION 'DP-3 retain review evidence failed';
  END IF;
  v_review := public.record_data_quarantine_review_v1(
    v_fail.quarantine_evidence_id, 'reviewed_release_requested', 'subject:reviewer-1',
    'operation:review-release', 'quarantine-review-policy-v1',
    'identity-mapping-v1', 'content-projection-v2', 'content-consumer-v2',
    'dryrun:dp3-smoke', true);
  IF v_review IS NULL THEN
    RAISE EXCEPTION 'DP-3 release-request evidence failed';
  END IF;
END;
$$;

DO $$
DECLARE
  v_work uuid;
  v_claim record;
  v_fail record;
BEGIN
  v_work := public.register_data_projection_work_v1(
    'work:unknown', 'event:dp3-smoke-5', 'content_projection_unknown',
    'content-projection-v1', clock_timestamp());
  SELECT * INTO v_claim FROM public.claim_data_projection_work_v1('worker:unknown', 1);
  SELECT * INTO v_fail FROM public.fail_data_projection_work_v1(
    v_work, 'worker:unknown', v_claim.claim_token,
    'future_unknown_failure', 'UNKNOWN_FAILURE',
    encode(public.digest(convert_to('unknown', 'UTF8'), 'sha256'), 'hex'),
    'git:5555555555555555555555555555555555555555');
  IF NOT v_fail.accepted OR
     (SELECT reason FROM public.data_projection_quarantine_evidence_v1 WHERE work_id = v_work)
       <> 'unclassified_failure' THEN
    RAISE EXCEPTION 'DP-3 unknown failure did not fail closed';
  END IF;
END;
$$;

DO $$
DECLARE
  v_work uuid;
  v_claim record;
  v_fail record;
  v_attempt integer;
  v_signature varchar(64);
BEGIN
  v_work := public.register_data_projection_work_v1(
    'work:exhaustion', 'event:dp3-smoke-6', 'trip_projection',
    'trip-projection-v1', clock_timestamp());
  FOR v_attempt IN 1..6 LOOP
    SELECT * INTO v_claim FROM public.claim_data_projection_work_v1('worker:exhaustion', 1);
    IF v_claim.work_id IS DISTINCT FROM v_work OR v_claim.attempt_number <> v_attempt THEN
      RAISE EXCEPTION 'DP-3 exhaustion claim failed at attempt %', v_attempt;
    END IF;
    v_signature := encode(public.digest(convert_to('exhaust-' || v_attempt, 'UTF8'), 'sha256'), 'hex');
    SELECT * INTO v_fail FROM public.fail_data_projection_work_v1(
      v_work, 'worker:exhaustion', v_claim.claim_token,
      'rate_limited', 'RATE_LIMITED_' || v_attempt, v_signature,
      'git:6666666666666666666666666666666666666666');
    IF v_attempt < 6 THEN
      IF v_fail.processing_outcome <> 'retry_scheduled' THEN
        RAISE EXCEPTION 'DP-3 retry exhausted too early at attempt %', v_attempt;
      END IF;
      UPDATE public.data_projection_work_state_v1
      SET ready_at = clock_timestamp(), updated_at = clock_timestamp()
      WHERE work_id = v_work;
    ELSE
      IF v_fail.processing_outcome <> 'retry_exhausted'
         OR v_fail.error_code <> 'EVENT_RETRY_EXHAUSTED' THEN
        RAISE EXCEPTION 'DP-3 final exhaustion quarantine failed';
      END IF;
    END IF;
  END LOOP;
  IF (SELECT count(*) FROM public.data_projection_quarantine_evidence_v1
      WHERE work_id = v_work AND reason = 'retry_exhausted') <> 1 THEN
    RAISE EXCEPTION 'DP-3 exhausted quarantine is not exactly once';
  END IF;
  IF EXISTS (SELECT 1 FROM public.data_projection_retry_schedule_v1
             WHERE work_id = v_work AND attempt_number > 6) THEN
    RAISE EXCEPTION 'DP-3 created an execution beyond the maximum';
  END IF;
END;
$$;

DO $$
DECLARE
  v_work uuid;
  v_claim record;
  v_fail record;
  v_index integer;
  v_signature varchar(64) := encode(public.digest(convert_to('same-signature', 'UTF8'), 'sha256'), 'hex');
BEGIN
  v_work := public.register_data_projection_work_v1(
    'work:repeated-signature', 'event:dp3-smoke-7', 'quality_projection',
    'quality-projection-v1', clock_timestamp());
  FOR v_index IN 1..3 LOOP
    SELECT * INTO v_claim FROM public.claim_data_projection_work_v1('worker:repeated', 1);
    SELECT * INTO v_fail FROM public.fail_data_projection_work_v1(
      v_work, 'worker:repeated', v_claim.claim_token,
      'serialization_failure', 'SERIALIZATION_FAILURE', v_signature,
      'git:7777777777777777777777777777777777777777');
    IF v_index < 3 THEN
      UPDATE public.data_projection_work_state_v1
      SET ready_at = clock_timestamp(), updated_at = clock_timestamp()
      WHERE work_id = v_work;
    END IF;
  END LOOP;
  IF (SELECT reason FROM public.data_projection_quarantine_evidence_v1 WHERE work_id = v_work)
       <> 'repeated_deterministic_failure' THEN
    RAISE EXCEPTION 'DP-3 repeated signature early quarantine failed';
  END IF;
END;
$$;

DO $$
DECLARE
  v_schedule uuid;
  v_quarantine uuid;
BEGIN
  SELECT retry_schedule_id INTO v_schedule FROM public.data_projection_retry_schedule_v1 LIMIT 1;
  BEGIN
    UPDATE public.data_projection_retry_schedule_v1
    SET ready_at = ready_at + interval '1 second'
    WHERE retry_schedule_id = v_schedule;
    RAISE EXCEPTION 'append-only retry schedule update unexpectedly succeeded';
  EXCEPTION WHEN SQLSTATE '55000' THEN
    NULL;
  END;
  BEGIN
    DELETE FROM public.data_projection_retry_schedule_v1 WHERE retry_schedule_id = v_schedule;
    RAISE EXCEPTION 'append-only retry schedule delete unexpectedly succeeded';
  EXCEPTION WHEN SQLSTATE '55000' THEN
    NULL;
  END;
  SELECT quarantine_evidence_id INTO v_quarantine FROM public.data_projection_quarantine_evidence_v1 LIMIT 1;
  BEGIN
    UPDATE public.data_projection_quarantine_evidence_v1
    SET reason = 'manual_hold'
    WHERE quarantine_evidence_id = v_quarantine;
    RAISE EXCEPTION 'append-only quarantine update unexpectedly succeeded';
  EXCEPTION WHEN SQLSTATE '55000' THEN
    NULL;
  END;
END;
$$;

DO $$
BEGIN
  IF NOT has_function_privilege('jc_data_retry_processor',
      'public.claim_data_projection_work_v1(character varying,integer)', 'EXECUTE')
     OR NOT has_function_privilege('jc_data_retry_processor',
      'public.heartbeat_data_projection_work_v1(uuid,character varying,uuid)', 'EXECUTE')
     OR NOT has_function_privilege('jc_data_retry_processor',
      'public.complete_data_projection_work_v1(uuid,character varying,uuid,character varying)', 'EXECUTE')
     OR NOT has_function_privilege('jc_data_retry_processor',
      'public.fail_data_projection_work_v1(uuid,character varying,uuid,character varying,character varying,character varying,character varying)', 'EXECUTE') THEN
    RAISE EXCEPTION 'DP-3 processor execution grant missing';
  END IF;
  IF has_table_privilege('jc_data_retry_processor', 'public.data_projection_work_state_v1', 'UPDATE')
     OR has_table_privilege('jc_data_retry_processor', 'public.data_platform_event_v1', 'UPDATE')
     OR has_table_privilege('jc_data_retry_processor', 'public.data_event_idempotency_binding_v1', 'UPDATE') THEN
    RAISE EXCEPTION 'DP-3 processor has a forbidden direct mutation grant';
  END IF;
  IF NOT has_table_privilege('jc_data_quarantine_reviewer',
      'public.data_projection_quarantine_reviewer_v1', 'SELECT')
     OR has_table_privilege('jc_data_quarantine_reviewer',
      'public.data_projection_work_state_v1', 'UPDATE') THEN
    RAISE EXCEPTION 'DP-3 reviewer privilege boundary invalid';
  END IF;
  IF has_function_privilege('jc_data_replay_executor',
      'public.claim_data_projection_work_v1(character varying,integer)', 'EXECUTE')
     OR has_function_privilege('jc_data_replay_executor',
      'public.record_data_quarantine_review_v1(uuid,character varying,character varying,character varying,character varying,character varying,character varying,character varying,character varying,boolean)', 'EXECUTE') THEN
    RAISE EXCEPTION 'DP-3 replay executor received an unauthorized execution grant';
  END IF;
  IF has_table_privilege('public', 'public.data_projection_retry_observability_v1', 'SELECT')
     OR has_table_privilege('public', 'public.data_projection_work_state_v1', 'SELECT') THEN
    RAISE EXCEPTION 'DP-3 PUBLIC access was not revoked';
  END IF;
END;
$$;

DO $$
BEGIN
  IF NOT EXISTS (SELECT 1 FROM public.data_projection_retry_observability_v1) THEN
    RAISE EXCEPTION 'DP-3 observability view returned no rows';
  END IF;
  IF NOT EXISTS (SELECT 1 FROM public.data_projection_quarantine_reason_metrics_v1) THEN
    RAISE EXCEPTION 'DP-3 quarantine reason metrics returned no rows';
  END IF;
  IF EXISTS (
    SELECT 1 FROM information_schema.routines
    WHERE routine_schema = 'public'
      AND routine_name ~ '(purge|delete|replay_execute|release_quarantine)'
      AND routine_name LIKE 'data_%'
  ) THEN
    RAISE EXCEPTION 'DP-3 created a prohibited purge/replay/release executor';
  END IF;
END;
$$;

SELECT 'DP3_POSTGRESQL_SMOKE_PASS' AS dp3_result;

ROLLBACK;
