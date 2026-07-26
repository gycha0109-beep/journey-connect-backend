# Manual Enablement Runbook Skeleton

| Field | Value |
|---|---|
| Official phase | `OP-0 RCA-2 Stage 1 Operations Preparation Baseline` |
| Work-start / authoritative main | `40ff229e2401e7d5d9c5323d469bcd012530e882` |
| RCA-2 exact final head | `511b19f80cdd42bb2fafde0563c7388b4f5b5f48` |
| RCA-2 merge commit | `b57c344c9b4e332966fe9f6d36a5da66a5faae71` |
| SC-6 exact final head | `20da93e932c50b5bebd549a56db40edb00ca1eea` |
| SC-6 merge commit | `40ff229e2401e7d5d9c5323d469bcd012530e882` |
| Artifact version | `op0-rca2-stage1-operations-preparation-v1` |
| Updated at | `2026-07-26T14:15:55Z` |


## Preflight

1. Bind operator session to exact deployment/config/dashboard/alert/credential digests.
2. Evaluate every Stage 1 gate condition; stop on any false value.
3. Confirm current traffic 0, production traffic 0, flag OFF and all critical counters zero.
4. Confirm Levels 1-7 controls and two-person execution roles.

## Manual action

1. Record explicit `MANUAL_ENABLEMENT_APPROVED` decision.
2. Set only the isolated non-production Stage 1 control to a 1% ceiling.
3. Verify selected/skipped accounting and no candidate serving.

## Observation

Observe at least 30 minutes **and** at least 100 shadow executions. Continuously monitor all critical violations and SC-6 rate ceilings.

## Abort

Any critical violation, ceiling breach, approval invalidation or evidence loss triggers Level 1 or stronger rollback and restores traffic 0. Production activation and authority transfer are never part of this runbook.

OP-0 does not execute these steps.
