-- SR-6F-F non-production manual Search CTR activation foundation. PostgreSQL 15+.
-- Prerequisite: journey-connect-db-v2.8/04..07.
BEGIN;

CREATE TABLE public.search_ctr_manual_run_audit_v1 (
  operation_id varchar(96) PRIMARY KEY
    CHECK (operation_id ~ '^search-ctr-manual-run:[0-9a-f]{32}$'),
  policy_version varchar(64) NOT NULL
    CHECK (policy_version = 'search-ctr-activation-finality-v1'),
  runtime_mode varchar(32) NOT NULL
    CHECK (runtime_mode = 'NONPRODUCTION_MANUAL'),
  environment varchar(16) NOT NULL
    CHECK (environment IN ('local', 'dev', 'test', 'stage')),
  window_start timestamptz NOT NULL,
  window_end timestamptz NOT NULL,
  observed_at timestamptz NOT NULL,
  requested_at timestamptz NOT NULL,
  completed_at timestamptz NOT NULL,
  idempotency_key varchar(160) NOT NULL,
  producer_build_id varchar(128) NOT NULL,
  expected_predecessor_projection_id varchar(96),
  write_status varchar(32) NOT NULL
    CHECK (write_status IN (
      'STORED', 'DUPLICATE', 'IDEMPOTENCY_CONFLICT', 'PREDECESSOR_CONFLICT'
    )),
  projection_id varchar(96),
  projection_fingerprint varchar(64),
  projection_status varchar(16),
  source_max_received_at timestamptz,
  finality_write_attempted boolean NOT NULL DEFAULT false
    CHECK (finality_write_attempted = false),
  created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CHECK (window_end = window_start + interval '1 hour'),
  CHECK (date_trunc('hour', window_start) = window_start),
  CHECK (observed_at >= window_end + interval '35 minutes'),
  CHECK (completed_at >= requested_at),
  CHECK (idempotency_key ~ '^[A-Za-z0-9][A-Za-z0-9._:-]{0,159}$'),
  CHECK (producer_build_id ~ '^[A-Za-z0-9][A-Za-z0-9._:-]{0,127}$'),
  CHECK (
    expected_predecessor_projection_id IS NULL
    OR expected_predecessor_projection_id ~ '^search-ctr-projection:[0-9a-f]{32}$'
  ),
  CHECK (projection_id IS NULL OR projection_id ~ '^search-ctr-projection:[0-9a-f]{32}$'),
  CHECK (projection_fingerprint IS NULL OR projection_fingerprint ~ '^[0-9a-f]{64}$'),
  CHECK (projection_status IS NULL OR projection_status = 'PROVISIONAL')
);

CREATE INDEX search_ctr_manual_run_window_idx
  ON public.search_ctr_manual_run_audit_v1(
    window_start, window_end, requested_at DESC, operation_id
  );
CREATE INDEX search_ctr_manual_run_status_idx
  ON public.search_ctr_manual_run_audit_v1(write_status, requested_at DESC);

CREATE OR REPLACE FUNCTION public.deny_search_ctr_manual_run_mutation_v1()
RETURNS trigger
LANGUAGE plpgsql
SECURITY INVOKER
SET search_path = pg_catalog, public, pg_temp
AS $$
BEGIN
  RAISE EXCEPTION 'search CTR manual run audit is append-only' USING ERRCODE = '55000';
END;
$$;

CREATE TRIGGER search_ctr_manual_run_append_only
BEFORE UPDATE OR DELETE ON public.search_ctr_manual_run_audit_v1
FOR EACH ROW EXECUTE FUNCTION public.deny_search_ctr_manual_run_mutation_v1();

CREATE OR REPLACE FUNCTION public.read_search_ctr_projection_head_v1(
  p_window_start timestamptz,
  p_window_end timestamptz,
  p_requester varchar
)
RETURNS TABLE (
  projection_id varchar,
  projection_fingerprint varchar,
  predecessor_projection_id varchar,
  metric_id varchar,
  metric_version varchar,
  window_start timestamptz,
  window_end timestamptz,
  status varchar,
  computed_at timestamptz,
  source_max_received_at timestamptz
)
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = pg_catalog, public, pg_temp
AS $$
DECLARE
  v_head_count integer;
