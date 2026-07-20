# IP-11 Handoff

## Current status

```text
IP-11: GOVERNANCE_DECISIONS_APPROVED_WITH_CONDITIONS
IP-11.5: TECHNICAL_CONTROLS_IMPLEMENTED / EXTERNAL_ATTESTATION_PENDING
IP-11.75: GOVERNANCE_APPROVAL_CLOSURE_COMPLETE / EXTERNAL_ATTESTATION_PENDING
Production shadow: DISABLED
Effective production sampling: 0 BPS
Search cutover: NOT STARTED
Go/No-Go: NO_GO
IP-12: HOLD_FOR_EXTERNAL_ATTESTATION_AND_OPERATIONAL_INPUTS
```

## Governance closure

Owner, privacy, retention, observability target, resource budget, sampling ceiling and internal cohort policy decisions are approved with explicit conditions. The project is no longer blocked by unassigned governance roles.

## Remaining blockers

- external Gradle/Spring/PostgreSQL attestation
- SQL 27/28 PostgreSQL execution
- actual account allowlist
- actual production switch/restart procedure and disable drill
- Micrometer binding/manual check verification
- persistence TTL/cleanup only if persistence is introduced

See [IP-11.75 Handoff](IP-11-75-HANDOFF.md).

## IP-12 current handoff

Governance remains approved with conditions. Production shadow is disabled, effective sampling is 0 BPS, actual cohort is empty and Search cutover is not started.
