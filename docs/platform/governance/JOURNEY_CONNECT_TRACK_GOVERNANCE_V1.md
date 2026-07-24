# Journey Connect Track Governance V1

## 1. Document identity

| Field | Value |
|---|---|
| revision | `V1.5 / SC-4 RCA-1B ENTRY` |
| status | `ACTIVE / RCA1_COMPLETE / RCA1B_ENTRY_AUTHORIZED` |
| authoritative main | `b2e7a5c316c6f6ee543ccedf35bca65353ab3aa4` |
| RCA-1 exact-final-head | `38896b2a37180633870282e9d9e305d9c9fbbf8a` |
| canonical DB | `journey-connect-db-v2.7/01..52` |
| SQL `53+` | `UNALLOCATED` |

## 2. Track responsibilities

### Data Platform

Owns candidate projection schema, checkpoint, lineage and reproducible fixture interpretation. Data does not approve P1/P2 semantic authority transfer.

### Intelligence Platform

Owns P1 reference query, exact/derived/window semantics, expected gaps and P1 DB reconciliation exit.

### Reliability Platform

Owns P2 exposure/outcome/metric semantics, dedupe/hash/release protection, P2 query acceptance and evidence integrity. `RP` is reserved for Reliability Platform.

### Operations Platform

Owns CI ephemeral PostgreSQL, credentials, network isolation, read-only role, timeout/resource limits, teardown and artifact lifecycle. Operations is a blocking approval for RCA-1B.

### Privacy/Security

Owns synthetic-only identity, redaction, secret handling, raw-row prohibition and retention. Approval is blocking.

### System Coordination

Owns phase entry/exit, registry, breaking changes, SQL allocation and prohibition of authority transfer.

## 3. Authoritative sequence

```text
Data Platform technical closure [COMPLETE]
→ RCA-0 Recommendation Data Consumer Contract & Fixture Alignment [COMPLETE / MERGED]
→ RCA-1 Recommendation Data Shadow Reconciliation [COMPLETE / MODEL A]
→ RCA-1B Recommendation Data Non-production Read-only Reconciliation [ENTRY AUTHORIZED]
→ RCA-2 Controlled Runtime Dark Read [NOT AUTHORIZED]
```

This is not a production release plan.

## 4. Workstream naming

`RCA` means Recommendation Consumer Adoption and is a cross-track workstream. `RP` remains reserved for Reliability Platform and must not mean Recommendation Platform. Classification: `JOINT_INTELLIGENCE_RELIABILITY_ADOPTION`.

## 5. RCA-1 historical completion

```text
RCA1_OFFLINE_SHADOW_RECONCILIATION_COMPLETE
RCA1_EXECUTION_MODEL=MODEL_A_OFFLINE_DETERMINISTIC_RECONCILIATION
P1_RESULT=RECONCILED_WITH_EXPECTED_GAPS
P2_RESULT=RECONCILED_WITH_MIGRATION_GAPS
IDENTITY_MODE=SYNTHETIC_ONLY
```

Historical RCA-0/RCA-1 implementation, fixtures and evidence remain unchanged.

## 6. RCA-1B execution model

```text
RCA1B_ENTRY_AUTHORIZED
RCA1B_EXECUTION_ENVIRONMENT=CI_EPHEMERAL_POSTGRESQL
POSTGRESQL_VERSION_MATRIX=15,18
RCA1B_DATASET_MODE=DETERMINISTIC_SYNTHETIC_DATABASE_FIXTURE
IDENTITY_MODE=SYNTHETIC_ONLY
TRANSACTION_READ_ONLY=REQUIRED
DB_WRITE=FORBIDDEN
PRODUCTION_DB=FORBIDDEN
```

Environment B is deferred. Production replica/derived Environment C is blocked. RCA-2 remains separately gated.

## 7. RCA-1B ownership

| Boundary | Responsible | Accountable | Approval |
|---|---|---|---|
| P1 query and result | Intelligence | Intelligence | `BLOCKING_APPROVAL` |
| P2 query and result | Reliability/shared implementation permitted | Reliability | `BLOCKING_APPROVAL` |
| candidate/checkpoint/lineage | Data | Data | `REQUIRED` |
| environment/credentials/resource | Operations | Operations | `BLOCKING_APPROVAL` |
| identity/redaction/retention | Privacy/Security | Privacy/Security | `BLOCKING_APPROVAL` |
| entry/exit/registry/SQL/authority | SC | SC | `BLOCKING_APPROVAL` |

