# SC RCA-1 Identity Mapping Governance Decision

## Scope

Decide identity handling for RCA-1 at authoritative main `f802a105e46a62718616acaa7a3db6c172e7ed10`.

## Current Baseline

`subject:<opaque-id>` and `user:<numeric-id>` are separate schemes. No physical mapping owner or runtime mapping repository is approved.

## Decision

Option A, synthetic identity only, is `APPROVED`. Option B is `DEFERRED`. Option C is `BLOCKED` for RCA-1.

```text
IDENTITY_MODE=SYNTHETIC_ONLY
IDENTITY_MAPPING_OWNER=NOT_REQUIRED_FOR_MODEL_A_SYNTHETIC_ONLY
IDENTITY_MAPPING_AUTHORITY=NONE
IDENTITY_MAPPING_STORAGE=STATIC_TEST_FIXTURE_ONLY
IDENTITY_MAPPING_RETENTION=FIXTURE_VERSION_LIFETIME
IDENTITY_MAPPING_DELETION=REMOVE_WITH_FIXTURE_VERSION
IDENTITY_MAPPING_INVALIDATION=FIXTURE_VERSION_INVALIDATION_ONLY
IDENTITY_MAPPING_AUDIT=HASHED_CASE_AND_CLASSIFICATION_ONLY
IDENTITY_MAPPING_PURPOSE_BINDING=RCA1_OFFLINE_RECONCILIATION_ONLY
IDENTITY_MAPPING_LOGGING_POLICY=NO_RAW_ID_OR_MAPPING_PAIR
IDENTITY_MAPPING_FAILURE_POLICY=FAIL_CLOSED
REAL_IDENTITY_MAPPING_OWNER=DEFERRED
```

## Rationale

Synthetic-only cases are sufficient for comparator correctness and eliminate production identity risk. Pseudonymized test binding still needs retention/deletion policy and a controlled dataset. Runtime mapping requires a full authority and security design.

## Authority

SC approves synthetic-only use. No track receives real mapping ownership.

## Dependencies

Versioned synthetic fixtures and deterministic case IDs.

## Allowed Changes

Static synthetic aliases, hashed evidence identifiers and fail-closed test cases.

## Forbidden Changes

Persistent mapping, runtime port implementation, production identity, raw ID logging, anonymous/nearest/alternate fallback, mapping-pair evidence and P2 row/hash rewrite.

## Identity/Privacy

Fail closed for absent, invalid, expired, deleted, mismatched, unauthorized-purpose and unauthorized-caller states.

## DB/SQL Impact

None. No table, view, role or grant.

## Production Impact

None.

## Verification

The fixture suite must include valid, absent, invalid, expired, deleted, mismatched, unauthorized-purpose and unauthorized-caller outcomes. Only the valid synthetic binding may proceed.

## Risks

Synthetic identities do not validate production lifecycle, encryption, revocation or deletion. Those remain blockers for any real reconciliation.

## Exit Criteria

Identity boundary enforced with no raw identity evidence and no fallback.

## Handoff

Any request for real identity data blocks RCA-1 Model A and requires a new SC proposal.
