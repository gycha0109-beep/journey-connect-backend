-- Journey Connect DB v2.7 extension - DP-3 retry processing procedures and roles
-- Target: PostgreSQL 15 and 18
-- Prerequisite: canonical SQL 01..32.

BEGIN;

DO $$
BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'jc_data_retry_processor') THEN
    CREATE ROLE jc_data_retry_processor
      NOLOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOREPLICATION NOBYPASSRLS;
  END IF;
  IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'jc_data_quarantine_reviewer') THEN
    CREATE ROLE jc_data_quarantine_reviewer
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
    'jc_data_retry_processor', 'jc_data_quarantine_reviewer', 'jc_data_replay_executor'
  )
    AND (rolsuper OR rolcreaterole OR rolcreatedb OR rolcanlogin OR rolreplication OR rolbypassrls);

  IF v_unsafe_roles IS NOT NULL THEN
    RAISE EXCEPTION 'Unsafe pre-existing DP-3 role attributes: %', v_unsafe_roles;
  END IF;
END;
$$;

REVOKE jc_data_retry_processor FROM jc_app, jc_auth, jc_admin, jc_security_owner,
  jc_recommendation, jc_data_event_writer, jc_data_event_reader,
  jc_data_quarantine_reviewer, jc_data_replay_executor;
REVOKE jc_data_quarantine_reviewer FROM jc_app, jc_auth, jc_admin, jc_security_owner,
  jc_recommendation, jc_data_event_writer, jc_data_event_reader,
  jc_data_retry_processor, jc_data_replay_executor;
REVOKE jc_data_replay_executor FROM jc_data_retry_processor, jc_data_quarantine_reviewer;
REVOKE jc_app, jc_auth, jc_admin, jc_recommendation, jc_data_event_writer
  FROM jc_data_retry_processor, jc_data_quarantine_reviewer;

CREATE OR REPLACE FUNCTION public.data_retry_jitter_basis_points_v1(
  p_work_ref varchar,
  p_automatic_retry_number integer
)
RETURNS smallint
LANGUAGE plpgsql
IMMUTABLE
STRICT
SECURITY INVOKER
SET search_path = pg_catalog, public, pg_temp
AS $$
DECLARE
  v_digest bytea;
  v_prefix bigint;
BEGIN
  IF p_work_ref !~ '^work:[A-Za-z0-9][A-Za-z0-9._~-]{0,127}$' THEN
    RAISE EXCEPTION 'Invalid privacy-safe work reference.' USING ERRCODE = '22023';
  END IF;
  IF p_automatic_retry_number NOT BETWEEN 1 AND 5 THEN
    RAISE EXCEPTION 'Automatic retry number must be 1..5.' USING ERRCODE = '22023';
  END IF;

  v_digest := public.digest(
    convert_to(concat_ws(E'\x1f', 'data-projection-retry-v1', p_work_ref,
      p_automatic_retry_number::text), 'UTF8'),
    'sha256');
  v_prefix := get_byte(v_digest, 0)::bigint * 16777216
    + get_byte(v_digest, 1)::bigint * 65536
    + get_byte(v_digest, 2)::bigint * 256
    + get_byte(v_digest, 3)::bigint;
  RETURN (v_prefix % 1001)::smallint;
END;
$$;

CREATE OR REPLACE FUNCTION public.data_retry_delay_v1(
  p_work_ref varchar,
  p_automatic_retry_number integer
)
RETURNS interval
LANGUAGE plpgsql
IMMUTABLE
STRICT
SECURITY INVOKER
SET search_path = pg_catalog, public, pg_temp
AS $$
DECLARE
  v_base interval;
  v_jitter smallint;
BEGIN
  v_base := CASE p_automatic_retry_number
    WHEN 1 THEN interval '1 minute'
    WHEN 2 THEN interval '5 minutes'
    WHEN 3 THEN interval '30 minutes'
    WHEN 4 THEN interval '2 hours'
    WHEN 5 THEN interval '12 hours'
    ELSE NULL
  END;
  IF v_base IS NULL THEN
    RAISE EXCEPTION 'Automatic retry number must be 1..5.' USING ERRCODE = '22023';
  END IF;
  v_jitter := public.data_retry_jitter_basis_points_v1(p_work_ref, p_automatic_retry_number);
  RETURN v_base + (v_base * (v_jitter::double precision / 10000.0));
END;
$$;

CREATE OR REPLACE FUNCTION public.record_data_claim_rejection_v1(
  p_work_id uuid,
  p_action varchar,
  p_claim_token uuid,
  p_worker_ref varchar
)
RETURNS void
LANGUAGE sql
SECURITY DEFINER
SET search_path = pg_catalog, public, pg_temp
AS $$
  INSERT INTO public.data_projection_claim_rejection_evidence_v1 (
    work_id, attempted_action, supplied_claim_token, supplied_worker_ref, rejection_code
  ) VALUES (
    p_work_id, p_action, p_claim_token, p_worker_ref, 'EVENT_CLAIM_STALE'
  );
$$;

