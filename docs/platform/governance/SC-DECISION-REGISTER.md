# SC Decision Register

## Document identity

| Field | Value |
|---|---|
| contract ID | `sc-decision-register-v1` |
| status | `ACTIVE / SC-4 RCA-1B ENTRY AUTHORIZED` |
| authoritative main | `b2e7a5c316c6f6ee543ccedf35bca65353ab3aa4` |
| RCA-1 exact-final-head | `38896b2a37180633870282e9d9e305d9c9fbbf8a` |
| updated | `2026-07-24` |

## Historical decisions retained

| Decision ID | Decision | Status |
|---|---|---|
| `SC-DP-CLOSE-001` | Data Platform DP-0..DP-7 | COMPLETE / NOT PRODUCTION READINESS |
| `SC-DP-CLOSE-002` | SQL `01..52` | IMMUTABLE BASELINE |
| `SC-DP-CLOSE-003` | SQL `53+` | UNALLOCATED |
| `SC-RCA-001` | official workstream `RCA` | ACTIVE / CROSS-TRACK |
| `SC-RCA-003` | `RP` means Reliability Platform | PROTECTED |
| `SC-RCA-004` | RCA-0 | COMPLETE / MERGED |
| `SC-RCA1-001` | RCA-1 entry | `RCA1_ENTRY_AUTHORIZED` / SATISFIED |
| `SC-RCA1-002` | Model A | COMPLETE |
| `SC-RCA1-005` | identity mode | `SYNTHETIC_ONLY` |
| `SC-RCA1-007` | P1 lane | `RECONCILED_WITH_EXPECTED_GAPS` |
| `SC-RCA1-008` | P2 lane | `RECONCILED_WITH_MIGRATION_GAPS` |
| `SC-RCA1-019` | P1/P2 authority | PROTECTED / UNCHANGED |
| `SC-RCA1-020` | runtime/production | NOT AUTHORIZED |

RCA-0 and RCA-1 historical documents, fixtures and verification evidence remain unchanged.

## SC-4 RCA-1B entry decisions

| Decision ID | Decision | Status | Restriction |
|---|---|---|---|
| `SC-RCA1B-001` | authorize RCA-1B entry | APPROVED | implementation requires separate PR |
| `SC-RCA1B-002` | execution environment | APPROVED | `CI_EPHEMERAL_POSTGRESQL` |
| `SC-RCA1B-003` | shared non-production DB | DEFERRED | separate SC/Operations/Privacy approval |
| `SC-RCA1B-004` | production replica/derived DB | BLOCKED | production data/identity risk |
| `SC-RCA1B-005` | PostgreSQL matrix | APPROVED | `15,18`; minimum 15 |
| `SC-RCA1B-006` | dataset | APPROVED | deterministic synthetic DB fixture |
| `SC-RCA1B-007` | identity | APPROVED | synthetic only; actual mapping blocked |
| `SC-RCA1B-008` | transaction | APPROVED | repeatable-read, explicit read-only |
| `SC-RCA1B-009` | timeouts/limits | APPROVED | finite values mandatory |
| `SC-RCA1B-010` | retry/parallel query | APPROVED | none/disabled |
| `SC-RCA1B-011` | query allowlist/fingerprint | APPROVED | prepared, bounded, ordered |
| `SC-RCA1B-012` | ephemeral test role | CONDITIONALLY_APPROVED | least privilege; no owner/superuser |
| `SC-RCA1B-013` | canonical SQL allocation | NOT_REQUIRED | SQL `53+` remains unallocated |
| `SC-RCA1B-014` | test seed/role SQL | CONDITIONALLY_APPROVED | noncanonical test-only |
| `SC-RCA1B-015` | P1 DB lane | CONDITIONALLY_APPROVED | expected gaps preserved |
| `SC-RCA1B-016` | P2 DB lane | CONDITIONALLY_APPROVED | exposure/window/event/fallback protected |
| `SC-RCA1B-017` | checkpoint equality | APPROVED | zero-lag deterministic fixture |
| `SC-RCA1B-018` | lineage | APPROVED | fingerprint required; mismatch fails |
| `SC-RCA1B-019` | evidence/redaction | APPROVED | no raw rows/IDs/credentials; 90 days |
| `SC-RCA1B-020` | Operations approval | BLOCKING_APPROVAL | environment/credential/resource |
| `SC-RCA1B-021` | Reliability approval | BLOCKING_APPROVAL | P2/evidence integrity |
| `SC-RCA1B-022` | Intelligence approval | BLOCKING_APPROVAL | P1 query/exit |
| `SC-RCA1B-023` | Privacy/Security approval | BLOCKING_APPROVAL | identity/redaction/retention |
| `SC-RCA1B-024` | runtime dark read | NOT AUTHORIZED | RCA-2 only |
| `SC-RCA1B-025` | authority transfer | FORBIDDEN | current sources unchanged |
| `SC-RCA1B-026` | RCA-1B exit | APPROVED DEFINITION | lane-separated exact-head evidence |

## Explicit field decisions

```text
RCA1B_ENTRY_AUTHORIZED
RCA1B_EXECUTION_ENVIRONMENT=CI_EPHEMERAL_POSTGRESQL
POSTGRESQL_VERSION_MATRIX=15,18
RCA1B_DATASET_MODE=DETERMINISTIC_SYNTHETIC_DATABASE_FIXTURE
IDENTITY_MODE=SYNTHETIC_ONLY
TRANSACTION_READ_ONLY=REQUIRED
DB_WRITE=FORBIDDEN
PRODUCTION_DB=FORBIDDEN
DB_CHANGE=NONE
SQL_ALLOCATION=NOT_REQUIRED
RUNTIME_WIRING=NOT_AUTHORIZED
PRODUCTION_IMPACT=NONE
PRODUCTION_ACTIVATION=NOT_AUTHORIZED
CURRENT_P1_P2_AUTHORITY_UNCHANGED
IMPLEMENTATION_REQUIRES_SEPARATE_PR
```

## Deferred and blocked work

- Environment B persistent shared non-production database;
- pseudonymized or actual identity governance;
- nonzero live checkpoint lag;
- persistent role/grant or canonical DB object;
- production DB/replica/derived extract;
- runtime dark read, feature flags, traffic, canary, monitoring and production credentials;
- source replacement, authority transfer and automatic RCA-2 entry.
