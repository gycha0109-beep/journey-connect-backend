-- Journey Connect DB v2.7 extension - DP-3 retry and quarantine evidence foundation
-- Target: PostgreSQL 15 and 18
-- Prerequisite: canonical SQL 01..31.
-- Canonical event and idempotency source rows remain immutable.

BEGIN;

DO $$
BEGIN
  IF to_regclass('public.data_platform_event_v1') IS NULL THEN
    RAISE EXCEPTION 'DP-3 prerequisite data_platform_event_v1 is missing.' USING ERRCODE = '42P01';
  END IF;
  IF to_regprocedure('public.prevent_data_event_append_only_mutation_v1()') IS NULL THEN
    RAISE EXCEPTION 'DP-3 prerequisite append-only function is missing.' USING ERRCODE = '42883';
  END IF;
END;
$$;

CREATE OR REPLACE FUNCTION public.data_projection_name_valid_v1(p_value varchar)
RETURNS boolean
LANGUAGE sql
IMMUTABLE
STRICT
SECURITY INVOKER
SET search_path = pg_catalog, pg_temp
AS $$
  SELECT p_value ~ '^[a-z][a-z0-9]*(?:_[a-z0-9]+){0,15}$'
     AND length(p_value) <= 80;
$$;

CREATE OR REPLACE FUNCTION public.data_projection_version_valid_v1(p_value varchar)
RETURNS boolean
LANGUAGE sql
IMMUTABLE
STRICT
SECURITY INVOKER
SET search_path = pg_catalog, pg_temp
AS $$
  SELECT p_value ~ '^[a-z0-9]+(?:-[a-z0-9]+)*-v[1-9][0-9]*$'
     AND length(p_value) <= 96;
$$;

CREATE OR REPLACE FUNCTION public.data_retry_failure_class_valid_v1(p_value varchar)
RETURNS boolean
LANGUAGE sql
IMMUTABLE
STRICT
SECURITY INVOKER
SET search_path = pg_catalog, pg_temp
AS $$
  SELECT p_value IN (
    'database_lock_timeout', 'serialization_failure', 'dependency_unavailable',
    'worker_interrupted', 'rate_limited', 'schema_unsupported',
    'source_hash_mismatch', 'source_binding_invalid', 'payload_unmappable',
    'payload_too_large', 'privacy_policy_violation', 'projection_invariant_failed',
    'lineage_source_missing', 'manual_hold', 'unclassified_failure'
  );
$$;

CREATE OR REPLACE FUNCTION public.data_retryable_failure_class_v1(p_value varchar)
RETURNS boolean
LANGUAGE sql
IMMUTABLE
STRICT
SECURITY INVOKER
SET search_path = pg_catalog, pg_temp
AS $$
  SELECT p_value IN (
    'database_lock_timeout', 'serialization_failure', 'dependency_unavailable',
    'worker_interrupted', 'rate_limited'
  );
$$;

CREATE OR REPLACE FUNCTION public.data_quarantine_reason_valid_v1(p_value varchar)
RETURNS boolean
LANGUAGE sql
IMMUTABLE
STRICT
SECURITY INVOKER
SET search_path = pg_catalog, pg_temp
AS $$
  SELECT p_value IN (
    'schema_unsupported', 'source_hash_mismatch', 'source_binding_invalid',
    'payload_unmappable', 'payload_too_large', 'privacy_policy_violation',
    'projection_invariant_failed', 'retry_exhausted',
    'repeated_deterministic_failure', 'lineage_source_missing',
    'manual_hold', 'unclassified_failure'
  );
$$;

