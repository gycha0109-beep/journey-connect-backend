# Handoff: Data to Recommendation V1

## Compatibility

Profile and experiment outcome are `CONDITIONALLY_COMPATIBLE`.

Current P1 source, `recommendation-evaluation-dataset-v1`, P2 exposure authority, `engagement_rate` and `fallback_rate` semantics remain authoritative and unchanged. Data projection is shadow/validation candidate only; consumer cutover and production write are not approved.

| Task | Objective | Acceptance criteria | Approval | Rollback |
|---|---|---|---|---|
| `REC-DATA-1` | Shadow Consumer Compatibility Validation | exact version parse, required fail-closed, no source replacement | Recommendation+SC | disable read |
| `REC-DATA-2` | Projection Parity and Drift Evaluation | counts/identity/features/exposure/metric windows/fingerprints reconcile | Recommendation+Reliability | retain current source |
| `REC-DATA-3` | Controlled Consumer Adoption Decision | authority/privacy/SLO/migration/rollback approved | Rec+Rel+Ops+SC | no adoption |
| `REC-DATA-4` | Staged Cutover and Rollback Plan | bounded dual-read, stages/cohort/kill/threshold/evidence | Rec+Rel+Ops | restore current source |

Dual-write is prohibited without separate atomicity, dedupe and rollback approval.
