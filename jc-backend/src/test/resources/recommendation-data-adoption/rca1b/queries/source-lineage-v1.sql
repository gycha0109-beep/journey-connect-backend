SELECT
  s.snapshot_ref,
  l.projection_record_ref,
  l.source_kind,
  l.lineage_entry_fingerprint
FROM public.data_projection_snapshot_v1 s
JOIN public.data_projection_lineage_v1 l
  ON l.snapshot_ref = s.snapshot_id
WHERE s.snapshot_ref = ?
ORDER BY l.projection_record_ref, l.source_event_ref, l.lineage_entry_fingerprint
LIMIT ?
