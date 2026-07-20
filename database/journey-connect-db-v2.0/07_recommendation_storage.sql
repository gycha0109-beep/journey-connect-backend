-- Journey Connect DB v1.9 - P0 recommendation trusted storage
-- Target: PostgreSQL 15+
-- Prerequisite: Journey Connect DB v1.8 files 01~06 applied successfully.
-- This migration is append-only by design. Existing v1.8 objects are not modified.

BEGIN;

CREATE EXTENSION IF NOT EXISTS pgcrypto;

DO $$
DECLARE
  v_missing text;
BEGIN
  SELECT string_agg(required_object, ', ' ORDER BY required_object)
    INTO v_missing
  FROM (
    VALUES
      ('public.app_users', to_regclass('public.app_users')),
      ('public.posts', to_regclass('public.posts')),
      ('public.regions', to_regclass('public.regions')),
      ('public.post_likes', to_regclass('public.post_likes')),
      ('public.bookmarks', to_regclass('public.bookmarks'))
  ) AS required(required_object, object_oid)
  WHERE object_oid IS NULL;

  IF v_missing IS NOT NULL THEN
    RAISE EXCEPTION 'Journey Connect DB v1.8 prerequisite objects are missing: %', v_missing
      USING ERRCODE = '42P01';
  END IF;

  IF NOT EXISTS (
    SELECT 1
    FROM information_schema.columns
    WHERE table_schema = 'public'
      AND table_name = 'posts'
      AND column_name = 'moderation_status'
  ) THEN
    RAISE EXCEPTION 'Journey Connect DB v1.8 prerequisite column public.posts.moderation_status is missing.'
      USING ERRCODE = '42703';
  END IF;

  IF NOT EXISTS (
    SELECT 1
    FROM information_schema.columns
    WHERE table_schema = 'public'
      AND table_name = 'app_users'
      AND column_name = 'account_status'
  ) THEN
    RAISE EXCEPTION 'Journey Connect DB v1.8 prerequisite column public.app_users.account_status is missing.'
      USING ERRCODE = '42703';
  END IF;
END;
$$;

CREATE OR REPLACE FUNCTION public.recommendation_sha256_hex(p_payload bytea)
RETURNS varchar(64)
LANGUAGE sql
IMMUTABLE
STRICT
SECURITY DEFINER
SET search_path = pg_catalog, public, pg_temp
AS $$
  SELECT encode(public.digest(p_payload, 'sha256'), 'hex')::varchar(64);
$$;

CREATE OR REPLACE FUNCTION public.recommendation_snapshot_sha256_hex(
  p_snapshot_kind varchar,
  p_schema_version varchar,
  p_payload bytea
)
RETURNS varchar(64)
LANGUAGE sql
IMMUTABLE
STRICT
SECURITY DEFINER
SET search_path = pg_catalog, public, pg_temp
AS $$
  SELECT public.recommendation_sha256_hex(
    convert_to('journey-connect:snapshot:v1', 'UTF8')
    || decode('00', 'hex')
    || convert_to(p_snapshot_kind, 'UTF8')
    || decode('00', 'hex')
    || convert_to(p_schema_version, 'UTF8')
    || decode('00', 'hex')
    || p_payload
  );
$$;

CREATE OR REPLACE FUNCTION public.prevent_recommendation_append_only_mutation()
RETURNS trigger
LANGUAGE plpgsql
SECURITY INVOKER
SET search_path = pg_catalog, pg_temp
AS $$
BEGIN
  RAISE EXCEPTION 'Recommendation history table %.% is append-only.', TG_TABLE_SCHEMA, TG_TABLE_NAME
    USING ERRCODE = '55000';
END;
$$;

CREATE TABLE public.recommendation_snapshot (
  snapshot_id varchar(128) PRIMARY KEY,
  snapshot_kind varchar(40) NOT NULL
    CHECK (snapshot_kind IN (
      'ranking_input_v1',
      'diversity_metadata_v1',
      'exploration_metadata_v1',
      'ranking_result_v1',
      'exposure_event_v1'
    )),
  schema_version varchar(64) NOT NULL,
  canonicalization_version varchar(64) NOT NULL,
  hash_algorithm varchar(16) NOT NULL DEFAULT 'sha256'
    CHECK (hash_algorithm = 'sha256'),
  content_hash varchar(64) NOT NULL
    CHECK (content_hash ~ '^[0-9a-f]{64}$'),
  canonical_payload bytea NOT NULL,
  payload_json jsonb,
  payload_size_bytes integer NOT NULL CHECK (payload_size_bytes BETWEEN 0 AND 16777216),
  created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT recommendation_snapshot_id_format_check
    CHECK (snapshot_id ~ '^[A-Za-z0-9][A-Za-z0-9._:-]{0,127}$'),
  CONSTRAINT recommendation_snapshot_payload_size_check
    CHECK (octet_length(canonical_payload) = payload_size_bytes),
  CONSTRAINT recommendation_snapshot_hash_check
    CHECK (content_hash = public.recommendation_snapshot_sha256_hex(
      snapshot_kind, schema_version, canonical_payload
    )),
  CONSTRAINT recommendation_snapshot_payload_json_check
    CHECK (payload_json IS NULL OR jsonb_typeof(payload_json) IN ('object', 'array')),
  CONSTRAINT recommendation_snapshot_content_uq
    UNIQUE (snapshot_kind, schema_version, canonicalization_version, hash_algorithm, content_hash)
);

