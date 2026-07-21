# IP-12.5 Go/No-Go Decision

## Gate result

| Gate | Current status | Blocking input/action |
|---|---|---|
| Production implementation | IMPLEMENTED | renewed CI required on branch |
| Default disabled | PASS by source/static inspection | none |
| Effective sampling 0 | PASS by source/static inspection | none |
| 10 BPS maximum | PASS by source/static inspection | none |
| Account hash contract | IMPLEMENTED | actual approved hashes missing |
| Activation approval | BLOCKED | approval reference and approver missing |
| Execution owner | BLOCKED | operator reference missing |
| Rollback owner | BLOCKED | operational confirmation missing |
| Activation window | BLOCKED | UTC window missing |
| Metric verification path | BLOCKED | concrete check path missing |
| Production-equivalent restart drill | PENDING | inputs and exact deployment procedure missing |
| Legacy compatibility regression | PENDING_CI | `verifyIp125` workflow required |

## Decision

`IP-12.5 HOLD_OPERATIONAL_INPUTS_PENDING`

No traffic activation is authorized. This may be reclassified to `READY_FOR_INTERNAL_PILOT_APPROVAL` only after the exact branch passes `verifyIp125` and all operational inputs are supplied. `INTERNAL_PILOT_APPROVED` requires a separate explicit user decision.
