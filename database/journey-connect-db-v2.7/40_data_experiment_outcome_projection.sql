-- Journey Connect DB v2.7 extension - DP-5 P2 experiment outcome shadow projection
-- Target: PostgreSQL 15 and 18. Prerequisite: SQL 01..39.
BEGIN;

CREATE TABLE public.data_experiment_outcome_input_projection_v1 (
  outcome_projection_id uuid PRIMARY KEY DEFAULT public.gen_random_uuid(),
  snapshot_ref uuid NOT NULL REFERENCES public.data_projection_snapshot_v1(snapshot_id) ON DELETE RESTRICT,
  projection_record_ref varchar(160) NOT NULL,
  experiment_ref varchar(160) NOT NULL,
  experiment_version varchar(96) NOT NULL,
  variant_ref varchar(16) NOT NULL CHECK (variant_ref IN ('baseline','treatment')),
  exposure_ref varchar(128) NOT NULL REFERENCES public.recommendation_p2_experiment_exposure(exposure_id) ON DELETE RESTRICT,
  run_ref varchar(128) NOT NULL REFERENCES public.recommendation_run(run_id) ON DELETE RESTRICT,
  source_user_ref varchar(160) NOT NULL,
  subject_ref varchar(160) NOT NULL,
  session_ref varchar(160) NOT NULL,
  exposed_at timestamptz NOT NULL,
  outcome_window_seconds bigint NOT NULL CHECK (outcome_window_seconds = 604800),
  clicked boolean NOT NULL,
  liked boolean NOT NULL,
  saved boolean NOT NULL,
  shared boolean NOT NULL,
  fallback_observed boolean NOT NULL,
  outcome_event_refs jsonb NOT NULL CHECK (jsonb_typeof(outcome_event_refs) = 'array'),
  source_checkpoint_ref uuid NOT NULL REFERENCES public.data_source_checkpoint_v1(checkpoint_id) ON DELETE RESTRICT,
  source_event_count bigint NOT NULL CHECK (source_event_count > 0),
  source_lineage_fingerprint varchar(64) NOT NULL CHECK (public.data_projection_fingerprint_valid_v1(source_lineage_fingerprint)),
  projection_record_fingerprint varchar(64) NOT NULL CHECK (public.data_projection_fingerprint_valid_v1(projection_record_fingerprint)),
  retention_class varchar(40) NOT NULL DEFAULT 'projection_evidence_90d' CHECK (retention_class = 'projection_evidence_90d'),
  retention_policy_version varchar(96) NOT NULL DEFAULT 'data-retention-policy-v1' CHECK (retention_policy_version = 'data-retention-policy-v1'),
  expires_at timestamptz NOT NULL DEFAULT (CURRENT_TIMESTAMP + interval '90 days'),
  created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT data_outcome_projection_record_ref_check CHECK (projection_record_ref ~ '^outcome_record:[A-Za-z0-9][A-Za-z0-9._:~-]{0,143}$'),
  CONSTRAINT data_outcome_projection_experiment_check CHECK (experiment_ref ~ '^experiment:[A-Za-z0-9][A-Za-z0-9._:~-]{0,143}$'),
  CONSTRAINT data_outcome_projection_user_check CHECK (source_user_ref ~ '^user:[1-9][0-9]*$'),
  CONSTRAINT data_outcome_projection_subject_check CHECK (subject_ref ~ '^subject:[A-Za-z0-9][A-Za-z0-9._~-]{0,127}$'),
  CONSTRAINT data_outcome_projection_session_check CHECK (session_ref ~ '^session:[A-Za-z0-9][A-Za-z0-9._~-]{0,127}$'),
  CONSTRAINT data_outcome_projection_retention_check CHECK (expires_at >= created_at + interval '90 days'),
  UNIQUE (snapshot_ref, exposure_ref),
  UNIQUE (snapshot_ref, projection_record_ref)
);

CREATE INDEX data_outcome_projection_experiment_idx ON public.data_experiment_outcome_input_projection_v1(experiment_ref, experiment_version, variant_ref, exposed_at DESC);
CREATE INDEX data_outcome_projection_exposure_idx ON public.data_experiment_outcome_input_projection_v1(exposure_ref, created_at DESC);
CREATE TRIGGER data_outcome_projection_append_only BEFORE UPDATE OR DELETE ON public.data_experiment_outcome_input_projection_v1 FOR EACH ROW EXECUTE FUNCTION public.prevent_data_event_append_only_mutation_v1();

COMMIT;