CREATE TABLE public.data_projection_work_state_v1 (
  work_id uuid PRIMARY KEY DEFAULT public.gen_random_uuid(),
  work_ref varchar(160) NOT NULL UNIQUE,
  source_event_ref varchar(160) NOT NULL
    REFERENCES public.data_platform_event_v1(event_id) ON DELETE RESTRICT,
  event_family varchar(40) NOT NULL,
  projection_name varchar(80) NOT NULL,
  projection_version varchar(96) NOT NULL,
  retry_policy_version varchar(96) NOT NULL DEFAULT 'data-projection-retry-v1',
  state varchar(32) NOT NULL DEFAULT 'retry_scheduled'
    CHECK (state IN ('retry_scheduled', 'retry_claimed', 'retry_succeeded', 'quarantined')),
  attempt_number smallint NOT NULL DEFAULT 1 CHECK (attempt_number BETWEEN 1 AND 6),
  ready_at timestamptz NOT NULL,
  claim_token uuid,
  worker_ref varchar(160),
  claimed_at timestamptz,
  lease_expires_at timestamptz,
  terminal_outcome varchar(32),
  terminal_quarantine_reason varchar(64),
  terminal_at timestamptz,
  retention_class varchar(40) NOT NULL DEFAULT 'retry_control_90d'
    CHECK (retention_class = 'retry_control_90d'),
  retention_policy_version varchar(96) NOT NULL DEFAULT 'data-retention-policy-v1'
    CHECK (retention_policy_version = 'data-retention-policy-v1'),
  expires_at timestamptz NOT NULL DEFAULT (CURRENT_TIMESTAMP + interval '90 days'),
  created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT data_projection_work_ref_check
    CHECK (work_ref ~ '^work:[A-Za-z0-9][A-Za-z0-9._~-]{0,127}$'),
  CONSTRAINT data_projection_work_event_family_check
    CHECK (event_family IN (
      'user_behavior', 'content_lifecycle', 'ai_analysis', 'search_runtime',
      'recommendation_runtime', 'experiment_runtime', 'admin_audit',
      'trip_planner_runtime', 'data_quality'
    )),
  CONSTRAINT data_projection_work_name_check
    CHECK (public.data_projection_name_valid_v1(projection_name)),
  CONSTRAINT data_projection_work_version_check
    CHECK (public.data_projection_version_valid_v1(projection_version)),
  CONSTRAINT data_projection_work_policy_check
    CHECK (retry_policy_version = 'data-projection-retry-v1'),
  CONSTRAINT data_projection_work_claim_shape_check
    CHECK (
      (state = 'retry_claimed'
       AND claim_token IS NOT NULL AND worker_ref IS NOT NULL
       AND claimed_at IS NOT NULL AND lease_expires_at IS NOT NULL
       AND lease_expires_at = claimed_at + interval '60 seconds')
      OR
      (state <> 'retry_claimed'
       AND claim_token IS NULL AND worker_ref IS NULL
       AND claimed_at IS NULL AND lease_expires_at IS NULL)
    ),
  CONSTRAINT data_projection_work_worker_check
    CHECK (worker_ref IS NULL OR worker_ref ~ '^worker:[A-Za-z0-9][A-Za-z0-9._~-]{0,127}$'),
  CONSTRAINT data_projection_work_terminal_check
    CHECK (
      (state = 'retry_succeeded' AND terminal_outcome = 'retry_succeeded'
       AND terminal_quarantine_reason IS NULL AND terminal_at IS NOT NULL)
      OR
      (state = 'quarantined' AND terminal_outcome IN ('retry_exhausted', 'quarantined')
       AND public.data_quarantine_reason_valid_v1(terminal_quarantine_reason)
       AND terminal_at IS NOT NULL)
      OR
      (state IN ('retry_scheduled', 'retry_claimed')
       AND terminal_outcome IS NULL AND terminal_quarantine_reason IS NULL AND terminal_at IS NULL)
    ),
  CONSTRAINT data_projection_work_retention_check
    CHECK (expires_at >= created_at + interval '90 days')
);

CREATE UNIQUE INDEX data_projection_work_identity_uq
  ON public.data_projection_work_state_v1 (source_event_ref, projection_name, projection_version);
CREATE INDEX data_projection_work_ready_idx
  ON public.data_projection_work_state_v1 (ready_at, work_id)
  WHERE state = 'retry_scheduled';
CREATE INDEX data_projection_work_lease_idx
  ON public.data_projection_work_state_v1 (lease_expires_at, work_id)
  WHERE state = 'retry_claimed';

