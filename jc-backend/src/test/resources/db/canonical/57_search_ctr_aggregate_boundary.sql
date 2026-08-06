-- SR-6F-C aggregate-only Search CTR identity bridge. PostgreSQL 15+.
-- Prerequisite: journey-connect-db-v2.8/01..03.
BEGIN;

DO $$
BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'jc_reliability') THEN
    CREATE ROLE jc_reliability NOLOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOREPLICATION NOBYPASSRLS;
  END IF;
END;
$$;

DO $$
DECLARE
  v_unsafe boolean;
  v_memberships text;
BEGIN
  SELECT (rolsuper OR rolcreaterole OR rolcreatedb OR rolcanlogin OR rolreplication OR rolbypassrls)
    INTO v_unsafe
  FROM pg_roles
  WHERE rolname = 'jc_reliability';
  IF v_unsafe IS DISTINCT FROM false THEN
    RAISE EXCEPTION 'jc_reliability role attributes are unsafe';
  END IF;

  SELECT string_agg(member_role.rolname || ' -> ' || granted_role.rolname, ', ')
    INTO v_memberships
  FROM pg_auth_members membership
  JOIN pg_roles member_role ON member_role.oid = membership.member
  JOIN pg_roles granted_role ON granted_role.oid = membership.roleid
  WHERE member_role.rolname = 'jc_reliability'
     OR granted_role.rolname = 'jc_reliability';
  IF v_memberships IS NOT NULL THEN
    RAISE EXCEPTION 'jc_reliability must not inherit or be inherited: %', v_memberships;
  END IF;
END;
$$;

GRANT USAGE ON SCHEMA public TO jc_reliability;

CREATE TABLE public.search_ctr_evaluation_access_audit_v1 (
  audit_id varchar(64) PRIMARY KEY
    CHECK (audit_id ~ '^search-ctr-audit:[0-9a-f]{32}$'),
  metric_id varchar(64) NOT NULL
    CHECK (metric_id = 'search-click-through-rate-v1'),
  metric_version varchar(64) NOT NULL
    CHECK (metric_version = 'search-ctr-projection-v1'),
  requester varchar(64) NOT NULL
    CHECK (requester = 'reliability-search-ctr'),
  window_start timestamptz NOT NULL,
  window_end timestamptz NOT NULL,
  accessed_at timestamptz NOT NULL,
  retention_until timestamptz NOT NULL,
  created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CHECK (window_start < window_end),
  CHECK (retention_until = accessed_at + interval '30 days')
);
CREATE INDEX search_ctr_evaluation_audit_retention_idx
  ON public.search_ctr_evaluation_access_audit_v1(retention_until);

CREATE OR REPLACE FUNCTION public.guard_search_ctr_audit_mutation_v1()
RETURNS trigger
LANGUAGE plpgsql
SECURITY INVOKER
SET search_path = pg_catalog, public, pg_temp
AS $$
BEGIN
  IF TG_OP = 'DELETE'
     AND current_user = 'jc_security_owner'
     AND current_setting('jc.controlled_retention_purge', true) = 'search-ctr-audit-retention-v1' THEN
    RETURN OLD;
  END IF;
  RAISE EXCEPTION 'search CTR access audit is append-only' USING ERRCODE = '55000';
END;
$$;

CREATE TRIGGER search_ctr_evaluation_audit_append_only
BEFORE UPDATE OR DELETE ON public.search_ctr_evaluation_access_audit_v1
FOR EACH ROW EXECUTE FUNCTION public.guard_search_ctr_audit_mutation_v1();