BEGIN
  IF p_requester <> 'reliability-search-ctr-manual' THEN
    RAISE EXCEPTION 'search CTR projection head requester denied' USING ERRCODE = '42501';
  END IF;
  IF p_window_start IS NULL
     OR p_window_end IS NULL
     OR p_window_end <> p_window_start + interval '1 hour'
     OR date_trunc('hour', p_window_start) <> p_window_start THEN
    RAISE EXCEPTION 'search CTR projection head window is invalid' USING ERRCODE = '22023';
  END IF;

  SELECT count(*) INTO v_head_count
  FROM public.search_ctr_projection_snapshot_v1 candidate
  WHERE candidate.metric_id = 'search-click-through-rate-v1'
    AND candidate.metric_version = 'search-ctr-projection-v1'
    AND candidate.window_start = p_window_start
    AND candidate.window_end = p_window_end
    AND NOT EXISTS (
      SELECT 1
      FROM public.search_ctr_projection_snapshot_v1 child
      WHERE child.predecessor_projection_id = candidate.projection_id
    );

  IF v_head_count > 1 THEN
    RAISE EXCEPTION 'search CTR projection lineage has multiple heads' USING ERRCODE = '23514';
  END IF;

  RETURN QUERY
  SELECT
    candidate.projection_id,
    candidate.payload_fingerprint,
    candidate.predecessor_projection_id,
    candidate.metric_id,
    candidate.metric_version,
    candidate.window_start,
    candidate.window_end,
    candidate.status,
    candidate.computed_at,
    candidate.source_max_received_at
  FROM public.search_ctr_projection_snapshot_v1 candidate
  WHERE candidate.metric_id = 'search-click-through-rate-v1'
    AND candidate.metric_version = 'search-ctr-projection-v1'
    AND candidate.window_start = p_window_start
    AND candidate.window_end = p_window_end
    AND NOT EXISTS (
      SELECT 1
      FROM public.search_ctr_projection_snapshot_v1 child
      WHERE child.predecessor_projection_id = candidate.projection_id
    );
END;
$$;

CREATE OR REPLACE FUNCTION public.execute_search_ctr_manual_v1(
  p_operation_id varchar,
  p_window_start timestamptz,
  p_window_end timestamptz,
  p_environment varchar,
  p_policy_version varchar,
  p_observed_at timestamptz,
  p_idempotency_key varchar,
  p_producer_build_id varchar,
  p_requester varchar
)
RETURNS TABLE (
  operation_id varchar,
  write_status varchar,
  projection_id varchar,
  projection_fingerprint varchar,
  predecessor_projection_id varchar,
  metric_id varchar,
  metric_version varchar,
  window_start timestamptz,
  window_end timestamptz,
  status varchar,
  eligible_exposure_count bigint,
  attributed_exposure_count bigint,
  ctr_basis_points integer,
  computed_at timestamptz,
  source_max_received_at timestamptz
)
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = pg_catalog, public, pg_temp
AS $$
DECLARE
  v_requested_at timestamptz := clock_timestamp();
  v_completed_at timestamptz;
  v_head record;
  v_write record;
