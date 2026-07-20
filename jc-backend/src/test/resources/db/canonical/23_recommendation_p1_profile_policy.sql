-- Journey Connect DB v2.6 - P1 behavior profile, policy assignment, and comparison evidence
BEGIN;


INSERT INTO public.tags (slug, name_ko, name_en, sort_order)
VALUES
  ('history', '역사', 'History', 70),
  ('adventure', '모험', 'Adventure', 80),
  ('wellness', '웰니스', 'Wellness', 90),
  ('running', '러닝', 'Running', 100),
  ('plogging', '플로깅', 'Plogging', 110),
  ('pilgrimage', '성지순례', 'Pilgrimage', 120),
  ('cycling', '사이클링', 'Cycling', 130)
ON CONFLICT (slug) DO UPDATE
SET name_ko = EXCLUDED.name_ko,
    name_en = EXCLUDED.name_en,
    sort_order = EXCLUDED.sort_order,
    is_active = true;

CREATE TABLE public.recommendation_user_preference (
  user_id bigint NOT NULL REFERENCES public.app_users(id) ON DELETE CASCADE,
  feature_id varchar(160) NOT NULL,
  preference_kind varchar(16) NOT NULL CHECK (preference_kind IN ('prefer', 'avoid')),
  strength double precision NOT NULL CHECK (
    strength >= 0 AND strength <= 1
    AND strength <> 'NaN'::float8
    AND strength <> 'Infinity'::float8
    AND strength <> '-Infinity'::float8
  ),
  active boolean NOT NULL DEFAULT true,
  updated_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (user_id, feature_id),
  CONSTRAINT recommendation_user_preference_feature_format
    CHECK (feature_id ~ '^[a-z][a-z0-9_]*:[a-z0-9][a-z0-9_:-]{0,127}$')
);

CREATE TABLE public.recommendation_p1_profile_snapshot (
  profile_snapshot_id varchar(128) PRIMARY KEY,
  user_id bigint NOT NULL REFERENCES public.app_users(id),
  reference_time timestamptz NOT NULL,
  profile_policy_version varchar(128) NOT NULL,
  feature_vocabulary_version varchar(128) NOT NULL,
  segment varchar(32) NOT NULL CHECK (segment IN ('empty', 'explicit_only', 'emerging', 'established')),
  explicit_preference_count integer NOT NULL CHECK (explicit_preference_count >= 0),
  input_event_count integer NOT NULL CHECK (input_event_count >= 0),
  accepted_event_count integer NOT NULL CHECK (accepted_event_count >= 0),
  ignored_event_count integer NOT NULL CHECK (ignored_event_count >= 0),
  duplicate_event_count integer NOT NULL CHECK (duplicate_event_count >= 0),
  accepted_behavior_weight double precision NOT NULL CHECK (
    accepted_behavior_weight >= 0
    AND accepted_behavior_weight <> 'NaN'::float8
    AND accepted_behavior_weight <> 'Infinity'::float8
    AND accepted_behavior_weight <> '-Infinity'::float8
  ),
  signal_count integer NOT NULL CHECK (signal_count >= 0),
  signals jsonb NOT NULL CHECK (jsonb_typeof(signals) = 'array'),
  fingerprint varchar(64) NOT NULL CHECK (fingerprint ~ '^[0-9a-f]{64}$'),
  created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT recommendation_p1_profile_snapshot_id_format
    CHECK (profile_snapshot_id ~ '^[A-Za-z0-9][A-Za-z0-9._:-]{0,127}$'),
  CONSTRAINT recommendation_p1_profile_event_partition
    CHECK (accepted_event_count + ignored_event_count + duplicate_event_count = input_event_count),
  CONSTRAINT recommendation_p1_profile_signal_count
    CHECK (signal_count = jsonb_array_length(signals)),
  UNIQUE (user_id, reference_time, profile_policy_version, feature_vocabulary_version, fingerprint)
);
CREATE INDEX recommendation_p1_profile_user_time_idx
  ON public.recommendation_p1_profile_snapshot(user_id, reference_time DESC);