CREATE INDEX recommendation_snapshot_created_idx
ON public.recommendation_snapshot (created_at DESC, snapshot_id);

CREATE TABLE public.recommendation_run (
  run_id varchar(128) PRIMARY KEY,
  request_id varchar(128) NOT NULL,
  run_mode varchar(16) NOT NULL
    CHECK (run_mode IN ('shadow', 'canary', 'live')),
  run_status varchar(20) NOT NULL
    CHECK (run_status IN ('succeeded', 'fallback', 'failed')),
  user_id bigint NOT NULL REFERENCES public.app_users(id) ON DELETE RESTRICT,
  session_id varchar(128) NOT NULL,
  context_id varchar(128) NOT NULL,
  surface varchar(20) NOT NULL
    CHECK (surface IN ('home', 'search', 'detail', 'profile', 'crew')),
  reference_time timestamptz NOT NULL,
  ranking_snapshot_id varchar(128) NOT NULL
    REFERENCES public.recommendation_snapshot(snapshot_id) ON DELETE RESTRICT,
  metadata_snapshot_id varchar(128) NOT NULL
    REFERENCES public.recommendation_snapshot(snapshot_id) ON DELETE RESTRICT,
  exploration_snapshot_id varchar(128) NOT NULL
    REFERENCES public.recommendation_snapshot(snapshot_id) ON DELETE RESTRICT,
  result_snapshot_id varchar(128) NOT NULL
    REFERENCES public.recommendation_snapshot(snapshot_id) ON DELETE RESTRICT,
  ranking_policy_version varchar(64) NOT NULL,
  base_integration_policy_version varchar(64) NOT NULL,
  base_ranking_policy_version varchar(64) NOT NULL,
  score_policy_version varchar(64) NOT NULL,
  component_policy_versions jsonb NOT NULL,
  diversity_policy_version varchar(64) NOT NULL,
  exploration_policy_version varchar(64) NOT NULL,
  exploration_seed varchar(256) NOT NULL,
  ranking_status varchar(16) NOT NULL
    CHECK (ranking_status IN ('ranked', 'empty')),
  ranking_empty_reason varchar(64),
  requested_limit integer,
  effective_limit integer NOT NULL CHECK (effective_limit >= 0),
  input_count integer NOT NULL CHECK (input_count >= 0),
  scored_candidate_count integer NOT NULL CHECK (scored_candidate_count >= 0),
  final_ranked_candidate_count integer NOT NULL CHECK (final_ranked_candidate_count >= 0),
  terminal_candidate_count integer NOT NULL CHECK (terminal_candidate_count >= 0),
  result_fingerprint varchar(64) NOT NULL
    CHECK (result_fingerprint ~ '^[0-9a-f]{64}$'),
  core_build_id varchar(128) NOT NULL,
  duration_ms bigint NOT NULL CHECK (duration_ms >= 0),
  fallback_reason varchar(64),
  created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT recommendation_run_id_format_check
    CHECK (run_id ~ '^[A-Za-z0-9][A-Za-z0-9._:-]{0,127}$'),
  CONSTRAINT recommendation_run_request_id_format_check
    CHECK (request_id ~ '^[A-Za-z0-9][A-Za-z0-9._:-]{0,127}$'),
  CONSTRAINT recommendation_run_session_id_format_check
    CHECK (session_id ~ '^[A-Za-z0-9][A-Za-z0-9._:-]{0,127}$'),
  CONSTRAINT recommendation_run_context_id_format_check
    CHECK (context_id ~ '^[A-Za-z0-9][A-Za-z0-9._:-]{0,127}$'),
  CONSTRAINT recommendation_run_component_policy_versions_check
    CHECK (jsonb_typeof(component_policy_versions) = 'object'),
  CONSTRAINT recommendation_run_requested_limit_check
    CHECK (requested_limit IS NULL OR requested_limit > 0),
  CONSTRAINT recommendation_run_count_partition_check
    CHECK (input_count = scored_candidate_count + terminal_candidate_count),
  CONSTRAINT recommendation_run_ranked_count_check
    CHECK (final_ranked_candidate_count <= scored_candidate_count),
  CONSTRAINT recommendation_run_empty_reason_check
    CHECK (
      (ranking_status = 'empty' AND ranking_empty_reason = 'no_scored_candidates'
       AND final_ranked_candidate_count = 0)
      OR
      (ranking_status = 'ranked' AND ranking_empty_reason IS NULL)
    ),
  CONSTRAINT recommendation_run_status_fallback_check
    CHECK (
      (run_status = 'fallback' AND fallback_reason IS NOT NULL)
      OR
      (run_status <> 'fallback' AND fallback_reason IS NULL)
    ),
  CONSTRAINT recommendation_run_request_uq UNIQUE (request_id)
);

CREATE INDEX recommendation_run_user_created_idx
ON public.recommendation_run (user_id, created_at DESC, run_id);
CREATE INDEX recommendation_run_session_created_idx
ON public.recommendation_run (session_id, created_at DESC, run_id);
CREATE INDEX recommendation_run_reference_time_idx
ON public.recommendation_run (reference_time DESC, run_id);
CREATE INDEX recommendation_run_snapshot_binding_idx
ON public.recommendation_run (
  ranking_snapshot_id, metadata_snapshot_id, exploration_snapshot_id, result_snapshot_id
);

