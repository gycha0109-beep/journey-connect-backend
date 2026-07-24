# SC RCA-1 P1 Reconciliation Authority and Equivalence Decision

## Scope

Define P1 lane comparison for authoritative main `f802a105e46a62718616acaa7a3db6c172e7ed10` without replacing `RecommendationP1ProfileSource` or `recommendation_p1_profile_snapshot`.

## Current Baseline

The Data aggregate omits event ordering, individual timestamps, explicit preferences, exact `BehaviorProfileEvent` partition behavior, feature transform, decay/saturation inputs and current fingerprint semantics.

## Decision

P1 entry is `CONDITIONALLY_APPROVED`.

| Dimension | Comparison rule | Acceptance |
|---|---|---|
| `EXACT_FIELD_PARITY` | equality of shared normalized contract/version/reference/checkpoint fields | zero mismatch |
| `DERIVED_VALUE_PARITY` | deterministic derivation from shared aggregate fields | zero mismatch |
| `AGGREGATE_WINDOW_PARITY` | 7/30/90-day window and count normalization | zero mismatch |
| `ORDERING_NOT_COMPARABLE` | candidate has no event order | expected gap |
| `EVENT_GRAIN_MISSING` | aggregate cannot reconstruct event partitions | expected gap |
| `EXPLICIT_PREFERENCE_MISSING` | no explicit preference source | expected gap |
| `TRANSFORM_POLICY_MISSING` | vocabulary/decay/saturation not candidate authority | expected/protected gap |
| `FINGERPRINT_SEMANTICS_PROTECTED` | current snapshot fingerprint not reimplemented | protected difference |
| `IDENTITY_BLOCKED` | synthetic binding absent/invalid/mismatched | fail closed |

## Rationale

Object equality would conflate shared aggregate facts with Intelligence-owned event/profile semantics. Exact comparison is limited to fields with the same contract meaning. Derived comparison is permitted only when the derivation is deterministic and explicitly versioned.

## Authority

Intelligence is accountable for P1 semantics and acceptance. Data is consulted for candidate/checkpoint/lineage meaning. SC controls phase exit.

## Dependencies

Recorded authoritative P1 reference cases, Data P1 candidate cases, synthetic identity, explicit window/reference time and deterministic normalization.

## Allowed Changes

Comparison DTOs, deterministic derivation, lane evidence and expected-gap classification.

## Forbidden Changes

Aggregate-to-event fabrication, source query replacement, profile snapshot rewrite, feature-policy redefinition and percentage thresholds that hide categorical gaps.

## Identity/Privacy

Synthetic fixture identity only. Identity failure yields `IDENTITY_MAPPING_REQUIRED` or `IDENTITY_SCHEME_MISMATCH`, never fallback.

## DB/SQL Impact

None.

## Production Impact

None.

## Verification

P1 implementation defect includes any mismatch in exact/derived dimensions, unsupported versions accepted, expected-gap omission, identity fail-open or forbidden event reconstruction.

Expected drift is limited to predeclared non-comparable dimensions. Authority-transfer-required differences are event-grain reconstruction, explicit preference source adoption, transform ownership and fingerprint replacement.

## Risks

A broad numeric match rate can incorrectly claim equivalence. Categorical gaps must remain visible.

## Exit Criteria

`P1_RECONCILIATION_EXECUTED` and `P1_DIFFERENCES_CLASSIFIED`, zero unexpected exact/derived mismatch, expected gap set complete, authority unchanged.

P1 PASS means offline lane reconciliation only, not source equivalence or cutover readiness.

## Handoff

The implementation prompt must create a separate P1 report and must not merge P1 and P2 into one PASS.