CREATE OR REPLACE FUNCTION public.register_data_projection_work_v1(
  p_work_ref varchar,
  p_source_event_ref varchar,
  p_projection_name varchar,
  p_projection_version varchar,
  p_ready_at timestamptz DEFAULT CURRENT_TIMESTAMP
)
RETURNS uuid
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = pg_catalog, public, pg_temp
AS $$
DECLARE
  v_event_family varchar(40);
  v_existing public.data_projection_work_state_v1%ROWTYPE;
  v_work_id uuid;
  v_now timestamptz := clock_timestamp();
  v_ready_at timestamptz;
BEGIN
  IF p_work_ref IS NULL OR p_work_ref !~ '^work:[A-Za-z0-9][A-Za-z0-9._~-]{0,127}$' THEN
    RAISE EXCEPTION 'Invalid work reference.' USING ERRCODE = '22023';
  END IF;
  IF NOT public.data_projection_name_valid_v1(p_projection_name)
     OR NOT public.data_projection_version_valid_v1(p_projection_version) THEN
    RAISE EXCEPTION 'Invalid projection name or version.' USING ERRCODE = '22023';
  END IF;
  IF p_ready_at IS NULL OR p_ready_at < v_now - interval '5 minutes' THEN
    RAISE EXCEPTION 'Initial ready time is invalid.' USING ERRCODE = '22023';
  END IF;

  v_ready_at := GREATEST(p_ready_at, v_now);

  SELECT event_family INTO v_event_family
  FROM public.data_platform_event_v1
  WHERE event_id = p_source_event_ref;
  IF NOT FOUND THEN
    RAISE EXCEPTION 'Canonical source event is missing.' USING ERRCODE = '23503';
  END IF;

  PERFORM pg_advisory_xact_lock(hashtextextended(p_work_ref, 0));
  SELECT * INTO v_existing
  FROM public.data_projection_work_state_v1
  WHERE work_ref = p_work_ref
     OR (source_event_ref = p_source_event_ref
         AND projection_name = p_projection_name
         AND projection_version = p_projection_version)
  FOR UPDATE;

  IF FOUND THEN
    IF v_existing.work_ref = p_work_ref
       AND v_existing.source_event_ref = p_source_event_ref
       AND v_existing.projection_name = p_projection_name
       AND v_existing.projection_version = p_projection_version THEN
      RETURN v_existing.work_id;
    END IF;
    RAISE EXCEPTION 'Projection work identity conflict.' USING ERRCODE = '23505';
  END IF;

  INSERT INTO public.data_projection_work_state_v1 (
    work_ref, source_event_ref, event_family, projection_name, projection_version,
    retry_policy_version, state, attempt_number, ready_at, expires_at
  ) VALUES (
    p_work_ref, p_source_event_ref, v_event_family, p_projection_name, p_projection_version,
    'data-projection-retry-v1', 'retry_scheduled', 1, v_ready_at,
    v_now + interval '90 days'
  ) RETURNING work_id INTO v_work_id;

  INSERT INTO public.data_projection_retry_schedule_v1 (
    work_id, source_event_ref, projection_name, projection_version,
    retry_policy_version, attempt_number, scheduled_at, ready_at,
    scheduling_jitter_basis_points, expires_at
  ) VALUES (
    v_work_id, p_source_event_ref, p_projection_name, p_projection_version,
    'data-projection-retry-v1', 1, v_now, v_ready_at, 0,
    v_now + interval '90 days'
  );

  RETURN v_work_id;
END;
$$;

CREATE OR REPLACE FUNCTION public.claim_data_projection_work_v1(
  p_worker_ref varchar,
  p_max_batch integer DEFAULT 100
)
RETURNS TABLE (
  work_id uuid,
  source_event_ref varchar,
  projection_name varchar,
  projection_version varchar,
  attempt_number smallint,
  claim_token uuid,
  lease_expires_at timestamptz
)
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = pg_catalog, public, pg_temp
AS $$
DECLARE
  v_work public.data_projection_work_state_v1%ROWTYPE;
  v_now timestamptz := clock_timestamp();
  v_token uuid;
  v_previous_token uuid;
BEGIN
  IF p_worker_ref IS NULL OR p_worker_ref !~ '^worker:[A-Za-z0-9][A-Za-z0-9._~-]{0,127}$' THEN
    RAISE EXCEPTION 'Invalid worker reference.' USING ERRCODE = '22023';
  END IF;
  IF p_max_batch IS NULL OR p_max_batch NOT BETWEEN 1 AND 100 THEN
    RAISE EXCEPTION 'Claim batch must be 1..100.' USING ERRCODE = '22023';
  END IF;

  FOR v_work IN
    SELECT state.*
    FROM public.data_projection_work_state_v1 state
    WHERE (state.state = 'retry_scheduled' AND state.ready_at <= v_now)
       OR (state.state = 'retry_claimed' AND state.lease_expires_at <= v_now)
    ORDER BY state.ready_at, state.work_id
    LIMIT p_max_batch
    FOR UPDATE SKIP LOCKED
  LOOP
    v_previous_token := CASE WHEN v_work.state = 'retry_claimed' THEN v_work.claim_token ELSE NULL END;
    v_token := public.gen_random_uuid();

    UPDATE public.data_projection_work_state_v1 state
    SET state = 'retry_claimed',
        claim_token = v_token,
        worker_ref = p_worker_ref,
        claimed_at = v_now,
        lease_expires_at = v_now + interval '60 seconds',
        updated_at = v_now
    WHERE state.work_id = v_work.work_id;

    INSERT INTO public.data_projection_claim_evidence_v1 (
      work_id, source_event_ref, projection_name, projection_version,
      attempt_number, worker_ref, claim_token, reclaimed_from_claim_token,
      claimed_at, lease_expires_at, expires_at
    ) VALUES (
      v_work.work_id, v_work.source_event_ref, v_work.projection_name, v_work.projection_version,
      v_work.attempt_number, p_worker_ref, v_token, v_previous_token,
      v_now, v_now + interval '60 seconds', v_now + interval '90 days'
    );

    RETURN QUERY SELECT v_work.work_id, v_work.source_event_ref,
      v_work.projection_name, v_work.projection_version, v_work.attempt_number,
      v_token, v_now + interval '60 seconds';
  END LOOP;