CREATE TABLE public.recommendation_p1_policy_assignment (
  assignment_id varchar(128) PRIMARY KEY,
  baseline_run_id varchar(128) NOT NULL REFERENCES public.recommendation_run(run_id),
  treatment_run_id varchar(128) NOT NULL UNIQUE REFERENCES public.recommendation_run(run_id),
  user_id bigint NOT NULL REFERENCES public.app_users(id),
  session_id varchar(128) NOT NULL,
  profile_snapshot_id varchar(128) NOT NULL REFERENCES public.recommendation_p1_profile_snapshot(profile_snapshot_id),
  release_id varchar(128) NOT NULL,
  experiment_assignment varchar(16) NOT NULL CHECK (experiment_assignment = 'treatment'),
  segment varchar(32) NOT NULL CHECK (segment IN ('empty', 'explicit_only', 'emerging', 'established')),
  selection_reasons jsonb NOT NULL CHECK (
    jsonb_typeof(selection_reasons) = 'array'
    AND jsonb_array_length(selection_reasons) > 0
  ),
  profile_policy_version varchar(128) NOT NULL,
  feature_vocabulary_version varchar(128) NOT NULL,
  retrieval_policy_version varchar(128) NOT NULL,
  policy_bundle_version varchar(128) NOT NULL,
  score_policy_version varchar(128) NOT NULL,
  diversity_policy_version varchar(128) NOT NULL,
  low_exposure_policy_version varchar(128) NOT NULL,
  exploration_policy_version varchar(128) NOT NULL,
  created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT recommendation_p1_assignment_id_format
    CHECK (assignment_id ~ '^[A-Za-z0-9][A-Za-z0-9._:-]{0,127}$'),
  CONSTRAINT recommendation_p1_assignment_release_format
    CHECK (release_id ~ '^[A-Za-z0-9][A-Za-z0-9._:-]{0,127}$'),
  CONSTRAINT recommendation_p1_assignment_distinct_runs
    CHECK (baseline_run_id <> treatment_run_id)
);
CREATE INDEX recommendation_p1_assignment_release_idx
  ON public.recommendation_p1_policy_assignment(release_id, created_at DESC);
CREATE INDEX recommendation_p1_assignment_user_idx
  ON public.recommendation_p1_policy_assignment(user_id, created_at DESC);

