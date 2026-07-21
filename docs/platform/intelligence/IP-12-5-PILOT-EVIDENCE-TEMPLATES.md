# IP-12.5 Pilot Evidence Templates

## Start evidence

| Field | Required | Value rule |
|---|---:|---|
| sourceCommitSha | yes | exact 40-hex commit |
| approvalRef | yes | bounded opaque reference |
| activationApproverRef | yes | bounded role/authority reference |
| activationExecutorRef | yes | bounded operator reference |
| rollbackOwnerRef | yes | bounded owner reference |
| metricVerificationRef | yes | bounded metric/dashboard reference |
| activationWindowStart | yes | UTC Instant |
| activationWindowEnd | yes | UTC Instant after start |
| requestedSamplingBps | yes | 1..10 |
| allowlistCount | yes | 1..3; hashes not copied into evidence |
| killSwitchVerified | yes | boolean |
| verifyIp125Run | yes | workflow/run reference |
| legacyContractVerified | yes | boolean |

## Observation evidence

Only bounded aggregates are permitted: dispatch attempted/accepted/rejected, killed, cohort blocked, sampling blocked, timeout, failure category, latency bucket, queue depth and active worker count. Query, user/account ID, account hash, JWT/session, post/candidate ID and payloads are prohibited.

## Stop/failure evidence

| Field | Required |
|---|---:|
| stopReason | yes, bounded enum |
| stoppedAt | yes |
| killSwitchAppliedAt | yes |
| restartCompletedAt | yes |
| zeroDispatchVerified | yes |
| legacyEndpointVerified | yes |
| rollbackOwnerRef | yes |
| sanitizedIncidentRef | yes |
| reactivationAllowed | explicit decision only |

Machine-readable templates are in `verification/ip12-5/`.