Physical code location does not transfer semantic authority.

## 8. Read-only and resource governance

```text
TRANSACTION_ISOLATION=REPEATABLE_READ
AUTOCOMMIT=DISABLED
STATEMENT_TIMEOUT_MS=5000
LOCK_TIMEOUT_MS=1000
IDLE_IN_TRANSACTION_TIMEOUT_MS=5000
MAX_RESULT_ROWS_PER_QUERY=1000
MAX_RECONCILIATION_CASES=10000
MAX_EXECUTION_DURATION_SECONDS=900
PARALLEL_QUERY=DISABLED
RETRY_POLICY=NONE
```

A separate ephemeral test login is required. Owner/superuser use, write grant, RLS bypass, DDL/DML, temp objects, migration and unbounded queries are prohibited.

## 9. Query governance

Only explicit version-controlled query IDs and SHA-256 fingerprints may execute. Queries must be prepared, parameterized, bounded and deterministically ordered. Production data, actual identity mapping, canonical dataset rows, release evidence and unrestricted raw rows are outside the allowlist.

## 10. Dataset and identity governance

Use canonical SQL `01..52` in an ephemeral container plus deterministic noncanonical test-only seed. Dataset B is deferred; production-derived data is blocked. Identity remains synthetic-only with fail-closed absence, invalidity, expiry, deletion, mismatch, unauthorized purpose and unauthorized caller.

## 11. Lane separation

P1 and P2 produce independent DB verdicts and mismatch inventories. P1 expected semantic gaps remain explicit. P2 exact exposure/window/event/fallback protection remains mandatory; stale assignment and persisted dedupe remain migration-required. A combined PASS cannot hide a lane failure.

## 12. Checkpoint, lineage and evidence

Exact parity requires equal checkpoint, equal snapshot time and matching lineage. Deterministic fixture lag is zero. Evidence is redacted, query-fingerprinted and retained at most 90 days; database/raw results live only for the job.

## 13. DB and SQL governance

```text
DB_CHANGE=NONE
SQL_ALLOCATION=NOT_REQUIRED
NEW_TABLE_REQUIRED=NO
NEW_VIEW_REQUIRED=NO
NEW_ROLE_REQUIRED=YES_EPHEMERAL_TEST_ONLY
NEW_GRANT_REQUIRED=YES_EPHEMERAL_TEST_ONLY
TEST_FIXTURE_SQL_REQUIRED=YES_NONCANONICAL_TEST_ONLY
SQL_53_PLUS=UNALLOCATED
```

No canonical migration is authorized.

## 14. Entry and exit

Entry verdict:

```text
RCA1B_ENTRY_AUTHORIZED
IMPLEMENTATION_REQUIRES_SEPARATE_PR
```

Exit requires independent P1/P2 database results, read-only/checkpoint/lineage/identity enforcement, cross-version equivalence, all blocking approvals, unchanged authority and no production DB/traffic.

## 15. Integration refusal

Reject any implementation that uses production endpoints/data, owner/superuser, write privilege, dynamic/unbounded SQL, actual identity mapping, canonical dataset/release access, SQL `53+`, runtime wiring, combined lane masking or authority-transfer language.

## 16. Canonical governance paths

- [System Contract](JOURNEY_CONNECT_SYSTEM_CONTRACT_V1.md)
- [Decision Register](SC-DECISION-REGISTER.md)
- [Platform Registry](SC-PLATFORM-REGISTRY.md)
- [RACI](SC-RACI.md)
- [SC Handoff](SC-HANDOFF.md)
- [SC-4 master](sc-next-track/SC-4-RCA-1B-ENTRY-AUTHORIZATION-AND-EXECUTION-BOUNDARY.md)
- [RCA-1B implementation prompt](sc-next-track/37-RCA-1B-IMPLEMENTATION-HANDOFF-PROMPT.md)
