# SC RCA-2 Observability Metric and Logging Policy

## Scope
Define runtime telemetry, cardinality, retention and redaction.

## Current Baseline
Authoritative work-start `3efbf96ebf25ae1645a62f35269c4b569425a9ca`; PR #27 merged; RCA-1B exact-final-head `dbb6b5397ad0fe675856b195e280faf9a0f3030c`; raw result and identity retention remain NONE.

## Decision
Required metrics: `shadow_request_count`, `shadow_execution_count`, `shadow_success_count`, `shadow_timeout_count`, `shadow_exception_count`, `shadow_circuit_open_count`, `shadow_queue_rejected_count`, `shadow_late_result_discard_count`, `shadow_latency_ms`, `primary_latency_ms`, `p1_result_mismatch_count`, `p2_result_mismatch_count`, `checkpoint_mismatch_count`, `lineage_mismatch_count`, `stale_candidate_count`, `identity_blocked_count`, `redaction_failure_count`.

## Rationale
Runtime entry is unsafe without measurable isolation and automatic violation detection.

## Authority
Operations owns dashboards/alerts; Reliability owns evidence integrity; lane owners own mismatch semantics; Privacy/Security owns redaction/retention.

## Dependencies
Existing observability infrastructure; no persisted product-data evidence.

## Runtime Environment
Metrics are enabled only in isolated non-production.

## Runtime Model
Task lifecycle emits bounded counters/timers without exposing task payload.

## Feature Flag
Record flag version and evaluated state class, not raw configuration.

## Traffic Boundary
Dashboard includes configured and observed traffic stage.

## Primary/Shadow Authority
Record normalized primary/candidate digest only; never raw result.

## Timeout/Fallback
Timeout, rejection, cancellation, late discard and breaker state are distinct.

## Credential/Network
Never log credential, connection string, endpoint token or secret-manager path.

## Identity/Privacy
Never use raw user/subject/session/run/exposure identifiers as metric labels or logs.

## P1 Result Boundary
P1 mismatches exclude expected/protected gap classes.

## P2 Result Boundary
P2 authority mismatch is distinct from migration-required gaps.

## Checkpoint/Lineage
Store hashed checkpoint/reference, compatibility class and lineage digest only.

## Observability
Metric labels limited to environment, lane, bounded result/error class, flag version and deployment version. Metrics retention `30d`; redacted logs `14d`; exact-head review artifacts `90d`. Critical failures sampled `100%`; successful detail at most `10%`. These are operational metrics, not P2 product metrics or production SLOs.

## Rollback
Redaction failure or response mutation triggers immediate global kill and incident evidence.

## DB/SQL Impact
None; `PERSISTED_EVIDENCE_REQUIRED=NO`.

## Production Impact
No production telemetry approval.

## Verification
Implementation must test metric inventory, label cardinality and redaction. SC-5 runtime telemetry is `NOT_EXECUTED`.

## Risks
Unbounded labels or accidental payload logging would invalidate entry.

## Exit Criteria
All required metrics active, dashboards/alerts reviewed, raw-data scans clean and retention enforced.

## Handoff
Implement the exact inventory and fail CI on prohibited labels or content.