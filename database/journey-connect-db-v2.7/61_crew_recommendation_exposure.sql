-- Journey Connect DB v2.7 - PF4 Crew recommendation delivery exposure
-- Target: PostgreSQL 15+
-- Authority: sc-pf4-crew-exposure-allocation-v1

BEGIN;

DO $$
BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'jc_recommendation')
     OR NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'jc_admin')
     OR NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'jc_app')
     OR NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'jc_auth') THEN
    RAISE EXCEPTION 'PF4 prerequisite runtime roles are missing.';
  END IF;
END;
$$;

CREATE TABLE public.crew_recommendation_exposure_event (
  exposure_id varchar(128) PRIMARY KEY,
  schema_version varchar(64) NOT NULL,
  exposure_semantic varchar(64) NOT NULL,
  user_id bigint NOT NULL CHECK (user_id > 0),
  surface varchar(64) NOT NULL,
  served_at timestamptz NOT NULL,
  reference_time timestamptz NOT NULL,
  contract_version varchar(128) NOT NULL,
  ranking_policy_version varchar(128) NOT NULL,
  score_policy_version varchar(128) NOT NULL,
  profile_policy_version varchar(128) NOT NULL,
  feature_vocabulary_version varchar(128) NOT NULL,
  profile_fingerprint varchar(64) NOT NULL,
  requested_limit integer NOT NULL CHECK (requested_limit BETWEEN 1 AND 20),
  returned_count integer NOT NULL CHECK (returned_count BETWEEN 0 AND requested_limit),
  canonical_fingerprint varchar(64) NOT NULL,
  canonical_payload bytea NOT NULL,
  payload_size_bytes integer NOT NULL CHECK (
    payload_size_bytes > 0
    AND payload_size_bytes = octet_length(canonical_payload)
  ),
  created_at timestamptz NOT NULL DEFAULT transaction_timestamp(),
  CONSTRAINT crew_rec_exp_schema_ck
    CHECK (schema_version = 'crew_recommendation_exposure_v1'),
  CONSTRAINT crew_rec_exp_semantic_ck
    CHECK (exposure_semantic = 'server_delivery_commit_v1'),
  CONSTRAINT crew_rec_exp_surface_ck
    CHECK (surface = 'crew_list'),
  CONSTRAINT crew_rec_exp_profile_fingerprint_ck
    CHECK (profile_fingerprint ~ '^[0-9a-f]{64}$'),
  CONSTRAINT crew_rec_exp_canonical_fingerprint_ck
    CHECK (canonical_fingerprint ~ '^[0-9a-f]{64}$')
);

CREATE TABLE public.crew_recommendation_exposure_candidate (
  exposure_id varchar(128) NOT NULL
    REFERENCES public.crew_recommendation_exposure_event(exposure_id) ON DELETE RESTRICT,
  absolute_rank integer NOT NULL CHECK (absolute_rank > 0),
  crew_id bigint NOT NULL CHECK (crew_id > 0),
  score double precision NOT NULL,
  coverage_mode varchar(32) NOT NULL,
  candidate_fingerprint varchar(64) NOT NULL,
  canonical_candidate bytea NOT NULL,
  candidate_size_bytes integer NOT NULL CHECK (
    candidate_size_bytes > 0
    AND candidate_size_bytes = octet_length(canonical_candidate)
  ),
  created_at timestamptz NOT NULL DEFAULT transaction_timestamp(),
  PRIMARY KEY (exposure_id, absolute_rank),
  UNIQUE (exposure_id, crew_id),
  CONSTRAINT crew_rec_exp_candidate_score_finite_ck
    CHECK (
      score <> 'NaN'::double precision
      AND score <> 'Infinity'::double precision
      AND score <> '-Infinity'::double precision
    ),
  CONSTRAINT crew_rec_exp_candidate_coverage_ck
    CHECK (coverage_mode IN ('full_featured', 'legacy_tagless')),
  CONSTRAINT crew_rec_exp_candidate_fingerprint_ck
    CHECK (candidate_fingerprint ~ '^[0-9a-f]{64}$')
);