CREATE TABLE public.data_projection_retry_schedule_v1 (
  retry_schedule_id uuid PRIMARY KEY DEFAULT public.gen_random_uuid(),
  work_id uuid NOT NULL REFERENCES public.data_projection_work_state_v1(work_id) ON DELETE RESTRICT,
  source_event_ref varchar(160) NOT NULL REFERENCES public.data_platform_event_v1(event_id) ON DELETE RESTRICT,
  projection_name varchar(80) NOT NULL,
  projection_version varchar(96) NOT NULL,
  retry_policy_version varchar(96) NOT NULL,
  attempt_number smallint NOT NULL CHECK (attempt_number BETWEEN 1 AND 6),
  scheduled_at timestamptz NOT NULL,
  ready_at timestamptz NOT NULL,
  failure_class varchar(64),
  failure_code varchar(64),
  failure_signature varchar(64),
  scheduling_jitter_basis_points smallint NOT NULL DEFAULT 0
    CHECK (scheduling_jitter_basis_points BETWEEN 0 AND 1000),
  retention_class varchar(40) NOT NULL DEFAULT 'retry_schedule_90d'
    CHECK (retention_class = 'retry_schedule_90d'),
  retention_policy_version varchar(96) NOT NULL DEFAULT 'data-retention-policy-v1'
    CHECK (retention_policy_version = 'data-retention-policy-v1'),
  expires_at timestamptz NOT NULL DEFAULT (CURRENT_TIMESTAMP + interval '90 days'),
  created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT data_projection_retry_schedule_name_check
    CHECK (public.data_projection_name_valid_v1(projection_name)),
  CONSTRAINT data_projection_retry_schedule_version_check
    CHECK (public.data_projection_version_valid_v1(projection_version)),
  CONSTRAINT data_projection_retry_schedule_policy_check
    CHECK (retry_policy_version = 'data-projection-retry-v1'),
  CONSTRAINT data_projection_retry_schedule_time_check CHECK (ready_at >= scheduled_at),
  CONSTRAINT data_projection_retry_schedule_failure_check CHECK (
    (attempt_number = 1 AND failure_class IS NULL AND failure_code IS NULL AND failure_signature IS NULL)
    OR
    (attempt_number > 1
      AND public.data_retry_failure_class_valid_v1(failure_class)
      AND failure_code ~ '^[A-Z][A-Z0-9_]{0,63}$'
      AND failure_signature ~ '^[0-9a-f]{64}$')
  ),
  CONSTRAINT data_projection_retry_schedule_retention_check
    CHECK (expires_at >= created_at + interval '90 days'),
  UNIQUE (work_id, attempt_number)
);

CREATE TABLE public.data_projection_claim_evidence_v1 (
  claim_evidence_id uuid PRIMARY KEY DEFAULT public.gen_random_uuid(),
  work_id uuid NOT NULL REFERENCES public.data_projection_work_state_v1(work_id) ON DELETE RESTRICT,
  source_event_ref varchar(160) NOT NULL REFERENCES public.data_platform_event_v1(event_id) ON DELETE RESTRICT,
  projection_name varchar(80) NOT NULL,
  projection_version varchar(96) NOT NULL,
  attempt_number smallint NOT NULL CHECK (attempt_number BETWEEN 1 AND 6),
  worker_ref varchar(160) NOT NULL,
  claim_token uuid NOT NULL UNIQUE,
  reclaimed_from_claim_token uuid,
  claimed_at timestamptz NOT NULL,
  lease_expires_at timestamptz NOT NULL,
  retention_class varchar(40) NOT NULL DEFAULT 'processing_evidence_90d'
    CHECK (retention_class = 'processing_evidence_90d'),
  retention_policy_version varchar(96) NOT NULL DEFAULT 'data-retention-policy-v1'
    CHECK (retention_policy_version = 'data-retention-policy-v1'),
  expires_at timestamptz NOT NULL DEFAULT (CURRENT_TIMESTAMP + interval '90 days'),
  created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT data_projection_claim_worker_check
    CHECK (worker_ref ~ '^worker:[A-Za-z0-9][A-Za-z0-9._~-]{0,127}$'),
  CONSTRAINT data_projection_claim_lease_check
    CHECK (lease_expires_at = claimed_at + interval '60 seconds'),
  CONSTRAINT data_projection_claim_retention_check
    CHECK (expires_at >= created_at + interval '90 days')
);

CREATE TABLE public.data_projection_heartbeat_evidence_v1 (
  heartbeat_evidence_id uuid PRIMARY KEY DEFAULT public.gen_random_uuid(),
  work_id uuid NOT NULL REFERENCES public.data_projection_work_state_v1(work_id) ON DELETE RESTRICT,
  claim_token uuid NOT NULL,
  worker_ref varchar(160) NOT NULL,
  heartbeat_at timestamptz NOT NULL,
  previous_lease_expires_at timestamptz NOT NULL,
  renewed_lease_expires_at timestamptz NOT NULL,
  retention_class varchar(40) NOT NULL DEFAULT 'processing_evidence_90d'
    CHECK (retention_class = 'processing_evidence_90d'),
  retention_policy_version varchar(96) NOT NULL DEFAULT 'data-retention-policy-v1'
    CHECK (retention_policy_version = 'data-retention-policy-v1'),
  expires_at timestamptz NOT NULL DEFAULT (CURRENT_TIMESTAMP + interval '90 days'),
  created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT data_projection_heartbeat_worker_check
    CHECK (worker_ref ~ '^worker:[A-Za-z0-9][A-Za-z0-9._~-]{0,127}$'),
  CONSTRAINT data_projection_heartbeat_lease_check
    CHECK (renewed_lease_expires_at = heartbeat_at + interval '60 seconds'
       AND renewed_lease_expires_at > previous_lease_expires_at),
  CONSTRAINT data_projection_heartbeat_retention_check
    CHECK (expires_at >= created_at + interval '90 days')
);

