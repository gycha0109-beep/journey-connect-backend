# Risk Register

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


| ID | Severity | Owner | Risk | Mitigation |
|---|---|---|---|---|
| `OP0-R001` | `HIGH` | INTELLIGENCE | Candidate mirror mistaken for real candidate readiness | WS-5 must remain BLOCKED until actual source/protocol are named |
| `OP0-R002` | `HIGH` | RELIABILITY | Metric alias/unit drift between SC-6 inventory and OP implementation | retain 27 metric registry and explicit mapping/conversion tests |
| `OP0-R003` | `HIGH` | OPERATIONS | External infrastructure changes occur outside reviewed exact head | bind evidence to immutable config/image digests and approval package |
| `OP0-R004` | `CRITICAL` | PRIVACY_SECURITY | Raw identity or hash leaks through metrics/logs | no identity labels; hashed audit only in restricted store; redaction tests |
| `OP0-R005` | `CRITICAL` | OPERATIONS | Manual action accidentally enables production route or scope | deny production DNS/namespace/database/credential; Level 7 drill |
| `OP0-R006` | `HIGH` | SYSTEM_COORDINATION | Approval inferred from workflow success | approval matrix actual_approval_recorded=false until explicit decision |
| `OP0-R007` | `HIGH` | SYSTEM_COORDINATION | Automatic rollback logic becomes automatic rollout logic | automatic rollout forbidden; disable actions only |
| `OP0-R008` | `HIGH` | SYSTEM_COORDINATION | Historical RCA/SC evidence modified during preparation | exact path allowlist and historical-evidence CI job |
| `OP0-R009` | `HIGH` | RELIABILITY | Observation thresholds changed without SC approval | verifier locks SC-6 duration/count/rate/zero-tolerance values |
| `OP0-R010` | `HIGH` | OPERATIONS | Rollback drill cannot prove effective traffic zero | selected/skipped/executor metrics and before/after evidence required |
