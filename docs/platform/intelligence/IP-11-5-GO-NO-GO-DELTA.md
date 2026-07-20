# IP-11.5 Go/No-Go Delta

## IP-11.75 reclassification

| Gate | IP-11.5 technical state | IP-11.75 governance state |
|---|---|---|
| owner roles | open | approved with conditions |
| resource policy | implemented/provisional | initial pilot bounds approved |
| sampling | 0 BPS enforced | current 0, pilot ceiling 10 BPS approved |
| privacy | safe contract implemented | policy approved with conditions |
| retention | no persistence | 14/30-day policy approved; implementation pending |
| observability | abstraction/no-op/test sink | Spring logs + Micrometer target approved; dashboard/alert deferred |
| cohort | empty/test allowlist | internal policy approved; actual allowlist pending |
| disable | technical drill | responsibility assigned; production drill pending |
| external attestation | not executed | still open |

Decision remains `NO_GO`. Technical capability is not activation authority.

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

## IP-12 superseding status

IP-11.9 closed the baseline external attestation. IP-12 adds production operational wiring, therefore the changed source requires a new IP-12 Gradle/Spring attestation. Production remains disabled, effective sampling is 0 BPS and the cohort is empty.
