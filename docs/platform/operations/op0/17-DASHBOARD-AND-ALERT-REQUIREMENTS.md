# Dashboard and Alert Requirements

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


## Dashboard sections

Traffic selection; executor; latency; timeout; exception; queue rejection; late discard; circuit breaker; kill switch; P1 mismatch; P2 mismatch; checkpoint lag; lineage mismatch; identity blocked; redaction; write/event/mutation violations.

## Critical alerts

Response mutation, write attempt, event emission, redaction failure, production route, production identity, authority mismatch, traffic ceiling violation and credential violation. Critical routing must be tested and acknowledged in OP-2. Alert actions may disable or revoke, but must never increase traffic automatically.

## Current state

Dashboard and alert contracts are defined; no dashboard is deployed and no alert route is connected. External repository/path and staffed route remain blockers.
