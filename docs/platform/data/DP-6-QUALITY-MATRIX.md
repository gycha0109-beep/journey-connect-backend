# DP-6 Quality Matrix

## Status

`IMPLEMENTED / EXACT-HEAD CI PENDING`

| Validation scope | Source object | Expected invariant | Observed value | Failure code | Severity | Quality metric | Threshold | Verdict impact | Evidence |
|---|---|---|---|---|---|---|---|---|---|
| SOURCE_COMPLETENESS | DP-5 checkpoint + authoritative sources | exact distinct count | recomputed count | source_count_mismatch | BLOCKER | source_completeness_rate | 100% | REJECTED | check + metric |
| SOURCE_COMPLETENESS | checkpoint member set | exact canonical fingerprint | recomputed SHA-256 | source_set_fingerprint_mismatch | BLOCKER | fingerprint_match_rate | 100% | REJECTED | check |
| SOURCE_COMPLETENESS | source timestamps/range | authority and half-open range match | source observation | source_timestamp_mismatch / source_range_mismatch | BLOCKER | source_completeness_rate | 100% | REJECTED | check/anomaly |
| PROJECTION_COMPLETENESS | profile/outcome records | all eligible records exactly once | rebuilt records | projection_record_missing / projection_record_unexpected | BLOCKER | projection_coverage_rate | 100% | REJECTED | check + rebuild |
| PROJECTION_COMPLETENESS | profile windows/outcome window | 7/30/90 and P2 seven-day bounds exact | rebuilt aggregates | projection_window_violation / outcome_window_violation | BLOCKER | projection_coverage_rate | 100% | REJECTED | check |
| PROJECTION_COMPLETENESS | adapter evidence | rejected/conflicted/unsupported absent | checkpoint-member states | invalid_adapter_evidence_included | BLOCKER | conflict_rate | 0 | REJECTED | check/anomaly |
| SNAPSHOT_CONSISTENCY | snapshot manifest | record/subject/source counts exact | recomputed counts | snapshot_record_count_mismatch / snapshot_subject_count_mismatch / snapshot_source_count_mismatch | BLOCKER | snapshot reconciliation rates | 100% | REJECTED | check + metric |
| SNAPSHOT_CONSISTENCY | snapshot fingerprints | content and lineage fingerprints exact | recomputed SHA-256 | snapshot_content_fingerprint_mismatch / snapshot_lineage_fingerprint_mismatch | BLOCKER | fingerprint_match_rate | 100% | REJECTED | check |
| LINEAGE_INTEGRITY | projection lineage | every record covered, no orphan/duplicate | recomputed sets | lineage_missing / lineage_orphan / lineage_duplicate | BLOCKER | lineage completeness/orphan/duplicate rates | 100% / 0% / 0% | REJECTED | check + metric |
| LINEAGE_INTEGRITY | source and policy binding | source/fingerprint/checkpoint/policy exact | authority comparison | lineage_source_fingerprint_mismatch / lineage_checkpoint_mismatch | BLOCKER | lineage_completeness_rate | 100% | REJECTED | check |
| IDENTITY_INTEGRITY | explicit binding evidence | valid version/source/fingerprint/scope, no inferred merge | binding comparison | identity_binding_invalid / identity_namespace_conflict | BLOCKER | identity_binding_valid_rate | 100% when required | REJECTED | check + metric |
| EXPOSURE_INTEGRITY | recommendation_p2_experiment_exposure | exact experiment/variant/subject/time authority | protected exposure comparison | exposure_binding_invalid / general_exposure_used_as_p2 | BLOCKER | exposure_binding_valid_rate | 100% for outcome | REJECTED | check + metric |
| DETERMINISTIC_REBUILD | DP-5 engines and immutable inputs | exact counts/order/fingerprints/aggregates | rebuilt result | rebuild_projection_fingerprint_mismatch / non_deterministic_output | BLOCKER | rebuild_match_rate | 100% | REJECTED | rebuild evidence |
| FULL | required checks and metrics | all required evidence executed | completeness set | quality_required_check_skipped / quality_evidence_incomplete | ERROR | required metric set | complete | INCONCLUSIVE | verdict evidence |
| FULL | verdict persistence | same logical identity is atomic | persisted identity/fingerprint | quality_verdict_conflict | BLOCKER | conflict_rate | 0 | CONFLICT, no new VALIDATED | conflict evidence |
| SOURCE_COMPLETENESS | late source | no mutation; append observation only | lateness and policy class | quality_threshold_failed when blocking | policy dependent | late_arrival_rate | policy threshold | warning or REJECTED | late-arrival evidence |

Zero denominators are explicitly `NOT_APPLICABLE`, `UNDEFINED` or `POLICY_DEFINED_ZERO_CASE`; none becomes implicit 100%.
