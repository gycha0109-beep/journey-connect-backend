# IP-11.9 Handoff

## Current state handed to IP-12

```text
IP-11: GOVERNANCE_DECISIONS_APPROVED_WITH_CONDITIONS
IP-11.5: TECHNICAL_CONTROLS_IMPLEMENTED / EXTERNAL_ATTESTATION_COMPLETE
IP-11.75: GOVERNANCE_APPROVAL_CLOSURE_COMPLETE
IP-11.9: EXTERNAL_ATTESTATION_COMPLETE_WITH_FIXES / OPERATIONAL_INPUTS_PENDING
Production shadow: DISABLED
Effective production sampling: 0 BPS
Actual cohort: empty / 0%
Search cutover: NOT STARTED
Go/No-Go: NO_GO
IP-12: HOLD_FOR_OPERATIONAL_WIRING_AND_10_BPS_ENFORCEMENT
```

## Required IP-12 work

- production profile/property wiring through the existing IP-9 bridge
- default-killed property/restart control
- opaque SHA-256 internal account allowlist contract
- maximum approved production sampling 10 BPS
- Micrometer and privacy-safe structured logs
- production-equivalent disable drill
- renewed external attestation for changed source