CREATE OR REPLACE FUNCTION public.validate_recommendation_run_snapshot_bindings()
RETURNS trigger
LANGUAGE plpgsql
SECURITY INVOKER
SET search_path = pg_catalog, public, pg_temp
AS $$
DECLARE
  v_ranking_kind varchar(40);
  v_metadata_kind varchar(40);
  v_exploration_kind varchar(40);
  v_result_kind varchar(40);
  v_account_status varchar(20);
BEGIN
  SELECT snapshot_kind INTO v_ranking_kind
  FROM public.recommendation_snapshot
  WHERE snapshot_id = NEW.ranking_snapshot_id;

  SELECT snapshot_kind INTO v_metadata_kind
  FROM public.recommendation_snapshot
  WHERE snapshot_id = NEW.metadata_snapshot_id;

  SELECT snapshot_kind INTO v_exploration_kind
  FROM public.recommendation_snapshot
  WHERE snapshot_id = NEW.exploration_snapshot_id;

  SELECT snapshot_kind INTO v_result_kind
  FROM public.recommendation_snapshot
  WHERE snapshot_id = NEW.result_snapshot_id;

  IF v_ranking_kind <> 'ranking_input_v1'
     OR v_metadata_kind <> 'diversity_metadata_v1'
     OR v_exploration_kind <> 'exploration_metadata_v1'
     OR v_result_kind <> 'ranking_result_v1' THEN
    RAISE EXCEPTION 'Recommendation run snapshot kinds do not match ranking/diversity/exploration/result bindings.'
      USING ERRCODE = '23514';
  END IF;

  SELECT account_status INTO v_account_status
  FROM public.app_users
  WHERE id = NEW.user_id;

  IF v_account_status <> 'active' THEN
    RAISE EXCEPTION 'Recommendation run user % must be active at run creation.', NEW.user_id
      USING ERRCODE = '23514';
  END IF;

  RETURN NEW;
END;
$$;

CREATE TRIGGER recommendation_run_validate_snapshot_bindings
BEFORE INSERT ON public.recommendation_run
FOR EACH ROW EXECUTE FUNCTION public.validate_recommendation_run_snapshot_bindings();

CREATE TABLE public.recommendation_run_candidate (
  run_id varchar(128) NOT NULL
    REFERENCES public.recommendation_run(run_id) ON DELETE RESTRICT,
  absolute_rank integer NOT NULL CHECK (absolute_rank > 0),
  entity_type varchar(20) NOT NULL CHECK (entity_type = 'post'),
  entity_key varchar(160) NOT NULL,
  source_entity_id bigint NOT NULL CHECK (source_entity_id > 0),
  origin varchar(20) NOT NULL CHECK (origin IN ('personalized', 'exploration')),
  score double precision,
  score_is_negative_zero boolean NOT NULL DEFAULT false,
  base_absolute_rank integer,
  diversified_absolute_rank integer,
  exploration_quality_score double precision,
  recent_exposure_count integer,
  seeded_tie_break_key bigint,
  exploration_pool_rank integer,
  target_insertion_rank integer,
  score_policy_version varchar(64) NOT NULL,
  provenance jsonb NOT NULL DEFAULT '{}'::jsonb,
  created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (run_id, absolute_rank),
  CONSTRAINT recommendation_run_candidate_entity_uq UNIQUE (run_id, entity_key),
  CONSTRAINT recommendation_run_candidate_entity_key_check
    CHECK (entity_key = 'post:' || source_entity_id::text),
  CONSTRAINT recommendation_run_candidate_score_zero_check
    CHECK (NOT score_is_negative_zero OR score = 0.0),
  CONSTRAINT recommendation_run_candidate_rank_provenance_check
    CHECK (
      (origin = 'personalized'
       AND score IS NOT NULL
       AND score BETWEEN 0.0 AND 1.0
       AND base_absolute_rank IS NOT NULL
       AND diversified_absolute_rank IS NOT NULL
       AND exploration_quality_score IS NULL
       AND recent_exposure_count IS NULL
       AND seeded_tie_break_key IS NULL
       AND exploration_pool_rank IS NULL
       AND target_insertion_rank IS NULL)
      OR
      (origin = 'exploration'
       AND score IS NULL
       AND score_is_negative_zero = false
       AND base_absolute_rank IS NULL
       AND diversified_absolute_rank IS NULL
       AND exploration_quality_score BETWEEN 0.0 AND 1.0
       AND recent_exposure_count IS NOT NULL
       AND seeded_tie_break_key IS NOT NULL
       AND exploration_pool_rank IS NOT NULL
       AND target_insertion_rank IS NOT NULL)
    ),
  CONSTRAINT recommendation_run_candidate_positive_provenance_check
    CHECK (
      (base_absolute_rank IS NULL OR base_absolute_rank > 0)
      AND (diversified_absolute_rank IS NULL OR diversified_absolute_rank > 0)
      AND (recent_exposure_count IS NULL OR recent_exposure_count >= 0)
      AND (seeded_tie_break_key IS NULL OR seeded_tie_break_key BETWEEN 0 AND 4294967295)
      AND (exploration_pool_rank IS NULL OR exploration_pool_rank > 0)
      AND (target_insertion_rank IS NULL OR target_insertion_rank > 0)
    ),
  CONSTRAINT recommendation_run_candidate_provenance_json_check
    CHECK (jsonb_typeof(provenance) = 'object')
);

