# DP-6 Quality Matrix

## Status

`IMPLEMENTATION_READY / SC ALLOCATION PENDING`

The matrix defines the required invariant, evidence and verdict effect. It does not claim that any DP-6 runtime validation has executed.

| Validation scope | Source object | Expected invariant | Observed value | Failure code | Severity | Quality metric | Threshold | Verdict impact | Evidence requirement |
|---|---|---|---|---|---|---|---|---|---|
| SOURCE_COMPLETENESS | DP-5 checkpoint | stored source count equals distinct checkpoint members | recomputed member count | `source_count_mismatch` | BLOCKER | `source_completeness_rate` | 100% | REJECTED | checkpoint definition + member identities |
| SOURCE_COMPLETENESS | canonical event / mapped evidence / P2 exposure | every checkpoint source exists in its authority | existence count | `source_event_missing` | BLOCKER | `source_completeness_rate` | 100% | REJECTED | authority ref + protected fingerprint only |
| SOURCE_COMPLETENESS | checkpoint members | stored source fingerprint equals authority fingerprint | match count | `source_set_fingerprint_mismatch` | BLOCKER | `fingerprint_match_rate` | 100% | REJECTED | recomputed source-set fingerprint |
| SOURCE_COMPLETENESS | checkpoint range | every source is inside `[from,to)` | out-of-range count | `source_event_out_of_range` | BLOCKER | `source_completeness_rate` | 100% | REJECTED | event time and bounded range |
| SOURCE_COMPLETENESS | checkpoint ingestion bound | member ingestion is at/before upper bound | late member count | `source_range_mismatch` | BLOCKER | `source_completeness_rate` | 100% | REJECTED | authoritative ingestion time |
| SOURCE_COMPLETENESS | source contract | stream/contract/schema matches checkpoint definition | version comparison | `source_schema_mismatch` | BLOCKER | `fingerprint_match_rate` | 100% | REJECTED | version IDs |
| SOURCE_COMPLETENESS | late authoritative event | event time in range, ingestion after upper bound | lateness duration | none when observed separately | WARNING or BLOCKER by policy | `late_arrival_rate` | policy tolerance | warning, INCONCLUSIVE or REJECTED | append-only late-arrival observation |
| PROJECTION_COMPLETENESS | checkpoint eligible sources | each eligible source is represented by a policy-valid record/lineage | uncovered source count | `projection_record_missing` | BLOCKER | `projection_coverage_rate` | 100% | REJECTED | eligibility decision + lineage binding |
| PROJECTION_COMPLETENESS | projection records | no record lacks an eligible source basis | unexpected record count | `projection_record_unexpected` | BLOCKER | `projection_coverage_rate` | 100% | REJECTED | record fingerprint + lineage summary |
| PROJECTION_COMPLETENESS | projection record | record source count equals distinct lineage source count | count difference | `projection_source_count_mismatch` | BLOCKER | `projection_coverage_rate` | 100% | REJECTED | record/lineage counts |
| PROJECTION_COMPLETENESS | profile projection | each record contains only its 7/30/90-day window sources | violating source count | `projection_window_violation` | BLOCKER | `projection_coverage_rate` | 100% | REJECTED | as-of + occurred-at boundaries |
| PROJECTION_COMPLETENESS | outcome projection | outcome lies in `[exposed_at,+7d)` and before as-of | violating outcome count | `projection_window_violation` | BLOCKER | `exposure_binding_valid_rate` | 100% | REJECTED | exposure/outcome times |
| PROJECTION_COMPLETENESS | duplicate source/evidence | one logical source contributes once | excess contribution count | `projection_duplicate_aggregation` | BLOCKER | `duplicate_source_rate` | 0 excess aggregations | REJECTED | logical source identity set |
| PROJECTION_COMPLETENESS | adapter evidence | rejected/conflicted/unsupported evidence is absent | invalid included count | `invalid_adapter_evidence_included` | BLOCKER | `conflict_rate` | 0 | REJECTED | adapter state + evidence ref |
| SNAPSHOT_CONSISTENCY | snapshot manifest | record count equals persisted projection record count | count difference | `snapshot_record_count_mismatch` | BLOCKER | `snapshot_record_reconciliation_rate` | 100% | REJECTED | record set fingerprint |
| SNAPSHOT_CONSISTENCY | snapshot manifest | subject count equals distinct projection subjects | count difference | `snapshot_subject_count_mismatch` | BLOCKER | `snapshot_subject_reconciliation_rate` | 100% | REJECTED | privacy-safe distinct-count evidence |
| SNAPSHOT_CONSISTENCY | snapshot manifest | source count equals distinct lineage source count | count difference | `snapshot_source_count_mismatch` | BLOCKER | `source_completeness_rate` | 100% | REJECTED | lineage source summary |
| SNAPSHOT_CONSISTENCY | snapshot content | recomputed content fingerprint equals stored fingerprint | fingerprint comparison | `snapshot_content_fingerprint_mismatch` | BLOCKER | `fingerprint_match_rate` | 100% | REJECTED | canonical record fingerprint list |
| SNAPSHOT_CONSISTENCY | snapshot lineage | recomputed lineage fingerprint equals stored fingerprint | fingerprint comparison | `snapshot_lineage_fingerprint_mismatch` | BLOCKER | `fingerprint_match_rate` | 100% | REJECTED | canonical lineage fingerprint list |
| SNAPSHOT_CONSISTENCY | snapshot contract | name/schema/policy/checkpoint match run and records | binding comparison | `snapshot_contract_mismatch` | BLOCKER | `fingerprint_match_rate` | 100% | REJECTED | version and checkpoint refs |
| LINEAGE_INTEGRITY | projection record | at least one lineage entry exists | missing record count | `lineage_missing` | BLOCKER | `lineage_completeness_rate` | 100% | REJECTED | record-to-lineage index |
| LINEAGE_INTEGRITY | lineage entry | referenced projection record exists | orphan count | `lineage_orphan` | BLOCKER | `lineage_orphan_rate` | 0% | REJECTED | lineage record ref |
| LINEAGE_INTEGRITY | lineage identity | no duplicate record/source/fingerprint/evidence binding | duplicate count | `lineage_duplicate` | BLOCKER | `duplicate_lineage_rate` | 0% | REJECTED | stable lineage identity |
| LINEAGE_INTEGRITY | lineage source | source exists in checkpoint and authority | missing source count | `lineage_source_missing` | BLOCKER | `lineage_completeness_rate` | 100% | REJECTED | checkpoint/source lookup evidence |
| LINEAGE_INTEGRITY | lineage fingerprint | source fingerprint matches checkpoint and authority | mismatch count | `lineage_source_fingerprint_mismatch` | BLOCKER | `fingerprint_match_rate` | 100% | REJECTED | protected fingerprints |
| LINEAGE_INTEGRITY | adapter lineage | mapped evidence reference exists and is approved | missing/invalid count | `lineage_adapter_evidence_missing` | BLOCKER | `lineage_completeness_rate` | 100% | REJECTED | adapter output state/ref |
| LINEAGE_INTEGRITY | checkpoint binding | lineage checkpoint equals record/run/snapshot checkpoint | mismatch count | `lineage_checkpoint_mismatch` | BLOCKER | `lineage_completeness_rate` | 100% | REJECTED | checkpoint refs |
| LINEAGE_INTEGRITY | mapping policy | lineage mapping policy matches approved adapter output | mismatch count | `lineage_policy_version_mismatch` | BLOCKER | `fingerprint_match_rate` | 100% | REJECTED | policy version IDs |
| IDENTITY_INTEGRITY | projection identity | required explicit binding exists | missing count | `identity_binding_missing` | BLOCKER | `identity_binding_valid_rate` | 100% | REJECTED | version/source/fingerprint/scope |
| IDENTITY_INTEGRITY | binding evidence | version, fingerprint and scope are valid | invalid count | `identity_binding_invalid` | BLOCKER | `identity_binding_valid_rate` | 100% | REJECTED | bounded binding evidence |
| IDENTITY_INTEGRITY | identity namespace | `subject:*` and `user:*` are not inferred or merged | conflict count | `identity_namespace_conflict` | BLOCKER | `identity_binding_valid_rate` | 100% | REJECTED | namespace comparison only |
| EXPOSURE_INTEGRITY | outcome snapshot | protected P2 exposure exists | missing count | `exposure_binding_missing` | BLOCKER | `exposure_binding_valid_rate` | 100% | REJECTED | P2 exposure ref |
| EXPOSURE_INTEGRITY | assignment/run/exposure | experiment/version/run/session/time binding is exact | invalid count | `exposure_binding_invalid` | BLOCKER | `exposure_binding_valid_rate` | 100% | REJECTED | protected binding fields |
| EXPOSURE_INTEGRITY | outcome subject | outcome identity equals approved exposure identity/binding | mismatch count | `exposure_subject_mismatch` | BLOCKER | `exposure_binding_valid_rate` | 100% | REJECTED | privacy-safe identity refs |
| EXPOSURE_INTEGRITY | variant | projection variant equals exposure variant | mismatch count | `exposure_variant_mismatch` | BLOCKER | `exposure_binding_valid_rate` | 100% | REJECTED | variant refs |
| EXPOSURE_INTEGRITY | exposure time | projection exposure time equals authority | mismatch count | `exposure_time_mismatch` | BLOCKER | `exposure_binding_valid_rate` | 100% | REJECTED | UTC instants |
| EXPOSURE_INTEGRITY | exposure authority | general exposure/impression is not accepted as P2 | substitution count | `general_exposure_used_as_p2` | BLOCKER | `exposure_binding_valid_rate` | 100% | REJECTED | authority class |
| DETERMINISTIC_REBUILD | rebuilt records | expected and stored record counts match | difference | `rebuild_record_mismatch` | BLOCKER | `rebuild_match_rate` | 100% | REJECTED | canonical rebuilt record set |
| DETERMINISTIC_REBUILD | rebuilt subjects | expected and stored subject counts match | difference | `rebuild_subject_count_mismatch` | BLOCKER | `rebuild_match_rate` | 100% | REJECTED | distinct count summary |
| DETERMINISTIC_REBUILD | record fingerprints | stable ordered fingerprint sets match | mismatch set | `rebuild_projection_fingerprint_mismatch` | BLOCKER | `rebuild_match_rate` | 100% | REJECTED | sorted fingerprint sets |
| DETERMINISTIC_REBUILD | snapshot fingerprint | rebuilt and stored snapshot fingerprints match | comparison | `rebuild_snapshot_fingerprint_mismatch` | BLOCKER | `rebuild_match_rate` | 100% | REJECTED | semantic fingerprint inputs |
| DETERMINISTIC_REBUILD | lineage fingerprint | rebuilt and stored lineage fingerprints match | comparison | `rebuild_lineage_fingerprint_mismatch` | BLOCKER | `rebuild_match_rate` | 100% | REJECTED | semantic lineage inputs |
| DETERMINISTIC_REBUILD | repeated execution | insertion/map order, locale, timezone and build ID do not change output | repeated comparison | `non_deterministic_output` | BLOCKER | `rebuild_match_rate` | 100% | REJECTED | repeated fixture fingerprints |
| FULL | all required checks | no required check is unexecuted | skipped required count | stable skip reason required | ERROR | none | 0 skipped required | INCONCLUSIVE | check execution inventory |
| FULL | versioned thresholds | every required metric meets `data-quality-policy-v1` | failed threshold count | `quality_threshold_failed` | BLOCKER or ERROR by policy | all required metrics | policy-defined | REJECTED or INCONCLUSIVE | metric/threshold evidence |

## Stable ordering

- source and lineage identities: stable reference ASC, fingerprint ASC, evidence reference ASC;
- record fingerprints: lexical ASC;
- ranked profile references retain DP-5 order: count DESC, last occurred DESC, stable reference ASC;
- outcome references retain DP-5 order: occurred at ASC, reference ASC, source fingerprint ASC.

No physical row order, locale collation, map iteration or random tie-break is permitted.

## Zero denominator

A metric with denominator zero records one explicit state: `NOT_APPLICABLE`, `UNDEFINED` or `POLICY_DEFINED_ZERO_CASE`. It never records an invented perfect score.