END;
$$;

CREATE OR REPLACE FUNCTION public.heartbeat_data_projection_work_v1(
  p_work_id uuid,
  p_worker_ref varchar,
  p_claim_token uuid
)
RETURNS TABLE (accepted boolean, renewed_lease_expires_at timestamptz, error_code varchar)
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = pg_catalog, public, pg_temp
AS $$
DECLARE
  v_work public.data_projection_work_state_v1%ROWTYPE;
  v_now timestamptz := clock_timestamp();
  v_new_expiry timestamptz;
BEGIN
  SELECT * INTO v_work
  FROM public.data_projection_work_state_v1
  WHERE work_id = p_work_id
  FOR UPDATE;
  IF NOT FOUND THEN
    RAISE EXCEPTION 'Projection work does not exist.' USING ERRCODE = 'P0002';
  END IF;

  IF v_work.state <> 'retry_claimed'
     OR v_work.claim_token IS DISTINCT FROM p_claim_token
     OR v_work.worker_ref IS DISTINCT FROM p_worker_ref
     OR v_work.lease_expires_at <= v_now THEN
    PERFORM public.record_data_claim_rejection_v1(p_work_id, 'heartbeat', p_claim_token, p_worker_ref);
    RETURN QUERY SELECT false, NULL::timestamptz, 'EVENT_CLAIM_STALE'::varchar;
    RETURN;
  END IF;

  v_new_expiry := v_now + interval '60 seconds';
  UPDATE public.data_projection_work_state_v1
  SET claimed_at = v_now,
      lease_expires_at = v_new_expiry,
      updated_at = v_now
  WHERE work_id = p_work_id;

  INSERT INTO public.data_projection_heartbeat_evidence_v1 (
    work_id, claim_token, worker_ref, heartbeat_at,
    previous_lease_expires_at, renewed_lease_expires_at, expires_at
  ) VALUES (
    p_work_id, p_claim_token, p_worker_ref, v_now,
    v_work.lease_expires_at, v_new_expiry, v_now + interval '90 days'
  );

  RETURN QUERY SELECT true, v_new_expiry, NULL::varchar;
END;
$$;

CREATE OR REPLACE FUNCTION public.complete_data_projection_work_v1(
  p_work_id uuid,
  p_worker_ref varchar,
  p_claim_token uuid,
  p_processor_build_id varchar
)
RETURNS TABLE (accepted boolean, processing_outcome varchar, error_code varchar)
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = pg_catalog, public, pg_temp
AS $$
DECLARE
  v_work public.data_projection_work_state_v1%ROWTYPE;
  v_schedule_id uuid;
  v_now timestamptz := clock_timestamp();
BEGIN
  IF p_processor_build_id IS NULL OR p_processor_build_id !~ '^git:[0-9a-f]{40}$' THEN
    RAISE EXCEPTION 'Invalid processor build ID.' USING ERRCODE = '22023';
  END IF;

  SELECT * INTO v_work
  FROM public.data_projection_work_state_v1
  WHERE work_id = p_work_id
  FOR UPDATE;
  IF NOT FOUND THEN
    RAISE EXCEPTION 'Projection work does not exist.' USING ERRCODE = 'P0002';
  END IF;

  IF v_work.state <> 'retry_claimed'
     OR v_work.claim_token IS DISTINCT FROM p_claim_token
     OR v_work.worker_ref IS DISTINCT FROM p_worker_ref
     OR v_work.lease_expires_at <= v_now THEN
    PERFORM public.record_data_claim_rejection_v1(p_work_id, 'complete', p_claim_token, p_worker_ref);
    RETURN QUERY SELECT false, NULL::varchar, 'EVENT_CLAIM_STALE'::varchar;
    RETURN;
  END IF;

  SELECT retry_schedule_id INTO v_schedule_id
  FROM public.data_projection_retry_schedule_v1
  WHERE work_id = p_work_id AND attempt_number = v_work.attempt_number;

  INSERT INTO public.data_projection_processing_attempt_v1 (
    work_id, retry_schedule_ref, source_event_ref, projection_name, projection_version,
    attempt_number, worker_ref, claim_token, claimed_at, lease_expires_at,
    started_at, completed_at, outcome, processor_build_id, expires_at
  ) VALUES (
    v_work.work_id, v_schedule_id, v_work.source_event_ref, v_work.projection_name,
    v_work.projection_version, v_work.attempt_number, p_worker_ref, p_claim_token,
    v_work.claimed_at, v_work.lease_expires_at, v_work.claimed_at, v_now,
    'retry_succeeded', p_processor_build_id, v_now + interval '90 days'
  );

  UPDATE public.data_projection_work_state_v1
  SET state = 'retry_succeeded',
      claim_token = NULL, worker_ref = NULL, claimed_at = NULL, lease_expires_at = NULL,
      terminal_outcome = 'retry_succeeded', terminal_at = v_now, updated_at = v_now
  WHERE work_id = p_work_id;

  RETURN QUERY SELECT true, 'retry_succeeded'::varchar, NULL::varchar;