CREATE OR REPLACE FUNCTION public.evaluate_search_ctr_v1(
  p_window_start timestamptz,
  p_window_end timestamptz,
  p_requester varchar
)
RETURNS TABLE (
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
  v_computed_at timestamptz := clock_timestamp();
BEGIN
  IF p_requester <> 'reliability-search-ctr' THEN
    RAISE EXCEPTION 'search CTR requester denied' USING ERRCODE = '42501';
  END IF;
  IF p_window_start IS NULL OR p_window_end IS NULL OR p_window_start >= p_window_end THEN
    RAISE EXCEPTION 'search CTR evaluation window is invalid' USING ERRCODE = '22023';
  END IF;

  IF EXISTS (
    SELECT 1
    FROM public.search_exposure_event_v1 exposure
    JOIN public.platform_identity_mapping_v1 mapping
      ON mapping.subject_ref = exposure.subject_ref
    JOIN public.platform_identity_mapping_invalidation_v1 invalidation
      ON invalidation.mapping_id = mapping.mapping_id
    WHERE exposure.exposed_at >= p_window_start
      AND exposure.exposed_at < p_window_end
  ) THEN
    RAISE EXCEPTION 'search CTR identity bridge unavailable for invalidated mapping'
      USING ERRCODE = '23514';
  END IF;

  INSERT INTO public.search_ctr_evaluation_access_audit_v1(
    audit_id, metric_id, metric_version, requester,
    window_start, window_end, accessed_at, retention_until
  ) VALUES (
    'search-ctr-audit:' || md5(p_window_start::text || p_window_end::text || v_computed_at::text || random()::text),
    'search-click-through-rate-v1', 'search-ctr-projection-v1', p_requester,
    p_window_start, p_window_end, v_computed_at, v_computed_at + interval '30 days'
  );

  RETURN QUERY
  WITH eligible_exposure AS (
    SELECT exposure.*
    FROM public.search_exposure_event_v1 exposure
    JOIN public.platform_identity_mapping_v1 mapping
      ON mapping.subject_ref = exposure.subject_ref
    WHERE exposure.schema_version = 'search-exposure-v1'
      AND exposure.surface = 'search'
      AND exposure.result_entity_type = 'post'
      AND exposure.visibility_rule_version = 'search-item-visible-v1'
      AND exposure.visible_ratio_basis_points >= 5000
      AND exposure.dwell_milliseconds >= 1000
      AND exposure.exposed_at >= p_window_start
      AND exposure.exposed_at < p_window_end
      AND NOT EXISTS (
        SELECT 1
        FROM public.platform_identity_mapping_invalidation_v1 invalidation
        WHERE invalidation.mapping_id = mapping.mapping_id
      )
  ),
  ranked_click_candidate AS (
    SELECT
      behavior.event_id AS click_event_id,
      exposure.exposure_id,
      row_number() OVER (
        PARTITION BY behavior.event_id
        ORDER BY exposure.exposed_at DESC, exposure.received_at DESC, exposure.exposure_id ASC
      ) AS candidate_rank
    FROM public.recommendation_behavior_event behavior
    JOIN public.platform_identity_mapping_v1 mapping
      ON mapping.user_id = behavior.user_id
    JOIN eligible_exposure exposure
      ON exposure.subject_ref = mapping.subject_ref
     AND exposure.session_id = behavior.session_id
     AND exposure.search_run_id = behavior.run_id
     AND exposure.result_entity_id = behavior.source_entity_id
    WHERE behavior.schema_version = 'search-behavior-event-v1'
      AND behavior.event_type = 'click'
      AND behavior.entity_type = 'post'
      AND behavior.metadata ->> 'surface' = 'search'
      AND behavior.metadata ->> 'source' = 'search-result-api'
      AND behavior.metadata ->> 'searchRunId' = exposure.search_run_id
      AND behavior.metadata ->> 'queryFingerprint' = exposure.query_fingerprint
      AND behavior.metadata ->> 'snapshotFingerprint' = exposure.result_snapshot_ref
      AND behavior.metadata ->> 'policyVersion' = exposure.ranking_policy_version
      AND CASE
            WHEN jsonb_typeof(behavior.metadata -> 'absoluteRank') = 'number'
             AND (behavior.metadata ->> 'absoluteRank') ~ '^[0-9]+$'
            THEN (behavior.metadata ->> 'absoluteRank')::integer
            ELSE NULL
          END = exposure.absolute_rank
      AND behavior.occurred_at >= exposure.exposed_at
      AND behavior.occurred_at < exposure.exposed_at + interval '30 minutes'
  ),
  attributed_exposure AS (
    SELECT DISTINCT exposure_id
    FROM ranked_click_candidate
    WHERE candidate_rank = 1
  ),
  aggregate_counts AS (
    SELECT
      (SELECT count(*) FROM eligible_exposure)::bigint AS denominator,
      (SELECT count(*) FROM attributed_exposure)::bigint AS numerator
  ),
  source_watermark AS (
    SELECT max(received_at) AS max_received_at
    FROM (
      SELECT exposure.received_at
      FROM eligible_exposure exposure
      UNION ALL
      SELECT behavior.received_at
      FROM public.recommendation_behavior_event behavior
      WHERE behavior.schema_version = 'search-behavior-event-v1'
        AND behavior.event_type = 'click'
        AND behavior.entity_type = 'post'
        AND behavior.metadata ->> 'surface' = 'search'
        AND behavior.occurred_at >= p_window_start
        AND behavior.occurred_at < p_window_end + interval '30 minutes'
    ) source
  )
  SELECT
    'search-click-through-rate-v1'::varchar,
    'search-ctr-projection-v1'::varchar,
    p_window_start,
    p_window_end,
    'PROVISIONAL'::varchar,
    counts.denominator,
    counts.numerator,
    CASE WHEN counts.denominator = 0 THEN NULL
         ELSE ((counts.numerator * 10000) / counts.denominator)::integer
    END,
    v_computed_at,
    watermark.max_received_at
  FROM aggregate_counts counts
  CROSS JOIN source_watermark watermark;
END;
$$;

CREATE OR REPLACE FUNCTION public.purge_expired_search_ctr_audit_v1(
  p_cutoff timestamptz,
  p_requester varchar
)
RETURNS bigint
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = pg_catalog, public, pg_temp
AS $$
DECLARE
  v_deleted bigint;
BEGIN
  IF p_requester <> 'system-coordination' THEN
    RAISE EXCEPTION 'requester denied' USING ERRCODE = '42501';
  END IF;
  IF p_cutoff IS NULL OR p_cutoff > CURRENT_TIMESTAMP THEN
    RAISE EXCEPTION 'invalid cutoff' USING ERRCODE = '22023';
  END IF;
  PERFORM set_config('jc.controlled_retention_purge', 'search-ctr-audit-retention-v1', true);
  DELETE FROM public.search_ctr_evaluation_access_audit_v1
  WHERE retention_until <= p_cutoff;
  GET DIAGNOSTICS v_deleted = ROW_COUNT;
  RETURN v_deleted;
END;
$$;

ALTER TABLE public.search_ctr_evaluation_access_audit_v1 OWNER TO jc_security_owner;
ALTER FUNCTION public.guard_search_ctr_audit_mutation_v1() OWNER TO jc_security_owner;
ALTER FUNCTION public.evaluate_search_ctr_v1(timestamptz,timestamptz,varchar) OWNER TO jc_security_owner;
ALTER FUNCTION public.purge_expired_search_ctr_audit_v1(timestamptz,varchar) OWNER TO jc_security_owner;

REVOKE ALL ON public.search_ctr_evaluation_access_audit_v1
  FROM PUBLIC, jc_app, jc_auth, jc_admin, jc_recommendation, jc_reliability;
GRANT SELECT, INSERT, DELETE ON public.search_ctr_evaluation_access_audit_v1 TO jc_security_owner;

REVOKE ALL ON public.platform_identity_mapping_v1,
  public.platform_identity_mapping_invalidation_v1,
  public.search_exposure_event_v1,
  public.recommendation_behavior_event
FROM jc_reliability;

REVOKE ALL ON FUNCTION public.evaluate_search_ctr_v1(timestamptz,timestamptz,varchar)
  FROM PUBLIC, jc_app, jc_auth, jc_admin, jc_recommendation, jc_reliability, jc_security_owner;
GRANT EXECUTE ON FUNCTION public.evaluate_search_ctr_v1(timestamptz,timestamptz,varchar)
  TO jc_reliability;

REVOKE ALL ON FUNCTION public.purge_expired_search_ctr_audit_v1(timestamptz,varchar)
  FROM PUBLIC, jc_app, jc_auth, jc_admin, jc_recommendation, jc_reliability;
GRANT EXECUTE ON FUNCTION public.purge_expired_search_ctr_audit_v1(timestamptz,varchar)
  TO jc_security_owner;

COMMIT;
