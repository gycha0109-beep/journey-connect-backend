-- Journey Connect DB v2.7 extension - DP-5 Recommendation profile shadow projection
-- Target: PostgreSQL 15 and 18. Prerequisite: SQL 01..38.
BEGIN;

CREATE TABLE public.data_recommendation_profile_input_projection_v1 (
  profile_projection_id uuid PRIMARY KEY DEFAULT public.gen_random_uuid(),
  snapshot_ref uuid NOT NULL REFERENCES public.data_projection_snapshot_v1(snapshot_id) ON DELETE RESTRICT,
  projection_record_ref varchar(160) NOT NULL,
  projection_subject_ref varchar(160) NOT NULL,
  projection_as_of timestamptz NOT NULL,
  source_checkpoint_ref uuid NOT NULL REFERENCES public.data_source_checkpoint_v1(checkpoint_id) ON DELETE RESTRICT,
  profile_schema_version varchar(96) NOT NULL CHECK (profile_schema_version = 'recommendation-profile-input-v1'),
  projection_policy_version varchar(96) NOT NULL CHECK (projection_policy_version = 'recommendation-profile-projection-policy-v1'),
  activity_window_days integer NOT NULL CHECK (activity_window_days IN (7,30,90)),
  interaction_counts jsonb NOT NULL CHECK (jsonb_typeof(interaction_counts) = 'object'),
  recent_regions jsonb NOT NULL CHECK (jsonb_typeof(recent_regions) = 'array'),
  recent_content_refs jsonb NOT NULL CHECK (jsonb_typeof(recent_content_refs) = 'array'),
  recent_tag_refs jsonb NOT NULL CHECK (jsonb_typeof(recent_tag_refs) = 'array'),
  engagement_signals jsonb NOT NULL CHECK (jsonb_typeof(engagement_signals) = 'object'),
  negative_signals jsonb NOT NULL CHECK (jsonb_typeof(negative_signals) = 'object'),
  source_event_count bigint NOT NULL CHECK (source_event_count > 0),
  source_lineage_fingerprint varchar(64) NOT NULL CHECK (public.data_projection_fingerprint_valid_v1(source_lineage_fingerprint)),
  projection_record_fingerprint varchar(64) NOT NULL CHECK (public.data_projection_fingerprint_valid_v1(projection_record_fingerprint)),
  retention_class varchar(40) NOT NULL DEFAULT 'projection_evidence_90d' CHECK (retention_class = 'projection_evidence_90d'),
  retention_policy_version varchar(96) NOT NULL DEFAULT 'data-retention-policy-v1' CHECK (retention_policy_version = 'data-retention-policy-v1'),
  expires_at timestamptz NOT NULL DEFAULT (CURRENT_TIMESTAMP + interval '90 days'),
  created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT data_profile_projection_record_ref_check CHECK (projection_record_ref ~ '^profile_record:[A-Za-z0-9][A-Za-z0-9._:~-]{0,143}$'),
  CONSTRAINT data_profile_projection_subject_check CHECK (projection_subject_ref ~ '^subject:[A-Za-z0-9][A-Za-z0-9._~-]{0,127}$'),
  CONSTRAINT data_profile_projection_retention_check CHECK (expires_at >= created_at + interval '90 days'),
  UNIQUE (snapshot_ref, projection_subject_ref, activity_window_days),
  UNIQUE (snapshot_ref, projection_record_ref)
);

CREATE INDEX data_profile_projection_subject_idx ON public.data_recommendation_profile_input_projection_v1(projection_subject_ref, projection_as_of DESC);
CREATE INDEX data_profile_projection_window_idx ON public.data_recommendation_profile_input_projection_v1(activity_window_days, projection_as_of DESC);
CREATE TRIGGER data_profile_projection_append_only BEFORE UPDATE OR DELETE ON public.data_recommendation_profile_input_projection_v1 FOR EACH ROW EXECUTE FUNCTION public.prevent_data_event_append_only_mutation_v1();

COMMIT;
