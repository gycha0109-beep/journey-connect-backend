# IP-11 Emergency Disable and Rollback Runbook

## 상태

`RESPONSIBILITY_ASSIGNED / PRODUCTION_PATH_AND_DRILL_PENDING`

## Owners

- primary kill-switch authority: Project Owner
- backup: 팀장 영탁
- technical rollback/disable executor: Backend Owner

## Current path

Production shadow is already disabled, cohort empty and effective sampling 0 BPS. A future pilot may require property/config change and restart/deployment; no remote/dynamic switch is claimed.

## Procedure

1. Project Owner declares disable/rollback.
2. Backend Owner sets approved mode/sample to disabled/0 using the verified operating path.
3. Stop new dispatch; drain/cancel bounded work.
4. Verify provider/runtime/comparison/evidence calls stop.
5. Verify `/api/v1/explore` body/status/order/page remain legacy exact.
6. Preserve privacy-safe logs/metrics and incident timeline.
7. Team Lead Youngtak acts as backup authority when primary is unavailable.
8. Reactivation requires new approval, attestation, allowlist and drill evidence.

Production-equivalent drill and measured SLA remain open.