BEGIN
  IF p_requester <> 'reliability-search-ctr-manual' THEN
    RAISE EXCEPTION 'search CTR manual requester denied' USING ERRCODE = '42501';
  END IF;
  IF p_operation_id IS NULL
     OR p_operation_id !~ '^search-ctr-manual-run:[0-9a-f]{32}$' THEN
    RAISE EXCEPTION 'search CTR manual operation id is invalid' USING ERRCODE = '22023';
  END IF;
  IF p_environment IS NULL OR p_environment NOT IN ('local', 'dev', 'test', 'stage') THEN
    RAISE EXCEPTION 'search CTR manual environment is not allowlisted' USING ERRCODE = '42501';
  END IF;
  IF p_policy_version <> 'search-ctr-activation-finality-v1' THEN
    RAISE EXCEPTION 'search CTR manual policy version mismatch' USING ERRCODE = '22023';
  END IF;
  IF p_window_start IS NULL
     OR p_window_end IS NULL
     OR p_window_end <> p_window_start + interval '1 hour'
     OR date_trunc('hour', p_window_start) <> p_window_start THEN
    RAISE EXCEPTION 'search CTR manual window is invalid' USING ERRCODE = '22023';
  END IF;
  IF p_observed_at IS NULL OR p_observed_at < p_window_end + interval '35 minutes' THEN
    RAISE EXCEPTION 'search CTR manual window is not provisionally eligible' USING ERRCODE = '22023';
  END IF;
  IF p_idempotency_key IS NULL
     OR p_idempotency_key !~ '^[A-Za-z0-9][A-Za-z0-9._:-]{0,159}$' THEN
    RAISE EXCEPTION 'search CTR manual idempotency key is invalid' USING ERRCODE = '22023';
  END IF;
  IF p_producer_build_id IS NULL
     OR p_producer_build_id !~ '^[A-Za-z0-9][A-Za-z0-9._:-]{0,127}$' THEN
    RAISE EXCEPTION 'search CTR manual producer build is invalid' USING ERRCODE = '22023';
  END IF;

  SELECT * INTO v_head
  FROM public.read_search_ctr_projection_head_v1(
    p_window_start, p_window_end, 'reliability-search-ctr-manual'
  );

  SELECT * INTO v_write
  FROM public.write_search_ctr_projection_v1(
    p_window_start,
    p_window_end,
    'reliability-search-ctr',
    CASE WHEN v_head.projection_id IS NULL THEN NULL ELSE v_head.projection_id END,
    p_idempotency_key,
    p_producer_build_id
  );

  v_completed_at := clock_timestamp();

  INSERT INTO public.search_ctr_manual_run_audit_v1(
    operation_id, policy_version, runtime_mode, environment,
    window_start, window_end, observed_at, requested_at, completed_at,
    idempotency_key, producer_build_id, expected_predecessor_projection_id,
    write_status, projection_id, projection_fingerprint,
    projection_status, source_max_received_at, finality_write_attempted
  ) VALUES (
    p_operation_id, p_policy_version, 'NONPRODUCTION_MANUAL', p_environment,
    p_window_start, p_window_end, p_observed_at, v_requested_at, v_completed_at,
    p_idempotency_key, p_producer_build_id,
    CASE WHEN v_head.projection_id IS NULL THEN NULL ELSE v_head.projection_id END,
    v_write.write_status, v_write.projection_id, v_write.projection_fingerprint,
    v_write.status, v_write.source_max_received_at, false
  );

  RETURN QUERY SELECT
    p_operation_id,
    v_write.write_status::varchar,
    v_write.projection_id::varchar,
    v_write.projection_fingerprint::varchar,
    v_write.predecessor_projection_id::varchar,
    v_write.metric_id::varchar,
    v_write.metric_version::varchar,
    v_write.window_start,
    v_write.window_end,
    v_write.status::varchar,
    v_write.eligible_exposure_count,
    v_write.attributed_exposure_count,
    v_write.ctr_basis_points,
    v_write.computed_at,
    v_write.source_max_received_at;
END;
$$;

ALTER TABLE public.search_ctr_manual_run_audit_v1 OWNER TO jc_security_owner;
ALTER FUNCTION public.deny_search_ctr_manual_run_mutation_v1() OWNER TO jc_security_owner;
ALTER FUNCTION public.read_search_ctr_projection_head_v1(timestamptz,timestamptz,varchar)
  OWNER TO jc_security_owner;
ALTER FUNCTION public.execute_search_ctr_manual_v1(
  varchar,timestamptz,timestamptz,varchar,varchar,timestamptz,varchar,varchar,varchar
) OWNER TO jc_security_owner;

REVOKE ALL ON public.search_ctr_manual_run_audit_v1
  FROM PUBLIC, jc_app, jc_auth, jc_admin, jc_recommendation, jc_reliability;
GRANT SELECT, INSERT ON public.search_ctr_manual_run_audit_v1 TO jc_security_owner;

REVOKE ALL ON FUNCTION public.read_search_ctr_projection_head_v1(
  timestamptz,timestamptz,varchar
) FROM PUBLIC, jc_app, jc_auth, jc_admin, jc_recommendation, jc_reliability;
GRANT EXECUTE ON FUNCTION public.read_search_ctr_projection_head_v1(
  timestamptz,timestamptz,varchar
) TO jc_reliability;

GRANT EXECUTE ON FUNCTION public.write_search_ctr_projection_v1(
  timestamptz,timestamptz,varchar,varchar,varchar,varchar
) TO jc_security_owner;

REVOKE ALL ON FUNCTION public.execute_search_ctr_manual_v1(
  varchar,timestamptz,timestamptz,varchar,varchar,timestamptz,varchar,varchar,varchar
) FROM PUBLIC, jc_app, jc_auth, jc_admin, jc_recommendation, jc_reliability, jc_security_owner;
GRANT EXECUTE ON FUNCTION public.execute_search_ctr_manual_v1(
  varchar,timestamptz,timestamptz,varchar,varchar,timestamptz,varchar,varchar,varchar
) TO jc_reliability;

COMMIT;
