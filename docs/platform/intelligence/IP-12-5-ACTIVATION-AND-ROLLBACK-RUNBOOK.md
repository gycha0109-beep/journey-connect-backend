# IP-12.5 Activation and Rollback Runbook

## Preconditions

All fields in `verification/ip12-5/IP125_OPERATIONAL_INPUT_TEMPLATE.properties` must be populated by authorized operators. Raw account IDs are prohibited; only lowercase 64-character SHA-256 cohort hashes are accepted.

## Preparation — no traffic

1. Record baseline branch and commit SHA.
2. Confirm `enabled=false`, `kill-switch=true`, `sampling-bps=0`, empty allowlist.
3. Confirm `/api/v1/explore` legacy response health.
4. Confirm Micrometer registry is bound and the agreed metric path is observable.
5. Record activation approver, execution owner, rollback owner and approval reference.
6. Record an activation window in UTC.
7. Add only approved opaque account hashes.
8. Set requested sampling between 1 and 10 BPS, but keep kill-switch active.
9. Run `verifyIp125` on the exact commit.

## Explicit pilot start

1. Verify all preconditions and evidence template fields.
2. Change `enabled=true` while kill-switch remains active; restart and verify dispatch remains zero.
3. Change kill-switch to inactive only after explicit approval and within the activation window.
4. Restart the application.
5. Verify startup log: enabled, kill-switch inactive, approved sampling, expected allowlist count, operational inputs present.
6. Verify only allowlisted internal accounts can enter deterministic sampling.
7. Verify legacy response body, status, ordering and pagination remain authoritative.

## Immediate stop / rollback

1. Set `JC_SEARCH_SHADOW_PRODUCTION_KILL_SWITCH=true`.
2. Restart the production application.
3. Verify `DISABLED_BY_KILL_SWITCH`/killed metric and zero new dispatch.
4. Verify queue drains or cancellation completes within the bounded executor policy.
5. Verify `/api/v1/explore` remains healthy and unchanged.
6. Preserve privacy-safe aggregate metrics and sanitized error categories only.
7. Record stop time, reason, executor and rollback owner.
8. Any reactivation requires a new approval reference and activation window.

## Hard rejection conditions

- empty allowlist
- malformed/raw account identifier
- sampling outside 1..10 BPS
- kill-switch state unclear
- missing approval, approver, execution owner or rollback owner
- missing metric verification reference
- missing/closed activation window
- production profile or MeterRegistry binding failure
- regression or legacy response incompatibility
