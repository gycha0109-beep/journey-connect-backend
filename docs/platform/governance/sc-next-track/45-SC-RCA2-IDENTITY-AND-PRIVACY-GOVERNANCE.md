# SC RCA-2 Identity and Privacy Governance

## Scope
Authorize only synthetic and explicit non-production test-account identity.

## Current Baseline
Authoritative work-start `3efbf96ebf25ae1645a62f35269c4b569425a9ca`; PR #27 merged; RCA-1B exact-final-head `dbb6b5397ad0fe675856b195e280faf9a0f3030c`; merge tree identical; actual identity mapping remains absent.

## Decision
```text
IDENTITY_MODE=SYNTHETIC_OR_TEST_ACCOUNT_ONLY
IDENTITY_OWNER=PRIVACY_SECURITY
IDENTITY_AUTHORITY=RCA2_NONPRODUCTION_TEST_ACCOUNT_ALLOWLIST_V1
IDENTITY_PURPOSE_BINDING=RCA2_ISOLATED_NONPRODUCTION_DARK_READ_ONLY
IDENTITY_STORAGE=ENCRYPTED_ENVIRONMENT_SCOPED_REGISTRY
IDENTITY_RETENTION=MAX_30_DAYS_PER_ALLOWLIST_ENTRY
IDENTITY_DELETION=IMMEDIATE_ON_REQUEST_OR_EXPIRY
IDENTITY_INVALIDATION=IMMEDIATE_FAIL_CLOSED
IDENTITY_AUDIT=HASHED_REFERENCE_AND_DECISION_ONLY_90_DAYS
IDENTITY_ENCRYPTION=REQUIRED_AT_REST_AND_IN_TRANSIT
IDENTITY_LOGGING_POLICY=NO_RAW_ID_OR_MAPPING_PAIR
IDENTITY_FAILURE_POLICY=FAIL_CLOSED_KEEP_PRIMARY
```

## Rationale
Production identity lacks approved owner, mapping, key, deletion and incident controls.

## Authority
Privacy/Security is accountable and blocking; SC controls expansion of identity mode.

## Dependencies
Explicit allowlist owner, purpose, expiry, deletion, invalidation and audit procedure.

## Runtime Environment
Isolated non-production only.

## Runtime Model
Identity is copied minimally into the bounded task and destroyed at completion/cancellation.

## Feature Flag
Identity eligibility and flag eligibility are both required.

## Traffic Boundary
Only synthetic/test-account cohort; initial 0%.

## Primary/Shadow Authority
Identity cannot authorize candidate serving or alternate user inference.

## Timeout/Fallback
Missing/invalid/expired/deleted/mismatched identity keeps primary and blocks shadow.

## Credential/Network
Identity storage is separate from workload credentials.

## Identity/Privacy
Anonymous, nearest-user, alternate-subject and inferred-ID fallback are forbidden. Actual production identity is blocked.

## P1 Result Boundary
P1 subject comparison uses only approved identity mode.

## P2 Result Boundary
P2 subject/session/run/exposure binding must fail closed on mismatch.

## Checkpoint/Lineage
Identity scheme/version is included in redacted lineage metadata.

## Observability
Count identity-blocked reasons with low-cardinality classes; no raw identifiers.

## Rollback
Invalidate allowlist, disable lane/global flag and purge pending tasks.

## DB/SQL Impact
No mapping table or persistent DB object.

## Production Impact
No actual production identity approval.

## Verification
Actual identity mapping is `NOT_EXECUTED`; implementation must test every fail-closed state.

## Risks
Test accounts do not prove production privacy or deletion behavior.

## Exit Criteria
Allowlist, encryption, expiry, deletion, invalidation, audit and no-raw-ID evidence verified.

## Handoff
Keep production identity blocked and request separate Privacy/Security and SC authorization if needed.