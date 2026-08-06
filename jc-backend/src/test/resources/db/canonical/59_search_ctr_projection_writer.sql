-- SR-6F-D append-only Search CTR projection snapshot and single writer. PostgreSQL 15+.
-- Prerequisite: journey-connect-db-v2.8/04..05.
BEGIN;

CREATE TABLE public.search_ctr_projection_snapshot_v1 (
  projection_id varchar(96) PRIMARY KEY
    CHECK (projection_id ~ '^search-ctr-projection:[0-9a-f]{32}$'),
  idempotency_key varchar(160) NOT NULL UNIQUE
    CHECK (idempotency_key ~ '^[A-Za-z0-9][A-Za-z0-9._:-]{0,159}$'),
  schema_version varchar(64) NOT NULL
    CHECK (schema_version = 'search-ctr-projection-snapshot-v1'),
  canonicalization_version varchar(64) NOT NULL
    CHECK (canonicalization_version = 'search-ctr-aggregate-canonical-json-v1'),
  payload_fingerprint varchar(64) NOT NULL
    CHECK (payload_fingerprint ~ '^[0-9a-f]{64}$'),
  canonical_payload bytea NOT NULL,
  payload_size_bytes integer NOT NULL CHECK (payload_size_bytes BETWEEN 1 AND 262144),
  metric_id varchar(64) NOT NULL
    CHECK (metric_id = 'search-click-through-rate-v1'),
  metric_version varchar(64) NOT NULL
    CHECK (metric_version = 'search-ctr-projection-v1'),
  window_start timestamptz NOT NULL,
  window_end timestamptz NOT NULL,
  status varchar(16) NOT NULL CHECK (status = 'PROVISIONAL'),
  eligible_exposure_count bigint NOT NULL CHECK (eligible_exposure_count >= 0),
  attributed_exposure_count bigint NOT NULL
    CHECK (attributed_exposure_count BETWEEN 0 AND eligible_exposure_count),
  ctr_basis_points integer,
  computed_at timestamptz NOT NULL,
  source_max_received_at timestamptz,
  predecessor_projection_id varchar(96) UNIQUE
    REFERENCES public.search_ctr_projection_snapshot_v1(projection_id) ON DELETE RESTRICT,
  writer_version varchar(64) NOT NULL
    CHECK (writer_version = 'search-ctr-single-writer-v1'),
  producer_build_id varchar(128) NOT NULL
    CHECK (producer_build_id ~ '^[A-Za-z0-9][A-Za-z0-9._:-]{0,127}$'),
  created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CHECK (window_start < window_end),
  CHECK (octet_length(canonical_payload) = payload_size_bytes),
  CHECK (payload_fingerprint = public.recommendation_sha256_hex(canonical_payload)),
  CHECK (
    (eligible_exposure_count = 0 AND attributed_exposure_count = 0 AND ctr_basis_points IS NULL)
    OR
    (eligible_exposure_count > 0
      AND ctr_basis_points = ((attributed_exposure_count * 10000) / eligible_exposure_count)::integer
      AND ctr_basis_points BETWEEN 0 AND 10000)
  ),
  CHECK (predecessor_projection_id IS NULL OR predecessor_projection_id <> projection_id),
  UNIQUE (
    metric_id, metric_version, window_start, window_end, payload_fingerprint
  )
);

CREATE INDEX search_ctr_projection_window_idx
  ON public.search_ctr_projection_snapshot_v1(
    metric_id, metric_version, window_start, window_end, computed_at DESC, projection_id
  );
CREATE INDEX search_ctr_projection_predecessor_idx
  ON public.search_ctr_projection_snapshot_v1(predecessor_projection_id)
  WHERE predecessor_projection_id IS NOT NULL;

CREATE OR REPLACE FUNCTION public.deny_search_ctr_projection_mutation_v1()
RETURNS trigger
LANGUAGE plpgsql
SECURITY INVOKER
SET search_path = pg_catalog, public, pg_temp
AS $$
BEGIN
  RAISE EXCEPTION 'search CTR projection snapshot is append-only' USING ERRCODE = '55000';
END;
$$;