CREATE TABLE public.data_projection_processing_attempt_v1 (
  processing_attempt_id uuid PRIMARY KEY DEFAULT public.gen_random_uuid(),
  work_id uuid NOT NULL REFERENCES public.data_projection_work_state_v1(work_id) ON DELETE RESTRICT,
  retry_schedule_ref uuid NOT NULL REFERENCES public.data_projection_retry_schedule_v1(retry_schedule_id) ON DELETE RESTRICT,
  source_event_ref varchar(160) NOT NULL REFERENCES public.data_platform_event_v1(event_id) ON DELETE RESTRICT,
  projection_name varchar(80) NOT NULL,
  projection_version varchar(96) NOT NULL,
  attempt_number smallint NOT NULL CHECK (attempt_number BETWEEN 1 AND 6),
  worker_ref varchar(160) NOT NULL,
  claim_token uuid NOT NULL,
  claimed_at timestamptz NOT NULL,
  lease_expires_at timestamptz NOT NULL,
  started_at timestamptz NOT NULL,
  completed_at timestamptz NOT NULL,
  outcome varchar(32) NOT NULL CHECK (outcome IN (
    'retry_succeeded', 'retry_scheduled', 'retry_exhausted', 'quarantined'
  )),
  failure_class varchar(64),
  failure_code varchar(64),
  failure_signature varchar(64),
  processor_build_id varchar(44) NOT NULL CHECK (processor_build_id ~ '^git:[0-9a-f]{40}$'),
  retention_class varchar(40) NOT NULL DEFAULT 'processing_evidence_90d'
    CHECK (retention_class = 'processing_evidence_90d'),
  retention_policy_version varchar(96) NOT NULL DEFAULT 'data-retention-policy-v1'
    CHECK (retention_policy_version = 'data-retention-policy-v1'),
  expires_at timestamptz NOT NULL DEFAULT (CURRENT_TIMESTAMP + interval '90 days'),
  created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT data_projection_attempt_worker_check
    CHECK (worker_ref ~ '^worker:[A-Za-z0-9][A-Za-z0-9._~-]{0,127}$'),
  CONSTRAINT data_projection_attempt_time_check
    CHECK (started_at >= claimed_at AND completed_at >= started_at),
  CONSTRAINT data_projection_attempt_failure_check CHECK (
    (outcome = 'retry_succeeded'
      AND failure_class IS NULL AND failure_code IS NULL AND failure_signature IS NULL)
    OR
    (outcome <> 'retry_succeeded'
      AND public.data_retry_failure_class_valid_v1(failure_class)
      AND failure_code ~ '^[A-Z][A-Z0-9_]{0,63}$'
      AND failure_signature ~ '^[0-9a-f]{64}$')
  ),
  CONSTRAINT data_projection_attempt_retention_check
    CHECK (expires_at >= created_at + interval '90 days'),
  UNIQUE (work_id, attempt_number)
);