END;
$$;

CREATE OR REPLACE FUNCTION public.fail_data_projection_work_v1(
  p_work_id uuid,
  p_worker_ref varchar,
  p_claim_token uuid,
  p_failure_class varchar,
  p_failure_code varchar,
  p_failure_signature varchar,
  p_processor_build_id varchar
)
RETURNS TABLE (
  accepted boolean,
  processing_outcome varchar,
  next_ready_at timestamptz,
  quarantine_evidence_id uuid,
  error_code varchar
)
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = pg_catalog, public, pg_temp
AS $$
DECLARE
  v_work public.data_projection_work_state_v1%ROWTYPE;
  v_schedule_id uuid;
  v_attempt_id uuid;
  v_quarantine_id uuid;
  v_now timestamptz := clock_timestamp();
  v_failure_class varchar(64);
  v_same_signature_count integer;
  v_jitter smallint;
  v_delay interval;
  v_ready_at timestamptz;
  v_reason varchar(64);
  v_outcome varchar(32);
BEGIN
  IF p_failure_code IS NULL OR p_failure_code !~ '^[A-Z][A-Z0-9_]{0,63}$'
     OR p_failure_signature IS NULL OR p_failure_signature !~ '^[0-9a-f]{64}$'
     OR p_processor_build_id IS NULL OR p_processor_build_id !~ '^git:[0-9a-f]{40}$' THEN
    RAISE EXCEPTION 'Invalid bounded failure evidence.' USING ERRCODE = '22023';
  END IF;

  v_failure_class := CASE
    WHEN public.data_retry_failure_class_valid_v1(p_failure_class) THEN p_failure_class
    ELSE 'unclassified_failure'
  END;

  SELECT * INTO v_work
  FROM public.data_projection_work_state_v1
  WHERE work_id = p_work_id
  FOR UPDATE;
  IF NOT FOUND THEN
    RAISE EXCEPTION 'Projection work does not exist.' USING ERRCODE = 'P0002';
  END IF;

  IF v_work.state <> 'retry_claimed'
     OR v_work.claim_token IS DISTINCT FROM p_claim_token
     OR v_work.worker_ref IS DISTINCT FROM p_worker_ref
     OR v_work.lease_expires_at <= v_now THEN
    PERFORM public.record_data_claim_rejection_v1(p_work_id, 'fail', p_claim_token, p_worker_ref);
    RETURN QUERY SELECT false, NULL::varchar, NULL::timestamptz, NULL::uuid,
      'EVENT_CLAIM_STALE'::varchar;
    RETURN;
  END IF;

  SELECT retry_schedule_id INTO v_schedule_id
  FROM public.data_projection_retry_schedule_v1
  WHERE work_id = p_work_id AND attempt_number = v_work.attempt_number;

  SELECT count(*) INTO v_same_signature_count
  FROM (
    SELECT failure_signature
    FROM public.data_projection_processing_attempt_v1
    WHERE work_id = p_work_id AND failure_signature IS NOT NULL
    ORDER BY completed_at DESC
    LIMIT 2
  ) recent
  WHERE recent.failure_signature = p_failure_signature;

  IF public.data_retryable_failure_class_v1(v_failure_class)
     AND v_work.attempt_number < 6
     AND v_same_signature_count < 2 THEN
    v_jitter := public.data_retry_jitter_basis_points_v1(v_work.work_ref, v_work.attempt_number);
    v_delay := public.data_retry_delay_v1(v_work.work_ref, v_work.attempt_number);
    v_ready_at := v_now + v_delay;

    INSERT INTO public.data_projection_processing_attempt_v1 (
      work_id, retry_schedule_ref, source_event_ref, projection_name, projection_version,
      attempt_number, worker_ref, claim_token, claimed_at, lease_expires_at,
      started_at, completed_at, outcome, failure_class, failure_code,
      failure_signature, processor_build_id, expires_at
    ) VALUES (
      v_work.work_id, v_schedule_id, v_work.source_event_ref, v_work.projection_name,
      v_work.projection_version, v_work.attempt_number, p_worker_ref, p_claim_token,
      v_work.claimed_at, v_work.lease_expires_at, v_work.claimed_at, v_now,
      'retry_scheduled', v_failure_class, p_failure_code, p_failure_signature,
      p_processor_build_id, v_now + interval '90 days'
    );

    INSERT INTO public.data_projection_retry_schedule_v1 (
      work_id, source_event_ref, projection_name, projection_version,
      retry_policy_version, attempt_number, scheduled_at, ready_at,
      failure_class, failure_code, failure_signature,
      scheduling_jitter_basis_points, expires_at
    ) VALUES (
      v_work.work_id, v_work.source_event_ref, v_work.projection_name,
      v_work.projection_version, v_work.retry_policy_version,
      v_work.attempt_number + 1, v_now, v_ready_at,
      v_failure_class, p_failure_code, p_failure_signature,
      v_jitter, v_now + interval '90 days'
    );

    UPDATE public.data_projection_work_state_v1
    SET state = 'retry_scheduled', attempt_number = attempt_number + 1,
        ready_at = v_ready_at,
        claim_token = NULL, worker_ref = NULL, claimed_at = NULL, lease_expires_at = NULL,
        updated_at = v_now
    WHERE work_id = p_work_id;

    RETURN QUERY SELECT true, 'retry_scheduled'::varchar, v_ready_at, NULL::uuid, NULL::varchar;
    RETURN;
  END IF;

  IF public.data_retryable_failure_class_v1(v_failure_class) AND v_work.attempt_number >= 6 THEN
    v_reason := 'retry_exhausted';
    v_outcome := 'retry_exhausted';
  ELSIF public.data_retryable_failure_class_v1(v_failure_class) AND v_same_signature_count >= 2 THEN
    v_reason := 'repeated_deterministic_failure';
    v_outcome := 'quarantined';
  ELSE
    v_reason := CASE v_failure_class
      WHEN 'schema_unsupported' THEN 'schema_unsupported'
      WHEN 'source_hash_mismatch' THEN 'source_hash_mismatch'
      WHEN 'source_binding_invalid' THEN 'source_binding_invalid'
      WHEN 'payload_unmappable' THEN 'payload_unmappable'
      WHEN 'payload_too_large' THEN 'payload_too_large'
      WHEN 'privacy_policy_violation' THEN 'privacy_policy_violation'
      WHEN 'projection_invariant_failed' THEN 'projection_invariant_failed'
      WHEN 'lineage_source_missing' THEN 'lineage_source_missing'
      WHEN 'manual_hold' THEN 'manual_hold'
      ELSE 'unclassified_failure'
    END;
    v_outcome := 'quarantined';
  END IF;

  INSERT INTO public.data_projection_processing_attempt_v1 (
    work_id, retry_schedule_ref, source_event_ref, projection_name, projection_version,
    attempt_number, worker_ref, claim_token, claimed_at, lease_expires_at,
    started_at, completed_at, outcome, failure_class, failure_code,
    failure_signature, processor_build_id, expires_at
  ) VALUES (
    v_work.work_id, v_schedule_id, v_work.source_event_ref, v_work.projection_name,
    v_work.projection_version, v_work.attempt_number, p_worker_ref, p_claim_token,
    v_work.claimed_at, v_work.lease_expires_at, v_work.claimed_at, v_now,
    v_outcome, v_failure_class, p_failure_code, p_failure_signature,
    p_processor_build_id, v_now + interval '90 days'
  ) RETURNING processing_attempt_id INTO v_attempt_id;

  INSERT INTO public.data_projection_quarantine_evidence_v1 AS quarantine (
    work_id, source_event_ref, projection_name, projection_version, reason,
    failure_signature, triggering_attempt_ref, operation_ref, actor_ref, expires_at
  ) VALUES (
    v_work.work_id, v_work.source_event_ref, v_work.projection_name,
    v_work.projection_version, v_reason, p_failure_signature, v_attempt_id,
    'operation:retry-' || public.gen_random_uuid()::text,
    p_worker_ref, v_now + interval '90 days'
  ) RETURNING quarantine.quarantine_evidence_id INTO v_quarantine_id;

  UPDATE public.data_projection_work_state_v1
  SET state = 'quarantined',
      claim_token = NULL, worker_ref = NULL, claimed_at = NULL, lease_expires_at = NULL,
      terminal_outcome = v_outcome, terminal_quarantine_reason = v_reason,
      terminal_at = v_now, updated_at = v_now
  WHERE work_id = p_work_id;

  RETURN QUERY SELECT true, v_outcome, NULL::timestamptz, v_quarantine_id,
    CASE WHEN v_outcome = 'retry_exhausted' THEN 'EVENT_RETRY_EXHAUSTED'::varchar
         ELSE 'EVENT_QUARANTINED'::varchar END;
