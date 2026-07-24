\set ON_ERROR_STOP on

DROP SCHEMA IF EXISTS rca1b_fixture CASCADE;
CREATE SCHEMA rca1b_fixture;

CREATE TABLE rca1b_fixture.scenario_registry (
  lane varchar(2) NOT NULL CHECK (lane IN ('P1','P2')),
  scenario varchar(128) NOT NULL,
  role varchar(32) NOT NULL CHECK (role IN ('BASELINE','EXPECTED_NEGATIVE','EXPECTED_GAP')),
  dimension varchar(96) NOT NULL,
  expected_classification varchar(96) NOT NULL,
  PRIMARY KEY (lane, scenario)
);

CREATE TABLE rca1b_fixture.p1_case_map (
  case_id varchar(128) PRIMARY KEY,
  profile_snapshot_id varchar(128) NOT NULL,
  snapshot_ref uuid NOT NULL,
  subject_ref varchar(160) NOT NULL,
  checkpoint_ref varchar(160) NOT NULL,
  checkpoint_sequence bigint NOT NULL,
  checkpoint_at timestamptz NOT NULL,
  snapshot_at timestamptz NOT NULL,
  lineage_fingerprint varchar(64) NOT NULL
);

CREATE TABLE rca1b_fixture.p2_case_map (
  case_id varchar(128) PRIMARY KEY,
  assignment_id varchar(128) NOT NULL,
  exposure_id varchar(128) NOT NULL,
  snapshot_ref uuid NOT NULL,
  synthetic_subject_ref varchar(160) NOT NULL,
  checkpoint_ref varchar(160) NOT NULL,
  checkpoint_sequence bigint NOT NULL,
  checkpoint_at timestamptz NOT NULL,
  snapshot_at timestamptz NOT NULL,
  lineage_fingerprint varchar(64) NOT NULL
);

CREATE TABLE rca1b_fixture.seed_assertion (
  assertion_id varchar(128) PRIMARY KEY,
  status varchar(32) NOT NULL,
  sqlstate_class varchar(5) NOT NULL
);

CREATE TABLE rca1b_fixture.row_limit_probe (
  ordinal integer PRIMARY KEY CHECK (ordinal > 0)
);

DROP ROLE IF EXISTS rca1b_readonly;
CREATE ROLE rca1b_readonly
  LOGIN PASSWORD :'role_password'
  NOINHERIT NOSUPERUSER NOCREATEDB NOCREATEROLE NOREPLICATION NOBYPASSRLS;

ALTER ROLE rca1b_readonly SET default_transaction_read_only TO on;
ALTER ROLE rca1b_readonly SET default_transaction_isolation TO 'repeatable read';
ALTER ROLE rca1b_readonly SET statement_timeout TO '5s';
ALTER ROLE rca1b_readonly SET lock_timeout TO '1s';
ALTER ROLE rca1b_readonly SET idle_in_transaction_session_timeout TO '5s';
ALTER ROLE rca1b_readonly SET TimeZone TO 'UTC';
ALTER ROLE rca1b_readonly SET max_parallel_workers_per_gather TO 0;

REVOKE ALL ON DATABASE :"db_name" FROM PUBLIC;
GRANT CONNECT ON DATABASE :"db_name" TO rca1b_readonly;
REVOKE TEMPORARY ON DATABASE :"db_name" FROM rca1b_readonly;

REVOKE ALL ON SCHEMA public, rca1b_fixture FROM PUBLIC;
REVOKE ALL ON ALL TABLES IN SCHEMA public, rca1b_fixture FROM PUBLIC;
REVOKE ALL ON ALL SEQUENCES IN SCHEMA public, rca1b_fixture FROM PUBLIC;
REVOKE EXECUTE ON ALL FUNCTIONS IN SCHEMA public FROM PUBLIC;

GRANT USAGE ON SCHEMA public, rca1b_fixture TO rca1b_readonly;
GRANT SELECT ON
  public.recommendation_p1_profile_snapshot,
  public.recommendation_user_preference,
  public.tags,
  public.data_recommendation_profile_input_projection_v1,
  public.recommendation_p2_experiment_assignment,
  public.recommendation_p2_experiment_exposure,
  public.recommendation_run,
  public.recommendation_behavior_event,
  public.data_experiment_outcome_input_projection_v1,
  public.data_source_checkpoint_v1,
  public.data_projection_snapshot_v1,
  public.data_projection_lineage_v1,
  rca1b_fixture.scenario_registry,
  rca1b_fixture.p1_case_map,
  rca1b_fixture.p2_case_map,
  rca1b_fixture.seed_assertion,
  rca1b_fixture.row_limit_probe
TO rca1b_readonly;