CREATE TABLE public.recommendation_p1_comparison (
  comparison_id varchar(128) PRIMARY KEY,
  baseline_run_id varchar(128) NOT NULL REFERENCES public.recommendation_run(run_id),
  treatment_run_id varchar(128) NOT NULL UNIQUE REFERENCES public.recommendation_run(run_id),
  baseline_result_fingerprint varchar(64) NOT NULL CHECK (baseline_result_fingerprint ~ '^[0-9a-f]{64}$'),
  treatment_result_fingerprint varchar(64) NOT NULL CHECK (treatment_result_fingerprint ~ '^[0-9a-f]{64}$'),
  baseline_policy_version varchar(128) NOT NULL,
  treatment_policy_version varchar(128) NOT NULL,
  cutoff integer NOT NULL CHECK (cutoff BETWEEN 1 AND 100),
  baseline_count integer NOT NULL CHECK (baseline_count >= 0),
  treatment_count integer NOT NULL CHECK (treatment_count >= 0),
  overlap_count integer NOT NULL CHECK (overlap_count >= 0),
  overlap_rate double precision NOT NULL CHECK (
    overlap_rate >= 0 AND overlap_rate <= 1
    AND overlap_rate <> 'NaN'::float8
    AND overlap_rate <> 'Infinity'::float8
    AND overlap_rate <> '-Infinity'::float8
  ),
  mean_absolute_rank_displacement double precision NOT NULL CHECK (
    mean_absolute_rank_displacement >= 0
    AND mean_absolute_rank_displacement <> 'NaN'::float8
    AND mean_absolute_rank_displacement <> 'Infinity'::float8
    AND mean_absolute_rank_displacement <> '-Infinity'::float8
  ),
  treatment_unique_author_count integer NOT NULL CHECK (treatment_unique_author_count >= 0),
  treatment_unique_region_count integer NOT NULL CHECK (treatment_unique_region_count >= 0),
  treatment_unique_theme_count integer NOT NULL CHECK (treatment_unique_theme_count >= 0),
  treatment_low_exposure_share double precision NOT NULL CHECK (
    treatment_low_exposure_share >= 0 AND treatment_low_exposure_share <= 1
    AND treatment_low_exposure_share <> 'NaN'::float8
    AND treatment_low_exposure_share <> 'Infinity'::float8
    AND treatment_low_exposure_share <> '-Infinity'::float8
  ),
  treatment_top_author_share double precision NOT NULL CHECK (
    treatment_top_author_share >= 0 AND treatment_top_author_share <= 1
    AND treatment_top_author_share <> 'NaN'::float8
    AND treatment_top_author_share <> 'Infinity'::float8
    AND treatment_top_author_share <> '-Infinity'::float8
  ),
  treatment_top_region_share double precision NOT NULL CHECK (
    treatment_top_region_share >= 0 AND treatment_top_region_share <= 1
    AND treatment_top_region_share <> 'NaN'::float8
    AND treatment_top_region_share <> 'Infinity'::float8
    AND treatment_top_region_share <> '-Infinity'::float8
  ),
  treatment_mean_adjusted_popularity double precision NOT NULL CHECK (
    treatment_mean_adjusted_popularity >= 0 AND treatment_mean_adjusted_popularity <= 1
    AND treatment_mean_adjusted_popularity <> 'NaN'::float8
    AND treatment_mean_adjusted_popularity <> 'Infinity'::float8
    AND treatment_mean_adjusted_popularity <> '-Infinity'::float8
  ),
  comparison_fingerprint varchar(64) NOT NULL CHECK (comparison_fingerprint ~ '^[0-9a-f]{64}$'),
  created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT recommendation_p1_comparison_id_format
    CHECK (comparison_id ~ '^[A-Za-z0-9][A-Za-z0-9._:-]{0,127}$'),
  CONSTRAINT recommendation_p1_comparison_distinct_runs
    CHECK (baseline_run_id <> treatment_run_id),
  CONSTRAINT recommendation_p1_comparison_overlap
    CHECK (overlap_count <= baseline_count AND overlap_count <= treatment_count)
);
CREATE INDEX recommendation_p1_comparison_created_idx
  ON public.recommendation_p1_comparison(created_at DESC);
CREATE INDEX recommendation_p1_comparison_baseline_idx
  ON public.recommendation_p1_comparison(baseline_run_id, created_at DESC);

CREATE OR REPLACE FUNCTION public.validate_recommendation_p1_assignment_binding()
RETURNS trigger
LANGUAGE plpgsql
AS $$
DECLARE
  baseline public.recommendation_run%ROWTYPE;
  treatment public.recommendation_run%ROWTYPE;
  profile public.recommendation_p1_profile_snapshot%ROWTYPE;
BEGIN
  SELECT * INTO baseline FROM public.recommendation_run WHERE run_id = NEW.baseline_run_id;
  SELECT * INTO treatment FROM public.recommendation_run WHERE run_id = NEW.treatment_run_id;
  SELECT * INTO profile FROM public.recommendation_p1_profile_snapshot
    WHERE profile_snapshot_id = NEW.profile_snapshot_id;
  IF baseline.user_id <> NEW.user_id OR treatment.user_id <> NEW.user_id
     OR baseline.session_id <> NEW.session_id OR treatment.session_id <> NEW.session_id
     OR baseline.context_id <> treatment.context_id
     OR baseline.reference_time <> treatment.reference_time
     OR baseline.run_mode <> treatment.run_mode
     OR profile.user_id <> NEW.user_id
     OR profile.reference_time <> treatment.reference_time
     OR profile.segment <> NEW.segment
     OR profile.profile_policy_version <> NEW.profile_policy_version
     OR profile.feature_vocabulary_version <> NEW.feature_vocabulary_version
     OR treatment.ranking_policy_version <> NEW.policy_bundle_version
     OR treatment.score_policy_version <> NEW.score_policy_version
     OR treatment.diversity_policy_version <> NEW.diversity_policy_version
     OR treatment.exploration_policy_version <> NEW.exploration_policy_version THEN
    RAISE EXCEPTION 'P1 policy assignment binding mismatch' USING ERRCODE = '23514';
  END IF;
  RETURN NEW;
