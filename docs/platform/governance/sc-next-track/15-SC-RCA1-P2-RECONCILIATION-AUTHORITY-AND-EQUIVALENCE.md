# SC RCA-1 P2 Reconciliation Authority and Equivalence Decision

## Scope

Define P2 lane comparison for authoritative main `f802a105e46a62718616acaa7a3db6c172e7ed10` while protecting current exposure, dataset, metrics, hash and release evidence.

## Current Baseline

Authority is `RecommendationP2ObservationSource`, `recommendation_p2_experiment_exposure`, `recommendation-evaluation-dataset-v1`, `engagement_rate` and `fallback_rate`.

## Decision

P2 entry is `CONDITIONALLY_APPROVED`.

| Dimension | Rule | Verdict expectation |
|---|---|---|
| `EXPOSURE_REFERENCE_PARITY` | exact authoritative P2 exposure only | exact required |
| `ASSIGNMENT_PARITY` | experiment/version/variant exact | exact required |
| `SUBJECT_SESSION_RUN_PARITY` | synthetic subject, session, run and exposure binding exact | exact required |
| `OUTCOME_WINDOW_PARITY` | exactly `604800` seconds after exposure | exact required |
| `ENGAGEMENT_EVENT_PARITY` | click/like/save/share only | exact required |
| `FALLBACK_BINDING_PARITY` | fallback from bound Recommendation run only | exact required |
| `STALE_UNEXPOSED_ASSIGNMENT_GAP` | authoritative source exclusion must be detected | migration required |
| `OBSERVATION_DEDUPE_GAP` | key `(experimentRef, experimentVersion, subjectRef)` plus exposure/run/session consistency | detection required; persistence equivalence migration required |
| `CANONICAL_DATASET_HASH_PROTECTED` | do not recalculate or rewrite | protected |
| `RELEASE_EVIDENCE_PROTECTED` | not a comparison payload; unchanged only | protected |
| `IDENTITY_BLOCKED` | invalid synthetic binding | fail closed |

## Rationale

Exposure, attribution and fallback are Reliability metric semantics and can be exact in deterministic cases. Persisted dedupe, canonical bytes/hash and release evidence are current-authority artifacts and cannot be silently reproduced by a candidate projection.

## Authority

Reliability is accountable for P2 semantics, mismatch acceptance and result integrity. Intelligence may implement the pure Java comparator. SC controls breaking changes.

## Dependencies

Recorded authoritative P2 cases, Data outcome candidates, synthetic identity and explicit exposure/reference times.

## Allowed Changes

Comparison logic, duplicate detection in fixture inventory, authority-mismatch classification and redacted evidence.

## Forbidden Changes

General exposure or behavior impression substitution; inclusion of `view`, `hide` or `report`; non-604800 windows; unbound fallback; P2 row/hash/release rewrite.

## Identity/Privacy

Synthetic-only. One observation key is evaluated only within the synthetic fixture set and does not create a mapping authority.

## DB/SQL Impact

None.

## Production Impact

None.

## Verification

Any general exposure, impression, `view`, `hide` or `report` in the metric set yields `EXPOSURE_AUTHORITY_MISMATCH` or `PROTECTED_AUTHORITY_DIFFERENCE`. Window mismatch yields `OUTCOME_WINDOW_MISMATCH`; unbound fallback yields `FALLBACK_BINDING_MISMATCH`.

Canonical dataset bytes/hash are protected only and are not recalculated. Release evidence is a forbidden-change target, not a comparison result.

## Risks

Fixture dedupe detection is not proof of persisted dataset equivalence. A matching candidate row does not transfer exposure or dataset authority.

## Exit Criteria

`P2_RECONCILIATION_EXECUTED`, `P2_DIFFERENCES_CLASSIFIED`, all exact dimensions pass, migration dimensions classified, no protected modification.

Required marker:

```text
P2_SHADOW_RECONCILIATION_ONLY
CURRENT_P2_AUTHORITY_UNCHANGED
NO_AUTHORITY_TRANSFER
```

## Handoff

Reliability approval is mandatory before P2 lane completion.