CREATE TABLE public.data_projection_quarantine_evidence_v1 (
  quarantine_evidence_id uuid PRIMARY KEY DEFAULT public.gen_random_uuid(),
  work_id uuid NOT NULL REFERENCES public.data_projection_work_state_v1(work_id) ON DELETE RESTRICT,
  source_event_ref varchar(160) NOT NULL REFERENCES public.data_platform_event_v1(event_id) ON DELETE RESTRICT,
  projection_name varchar(80) NOT NULL,
  projection_version varchar(96) NOT NULL,
  reason varchar(64) NOT NULL,
  failure_signature varchar(64) NOT NULL CHECK (failure_signature ~ '^[0-9a-f]{64}$'),
  triggering_attempt_ref uuid REFERENCES public.data_projection_processing_attempt_v1(processing_attempt_id) ON DELETE RESTRICT,
  operation_ref varchar(160) NOT NULL,
  actor_ref varchar(160) NOT NULL,
  retention_class varchar(40) NOT NULL DEFAULT 'quarantine_evidence_90d'
    CHECK (retention_class = 'quarantine_evidence_90d'),
  retention_policy_version varchar(96) NOT NULL DEFAULT 'data-retention-policy-v1'
    CHECK (retention_policy_version = 'data-retention-policy-v1'),
  expires_at timestamptz NOT NULL DEFAULT (CURRENT_TIMESTAMP + interval '90 days'),
  created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT data_projection_quarantine_reason_check
    CHECK (public.data_quarantine_reason_valid_v1(reason)),
  CONSTRAINT data_projection_quarantine_operation_check
    CHECK (operation_ref ~ '^operation:[A-Za-z0-9][A-Za-z0-9._~-]{0,127}$'),
  CONSTRAINT data_projection_quarantine_actor_check
    CHECK (actor_ref ~ '^(worker|subject):[A-Za-z0-9][A-Za-z0-9._~-]{0,127}$'),
  CONSTRAINT data_projection_quarantine_retention_check
    CHECK (expires_at >= created_at + interval '90 days'),
  UNIQUE (work_id)
);

CREATE TABLE public.data_projection_quarantine_review_evidence_v1 (
  review_evidence_id uuid PRIMARY KEY DEFAULT public.gen_random_uuid(),
  quarantine_evidence_ref uuid NOT NULL
    REFERENCES public.data_projection_quarantine_evidence_v1(quarantine_evidence_id) ON DELETE RESTRICT,
  review_action varchar(40) NOT NULL CHECK (review_action IN (
    'reviewed_retain', 'reviewed_release_requested'
  )),
  reviewer_ref varchar(160) NOT NULL,
  operation_ref varchar(160) NOT NULL,
  policy_version varchar(96) NOT NULL,
  mapping_version varchar(96),
  schema_version varchar(96),
  consumer_version varchar(96),
  dry_run_ref varchar(160),
  integrity_checks_passed boolean NOT NULL,
  retention_class varchar(40) NOT NULL DEFAULT 'quarantine_review_90d'
    CHECK (retention_class = 'quarantine_review_90d'),
  retention_policy_version varchar(96) NOT NULL DEFAULT 'data-retention-policy-v1'
    CHECK (retention_policy_version = 'data-retention-policy-v1'),
  expires_at timestamptz NOT NULL DEFAULT (CURRENT_TIMESTAMP + interval '90 days'),
  created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT data_projection_review_reviewer_check
    CHECK (reviewer_ref ~ '^subject:[A-Za-z0-9][A-Za-z0-9._~-]{0,127}$'),
  CONSTRAINT data_projection_review_operation_check
    CHECK (operation_ref ~ '^operation:[A-Za-z0-9][A-Za-z0-9._~-]{0,127}$'),
  CONSTRAINT data_projection_review_release_shape_check CHECK (
    review_action = 'reviewed_retain'
    OR (
      mapping_version IS NOT NULL
      AND schema_version IS NOT NULL
      AND consumer_version IS NOT NULL
      AND dry_run_ref ~ '^dryrun:[A-Za-z0-9][A-Za-z0-9._~-]{0,127}$'
      AND integrity_checks_passed
    )
  ),
  CONSTRAINT data_projection_review_retention_check
    CHECK (expires_at >= created_at + interval '90 days')
);

CREATE TABLE public.data_projection_claim_rejection_evidence_v1 (
  rejection_evidence_id uuid PRIMARY KEY DEFAULT public.gen_random_uuid(),
  work_id uuid NOT NULL REFERENCES public.data_projection_work_state_v1(work_id) ON DELETE RESTRICT,
  attempted_action varchar(24) NOT NULL CHECK (attempted_action IN ('heartbeat', 'complete', 'fail')),
  supplied_claim_token uuid,
  supplied_worker_ref varchar(160),
  rejection_code varchar(64) NOT NULL CHECK (rejection_code = 'EVENT_CLAIM_STALE'),
  rejected_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
  retention_class varchar(40) NOT NULL DEFAULT 'processing_evidence_90d'
    CHECK (retention_class = 'processing_evidence_90d'),
  retention_policy_version varchar(96) NOT NULL DEFAULT 'data-retention-policy-v1'
    CHECK (retention_policy_version = 'data-retention-policy-v1'),
  expires_at timestamptz NOT NULL DEFAULT (CURRENT_TIMESTAMP + interval '90 days'),
  created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT data_projection_rejection_worker_check
    CHECK (supplied_worker_ref IS NULL OR supplied_worker_ref ~ '^worker:[A-Za-z0-9][A-Za-z0-9._~-]{0,127}$'),
  CONSTRAINT data_projection_rejection_retention_check
    CHECK (expires_at >= created_at + interval '90 days')
);

