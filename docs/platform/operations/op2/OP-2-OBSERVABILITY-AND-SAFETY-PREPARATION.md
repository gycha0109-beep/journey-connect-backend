# OP-2 RCA-2 Stage 1 Observability and Safety Preparation

## Status

`RCA2_STAGE1_OBSERVABILITY_AND_SAFETY_APPLICATION_BOUNDARY_COMPLETE`

- work start: `f17fc3e515264eefcf2ca2b113a0e5875bbde6ae`
- OP-1 exact head: `6c89e78e32f54a1f830d0c84db07a01de951e39c`
- OP-1 merge commit: `f17fc3e515264eefcf2ca2b113a0e5875bbde6ae`
- feature flag default: `OFF`
- configured/effective non-production traffic: `0% / 0%`
- production traffic: `0%`
- automatic rollout: `FORBIDDEN`
- primary authority: `CURRENT_P1_P2_ONLY`
- shadow authority/serving: `NONE / FORBIDDEN`

This document is the authoritative repository narrative for the required OP-2 entry verification, OP-1 continuity verification, observability scope, metric continuity and implementation result, dashboard architecture/readiness, alert inventories/routing, kill-switch and circuit-breaker continuity, rollback levels and drill results, credential/network revoke drills, approval package, manual enablement runbook, OP-3 gate, evidence retention, blockers, risks, incident escalation, OP-3 handoff, and OP-3 execution prompt.

## Metric continuity and implementation

The existing `Rca2Metrics.REQUIRED` array remains the authoritative 27-metric inventory. Names, types, protected-gap classifications, and zero-tolerance semantics are unchanged. OP-2 adds exactly seven backlog metrics: `traffic_selected_count`, `traffic_skipped_count`, `executor_active_count`, `executor_queue_depth`, `shadow_task_age_ms`, `shadow_cancelled_count`, and `checkpoint_lag_ms`.

All metrics use only `environment`, `lane`, `result_class`, and `breaker_state`. Raw identity, token, full endpoint URL, checkpoint identity, and unbounded exception text are forbidden. Counter, gauge, timer, type-confusion, nonnegative-value, and redaction behavior are covered by `Rca2Op2MetricsTest`.

## Dashboard contract

Application dashboard contract is ready for these sections: Traffic selection; Executor active count; Executor queue depth; Task age; Cancellation; Latency; Timeout; Exception; Queue rejection; Late discard; Circuit breaker; Kill switch; P1 mismatch; P2 mismatch; Checkpoint lag; Lineage mismatch; Identity blocked; Redaction; Response mutation; Write/event violations; Production route detection; Authority mismatch.

`APPLICATION_DASHBOARD_CONTRACT_READY=YES`

No dashboard provisioning system or infrastructure repository is available. Therefore `EXTERNAL_DASHBOARD_READY=NO`. The machine-readable inventory is `verification/operations/op2/op2-evidence.json`.

## Alert rules and routing

Critical zero-tolerance rules: response mutation, database write, cache write, event emission, notification emission, redaction failure, production route, production identity, authority mismatch, traffic ceiling violation, and credential scope violation. Safety actions are constrained to flag off, lane kill, global shadow disable, credential revoke, and network route revoke.

Warning rules: timeout rate, exception rate, queue rejection rate, late discard rate, task age, checkpoint lag, executor saturation, credential refresh failure, allowlist lookup failure, unexpected P1 mismatch, and unexpected P2 mismatch. SC-6 thresholds are not changed. Any threshold change requires System Coordination reapproval.

`CRITICAL_ALERT_RULES_READY=YES`, `WARNING_ALERT_RULES_READY=YES`, `CRITICAL_ALERT_ROUTE_READY=NO`. No external paging/alert route is configured in this repository.

## Kill switch and circuit breaker continuity

Feature flag OFF, lane kill, global disable, candidate invocation blocking, queued cancellation, late discard, in-flight timeout, fallback-to-primary, unchanged primary response, no automatic restart/ramp, and safe defaults after process restart remain protected by RCA-2/OP-1 implementation and regression workflows. Kill controls affect only the shadow lane and must not interrupt the primary path.

