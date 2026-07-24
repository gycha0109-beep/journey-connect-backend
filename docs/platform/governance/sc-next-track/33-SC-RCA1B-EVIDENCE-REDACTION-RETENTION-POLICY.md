# SC RCA-1B Evidence, Redaction and Retention Policy

## Scope

Extend RCA-1 evidence for database execution without retaining raw rows, credentials or production-derived material.

## Current Baseline

Work-start `b2e7a5c316c6f6ee543ccedf35bca65353ab3aa4`; RCA-1 evidence uses hashed cases, deterministic JSON/TSV and 90-day CI retention.

## Decision

Allowed fields:

```text
hashedCaseId
lane
contractId
contractVersion
queryId
queryFingerprint
comparisonDimension
classification
normalizedExpected
normalizedActual
sourceCheckpoint
candidateCheckpoint
lineageFingerprint
sourceRowCount
candidateRowCount
databaseVersion
executionEnvironment
transactionIsolation
transactionReadOnly
statementTimeoutMs
testedSha
verifierVersion
evidenceTimestamp
```

```text
CI_EVIDENCE_RETENTION_DAYS=90
DB_SNAPSHOT_RETENTION=EXECUTION_LIFETIME_ONLY
RAW_RESULT_RETENTION=NONE
CREDENTIAL_RETENTION=NONE
```

## Rationale

Database evidence must prove the comparison and execution boundary without becoming a data export.

## Authority

Reliability owns evidence integrity; Privacy/Security owns redaction/retention; Operations owns artifact and secret handling; SC registers schema.

## Dependencies

Deterministic evidence writer, fixed JSON field order, fixed TSV columns, duplicate-case rejection and exact tested SHA.

## Execution Environment

Artifacts are generated only in CI and must use 90-day or shorter retention.

## DB Access Boundary

Never store host/IP, connection string, username/password, token, role secret or server configuration dump.

## Query Boundary

Never store SQL parameters, raw query results, unrestricted row history, canonical dataset rows or release evidence.

## Identity/Privacy

Raw user, subject, session, run, exposure and mapping identifiers are prohibited. Normalized values must be synthetic-safe and redacted before serialization.

## Evidence

JSON and TSV must be deterministic, duplicate-free, lane-separated and bound to query fingerprint and exact PR head.

## DB/SQL Impact

None.

## Production Impact

None.

## Verification

SC-4 validates allowed/prohibited inventories and retention values. Actual database evidence generation is `NOT_EXECUTED`.

## Risks

Normalized values can still leak identifiers if redaction occurs after serialization. Redaction must precede evidence object construction.

## Exit Criteria

No prohibited token or raw row appears in artifacts/logs, and evidence is reproducible across PostgreSQL 15/18 apart from explicit version fields.

## Handoff

Implementation must include automated redaction scans, duplicate checks and artifact-retention assertions.