CREATE INDEX recommendation_run_candidate_source_idx
ON public.recommendation_run_candidate (source_entity_id, run_id);
CREATE INDEX recommendation_run_candidate_origin_idx
ON public.recommendation_run_candidate (run_id, origin, absolute_rank);

CREATE OR REPLACE FUNCTION public.validate_recommendation_run_candidate_source()
RETURNS trigger
LANGUAGE plpgsql
SECURITY INVOKER
SET search_path = pg_catalog, public, pg_temp
AS $$
BEGIN
  IF NOT EXISTS (
    SELECT 1
    FROM public.recommendation_run r
    JOIN public.posts p ON p.id = NEW.source_entity_id
    JOIN public.app_users author_user ON author_user.id = p.author_id
    WHERE r.run_id = NEW.run_id
      AND p.status = 'published'
      AND p.moderation_status = 'visible'
      AND author_user.account_status = 'active'
      AND (
        p.visibility = 'public'
        OR p.author_id = r.user_id
        OR (
          p.visibility = 'followers'
          AND EXISTS (
            SELECT 1
            FROM public.follows f
            WHERE f.follower_id = r.user_id
              AND f.following_id = p.author_id
          )
        )
      )
  ) THEN
    RAISE EXCEPTION 'Recommendation candidate source post % is not accessible, active-author, visible and published for run %.',
      NEW.source_entity_id, NEW.run_id
      USING ERRCODE = '23514';
  END IF;

  RETURN NEW;
END;
$$;

CREATE TRIGGER recommendation_run_candidate_validate_source
BEFORE INSERT ON public.recommendation_run_candidate
FOR EACH ROW EXECUTE FUNCTION public.validate_recommendation_run_candidate_source();

CREATE TABLE public.recommendation_run_terminal_candidate (
  run_id varchar(128) NOT NULL
    REFERENCES public.recommendation_run(run_id) ON DELETE RESTRICT,
  entity_type varchar(20) NOT NULL CHECK (entity_type = 'post'),
  entity_key varchar(160) NOT NULL,
  source_entity_id bigint NOT NULL CHECK (source_entity_id > 0),
  score_status varchar(32) NOT NULL
    CHECK (score_status IN ('not_applicable', 'hard_excluded')),
  not_applicable_reason varchar(64),
  hard_exclusion_reason varchar(64),
  score_policy_version varchar(64) NOT NULL,
  audit_payload jsonb NOT NULL DEFAULT '{}'::jsonb,
  created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (run_id, entity_key),
  CONSTRAINT recommendation_run_terminal_candidate_entity_key_check
    CHECK (entity_key = 'post:' || source_entity_id::text),
  CONSTRAINT recommendation_run_terminal_candidate_reason_check
    CHECK (
      (score_status = 'not_applicable'
       AND not_applicable_reason IN (
         'unsupported_entity_type', 'expired_context', 'no_anchor_component'
       )
       AND hard_exclusion_reason IS NULL)
      OR
      (score_status = 'hard_excluded'
       AND hard_exclusion_reason IN (
         'context_hard_exclusion', 'interest_hard_exclusion', 'multiple_hard_exclusions'
       )
       AND not_applicable_reason IS NULL)
    ),
  CONSTRAINT recommendation_run_terminal_candidate_audit_json_check
    CHECK (jsonb_typeof(audit_payload) = 'object')
);

CREATE INDEX recommendation_run_terminal_candidate_source_idx
ON public.recommendation_run_terminal_candidate (source_entity_id, run_id);

CREATE TRIGGER recommendation_run_terminal_candidate_validate_source
BEFORE INSERT ON public.recommendation_run_terminal_candidate
FOR EACH ROW EXECUTE FUNCTION public.validate_recommendation_run_candidate_source();

CREATE OR REPLACE FUNCTION public.assert_recommendation_run_integrity(p_run_id varchar)
RETURNS void
LANGUAGE plpgsql
SECURITY INVOKER
SET search_path = pg_catalog, public, pg_temp
AS $$
DECLARE
  v_expected_ranked_count integer;
  v_expected_terminal_count integer;
  v_actual_ranked_count integer;
  v_actual_terminal_count integer;
  v_min_rank integer;
  v_max_rank integer;
BEGIN
  SELECT final_ranked_candidate_count, terminal_candidate_count
    INTO v_expected_ranked_count, v_expected_terminal_count
  FROM public.recommendation_run
  WHERE run_id = p_run_id;

  IF NOT FOUND THEN
    RETURN;
  END IF;

  SELECT count(*), min(absolute_rank), max(absolute_rank)
    INTO v_actual_ranked_count, v_min_rank, v_max_rank
  FROM public.recommendation_run_candidate
  WHERE run_id = p_run_id;

  SELECT count(*)
    INTO v_actual_terminal_count
  FROM public.recommendation_run_terminal_candidate
  WHERE run_id = p_run_id;

  IF v_actual_ranked_count <> v_expected_ranked_count THEN
    RAISE EXCEPTION 'Recommendation run % expected % ranked candidates but has %.',
      p_run_id, v_expected_ranked_count, v_actual_ranked_count
      USING ERRCODE = '23514';
  END IF;

  IF v_expected_ranked_count > 0
     AND (v_min_rank <> 1 OR v_max_rank <> v_expected_ranked_count) THEN
    RAISE EXCEPTION 'Recommendation run % ranked candidate positions are not contiguous.', p_run_id
      USING ERRCODE = '23514';
  END IF;

  IF v_actual_terminal_count <> v_expected_terminal_count THEN
    RAISE EXCEPTION 'Recommendation run % expected % terminal candidates but has %.',
      p_run_id, v_expected_terminal_count, v_actual_terminal_count
      USING ERRCODE = '23514';
  END IF;

  IF EXISTS (
    SELECT 1
    FROM public.recommendation_run_candidate ranked
    JOIN public.recommendation_run_terminal_candidate terminal
      ON terminal.run_id = ranked.run_id
     AND terminal.entity_key = ranked.entity_key
    WHERE ranked.run_id = p_run_id
  ) THEN
    RAISE EXCEPTION 'Recommendation run % contains an entity in both ranked and terminal partitions.', p_run_id
      USING ERRCODE = '23514';
  END IF;
