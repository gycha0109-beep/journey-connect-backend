# Credential Revoke Drill

The application contract validates short-lived, read-only, non-production credential metadata and fail-closed missing/revoked behavior. A local fake provider is used only for contract tests and is not represented as an external secret manager.

```text
SECRET_MANAGER=UNRESOLVED
EXTERNAL_CREDENTIAL_READY=NO
CREDENTIAL_REVOKE_DRILL=BLOCKED_EXTERNAL_DEPENDENCY
```

Required external evidence: lease identifier hash, revoke command/audit reference, timestamp, provider returns revoked/missing, no production identity, and recovery approval.
