# IP-12 Handoff

## Repository-bound state

```text
Baseline branch: main
Baseline HEAD: 356cc0e25c9da2b57a5f9ed292f997bc3cea3119
IP-12: IMPLEMENTATION_COMPLETE / REPOSITORY_ATTESTATION_RENEWAL_REQUIRED
Production shadow: DISABLED
Effective production sampling: 0 BPS
Actual cohort: empty / 0%
Search cutover: NOT STARTED
Go/No-Go: NO_GO_FOR_TRAFFIC
```

The GitHub HEAD implementation contains the production wiring, 10 BPS ceiling, hashed allowlist, kill-switch and Micrometer adapter. The committed historical verification artifact still records Gradle execution as NOT_EXECUTED, so IP-12.5 must generate new branch-bound CI evidence instead of treating the stale artifact as PASS.

## IP-12.5 handoff

- positive sampling now additionally requires approval, approver, execution owner, rollback owner, metric verification reference and an active UTC window
- safe production resource files are committed with disabled/zero/empty defaults
- actual account hashes and activation approval remain absent
- final decision remains `IP-12.5 HOLD_OPERATIONAL_INPUTS_PENDING`

See [IP-12.5 Handoff](IP-12-5-HANDOFF.md).