CREATE TRIGGER search_ctr_projection_append_only
BEFORE UPDATE OR DELETE ON public.search_ctr_projection_snapshot_v1
FOR EACH ROW EXECUTE FUNCTION public.deny_search_ctr_projection_mutation_v1();

CREATE OR REPLACE FUNCTION public.search_ctr_canonical_timestamp_v1(p_value timestamptz)
RETURNS varchar
LANGUAGE sql
IMMUTABLE
STRICT
SECURITY DEFINER
SET search_path = pg_catalog, public, pg_temp
AS $$
  SELECT to_char(p_value AT TIME ZONE 'UTC', 'YYYY-MM-DD"T"HH24:MI:SS.US"Z"')::varchar;
$$;

CREATE OR REPLACE FUNCTION public.write_search_ctr_projection_v1(
  p_window_start timestamptz,
  p_window_end timestamptz,
  p_requester varchar,
  p_expected_predecessor_projection_id varchar,
  p_idempotency_key varchar,
  p_producer_build_id varchar
)
RETURNS TABLE (
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
  v_evaluation record;
  v_existing public.search_ctr_projection_snapshot_v1%ROWTYPE;
  v_head public.search_ctr_projection_snapshot_v1%ROWTYPE;
  v_head_count integer;
  v_payload_text text;
  v_payload bytea;
  v_fingerprint varchar(64);
  v_projection_id varchar(96);
BEGIN
  IF p_requester <> 'reliability-search-ctr' THEN
    RAISE EXCEPTION 'search CTR writer requester denied' USING ERRCODE = '42501';
  END IF;
  IF p_window_start IS NULL OR p_window_end IS NULL OR p_window_start >= p_window_end THEN
    RAISE EXCEPTION 'search CTR projection window is invalid' USING ERRCODE = '22023';
  END IF;
  IF p_expected_predecessor_projection_id IS NOT NULL
     AND p_expected_predecessor_projection_id !~ '^search-ctr-projection:[0-9a-f]{32}$' THEN
    RAISE EXCEPTION 'search CTR expected predecessor is invalid' USING ERRCODE = '22023';
  END IF;
  IF p_idempotency_key IS NULL
     OR p_idempotency_key !~ '^[A-Za-z0-9][A-Za-z0-9._:-]{0,159}$' THEN
    RAISE EXCEPTION 'search CTR idempotency key is invalid' USING ERRCODE = '22023';
  END IF;
  IF p_producer_build_id IS NULL
     OR p_producer_build_id !~ '^[A-Za-z0-9][A-Za-z0-9._:-]{0,127}$' THEN
    RAISE EXCEPTION 'search CTR producer build is invalid' USING ERRCODE = '22023';
  END IF;

  PERFORM pg_catalog.pg_advisory_xact_lock(pg_catalog.hashtextextended(
    'search_ctr_projection_snapshot_v1:'
      || p_window_start::text || ':' || p_window_end::text,
    0
  ));

  SELECT * INTO v_evaluation
  FROM public.evaluate_search_ctr_v1(
    p_window_start, p_window_end, 'reliability-search-ctr'
  );

  v_payload_text :=
      '{"attributedExposureCount":' || v_evaluation.attributed_exposure_count::text
    || ',"ctrBasisPoints":' || COALESCE(v_evaluation.ctr_basis_points::text, 'null')
    || ',"eligibleExposureCount":' || v_evaluation.eligible_exposure_count::text
    || ',"metricId":' || to_json(v_evaluation.metric_id::text)::text
    || ',"metricVersion":' || to_json(v_evaluation.metric_version::text)::text
    || ',"sourceMaxReceivedAt":'
       || CASE WHEN v_evaluation.source_max_received_at IS NULL THEN 'null'
               ELSE to_json(public.search_ctr_canonical_timestamp_v1(
                 v_evaluation.source_max_received_at
               )::text)::text END
    || ',"status":' || to_json(v_evaluation.status::text)::text
    || ',"windowEnd":' || to_json(public.search_ctr_canonical_timestamp_v1(
         v_evaluation.window_end
       )::text)::text
    || ',"windowStart":' || to_json(public.search_ctr_canonical_timestamp_v1(
         v_evaluation.window_start
       )::text)::text
    || '}';
  v_payload := convert_to(v_payload_text, 'UTF8');
  v_fingerprint := public.recommendation_sha256_hex(v_payload);
  v_projection_id := 'search-ctr-projection:' || md5(
    v_evaluation.metric_id || ':' || v_evaluation.metric_version || ':'
    || public.search_ctr_canonical_timestamp_v1(v_evaluation.window_start) || ':'
    || public.search_ctr_canonical_timestamp_v1(v_evaluation.window_end) || ':'
    || v_fingerprint
  );

  SELECT * INTO v_existing
  FROM public.search_ctr_projection_snapshot_v1
  WHERE idempotency_key = p_idempotency_key;

  IF FOUND THEN
    IF v_existing.metric_id = v_evaluation.metric_id
       AND v_existing.metric_version = v_evaluation.metric_version
       AND v_existing.window_start = v_evaluation.window_start
       AND v_existing.window_end = v_evaluation.window_end
       AND v_existing.payload_fingerprint = v_fingerprint
       AND v_existing.canonical_payload = v_payload
       AND v_existing.producer_build_id = p_producer_build_id THEN
      RETURN QUERY SELECT
        'DUPLICATE'::varchar,
        v_existing.projection_id,
        v_existing.payload_fingerprint,
        v_existing.predecessor_projection_id,
        v_existing.metric_id,
        v_existing.metric_version,
        v_existing.window_start,
        v_existing.window_end,
        v_existing.status,
        v_existing.eligible_exposure_count,
        v_existing.attributed_exposure_count,
        v_existing.ctr_basis_points,
        v_existing.computed_at,
        v_existing.source_max_received_at;
    ELSE
      RETURN QUERY SELECT
        'IDEMPOTENCY_CONFLICT'::varchar,
        v_existing.projection_id,
        v_existing.payload_fingerprint,
        v_existing.predecessor_projection_id,
        v_existing.metric_id,
        v_existing.metric_version,
        v_existing.window_start,
        v_existing.window_end,
        v_existing.status,
        v_existing.eligible_exposure_count,
        v_existing.attributed_exposure_count,
        v_existing.ctr_basis_points,
        v_existing.computed_at,
        v_existing.source_max_received_at;
    END IF;
    RETURN;
  END IF;

  SELECT count(*) INTO v_head_count
  FROM public.search_ctr_projection_snapshot_v1 candidate
  WHERE candidate.metric_id = v_evaluation.metric_id
    AND candidate.metric_version = v_evaluation.metric_version
    AND candidate.window_start = v_evaluation.window_start
    AND candidate.window_end = v_evaluation.window_end
    AND NOT EXISTS (
      SELECT 1
      FROM public.search_ctr_projection_snapshot_v1 child
      WHERE child.predecessor_projection_id = candidate.projection_id
    );

  IF v_head_count > 1 THEN
    RAISE EXCEPTION 'search CTR projection lineage has multiple heads' USING ERRCODE = '23514';
  END IF;

  SELECT * INTO v_head
  FROM public.search_ctr_projection_snapshot_v1 candidate
  WHERE candidate.metric_id = v_evaluation.metric_id
    AND candidate.metric_version = v_evaluation.metric_version
    AND candidate.window_start = v_evaluation.window_start
    AND candidate.window_end = v_evaluation.window_end
    AND NOT EXISTS (
      SELECT 1
      FROM public.search_ctr_projection_snapshot_v1 child
      WHERE child.predecessor_projection_id = candidate.projection_id
    );

  IF FOUND AND v_head.payload_fingerprint = v_fingerprint
     AND v_head.canonical_payload = v_payload THEN
    RETURN QUERY SELECT
      'DUPLICATE'::varchar,
      v_head.projection_id,
      v_head.payload_fingerprint,
      v_head.predecessor_projection_id,
      v_head.metric_id,
      v_head.metric_version,
      v_head.window_start,
      v_head.window_end,
      v_head.status,
      v_head.eligible_exposure_count,
      v_head.attributed_exposure_count,
      v_head.ctr_basis_points,
      v_head.computed_at,
      v_head.source_max_received_at;
    RETURN;
  END IF;

  IF (v_head_count = 0 AND p_expected_predecessor_projection_id IS NOT NULL)
     OR
     (v_head_count = 1
       AND p_expected_predecessor_projection_id IS DISTINCT FROM v_head.projection_id) THEN
    RETURN QUERY SELECT
      'PREDECESSOR_CONFLICT'::varchar,
      CASE WHEN v_head_count = 1 THEN v_head.projection_id ELSE NULL END,
      CASE WHEN v_head_count = 1 THEN v_head.payload_fingerprint ELSE NULL END,
      CASE WHEN v_head_count = 1 THEN v_head.predecessor_projection_id ELSE NULL END,
      v_evaluation.metric_id,
      v_evaluation.metric_version,
      v_evaluation.window_start,
      v_evaluation.window_end,
      v_evaluation.status,
      v_evaluation.eligible_exposure_count,
      v_evaluation.attributed_exposure_count,
      v_evaluation.ctr_basis_points,
      v_evaluation.computed_at,
      v_evaluation.source_max_received_at;
    RETURN;
  END IF;

  INSERT INTO public.search_ctr_projection_snapshot_v1(
    projection_id, idempotency_key, schema_version, canonicalization_version,
    payload_fingerprint, canonical_payload, payload_size_bytes,
    metric_id, metric_version, window_start, window_end, status,
    eligible_exposure_count, attributed_exposure_count, ctr_basis_points,
    computed_at, source_max_received_at, predecessor_projection_id,
    writer_version, producer_build_id
  ) VALUES (
    v_projection_id, p_idempotency_key,
    'search-ctr-projection-snapshot-v1',
    'search-ctr-aggregate-canonical-json-v1',
    v_fingerprint, v_payload, octet_length(v_payload),
    v_evaluation.metric_id, v_evaluation.metric_version,
    v_evaluation.window_start, v_evaluation.window_end, v_evaluation.status,
    v_evaluation.eligible_exposure_count, v_evaluation.attributed_exposure_count,
    v_evaluation.ctr_basis_points, v_evaluation.computed_at,
    v_evaluation.source_max_received_at,
    CASE WHEN v_head_count = 1 THEN v_head.projection_id ELSE NULL END,
    'search-ctr-single-writer-v1', p_producer_build_id
  );

  RETURN QUERY SELECT
    'STORED'::varchar,
    stored.projection_id,
    stored.payload_fingerprint,
    stored.predecessor_projection_id,
    stored.metric_id,
    stored.metric_version,
    stored.window_start,
    stored.window_end,
    stored.status,
    stored.eligible_exposure_count,
    stored.attributed_exposure_count,
    stored.ctr_basis_points,
    stored.computed_at,
    stored.source_max_received_at
  FROM public.search_ctr_projection_snapshot_v1 stored
  WHERE stored.projection_id = v_projection_id;
END;
$$;

ALTER TABLE public.search_ctr_projection_snapshot_v1 OWNER TO jc_security_owner;
ALTER FUNCTION public.deny_search_ctr_projection_mutation_v1() OWNER TO jc_security_owner;
ALTER FUNCTION public.search_ctr_canonical_timestamp_v1(timestamptz) OWNER TO jc_security_owner;
ALTER FUNCTION public.write_search_ctr_projection_v1(
  timestamptz,timestamptz,varchar,varchar,varchar,varchar
) OWNER TO jc_security_owner;

REVOKE ALL ON public.search_ctr_projection_snapshot_v1
  FROM PUBLIC, jc_app, jc_auth, jc_admin, jc_recommendation, jc_reliability;
GRANT SELECT, INSERT ON public.search_ctr_projection_snapshot_v1 TO jc_security_owner;

REVOKE ALL ON FUNCTION public.search_ctr_canonical_timestamp_v1(timestamptz)
  FROM PUBLIC, jc_app, jc_auth, jc_admin, jc_recommendation, jc_reliability;

REVOKE ALL ON FUNCTION public.write_search_ctr_projection_v1(
  timestamptz,timestamptz,varchar,varchar,varchar,varchar
) FROM PUBLIC, jc_app, jc_auth, jc_admin, jc_recommendation, jc_reliability, jc_security_owner;
GRANT EXECUTE ON FUNCTION public.write_search_ctr_projection_v1(
  timestamptz,timestamptz,varchar,varchar,varchar,varchar
) TO jc_reliability;

COMMIT;