END;
$$;

CREATE OR REPLACE FUNCTION public.record_data_quarantine_review_v1(
  p_quarantine_evidence_ref uuid,
  p_review_action varchar,
  p_reviewer_ref varchar,
  p_operation_ref varchar,
  p_policy_version varchar,
  p_mapping_version varchar DEFAULT NULL,
  p_schema_version varchar DEFAULT NULL,
  p_consumer_version varchar DEFAULT NULL,
  p_dry_run_ref varchar DEFAULT NULL,
  p_integrity_checks_passed boolean DEFAULT false
)
RETURNS uuid
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = pg_catalog, public, pg_temp
AS $$
DECLARE
  v_review_id uuid;
BEGIN
  IF p_review_action NOT IN ('reviewed_retain', 'reviewed_release_requested') THEN
    RAISE EXCEPTION 'Unsupported quarantine review action.' USING ERRCODE = '22023';
  END IF;
  IF p_reviewer_ref IS NULL OR p_reviewer_ref !~ '^subject:[A-Za-z0-9][A-Za-z0-9._~-]{0,127}$'
     OR p_operation_ref IS NULL OR p_operation_ref !~ '^operation:[A-Za-z0-9][A-Za-z0-9._~-]{0,127}$'
     OR NOT public.data_projection_version_valid_v1(p_policy_version) THEN
    RAISE EXCEPTION 'Invalid quarantine review references.' USING ERRCODE = '22023';
  END IF;
  IF NOT EXISTS (
    SELECT 1 FROM public.data_projection_quarantine_evidence_v1
    WHERE quarantine_evidence_id = p_quarantine_evidence_ref
  ) THEN
    RAISE EXCEPTION 'Quarantine evidence does not exist.' USING ERRCODE = '23503';
  END IF;
  IF p_review_action = 'reviewed_release_requested' AND (
       p_mapping_version IS NULL OR p_schema_version IS NULL OR p_consumer_version IS NULL
       OR p_dry_run_ref IS NULL OR p_dry_run_ref !~ '^dryrun:[A-Za-z0-9][A-Za-z0-9._~-]{0,127}$'
       OR NOT p_integrity_checks_passed
     ) THEN
    RAISE EXCEPTION 'Release request prerequisites are incomplete.' USING ERRCODE = '22023';
  END IF;

  INSERT INTO public.data_projection_quarantine_review_evidence_v1 (
    quarantine_evidence_ref, review_action, reviewer_ref, operation_ref,
    policy_version, mapping_version, schema_version, consumer_version,
    dry_run_ref, integrity_checks_passed
  ) VALUES (
    p_quarantine_evidence_ref, p_review_action, p_reviewer_ref, p_operation_ref,
    p_policy_version, p_mapping_version, p_schema_version, p_consumer_version,
    p_dry_run_ref, p_integrity_checks_passed
  ) RETURNING review_evidence_id INTO v_review_id;

  RETURN v_review_id;