END;
$$;

CREATE OR REPLACE FUNCTION public.validate_recommendation_p1_comparison_binding()
RETURNS trigger
LANGUAGE plpgsql
AS $$
DECLARE
  baseline public.recommendation_run%ROWTYPE;
  treatment public.recommendation_run%ROWTYPE;
BEGIN
  SELECT * INTO baseline FROM public.recommendation_run WHERE run_id = NEW.baseline_run_id;
  SELECT * INTO treatment FROM public.recommendation_run WHERE run_id = NEW.treatment_run_id;
  IF baseline.user_id <> treatment.user_id
     OR baseline.session_id <> treatment.session_id
     OR baseline.context_id <> treatment.context_id
     OR baseline.reference_time <> treatment.reference_time
     OR baseline.run_mode <> treatment.run_mode
     OR baseline.result_fingerprint <> NEW.baseline_result_fingerprint
     OR treatment.result_fingerprint <> NEW.treatment_result_fingerprint
     OR baseline.ranking_policy_version <> NEW.baseline_policy_version
     OR treatment.ranking_policy_version <> NEW.treatment_policy_version
     OR baseline.final_ranked_candidate_count < NEW.baseline_count
     OR treatment.final_ranked_candidate_count < NEW.treatment_count THEN
    RAISE EXCEPTION 'P1 comparison binding mismatch' USING ERRCODE = '23514';
  END IF;
  RETURN NEW;
END;
$$;

CREATE OR REPLACE FUNCTION public.replace_recommendation_user_preferences(
  p_preferences jsonb
)
RETURNS integer
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = pg_catalog, public, pg_temp
AS $$
DECLARE
  v_user_id bigint;
  v_count integer;
BEGIN
  v_user_id := public.require_active_user();

  IF p_preferences IS NULL OR jsonb_typeof(p_preferences) <> 'array' THEN
    RAISE EXCEPTION 'Recommendation preferences must be a JSON array.'
      USING ERRCODE = '22023';
  END IF;
  IF jsonb_array_length(p_preferences) > 64 THEN
    RAISE EXCEPTION 'Recommendation preference count exceeds 64.'
      USING ERRCODE = '22023';
  END IF;
  IF EXISTS (
    SELECT 1
    FROM jsonb_array_elements(p_preferences) item
    WHERE jsonb_typeof(item) <> 'object'
       OR jsonb_typeof(item->'featureId') <> 'string'
       OR (item->>'featureId') !~ '^[a-z][a-z0-9_]*:[a-z0-9][a-z0-9_:-]{0,127}$'
       OR jsonb_typeof(item->'preferenceKind') <> 'string'
       OR (item->>'preferenceKind') NOT IN ('prefer', 'avoid')
       OR jsonb_typeof(item->'strength') <> 'number'
       OR (item->>'strength')::double precision < 0
       OR (item->>'strength')::double precision > 1
  ) THEN
    RAISE EXCEPTION 'Recommendation preference payload is invalid.'
      USING ERRCODE = '22023';
  END IF;
  IF EXISTS (
    SELECT 1
    FROM jsonb_array_elements(p_preferences) item
    GROUP BY item->>'featureId'
    HAVING count(*) > 1
  ) THEN
    RAISE EXCEPTION 'Recommendation preference feature IDs must be unique.'
      USING ERRCODE = '22023';
  END IF;

  DELETE FROM public.recommendation_user_preference
  WHERE user_id = v_user_id;

  INSERT INTO public.recommendation_user_preference(
    user_id, feature_id, preference_kind, strength, active, updated_at
  )
  SELECT v_user_id,
         item->>'featureId',
         item->>'preferenceKind',
         (item->>'strength')::double precision,
         true,
         CURRENT_TIMESTAMP
  FROM jsonb_array_elements(p_preferences) item;

  GET DIAGNOSTICS v_count = ROW_COUNT;
  RETURN v_count;
