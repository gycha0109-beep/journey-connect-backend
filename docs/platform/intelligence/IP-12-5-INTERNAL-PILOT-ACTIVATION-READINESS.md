# IP-12.5 Internal Pilot Activation Readiness

## Status

```text
Baseline branch: main
Baseline HEAD: 356cc0e25c9da2b57a5f9ed292f997bc3cea3119
IP-12.5: HOLD_OPERATIONAL_INPUTS_PENDING
Production shadow: DISABLED
Effective sampling: 0 BPS
Actual cohort: empty / 0%
Search cutover: NOT STARTED
```

## Purpose

Close the operational-input and explicit-approval boundary required before an internal production shadow pilot. This stage does not activate traffic.

## Actual HEAD findings

- Production properties, 10 BPS ceiling, SHA-256 allowlist validation, kill-switch, bounded executor and Micrometer adapter exist.
- `application-prod.yml` and `application-production.yml` were absent from GitHub HEAD even though the IP-12 static contract requires them.
- The committed IP-12 handoff and Gradle artifact still reported external attestation pending/not executed. IP-12.5 creates renewed branch-bound evidence instead of copying those statuses.
- Existing runtime dispatch did not require an approval reference, approver, executor, rollback owner, activation window or metric verification reference.

## Implemented gate

Positive production sampling is valid only when all are present:

1. `enabled=true`
2. kill-switch inactive
3. sampling in `1..10` BPS
4. non-empty allowlist of one to three lowercase SHA-256 hashes
5. bounded activation approval reference
6. bounded approver reference
7. bounded execution owner reference
8. bounded rollback owner reference
9. bounded metric verification reference
10. valid UTC activation window with start before end

Missing or malformed operational inputs fail startup. At runtime a closed activation window blocks before identity resolution and executor submission.

## State transition

```text
DISABLED / 0 BPS / EMPTY COHORT
→ receive approved opaque account hashes
→ populate operational references and activation window
→ validate kill-switch remains active during preparation
→ validate requested sample is 1..10 BPS
→ explicit approval record
→ restart with kill-switch inactive only inside approved window
→ observe Micrometer/structured logs
→ kill-switch + restart on stop condition
→ retain no raw query or raw identity evidence
```

## Current decision

No account hashes, initial sampling value, activation window or approval record were supplied. The correct decision is:

`IP-12.5 HOLD_OPERATIONAL_INPUTS_PENDING`