END;
$$;

CREATE OR REPLACE FUNCTION public.check_recommendation_run_integrity_from_run()
RETURNS trigger
LANGUAGE plpgsql
SECURITY INVOKER
SET search_path = pg_catalog, public, pg_temp
AS $$
BEGIN
  PERFORM public.assert_recommendation_run_integrity(NEW.run_id);
  RETURN NULL;
END;
$$;

CREATE OR REPLACE FUNCTION public.check_recommendation_run_integrity_from_ranked_candidate()
RETURNS trigger
LANGUAGE plpgsql
SECURITY INVOKER
SET search_path = pg_catalog, public, pg_temp
AS $$
BEGIN
  PERFORM public.assert_recommendation_run_integrity(
    CASE WHEN TG_OP = 'DELETE' THEN OLD.run_id ELSE NEW.run_id END
  );
  RETURN NULL;
END;
$$;

CREATE OR REPLACE FUNCTION public.check_recommendation_run_integrity_from_terminal_candidate()
RETURNS trigger
LANGUAGE plpgsql
SECURITY INVOKER
SET search_path = pg_catalog, public, pg_temp
AS $$
BEGIN
  PERFORM public.assert_recommendation_run_integrity(
    CASE WHEN TG_OP = 'DELETE' THEN OLD.run_id ELSE NEW.run_id END
  );
  RETURN NULL;
END;
$$;

CREATE CONSTRAINT TRIGGER recommendation_run_candidate_partition_check
AFTER INSERT ON public.recommendation_run
DEFERRABLE INITIALLY DEFERRED
FOR EACH ROW EXECUTE FUNCTION public.check_recommendation_run_integrity_from_run();

CREATE CONSTRAINT TRIGGER recommendation_ranked_candidate_partition_check
AFTER INSERT OR UPDATE OR DELETE ON public.recommendation_run_candidate
DEFERRABLE INITIALLY DEFERRED
FOR EACH ROW EXECUTE FUNCTION public.check_recommendation_run_integrity_from_ranked_candidate();

CREATE CONSTRAINT TRIGGER recommendation_terminal_candidate_partition_check
AFTER INSERT OR UPDATE OR DELETE ON public.recommendation_run_terminal_candidate
DEFERRABLE INITIALLY DEFERRED
FOR EACH ROW EXECUTE FUNCTION public.check_recommendation_run_integrity_from_terminal_candidate();

CREATE TABLE public.recommendation_exposure_event (
  event_id varchar(128) PRIMARY KEY,
  idempotency_key varchar(160) NOT NULL UNIQUE,
  schema_version varchar(64) NOT NULL,
  payload_fingerprint varchar(64) NOT NULL
    CHECK (payload_fingerprint ~ '^[0-9a-f]{64}$'),
  canonical_payload bytea NOT NULL,
  payload_size_bytes integer NOT NULL CHECK (payload_size_bytes BETWEEN 0 AND 2097152),
  run_id varchar(128) NOT NULL
    REFERENCES public.recommendation_run(run_id) ON DELETE RESTRICT,
  user_id bigint NOT NULL REFERENCES public.app_users(id) ON DELETE RESTRICT,
  session_id varchar(128) NOT NULL,
  context_id varchar(128) NOT NULL,
  surface varchar(20) NOT NULL
    CHECK (surface IN ('home', 'search', 'detail', 'profile', 'crew')),
  served_at timestamptz NOT NULL,
  replay_key varchar(256) NOT NULL,
  page_fingerprint varchar(64) NOT NULL
    CHECK (page_fingerprint ~ '^[0-9a-f]{64}$'),
  cursor_version varchar(64) NOT NULL,
  page_start_rank integer,
  page_end_rank integer,
  page_candidate_count integer NOT NULL CHECK (page_candidate_count >= 0),
  has_next_page boolean NOT NULL,
  created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT recommendation_exposure_event_id_format_check
    CHECK (event_id ~ '^[A-Za-z0-9][A-Za-z0-9._:-]{0,127}$'),
  CONSTRAINT recommendation_exposure_idempotency_format_check
    CHECK (char_length(btrim(idempotency_key)) BETWEEN 1 AND 160),
  CONSTRAINT recommendation_exposure_payload_size_check
    CHECK (octet_length(canonical_payload) = payload_size_bytes),
  CONSTRAINT recommendation_exposure_payload_hash_check
    CHECK (payload_fingerprint = public.recommendation_sha256_hex(canonical_payload)),
  CONSTRAINT recommendation_exposure_page_range_check
    CHECK (
      (page_candidate_count = 0 AND page_start_rank IS NULL AND page_end_rank IS NULL)
      OR
      (page_candidate_count > 0
       AND page_start_rank > 0
       AND page_end_rank >= page_start_rank
       AND page_end_rank - page_start_rank + 1 = page_candidate_count)
    )
);