END;
$$;

CREATE VIEW public.data_projection_quarantine_reviewer_v1
WITH (security_barrier = true)
AS
SELECT quarantine_evidence_id, work_id, source_event_ref, projection_name,
       projection_version, reason, failure_signature, triggering_attempt_ref,
       operation_ref, created_at, retention_class, retention_policy_version, expires_at
FROM public.data_projection_quarantine_evidence_v1;

CREATE VIEW public.data_projection_quarantine_review_reader_v1
WITH (security_barrier = true)
AS
SELECT review_evidence_id, quarantine_evidence_ref, review_action, operation_ref,
       policy_version, mapping_version, schema_version, consumer_version,
       dry_run_ref, integrity_checks_passed, created_at,
       retention_class, retention_policy_version, expires_at
FROM public.data_projection_quarantine_review_evidence_v1;

CREATE VIEW public.data_projection_retry_observability_v1
WITH (security_barrier = true)
AS
WITH work AS (
  SELECT projection_name, projection_version, retry_policy_version,
         count(*) FILTER (WHERE state = 'retry_scheduled') AS scheduled_count,
         count(*) FILTER (WHERE state = 'retry_scheduled' AND ready_at <= CURRENT_TIMESTAMP) AS ready_count,
         count(*) FILTER (WHERE state = 'retry_claimed') AS claimed_count,
         count(*) FILTER (WHERE state = 'retry_succeeded') AS succeeded_count,
         count(*) FILTER (WHERE state = 'quarantined') AS quarantine_count,
         count(*) FILTER (WHERE state = 'retry_claimed' AND lease_expires_at > CURRENT_TIMESTAMP) AS active_lease_count,
         count(*) FILTER (WHERE state = 'retry_claimed' AND lease_expires_at <= CURRENT_TIMESTAMP) AS expired_lease_count,
         max(EXTRACT(EPOCH FROM (CURRENT_TIMESTAMP - ready_at)))
           FILTER (WHERE state = 'retry_scheduled' AND ready_at <= CURRENT_TIMESTAMP) AS oldest_ready_age_seconds,
         avg(EXTRACT(EPOCH FROM (terminal_at - created_at)))
           FILTER (WHERE state = 'retry_succeeded') AS age_to_success_seconds
  FROM public.data_projection_work_state_v1
  GROUP BY projection_name, projection_version, retry_policy_version
), attempts AS (
  SELECT projection_name, projection_version,
         count(*) FILTER (WHERE outcome <> 'retry_succeeded') AS failed_count,
         count(*) FILTER (WHERE outcome = 'retry_scheduled') AS retry_scheduled_count,
         count(*) FILTER (WHERE outcome = 'retry_exhausted') AS retry_exhausted_count
  FROM public.data_projection_processing_attempt_v1
  GROUP BY projection_name, projection_version
), latency AS (
  SELECT projection_name, projection_version,
         avg(EXTRACT(EPOCH FROM (ready_at - scheduled_at)))
           FILTER (WHERE attempt_number > 1) AS retry_latency_seconds
  FROM public.data_projection_retry_schedule_v1
  GROUP BY projection_name, projection_version
), stale AS (
  SELECT state.projection_name, state.projection_version, count(*) AS stale_claim_rejection_count
  FROM public.data_projection_claim_rejection_evidence_v1 rejection
  JOIN public.data_projection_work_state_v1 state ON state.work_id = rejection.work_id
  GROUP BY state.projection_name, state.projection_version
), repeated AS (
  SELECT projection_name, projection_version, count(*) AS repeated_failure_signature_count
  FROM (
    SELECT projection_name, projection_version, failure_signature
    FROM public.data_projection_processing_attempt_v1
    WHERE failure_signature IS NOT NULL
    GROUP BY projection_name, projection_version, failure_signature
    HAVING count(*) >= 3
  ) signatures
  GROUP BY projection_name, projection_version
)
SELECT work.projection_name, work.projection_version, work.retry_policy_version,
       work.scheduled_count, work.ready_count, work.claimed_count, work.succeeded_count,
       COALESCE(attempts.failed_count, 0) AS failed_count,
       COALESCE(attempts.retry_scheduled_count, 0) AS retry_scheduled_count,
       COALESCE(attempts.retry_exhausted_count, 0) AS retry_exhausted_count,
       work.quarantine_count, work.active_lease_count, work.expired_lease_count,
       work.oldest_ready_age_seconds, latency.retry_latency_seconds,
       work.age_to_success_seconds,
       COALESCE(stale.stale_claim_rejection_count, 0) AS stale_claim_rejection_count,
       COALESCE(repeated.repeated_failure_signature_count, 0) AS repeated_failure_signature_count
