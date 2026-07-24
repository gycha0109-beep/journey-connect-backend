SELECT
  checkpoint_ref,
  source_event_count AS checkpoint_sequence,
  to_char(ingested_at_upper_bound AT TIME ZONE 'UTC', 'YYYY-MM-DD"T"HH24:MI:SS.US"Z"') AS captured_at,
  source_set_fingerprint,
  checkpoint_definition_fingerprint
FROM public.data_source_checkpoint_v1
WHERE checkpoint_ref = ?
ORDER BY checkpoint_ref
LIMIT ?