CREATE INDEX data_projection_retry_schedule_ready_idx
  ON public.data_projection_retry_schedule_v1 (ready_at, retry_schedule_id);
CREATE INDEX data_projection_claim_work_idx
  ON public.data_projection_claim_evidence_v1 (work_id, claimed_at DESC);
CREATE INDEX data_projection_attempt_work_idx
  ON public.data_projection_processing_attempt_v1 (work_id, attempt_number DESC);
CREATE INDEX data_projection_quarantine_reason_idx
  ON public.data_projection_quarantine_evidence_v1 (reason, created_at DESC);
CREATE INDEX data_projection_review_quarantine_idx
  ON public.data_projection_quarantine_review_evidence_v1 (quarantine_evidence_ref, created_at DESC);
CREATE INDEX data_projection_rejection_time_idx
  ON public.data_projection_claim_rejection_evidence_v1 (rejected_at DESC);

CREATE TRIGGER data_projection_retry_schedule_append_only_v1
BEFORE UPDATE OR DELETE ON public.data_projection_retry_schedule_v1
FOR EACH ROW EXECUTE FUNCTION public.prevent_data_event_append_only_mutation_v1();
CREATE TRIGGER data_projection_claim_evidence_append_only_v1
BEFORE UPDATE OR DELETE ON public.data_projection_claim_evidence_v1
FOR EACH ROW EXECUTE FUNCTION public.prevent_data_event_append_only_mutation_v1();
CREATE TRIGGER data_projection_heartbeat_evidence_append_only_v1
BEFORE UPDATE OR DELETE ON public.data_projection_heartbeat_evidence_v1
FOR EACH ROW EXECUTE FUNCTION public.prevent_data_event_append_only_mutation_v1();
CREATE TRIGGER data_projection_processing_attempt_append_only_v1
BEFORE UPDATE OR DELETE ON public.data_projection_processing_attempt_v1
FOR EACH ROW EXECUTE FUNCTION public.prevent_data_event_append_only_mutation_v1();
CREATE TRIGGER data_projection_quarantine_evidence_append_only_v1
BEFORE UPDATE OR DELETE ON public.data_projection_quarantine_evidence_v1
FOR EACH ROW EXECUTE FUNCTION public.prevent_data_event_append_only_mutation_v1();
CREATE TRIGGER data_projection_quarantine_review_append_only_v1
BEFORE UPDATE OR DELETE ON public.data_projection_quarantine_review_evidence_v1
FOR EACH ROW EXECUTE FUNCTION public.prevent_data_event_append_only_mutation_v1();
CREATE TRIGGER data_projection_claim_rejection_append_only_v1
BEFORE UPDATE OR DELETE ON public.data_projection_claim_rejection_evidence_v1
FOR EACH ROW EXECUTE FUNCTION public.prevent_data_event_append_only_mutation_v1();

REVOKE ALL ON TABLE
  public.data_projection_work_state_v1,
  public.data_projection_retry_schedule_v1,
  public.data_projection_claim_evidence_v1,
  public.data_projection_heartbeat_evidence_v1,
  public.data_projection_processing_attempt_v1,
  public.data_projection_quarantine_evidence_v1,
  public.data_projection_quarantine_review_evidence_v1,
  public.data_projection_claim_rejection_evidence_v1
FROM PUBLIC;
REVOKE EXECUTE ON FUNCTION public.data_projection_name_valid_v1(varchar) FROM PUBLIC;
REVOKE EXECUTE ON FUNCTION public.data_projection_version_valid_v1(varchar) FROM PUBLIC;
REVOKE EXECUTE ON FUNCTION public.data_retry_failure_class_valid_v1(varchar) FROM PUBLIC;
REVOKE EXECUTE ON FUNCTION public.data_retryable_failure_class_v1(varchar) FROM PUBLIC;
REVOKE EXECUTE ON FUNCTION public.data_quarantine_reason_valid_v1(varchar) FROM PUBLIC;

COMMIT;
