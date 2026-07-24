# SC RCA-1 Evidence and Privacy Policy

## Scope

Define committed and generated evidence for RCA-1 based on `f802a105e46a62718616acaa7a3db6c172e7ed10`.

## Current Baseline

RCA-0 evidence is historical and unchanged. RCA-1 has no executed comparison evidence yet.

## Decision

Evidence policy is `APPROVED`.

Allowed:

- synthetic reference;
- SHA-256 fixture identifier;
- contract/schema/normalization/verifier version;
- lane and comparison dimension;
- classification;
- normalized synthetic safe values;
- checkpoint metadata;
- lineage fingerprint;
- evidence timestamp;
- exact tested commit.

Prohibited:

- raw user or opaque subject ID;
- email, token, session secret;
- raw behavioral payload or unrestricted event history;
- identity mapping pair/source;
- production content body;
- P2 canonical dataset row or release evidence payload.

## Rationale

Reconciliation needs reproducible mismatch details but does not need personal or production payloads.

## Authority

SC owns evidence schema. Reliability owns P2 evidence-integrity acceptance. Intelligence owns P1 interpretation. Privacy/Security is consulted.

## Dependencies

Synthetic-only fixtures and field-level redaction allowlist.

## Allowed Changes

Machine-readable JSON/TSV summary, per-case redacted classifications and offline counter totals.

## Forbidden Changes

Raw payload dumps, unrestricted debug logs, production identifiers and committed runtime artifacts containing secrets.

## Identity/Privacy

Committed governance decision evidence remains in Git history and contains no personal data. Generated CI evidence is retained no longer than 90 days. Case-level expected/actual values must be omitted or hashed when not explicitly safe synthetic values.

## DB/SQL Impact

None.

## Production Impact

None.

## Verification

Verifier scans governance/implementation evidence definitions for prohibited terms, requires retention/redaction markers and records only executed checks as PASS.

## Risks

A future Model B/C implementation can accidentally broaden evidence. Its proposal must re-evaluate retention, access and redaction.

## Exit Criteria

Evidence schema/version fixed, retention explicit, prohibited fields absent and exact tested SHA recorded.

## Handoff

RCA-1 implementation must generate summary counters and redacted cases only.