## Rollback Level 1-7

| Level | Action | Status | Owner | Maximum time | Expected state |
|---|---|---|---|---|---|
| 1 | FLAG_OFF | PASS | Operations | immediate/config propagation | effective traffic 0 |
| 2 | LANE_KILL_SWITCH | PASS | Operations | immediate | selected lane blocked |
| 3 | GLOBAL_SHADOW_DISABLE | PASS | Operations | immediate | all candidate invocation blocked |
| 4 | CONFIG_ROLLBACK | NOT_EXECUTED | Operations | deployment-specific | prior safe config, traffic 0 |
| 5 | DEPLOYMENT_ROLLBACK | NOT_EXECUTED | Reliability | deployment-specific | prior exact deployment |
| 6 | CREDENTIAL_REVOKE | BLOCKED_EXTERNAL_DEPENDENCY | Privacy/Security | provider-specific | credential unusable |
| 7 | NETWORK_ROUTE_REVOKE | BLOCKED_EXTERNAL_DEPENDENCY | Reliability/Security | provider-specific | route unreachable |

Verification queries require feature flag OFF, effective traffic 0, candidate invocation blocked, primary retained, and no production route. Recovery requires root-cause evidence, six-role review, exact-head revalidation, and manual approval. No unexecuted external drill is reported as PASS.

## Approval package

The exact-head approval package covers INTELLIGENCE, RELIABILITY, DATA, OPERATIONS, PRIVACY_SECURITY, and SYSTEM_COORDINATION. Each role reviews the eventual PR exact head and artifact digest, role scope, blockers, conditions, rollback responsibility, and expiry/re-review conditions. All signatures remain `PENDING_USER_REVIEW`; CI is not human approval.

## Manual enablement runbook

OP-3 may execute only after: exact deployment/version; external endpoint; credential; allowlist; candidate source/version; metric readiness; dashboard; critical alert route; rollback owner; six approvals; and effective traffic 0 are confirmed. Manual 1% approval and enablement, immediate metrics, 30-minute/100-call observation, and exit/rollback are OP-3 actions and are forbidden in OP-2.

`MANUAL_ENABLEMENT_RUNBOOK_READY=YES`, `MANUAL_ENABLEMENT_APPROVED=NO`.

## OP-3 entry gate

All requested readiness predicates are AND conditions. Current false predicates include external metrics backend, external dashboard, critical alert route, rollback levels 4-7 execution/readiness, endpoint, credential, allowlist, candidate adapter, six human approvals, and manual enablement approval.

`OP3_ENTRY=BLOCKED`

## Blockers and risks

External blockers: infrastructure repository, metrics backend, dashboard path, paging route, secret manager/workload identity, non-production namespace and route, endpoint, allowlist storage, candidate source/protocol/API version, credential revoke, and network revoke. Risks include missing external retention guarantees, alert delivery uncertainty, deployment rollback command ownership, cardinality drift outside the application registry, and stale approval after the exact head changes.

## Incident escalation

Any critical signal requires immediate Level 1 and Level 3 safety action, preservation of exact-head evidence, Operations and Reliability notification, and Privacy/Security escalation for credential, identity, redaction, or route violations. Production-route or authority violations block OP-3 and require System Coordination review.

## Evidence retention and verifier

Machine evidence is retained in `verification/operations/op2/op2-evidence.json`; independent verification is `verification/operations/op2/run_op2_verification.py`. GitHub Actions artifacts are expected to use repository workflow retention. External dashboard/alert retention remains unresolved.

## OP-3 execution handoff and prompt

Start OP-3 only from the merged OP-2 exact head after all gate predicates are true and approvals are current. Re-verify `main`, the OP-2 merge tree, artifact digest, external endpoint/credential/allowlist/candidate version, dashboard, alert route, and rollback ownership. Execute no more than a manually approved 1% isolated non-production cohort, observe at least 30 minutes and 100 invocations, preserve primary authority, and rollback on any zero-tolerance signal. Production traffic, candidate serving, primary mutation, writes/events, automatic ramp, production activation, and authority transfer remain forbidden.