CREATE INDEX recommendation_exposure_event_run_served_idx
ON public.recommendation_exposure_event (run_id, served_at, event_id);
CREATE INDEX recommendation_exposure_event_user_served_idx
ON public.recommendation_exposure_event (user_id, served_at DESC, event_id);
CREATE INDEX recommendation_exposure_event_session_served_idx
ON public.recommendation_exposure_event (session_id, served_at DESC, event_id);
CREATE INDEX recommendation_exposure_event_replay_key_idx
ON public.recommendation_exposure_event (replay_key, served_at, event_id);

CREATE OR REPLACE FUNCTION public.validate_recommendation_exposure_event_binding()
RETURNS trigger
LANGUAGE plpgsql
SECURITY INVOKER
SET search_path = pg_catalog, public, pg_temp
AS $$
DECLARE
  v_user_id bigint;
  v_session_id varchar(128);
  v_context_id varchar(128);
  v_surface varchar(20);
  v_reference_time timestamptz;
BEGIN
  SELECT user_id, session_id, context_id, surface, reference_time
    INTO v_user_id, v_session_id, v_context_id, v_surface, v_reference_time
  FROM public.recommendation_run
  WHERE run_id = NEW.run_id;

  IF v_user_id IS DISTINCT FROM NEW.user_id
     OR v_session_id IS DISTINCT FROM NEW.session_id
     OR v_context_id IS DISTINCT FROM NEW.context_id
     OR v_surface IS DISTINCT FROM NEW.surface THEN
    RAISE EXCEPTION 'Recommendation exposure event user/session/context/surface binding does not match run %.', NEW.run_id
      USING ERRCODE = '23514';
  END IF;

  IF NEW.served_at < v_reference_time THEN
    RAISE EXCEPTION 'Recommendation exposure event served_at precedes run reference_time for run %.', NEW.run_id
      USING ERRCODE = '23514';
  END IF;

  RETURN NEW;
END;
$$;

CREATE TRIGGER recommendation_exposure_event_validate_binding
BEFORE INSERT ON public.recommendation_exposure_event
FOR EACH ROW EXECUTE FUNCTION public.validate_recommendation_exposure_event_binding();

CREATE TABLE public.recommendation_exposure_candidate (
  exposure_event_id varchar(128) NOT NULL
    REFERENCES public.recommendation_exposure_event(event_id) ON DELETE RESTRICT,
  absolute_rank integer NOT NULL CHECK (absolute_rank > 0),
  page_position integer NOT NULL CHECK (page_position > 0),
  entity_type varchar(20) NOT NULL CHECK (entity_type = 'post'),
  entity_key varchar(160) NOT NULL,
  source_entity_id bigint NOT NULL CHECK (source_entity_id > 0),
  origin varchar(20) NOT NULL CHECK (origin IN ('personalized', 'exploration')),
  score double precision,
  score_is_negative_zero boolean NOT NULL DEFAULT false,
  provenance jsonb NOT NULL DEFAULT '{}'::jsonb,
  created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (exposure_event_id, page_position),
  CONSTRAINT recommendation_exposure_candidate_rank_uq
    UNIQUE (exposure_event_id, absolute_rank),
  CONSTRAINT recommendation_exposure_candidate_entity_uq
    UNIQUE (exposure_event_id, entity_key),
  CONSTRAINT recommendation_exposure_candidate_entity_key_check
    CHECK (entity_key = 'post:' || source_entity_id::text),
  CONSTRAINT recommendation_exposure_candidate_score_zero_check
    CHECK (NOT score_is_negative_zero OR score = 0.0),
  CONSTRAINT recommendation_exposure_candidate_origin_score_check
    CHECK (
      (origin = 'personalized' AND score IS NOT NULL AND score BETWEEN 0.0 AND 1.0)
      OR
      (origin = 'exploration' AND score IS NULL AND score_is_negative_zero = false)
    ),
  CONSTRAINT recommendation_exposure_candidate_provenance_json_check
    CHECK (jsonb_typeof(provenance) = 'object')
);

CREATE INDEX recommendation_exposure_candidate_source_idx
ON public.recommendation_exposure_candidate (source_entity_id, exposure_event_id);
CREATE INDEX recommendation_exposure_candidate_absolute_rank_idx
ON public.recommendation_exposure_candidate (exposure_event_id, absolute_rank);

CREATE OR REPLACE FUNCTION public.validate_recommendation_exposure_candidate_binding()
RETURNS trigger
LANGUAGE plpgsql
SECURITY INVOKER
SET search_path = pg_catalog, public, pg_temp
AS $$
DECLARE
  v_run_id varchar(128);