END;
$$;

ALTER FUNCTION public.replace_recommendation_user_preferences(jsonb)
  OWNER TO jc_security_owner;

CREATE TRIGGER recommendation_p1_profile_snapshot_append_only
BEFORE UPDATE OR DELETE ON public.recommendation_p1_profile_snapshot
FOR EACH ROW EXECUTE FUNCTION public.prevent_recommendation_append_only_mutation();
CREATE TRIGGER recommendation_p1_policy_assignment_append_only
BEFORE UPDATE OR DELETE ON public.recommendation_p1_policy_assignment
FOR EACH ROW EXECUTE FUNCTION public.prevent_recommendation_append_only_mutation();
CREATE TRIGGER recommendation_p1_comparison_append_only
BEFORE UPDATE OR DELETE ON public.recommendation_p1_comparison
FOR EACH ROW EXECUTE FUNCTION public.prevent_recommendation_append_only_mutation();
CREATE TRIGGER recommendation_p1_policy_assignment_binding
BEFORE INSERT ON public.recommendation_p1_policy_assignment
FOR EACH ROW EXECUTE FUNCTION public.validate_recommendation_p1_assignment_binding();
CREATE TRIGGER recommendation_p1_comparison_binding
BEFORE INSERT ON public.recommendation_p1_comparison
FOR EACH ROW EXECUTE FUNCTION public.validate_recommendation_p1_comparison_binding();

REVOKE ALL ON
  public.recommendation_user_preference,
  public.recommendation_p1_profile_snapshot,
  public.recommendation_p1_policy_assignment,
  public.recommendation_p1_comparison
FROM PUBLIC, jc_app, jc_auth, jc_admin, jc_security_owner, jc_recommendation;
GRANT SELECT ON public.recommendation_user_preference TO jc_recommendation, jc_admin;
GRANT SELECT, INSERT, UPDATE, DELETE ON public.recommendation_user_preference
  TO jc_security_owner;
GRANT SELECT, INSERT ON
  public.recommendation_p1_profile_snapshot,
  public.recommendation_p1_policy_assignment,
  public.recommendation_p1_comparison
TO jc_recommendation;
GRANT SELECT ON
  public.recommendation_p1_profile_snapshot,
  public.recommendation_p1_policy_assignment,
  public.recommendation_p1_comparison
TO jc_admin;
REVOKE INSERT, UPDATE, DELETE, TRUNCATE ON
  public.recommendation_p1_profile_snapshot,
  public.recommendation_p1_policy_assignment,
  public.recommendation_p1_comparison
FROM jc_app, jc_auth, jc_admin;
REVOKE UPDATE, DELETE, TRUNCATE ON
  public.recommendation_p1_profile_snapshot,
  public.recommendation_p1_policy_assignment,
  public.recommendation_p1_comparison
FROM jc_recommendation;
REVOKE ALL ON FUNCTION public.replace_recommendation_user_preferences(jsonb)
  FROM PUBLIC, jc_app, jc_auth, jc_admin, jc_security_owner, jc_recommendation;
GRANT EXECUTE ON FUNCTION public.replace_recommendation_user_preferences(jsonb)
  TO jc_recommendation;
GRANT EXECUTE ON FUNCTION public.validate_recommendation_p1_assignment_binding()
  TO jc_recommendation;
GRANT EXECUTE ON FUNCTION public.validate_recommendation_p1_comparison_binding()
  TO jc_recommendation;

COMMIT;
