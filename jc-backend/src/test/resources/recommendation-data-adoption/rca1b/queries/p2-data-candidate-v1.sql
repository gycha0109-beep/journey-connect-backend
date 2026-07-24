SELECT
  m.case_id,
  'recommendation_p2_experiment_exposure' AS exposure_authority,
  p.experiment_ref,
  p.experiment_version,
  p.variant_ref,
  p.subject_ref,
  p.session_ref,
  p.run_ref,
  p.exposure_ref,
  p.outcome_window_seconds,
  p.clicked,
  p.liked,
  p.saved,
  p.shared,
  p.fallback_observed,
  c.checkpoint_ref,
  c.source_event_count AS checkpoint_sequence,
  to_char(c.ingested_at_upper_bound AT TIME ZONE 'UTC', 'YYYY-MM-DD"T"HH24:MI:SS.US"Z"') AS checkpoint_at,
  to_char(s.snapshot_as_of AT TIME ZONE 'UTC', 'YYYY-MM-DD"T"HH24:MI:SS.US"Z"') AS snapshot_at,
  p.source_lineage_fingerprint AS lineage_fingerprint,
  1::bigint AS exposure_row_count,
  jsonb_array_length(p.outcome_event_refs)::bigint AS outcome_row_count
FROM rca1b_fixture.p2_case_map m
JOIN public.data_experiment_outcome_input_projection_v1 p
  ON p.snapshot_ref = m.snapshot_ref
 AND p.exposure_ref = m.exposure_id
JOIN public.data_source_checkpoint_v1 c
  ON c.checkpoint_id = p.source_checkpoint_ref
JOIN public.data_projection_snapshot_v1 s
  ON s.snapshot_id = p.snapshot_ref
WHERE m.case_id = ?
ORDER BY p.experiment_ref, p.experiment_version, p.exposure_ref
LIMIT ?
