-- Journey Connect DB v2.4 - Persisted recommendation replay audit
-- Target: PostgreSQL 15+
-- Prerequisite: 01-18 canonical SQL

BEGIN;

CREATE TABLE public.recommendation_replay_audit (
  audit_id varchar(128) PRIMARY KEY,
  run_id varchar(128) NOT NULL
    REFERENCES public.recommendation_run(run_id) ON DELETE RESTRICT,
  evaluator_version varchar(64) NOT NULL,
  evaluator_build_id varchar(128) NOT NULL,
  replay_status varchar(32) NOT NULL
    CHECK (replay_status IN ('exact_match', 'mismatch', 'invalid_snapshot', 'invalid_binding')),
  mismatch_categories jsonb NOT NULL DEFAULT '[]'::jsonb,
  ranking_input_hash varchar(64) NOT NULL
    CHECK (ranking_input_hash ~ '^[0-9a-f]{64}$'),
  result_snapshot_hash varchar(64) NOT NULL
    CHECK (result_snapshot_hash ~ '^[0-9a-f]{64}$'),
  expected_result_fingerprint varchar(64),
  actual_result_fingerprint varchar(64),
  ranked_candidate_count integer NOT NULL CHECK (ranked_candidate_count >= 0),
  terminal_candidate_count integer NOT NULL CHECK (terminal_candidate_count >= 0),
  duration_ms bigint NOT NULL CHECK (duration_ms >= 0),
  created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT recommendation_replay_audit_id_format_check
    CHECK (audit_id ~ '^[A-Za-z0-9][A-Za-z0-9._:-]{0,127}$'),
  CONSTRAINT recommendation_replay_audit_mismatch_categories_check
    CHECK (jsonb_typeof(mismatch_categories) = 'array'),
  CONSTRAINT recommendation_replay_audit_fingerprint_format_check
    CHECK (
      (expected_result_fingerprint IS NULL OR expected_result_fingerprint ~ '^[0-9a-f]{64}$')
      AND (actual_result_fingerprint IS NULL OR actual_result_fingerprint ~ '^[0-9a-f]{64}$')
    ),
  CONSTRAINT recommendation_replay_audit_exact_partition_check
    CHECK (
      (replay_status = 'exact_match'
       AND mismatch_categories = '[]'::jsonb
       AND expected_result_fingerprint IS NOT NULL
       AND actual_result_fingerprint = expected_result_fingerprint)
      OR
      (replay_status <> 'exact_match'
       AND jsonb_array_length(mismatch_categories) > 0)
    ),
  CONSTRAINT recommendation_replay_audit_evaluator_uq
    UNIQUE (run_id, evaluator_version, evaluator_build_id)
);

CREATE INDEX recommendation_replay_audit_run_created_idx
ON public.recommendation_replay_audit (run_id, created_at DESC);

CREATE INDEX recommendation_replay_audit_status_created_idx
ON public.recommendation_replay_audit (replay_status, created_at DESC);

CREATE TRIGGER recommendation_replay_audit_append_only
BEFORE UPDATE OR DELETE ON public.recommendation_replay_audit
FOR EACH ROW EXECUTE FUNCTION public.prevent_recommendation_append_only_mutation();

REVOKE ALL ON public.recommendation_replay_audit
FROM PUBLIC, jc_app, jc_auth, jc_admin, jc_security_owner, jc_recommendation;

GRANT SELECT, INSERT ON public.recommendation_replay_audit TO jc_recommendation;
GRANT SELECT ON public.recommendation_replay_audit TO jc_admin;

REVOKE INSERT, UPDATE, DELETE, TRUNCATE ON public.recommendation_replay_audit
FROM jc_app, jc_auth, jc_admin;
REVOKE UPDATE, DELETE, TRUNCATE ON public.recommendation_replay_audit
FROM jc_recommendation;

COMMIT;