FROM work
LEFT JOIN attempts USING (projection_name, projection_version)
LEFT JOIN latency USING (projection_name, projection_version)
LEFT JOIN stale USING (projection_name, projection_version)
LEFT JOIN repeated USING (projection_name, projection_version);

CREATE VIEW public.data_projection_quarantine_reason_metrics_v1
WITH (security_barrier = true)
AS
SELECT projection_name, projection_version, reason, count(*) AS quarantine_count
FROM public.data_projection_quarantine_evidence_v1
GROUP BY projection_name, projection_version, reason;

GRANT jc_security_owner TO CURRENT_USER;
GRANT USAGE, CREATE ON SCHEMA public TO jc_security_owner;
GRANT USAGE ON SCHEMA public TO jc_data_retry_processor, jc_data_quarantine_reviewer;
GRANT EXECUTE ON FUNCTION public.digest(bytea, text), public.gen_random_uuid() TO jc_security_owner;

ALTER TABLE public.data_projection_work_state_v1 OWNER TO jc_security_owner;
ALTER TABLE public.data_projection_retry_schedule_v1 OWNER TO jc_security_owner;
ALTER TABLE public.data_projection_claim_evidence_v1 OWNER TO jc_security_owner;
ALTER TABLE public.data_projection_heartbeat_evidence_v1 OWNER TO jc_security_owner;
ALTER TABLE public.data_projection_processing_attempt_v1 OWNER TO jc_security_owner;
ALTER TABLE public.data_projection_quarantine_evidence_v1 OWNER TO jc_security_owner;
ALTER TABLE public.data_projection_quarantine_review_evidence_v1 OWNER TO jc_security_owner;
ALTER TABLE public.data_projection_claim_rejection_evidence_v1 OWNER TO jc_security_owner;
ALTER VIEW public.data_projection_quarantine_reviewer_v1 OWNER TO jc_security_owner;
ALTER VIEW public.data_projection_quarantine_review_reader_v1 OWNER TO jc_security_owner;
ALTER VIEW public.data_projection_retry_observability_v1 OWNER TO jc_security_owner;
ALTER VIEW public.data_projection_quarantine_reason_metrics_v1 OWNER TO jc_security_owner;
ALTER FUNCTION public.data_projection_name_valid_v1(varchar) OWNER TO jc_security_owner;
ALTER FUNCTION public.data_projection_version_valid_v1(varchar) OWNER TO jc_security_owner;
ALTER FUNCTION public.data_retry_failure_class_valid_v1(varchar) OWNER TO jc_security_owner;
ALTER FUNCTION public.data_retryable_failure_class_v1(varchar) OWNER TO jc_security_owner;
ALTER FUNCTION public.data_quarantine_reason_valid_v1(varchar) OWNER TO jc_security_owner;
ALTER FUNCTION public.data_retry_jitter_basis_points_v1(varchar, integer) OWNER TO jc_security_owner;
ALTER FUNCTION public.data_retry_delay_v1(varchar, integer) OWNER TO jc_security_owner;
ALTER FUNCTION public.record_data_claim_rejection_v1(uuid, varchar, uuid, varchar) OWNER TO jc_security_owner;
ALTER FUNCTION public.register_data_projection_work_v1(varchar, varchar, varchar, varchar, timestamptz) OWNER TO jc_security_owner;
ALTER FUNCTION public.claim_data_projection_work_v1(varchar, integer) OWNER TO jc_security_owner;
ALTER FUNCTION public.heartbeat_data_projection_work_v1(uuid, varchar, uuid) OWNER TO jc_security_owner;
ALTER FUNCTION public.complete_data_projection_work_v1(uuid, varchar, uuid, varchar) OWNER TO jc_security_owner;
ALTER FUNCTION public.fail_data_projection_work_v1(uuid, varchar, uuid, varchar, varchar, varchar, varchar) OWNER TO jc_security_owner;
ALTER FUNCTION public.record_data_quarantine_review_v1(
  uuid, varchar, varchar, varchar, varchar, varchar, varchar, varchar, varchar, boolean
) OWNER TO jc_security_owner;