BEGIN
  SELECT run_id INTO v_run_id
  FROM public.recommendation_exposure_event
  WHERE event_id = NEW.exposure_event_id;

  IF NOT EXISTS (
    SELECT 1
    FROM public.recommendation_run_candidate c
    WHERE c.run_id = v_run_id
      AND c.absolute_rank = NEW.absolute_rank
      AND c.entity_type = NEW.entity_type
      AND c.entity_key = NEW.entity_key
      AND c.source_entity_id = NEW.source_entity_id
      AND c.origin = NEW.origin
      AND c.score IS NOT DISTINCT FROM NEW.score
      AND c.score_is_negative_zero = NEW.score_is_negative_zero
  ) THEN
    RAISE EXCEPTION 'Exposure candidate does not match persisted run candidate for run %, rank %.',
      v_run_id, NEW.absolute_rank
      USING ERRCODE = '23514';
  END IF;

  RETURN NEW;
END;
$$;

CREATE TRIGGER recommendation_exposure_candidate_validate_binding
BEFORE INSERT ON public.recommendation_exposure_candidate
FOR EACH ROW EXECUTE FUNCTION public.validate_recommendation_exposure_candidate_binding();

CREATE OR REPLACE FUNCTION public.assert_recommendation_exposure_event_integrity(p_event_id varchar)
RETURNS void
LANGUAGE plpgsql
SECURITY INVOKER
SET search_path = pg_catalog, public, pg_temp
AS $$
DECLARE
  v_expected_count integer;
  v_start_rank integer;
  v_end_rank integer;
  v_actual_count integer;
  v_min_rank integer;
  v_max_rank integer;
  v_min_position integer;
  v_max_position integer;
BEGIN
  SELECT page_candidate_count, page_start_rank, page_end_rank
    INTO v_expected_count, v_start_rank, v_end_rank
  FROM public.recommendation_exposure_event
  WHERE event_id = p_event_id;

  IF NOT FOUND THEN
    RETURN;
  END IF;

  SELECT count(*), min(absolute_rank), max(absolute_rank), min(page_position), max(page_position)
    INTO v_actual_count, v_min_rank, v_max_rank, v_min_position, v_max_position
  FROM public.recommendation_exposure_candidate
  WHERE exposure_event_id = p_event_id;

  IF v_actual_count <> v_expected_count THEN
    RAISE EXCEPTION 'Exposure event % expected % candidates but has %.',
      p_event_id, v_expected_count, v_actual_count
      USING ERRCODE = '23514';
  END IF;

  IF v_expected_count > 0 AND (
       v_min_rank <> v_start_rank
       OR v_max_rank <> v_end_rank
       OR v_min_position <> 1
       OR v_max_position <> v_expected_count
     ) THEN
    RAISE EXCEPTION 'Exposure event % candidate rank or page-position range is not contiguous.', p_event_id
      USING ERRCODE = '23514';
  END IF;
END;
$$;

CREATE OR REPLACE FUNCTION public.check_recommendation_exposure_integrity_from_event()
RETURNS trigger
LANGUAGE plpgsql
SECURITY INVOKER
SET search_path = pg_catalog, public, pg_temp
AS $$
BEGIN
  PERFORM public.assert_recommendation_exposure_event_integrity(NEW.event_id);
  RETURN NULL;
END;
$$;

CREATE OR REPLACE FUNCTION public.check_recommendation_exposure_integrity_from_candidate()
RETURNS trigger
LANGUAGE plpgsql
SECURITY INVOKER
SET search_path = pg_catalog, public, pg_temp
AS $$
BEGIN
  PERFORM public.assert_recommendation_exposure_event_integrity(
    CASE WHEN TG_OP = 'DELETE' THEN OLD.exposure_event_id ELSE NEW.exposure_event_id END
  );
  RETURN NULL;
END;
$$;

CREATE CONSTRAINT TRIGGER recommendation_exposure_event_candidate_count_check
AFTER INSERT ON public.recommendation_exposure_event
DEFERRABLE INITIALLY DEFERRED
FOR EACH ROW EXECUTE FUNCTION public.check_recommendation_exposure_integrity_from_event();

CREATE CONSTRAINT TRIGGER recommendation_exposure_candidate_count_check
AFTER INSERT OR UPDATE OR DELETE ON public.recommendation_exposure_candidate
DEFERRABLE INITIALLY DEFERRED
FOR EACH ROW EXECUTE FUNCTION public.check_recommendation_exposure_integrity_from_candidate();

