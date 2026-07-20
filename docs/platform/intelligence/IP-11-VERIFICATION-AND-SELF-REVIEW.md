# IP-11 Verification and Self Review

## 문서 정보

| 항목 | 값 |
|---|---|
| 계약 ID | `ip-11-verification-self-review-v1` |
| 상태 | `DOCUMENT_VERIFICATION_COMPLETE` |

## 1. Verification scope

IP-11 is document-only. Production Java/Kotlin, Gradle build logic, resources, SQL and Recommendation source are not modified.

Verification checks:

- 10 required IP-11 documents and README links
- 12 unique decision IDs and allowed statuses
- RACI roles and Go/No-Go gates complete
- blockers aligned with final NO_GO
- no production source/config/build/SQL delta
- protected 320 paths and canonical SQL 01..26 exact
- document/file/ZIP manifests and re-extract verification
- top-level folder `JC-IP-11-5-Final` (40 chars 이하), all ZIP paths 240 chars 이하, non-ASCII/garbled path zero
- two garbled document names normalized with content SHA preservation and rename map

## 2. Self review 1 — responsibility and approval

Found and fixed:

1. Activation and rollback authority were initially too close.
   - Product/Operations joint Go, Operations rollback judgment, Release Operator execution were separated.
2. Named assignee absence could be hidden by role names.
   - Every operational role now has an explicit `UNASSIGNED / OPEN_BLOCKER` register.
3. Approval expiry and cancellation were missing.
   - 30-day/source-change expiry and veto/reapproval triggers were added.

## 3. Self review 2 — technical and operational realism

Found and fixed:

1. IP-10 synthetic catalog could be mistaken for a production source.
   - It is explicitly rejected as authoritative; versioned Search projection is preferred but open.
2. Test/stage resource values could be copied as production budget.
   - Values are separated; production proposals are provisional and effective execution remains zero.
3. Proposed observability tools could look present.
   - Only SLF4J/general logging and memory-only port are marked present; metrics/dashboard/tracing are open.

## 4. Self review 3 — security/privacy/Go-No-Go

Found and fixed:

1. Stable query hash was initially treated as harmless.
   - Unsalted stable hash is now approval-required due to dictionary risk; raw/normalized query remains prohibited.
2. Internal cohort could create identity leakage.
   - Cohort allowlist ownership and no-raw-ID evidence requirements were added.
3. Conditional role decisions could accidentally produce GO.
   - Matrix rule now forces NO_GO when owner/source/security/observability/attestation is incomplete.

## 5. Result

```text
Document/static verification: PASS
Production source/config/build/SQL change: NONE
External Gradle/Spring/PostgreSQL attestation: NOT EXECUTED
IP-11 decision: GOVERNANCE_DOCUMENTED / CONTROL_BLOCKERS_OPEN
IP-12: HOLD
```

Detailed machine-readable evidence is under `verification/ip11/`.

## IP-11.75 approval amendment

See [IP-11.75 Verification and Self Review](IP-11-75-VERIFICATION-AND-SELF-REVIEW.md). Governance statuses were reclassified without production source/config/build/SQL changes.