REVOKE ALL ON TABLE
  public.data_projection_work_state_v1,
  public.data_projection_retry_schedule_v1,
  public.data_projection_claim_evidence_v1,
  public.data_projection_heartbeat_evidence_v1,
  public.data_projection_processing_attempt_v1,
  public.data_projection_quarantine_evidence_v1,
  public.data_projection_quarantine_review_evidence_v1,
  public.data_projection_claim_rejection_evidence_v1
FROM PUBLIC, jc_app, jc_auth, jc_admin, jc_recommendation,
  jc_data_event_writer, jc_data_event_reader, jc_data_retry_processor,
  jc_data_quarantine_reviewer, jc_data_replay_executor;

REVOKE ALL ON TABLE
  public.data_projection_quarantine_reviewer_v1,
  public.data_projection_quarantine_review_reader_v1,
  public.data_projection_retry_observability_v1,
  public.data_projection_quarantine_reason_metrics_v1
FROM PUBLIC, jc_app, jc_auth, jc_admin, jc_recommendation,
  jc_data_event_writer, jc_data_event_reader, jc_data_retry_processor,
  jc_data_quarantine_reviewer, jc_data_replay_executor;

REVOKE EXECUTE ON FUNCTION public.record_data_claim_rejection_v1(uuid, varchar, uuid, varchar)
  FROM PUBLIC, jc_app, jc_auth, jc_admin, jc_recommendation,
    jc_data_event_writer, jc_data_event_reader, jc_data_retry_processor,
    jc_data_quarantine_reviewer, jc_data_replay_executor;
REVOKE EXECUTE ON FUNCTION public.register_data_projection_work_v1(varchar, varchar, varchar, varchar, timestamptz)
  FROM PUBLIC, jc_app, jc_auth, jc_admin, jc_recommendation,
    jc_data_event_writer, jc_data_event_reader, jc_data_quarantine_reviewer, jc_data_replay_executor;
REVOKE EXECUTE ON FUNCTION public.claim_data_projection_work_v1(varchar, integer)
  FROM PUBLIC, jc_app, jc_auth, jc_admin, jc_recommendation,
    jc_data_event_writer, jc_data_event_reader, jc_data_quarantine_reviewer, jc_data_replay_executor;
REVOKE EXECUTE ON FUNCTION public.heartbeat_data_projection_work_v1(uuid, varchar, uuid)
  FROM PUBLIC, jc_app, jc_auth, jc_admin, jc_recommendation,
    jc_data_event_writer, jc_data_event_reader, jc_data_quarantine_reviewer, jc_data_replay_executor;
REVOKE EXECUTE ON FUNCTION public.complete_data_projection_work_v1(uuid, varchar, uuid, varchar)
  FROM PUBLIC, jc_app, jc_auth, jc_admin, jc_recommendation,
    jc_data_event_writer, jc_data_event_reader, jc_data_quarantine_reviewer, jc_data_replay_executor;
REVOKE EXECUTE ON FUNCTION public.fail_data_projection_work_v1(uuid, varchar, uuid, varchar, varchar, varchar, varchar)
  FROM PUBLIC, jc_app, jc_auth, jc_admin, jc_recommendation,
    jc_data_event_writer, jc_data_event_reader, jc_data_quarantine_reviewer, jc_data_replay_executor;
REVOKE EXECUTE ON FUNCTION public.record_data_quarantine_review_v1(
  uuid, varchar, varchar, varchar, varchar, varchar, varchar, varchar, varchar, boolean
) FROM PUBLIC, jc_app, jc_auth, jc_admin, jc_recommendation,
  jc_data_event_writer, jc_data_event_reader, jc_data_retry_processor, jc_data_replay_executor;

GRANT EXECUTE ON FUNCTION public.register_data_projection_work_v1(varchar, varchar, varchar, varchar, timestamptz)
  TO jc_data_retry_processor;
GRANT EXECUTE ON FUNCTION public.claim_data_projection_work_v1(varchar, integer)
  TO jc_data_retry_processor;
GRANT EXECUTE ON FUNCTION public.heartbeat_data_projection_work_v1(uuid, varchar, uuid)
  TO jc_data_retry_processor;
GRANT EXECUTE ON FUNCTION public.complete_data_projection_work_v1(uuid, varchar, uuid, varchar)
  TO jc_data_retry_processor;
GRANT EXECUTE ON FUNCTION public.fail_data_projection_work_v1(uuid, varchar, uuid, varchar, varchar, varchar, varchar)
  TO jc_data_retry_processor;
GRANT SELECT ON public.data_projection_retry_observability_v1,
  public.data_projection_quarantine_reason_metrics_v1 TO jc_data_event_reader;
GRANT SELECT ON public.data_projection_quarantine_reviewer_v1,
  public.data_projection_quarantine_review_reader_v1,
  public.data_projection_quarantine_reason_metrics_v1 TO jc_data_quarantine_reviewer;
GRANT EXECUTE ON FUNCTION public.record_data_quarantine_review_v1(
  uuid, varchar, varchar, varchar, varchar, varchar, varchar, varchar, varchar, boolean
) TO jc_data_quarantine_reviewer;

REVOKE CREATE ON SCHEMA public FROM jc_security_owner;
REVOKE jc_security_owner FROM CURRENT_USER;

COMMIT;

-- Processor heartbeat cadence contract: interval '20 seconds'.
