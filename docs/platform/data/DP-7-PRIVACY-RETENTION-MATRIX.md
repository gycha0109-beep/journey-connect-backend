# DP-7 Privacy and Retention Matrix

## Privacy

| Field class | Persistence | Reader view |
|---|---|---|
| target/source track, scope, verdict, severity, failure code | allowed | aggregate only |
| subject/user ID | purpose-bound raw evidence only where contract requires | prohibited |
| source event/snapshot/exposure ID | raw evidence only | prohibited |
| raw payload, query, post text, PII, token | prohibited | prohibited |
| precise location | prohibited | prohibited |
| lineage | purpose-bound fingerprint/reference evidence | prohibited |
| unrestricted fingerprint | raw evidence only | prohibited |
| stack trace/unrestricted error message | prohibited | prohibited |

Safe metrics expose counts by track, scope, verdict, failure and severity plus oldest inconclusive age and latest compatible validation time. They expose no raw identity, event, snapshot, exposure, lineage or fingerprint dimension.

## Retention

Every run, status, check, mapping, boundary, verdict and conflict row uses:

```text
retention_class = cross_track_integration_evidence_90d
retention_policy_version = data-retention-policy-v1
expires_at >= created_at + 90 days
```

Target retention must not exceed source retention and deletion semantics must align. `expires_at` is eligibility metadata only. Automatic purge, physical delete, retention worker and scheduler remain absent.
