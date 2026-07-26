# Blocking Approval Package

Packages exist for Intelligence, Reliability, Data, Operations, Privacy/Security and System Coordination. Each package binds review to the PR exact head and CI artifact digest generated after the final push.

All signatures are intentionally:

```text
PENDING_USER_REVIEW
```

CI success is not human approval. Any head, artifact digest, metric definition, threshold, external endpoint, credential, allowlist, candidate source, dashboard, alert route or rollback path change expires prior review.

Role responsibilities, blockers, conditions and rollback ownership are recorded in `approval-matrix.json`.
