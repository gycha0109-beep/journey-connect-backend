# IP-12.5 Go/No-Go Decision

## Attested implementation

- Implementation HEAD: `f95c1928b6143d8c5d519d6f2504f158661d82cb`
- Backend `verifyIp125`: PASS — workflow run `29814868823`
- Recommendation Java Core: PASS — workflow run `29814868799`
- PostgreSQL 15/18 matrix: PASS — workflow run `29814868839`

## Gate result

| Gate | Current status | Blocking input/action |
|---|---|---|
| Production implementation | PASS | none |
| Default disabled | PASS | none |
| Effective sampling 0 | PASS | none |
| 10 BPS maximum | PASS | none |
| Legacy compatibility regression | PASS | none |
| Spring production/stage profile gates | PASS | none |
| P0/P1/P2 protected regression | PASS | none |
| PostgreSQL 15/18 | PASS | none |
| Account hash contract | IMPLEMENTED | actual approved hashes missing |
| Activation approval | BLOCKED | approval reference and approver missing |
| Execution owner | BLOCKED | operator reference missing |
| Rollback owner | BLOCKED | operational confirmation missing |
| Activation window | BLOCKED | UTC window missing |
| Metric verification path | BLOCKED | concrete check path missing |
| Production-equivalent restart drill | BLOCKED | inputs and exact deployment procedure missing |

## Decision

`IP-12.5 HOLD_OPERATIONAL_INPUTS_PENDING`

Technical readiness and full external regression attestation are complete. No traffic activation is authorized because approved account hashes, operational approval, activation window, execution/rollback ownership and metric verification path were not supplied. `INTERNAL_PILOT_APPROVED` requires a separate explicit approval decision.
