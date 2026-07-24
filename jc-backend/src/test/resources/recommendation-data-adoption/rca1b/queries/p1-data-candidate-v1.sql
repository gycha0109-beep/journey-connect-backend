SELECT
  m.case_id,
  p.activity_window_days AS window_days,
  p.interaction_counts ->> 'segment' AS exact_value,
  p.interaction_counts ->> 'total' AS derived_value,
  CASE WHEN p.engagement_signals ? 'optional'
       THEN 'VALUE:' || (p.engagement_signals ->> 'optional')
       ELSE 'NULL' END AS null_semantics,
  CASE WHEN jsonb_array_length(p.recent_tag_refs) = 0 THEN ''
       ELSE p.recent_tag_refs::text END AS empty_semantics,
  to_char((p.engagement_signals ->> 'acceptedBehaviorWeight')::numeric, 'FM999999990.000000') AS numeric_value,
  to_char(p.projection_as_of AT TIME ZONE 'UTC', 'YYYY-MM-DD"T"HH24:MI:SS.US"Z"') AS timestamp_value,
  c.checkpoint_ref,
  c.source_event_count AS checkpoint_sequence,
  to_char(c.ingested_at_upper_bound AT TIME ZONE 'UTC', 'YYYY-MM-DD"T"HH24:MI:SS.US"Z"') AS checkpoint_at,
  to_char(s.snapshot_as_of AT TIME ZONE 'UTC', 'YYYY-MM-DD"T"HH24:MI:SS.US"Z"') AS snapshot_at,
  p.source_lineage_fingerprint AS lineage_fingerprint
FROM rca1b_fixture.p1_case_map m
JOIN public.data_recommendation_profile_input_projection_v1 p
  ON p.snapshot_ref = m.snapshot_ref
 AND p.projection_subject_ref = m.subject_ref
JOIN public.data_source_checkpoint_v1 c
  ON c.checkpoint_id = p.source_checkpoint_ref
JOIN public.data_projection_snapshot_v1 s
  ON s.snapshot_id = p.snapshot_ref
WHERE m.case_id = ?
ORDER BY p.activity_window_days, p.projection_record_ref
LIMIT ?