CREATE TABLE public.recommendation_behavior_event (
  event_id varchar(128) PRIMARY KEY,
  idempotency_key varchar(160) NOT NULL UNIQUE,
  schema_version varchar(64) NOT NULL,
  payload_fingerprint varchar(64) NOT NULL
    CHECK (payload_fingerprint ~ '^[0-9a-f]{64}$'),
  canonical_payload bytea NOT NULL,
  payload_size_bytes integer NOT NULL CHECK (payload_size_bytes BETWEEN 0 AND 262144),
  user_id bigint REFERENCES public.app_users(id) ON DELETE RESTRICT,
  session_id varchar(128) NOT NULL,
  run_id varchar(128) REFERENCES public.recommendation_run(run_id) ON DELETE RESTRICT,
  event_type varchar(32) NOT NULL
    CHECK (event_type IN (
      'impression', 'view', 'click', 'like', 'unlike', 'save', 'unsave',
      'share', 'follow', 'unfollow', 'hide', 'report', 'search', 'tag_click',
      'crew_join', 'crew_leave'
    )),
  entity_type varchar(20)
    CHECK (entity_type IS NULL OR entity_type IN ('post', 'journey', 'place', 'crew', 'user')),
  entity_key varchar(160),
  source_entity_id bigint,
  occurred_at timestamptz NOT NULL,
  received_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
  metadata jsonb NOT NULL DEFAULT '{}'::jsonb,
  created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT recommendation_behavior_event_id_format_check
    CHECK (event_id ~ '^[A-Za-z0-9][A-Za-z0-9._:-]{0,127}$'),
  CONSTRAINT recommendation_behavior_idempotency_format_check
    CHECK (char_length(btrim(idempotency_key)) BETWEEN 1 AND 160),
  CONSTRAINT recommendation_behavior_payload_size_check
    CHECK (octet_length(canonical_payload) = payload_size_bytes),
  CONSTRAINT recommendation_behavior_payload_hash_check
    CHECK (payload_fingerprint = public.recommendation_sha256_hex(canonical_payload)),
  CONSTRAINT recommendation_behavior_metadata_check
    CHECK (jsonb_typeof(metadata) = 'object'),
  CONSTRAINT recommendation_behavior_entity_partition_check
    CHECK (
      (entity_type IS NULL AND entity_key IS NULL AND source_entity_id IS NULL)
      OR
      (entity_type IS NOT NULL AND entity_key IS NOT NULL AND source_entity_id IS NOT NULL
       AND source_entity_id > 0
       AND entity_key = entity_type || ':' || source_entity_id::text)
    ),
  CONSTRAINT recommendation_behavior_event_entity_requirement_check
    CHECK (
      (event_type = 'search' AND entity_type IS NULL)
      OR
      (event_type <> 'search')
    )
);

CREATE INDEX recommendation_behavior_event_user_occurred_idx
ON public.recommendation_behavior_event (user_id, occurred_at DESC, event_id)
WHERE user_id IS NOT NULL;
CREATE INDEX recommendation_behavior_event_session_occurred_idx
ON public.recommendation_behavior_event (session_id, occurred_at DESC, event_id);
CREATE INDEX recommendation_behavior_event_run_occurred_idx
ON public.recommendation_behavior_event (run_id, occurred_at, event_id)
WHERE run_id IS NOT NULL;
CREATE INDEX recommendation_behavior_event_entity_occurred_idx
ON public.recommendation_behavior_event (entity_type, source_entity_id, occurred_at DESC)
WHERE entity_type IS NOT NULL;
CREATE INDEX recommendation_behavior_event_type_occurred_idx
ON public.recommendation_behavior_event (event_type, occurred_at DESC, event_id);

CREATE OR REPLACE FUNCTION public.validate_recommendation_behavior_event_binding()
RETURNS trigger
LANGUAGE plpgsql
SECURITY INVOKER
SET search_path = pg_catalog, public, pg_temp
AS $$
DECLARE
  v_user_id bigint;
  v_session_id varchar(128);
BEGIN
  IF NEW.run_id IS NULL THEN
    RETURN NEW;
  END IF;

  SELECT user_id, session_id
    INTO v_user_id, v_session_id
  FROM public.recommendation_run
  WHERE run_id = NEW.run_id;

  IF v_user_id IS DISTINCT FROM NEW.user_id
     OR v_session_id IS DISTINCT FROM NEW.session_id THEN
    RAISE EXCEPTION 'Recommendation behavior event user/session binding does not match run %.', NEW.run_id
      USING ERRCODE = '23514';
  END IF;

  RETURN NEW;
END;
$$;

CREATE TRIGGER recommendation_behavior_event_validate_binding
BEFORE INSERT ON public.recommendation_behavior_event
FOR EACH ROW EXECUTE FUNCTION public.validate_recommendation_behavior_event_binding();

CREATE TRIGGER recommendation_snapshot_append_only
BEFORE UPDATE OR DELETE ON public.recommendation_snapshot
FOR EACH ROW EXECUTE FUNCTION public.prevent_recommendation_append_only_mutation();
CREATE TRIGGER recommendation_run_append_only
BEFORE UPDATE OR DELETE ON public.recommendation_run
FOR EACH ROW EXECUTE FUNCTION public.prevent_recommendation_append_only_mutation();
CREATE TRIGGER recommendation_run_candidate_append_only
BEFORE UPDATE OR DELETE ON public.recommendation_run_candidate
FOR EACH ROW EXECUTE FUNCTION public.prevent_recommendation_append_only_mutation();
CREATE TRIGGER recommendation_run_terminal_candidate_append_only
BEFORE UPDATE OR DELETE ON public.recommendation_run_terminal_candidate
FOR EACH ROW EXECUTE FUNCTION public.prevent_recommendation_append_only_mutation();
CREATE TRIGGER recommendation_exposure_event_append_only
BEFORE UPDATE OR DELETE ON public.recommendation_exposure_event
FOR EACH ROW EXECUTE FUNCTION public.prevent_recommendation_append_only_mutation();
CREATE TRIGGER recommendation_exposure_candidate_append_only
BEFORE UPDATE OR DELETE ON public.recommendation_exposure_candidate
FOR EACH ROW EXECUTE FUNCTION public.prevent_recommendation_append_only_mutation();
CREATE TRIGGER recommendation_behavior_event_append_only
BEFORE UPDATE OR DELETE ON public.recommendation_behavior_event
FOR EACH ROW EXECUTE FUNCTION public.prevent_recommendation_append_only_mutation();

COMMIT;