CREATE INDEX idx_crew_rec_exp_event_user_served
ON public.crew_recommendation_exposure_event (user_id, served_at DESC);

CREATE OR REPLACE FUNCTION public.crew_recommendation_exposure_append_only()
RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
  RAISE EXCEPTION '% is append-only', TG_TABLE_NAME
    USING ERRCODE = '55000';
END;
$$;

CREATE TRIGGER trg_crew_rec_exp_event_append_only
BEFORE UPDATE OR DELETE ON public.crew_recommendation_exposure_event
FOR EACH ROW EXECUTE FUNCTION public.crew_recommendation_exposure_append_only();

CREATE TRIGGER trg_crew_rec_exp_candidate_append_only
BEFORE UPDATE OR DELETE ON public.crew_recommendation_exposure_candidate
FOR EACH ROW EXECUTE FUNCTION public.crew_recommendation_exposure_append_only();

CREATE OR REPLACE FUNCTION public.crew_recommendation_exposure_exact_candidates()
RETURNS trigger
LANGUAGE plpgsql
AS $$
DECLARE
  v_exposure_id varchar(128);
  v_expected integer;
  v_actual integer;
  v_min_rank integer;
  v_max_rank integer;
BEGIN
  v_exposure_id := COALESCE(NEW.exposure_id, OLD.exposure_id);

  SELECT returned_count
    INTO v_expected
  FROM public.crew_recommendation_exposure_event
  WHERE exposure_id = v_exposure_id;

  IF NOT FOUND THEN
    RETURN NULL;
  END IF;

  SELECT count(*)::integer, min(absolute_rank), max(absolute_rank)
    INTO v_actual, v_min_rank, v_max_rank
  FROM public.crew_recommendation_exposure_candidate
  WHERE exposure_id = v_exposure_id;

  IF v_actual <> v_expected THEN
    RAISE EXCEPTION
      'Crew exposure % expected % candidates but found %.',
      v_exposure_id, v_expected, v_actual
      USING ERRCODE = '23514';
  END IF;

  IF v_expected = 0 THEN
    IF v_min_rank IS NOT NULL OR v_max_rank IS NOT NULL THEN
      RAISE EXCEPTION 'Empty Crew exposure % has rank evidence.', v_exposure_id
        USING ERRCODE = '23514';
    END IF;
  ELSIF v_min_rank <> 1 OR v_max_rank <> v_expected THEN
    RAISE EXCEPTION
      'Crew exposure % ranks must be contiguous 1..%, found %..%.',
      v_exposure_id, v_expected, v_min_rank, v_max_rank
      USING ERRCODE = '23514';
  END IF;

  RETURN NULL;
END;
$$;

CREATE CONSTRAINT TRIGGER ctrg_crew_rec_exp_event_exact_candidates
AFTER INSERT ON public.crew_recommendation_exposure_event
DEFERRABLE INITIALLY DEFERRED
FOR EACH ROW EXECUTE FUNCTION public.crew_recommendation_exposure_exact_candidates();

CREATE CONSTRAINT TRIGGER ctrg_crew_rec_exp_candidate_exact_candidates
AFTER INSERT ON public.crew_recommendation_exposure_candidate
DEFERRABLE INITIALLY DEFERRED
FOR EACH ROW EXECUTE FUNCTION public.crew_recommendation_exposure_exact_candidates();

REVOKE ALL ON public.crew_recommendation_exposure_event FROM PUBLIC;
REVOKE ALL ON public.crew_recommendation_exposure_candidate FROM PUBLIC;

REVOKE ALL ON public.crew_recommendation_exposure_event
FROM jc_recommendation, jc_admin, jc_app, jc_auth;
REVOKE ALL ON public.crew_recommendation_exposure_candidate
FROM jc_recommendation, jc_admin, jc_app, jc_auth;

GRANT SELECT, INSERT ON public.crew_recommendation_exposure_event TO jc_recommendation;
GRANT SELECT, INSERT ON public.crew_recommendation_exposure_candidate TO jc_recommendation;

GRANT SELECT ON public.crew_recommendation_exposure_event TO jc_admin;
GRANT SELECT ON public.crew_recommendation_exposure_candidate TO jc_admin;

COMMIT;
