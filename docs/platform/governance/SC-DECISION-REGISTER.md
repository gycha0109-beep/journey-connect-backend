# SC Decision Register

## Document identity

| Field | Value |
|---|---|
| contract ID | `sc-decision-register-v1` |
| status | `ACTIVE / SC-3 RCA-1 ENTRY AUTHORIZED` |
| authoritative main | `f802a105e46a62718616acaa7a3db6c172e7ed10` |
| RCA-0 exact-final-head | `d33f7e152d0e40999ed8dc3f16c0a3f0bb980a9d` |
| updated | `2026-07-24` |

## Historical decisions retained

| Decision ID | Decision | Status | Restriction |
|---|---|---|---|
| `SC-DP-CLOSE-001` | DP-0 through DP-7 technical roadmap | COMPLETE | not production readiness |
| `SC-DP-CLOSE-002` | SQL `01..52` | IMMUTABLE BASELINE | historical rewrite prohibited |
| `SC-DP-CLOSE-003` | SQL `53+` | UNALLOCATED | SC assignment required |
| `SC-DP-CLOSE-004` | production activation | NOT_AUTHORIZED | gates independent |
| `SC-RCA-001` | official workstream `RCA` | APPROVED / ACTIVE | cross-track, not platform |
| `SC-RCA-002` | classification `JOINT_INTELLIGENCE_RELIABILITY_ADOPTION` | APPROVED / ACTIVE | P1 Intelligence, P2 Reliability |
| `SC-RCA-003` | `RP` means Reliability Platform | APPROVED / PROTECTED | Recommendation Platform prohibited |
| `SC-RCA-004` | RCA-0 phase | COMPLETE / MERGED | PR #23 |
| `SC-RCA-006` | RCA-1 separately gated | SATISFIED BY SC-3 | this decision |
| `SC-RCA-007` | P1 authority unchanged | APPROVED / PROTECTED | no source replacement |
| `SC-RCA-008` | P2 authority unchanged | APPROVED / PROTECTED | no metric/dataset/release change |
| `SC-RCA-009` | RCA-0 identity synthetic/port only | COMPLETE | no real mapping |
| `SC-RCA-010` | RCA-0 DB | NONE | no SQL |
| `SC-RCA-011` | RCA-0 production impact | NONE / NOT_AUTHORIZED | gates unchanged |

Historical RCA-0 documents, fixtures and verification evidence remain unchanged.

## SC-3 RCA-1 entry decisions

| Decision ID | Decision | Status | Basis / restriction |
|---|---|---|---|
| `SC-RCA1-001` | authorize `RCA-1 Recommendation Data Shadow Reconciliation` | APPROVED | implementation requires separate PR |
| `SC-RCA1-002` | official execution model | APPROVED | `MODEL_A_OFFLINE_DETERMINISTIC_RECONCILIATION` |
| `SC-RCA1-003` | Model B non-production DB read | DEFERRED | separate SC + Operations approval |
| `SC-RCA1-004` | Model C runtime dark read | BLOCKED IN RCA-1 | RCA-2 candidate |
| `SC-RCA1-005` | identity mode | APPROVED | `SYNTHETIC_ONLY` |
| `SC-RCA1-006` | real identity mapping governance | DEFERRED / BLOCKED | physical owner and controls unresolved |
| `SC-RCA1-007` | P1 lane entry | CONDITIONALLY_APPROVED | expected gaps must remain explicit |
| `SC-RCA1-008` | P2 lane entry | CONDITIONALLY_APPROVED | exact exposure/window/event/fallback protection |
| `SC-RCA1-009` | aggregate-to-event reconstruction | BLOCKED | structurally invalid |
| `SC-RCA1-010` | P1 deterministic exact/derived tolerance | APPROVED | zero mismatch in Model A |
| `SC-RCA1-011` | P2 stale assignment and persisted dedupe | MIGRATION_REQUIRED | detection only |
| `SC-RCA1-012` | canonical P2 dataset bytes/hash | PROTECTED | do not recalculate |
| `SC-RCA1-013` | release evidence | PROTECTED | non-modification target only |
| `SC-RCA1-014` | evidence/privacy policy | APPROVED | synthetic/redacted; 90-day generated artifact limit |
| `SC-RCA1-015` | offline counters | APPROVED | verification summary, not SLO |
| `SC-RCA1-016` | DB/SQL impact | NOT_REQUIRED | no object, role or grant |
| `SC-RCA1-017` | Operations prerequisite for Model A | NOT_REQUIRED / CONSULTED | production diff protection only |
| `SC-RCA1-018` | Reliability prerequisite | REQUIRED | P2 semantics and acceptance |
| `SC-RCA1-019` | current P1/P2 authority | PROTECTED / UNCHANGED | no transfer |
| `SC-RCA1-020` | runtime wiring and production activation | NOT_AUTHORIZED | unchanged gates |
| `SC-RCA1-021` | RCA-1 exit criteria | APPROVED | lane-separated evidence |
| `SC-RCA1-022` | implementation PR separation | REQUIRED | no implementation in SC-3 |

## Explicit field decisions

```text
RCA1_ENTRY=AUTHORIZED
RCA1_EXECUTION_MODEL=MODEL_A_OFFLINE_DETERMINISTIC_RECONCILIATION
IDENTITY_MODE=SYNTHETIC_ONLY
DB_CHANGE=NONE
SQL_ALLOCATION=NOT_REQUIRED
RUNTIME_WIRING=NOT_AUTHORIZED
PRODUCTION_IMPACT=NONE
PRODUCTION_ACTIVATION=NOT_AUTHORIZED
CURRENT_P1_P2_AUTHORITY_UNCHANGED
IMPLEMENTATION_REQUIRES_SEPARATE_PR
```

## Non-decisions and deferred work

- physical production identity mapping owner, storage, encryption, deletion and runtime audit;
- non-production DB role and environment for Model B;
- runtime timeout/fallback/feature flag/dashboard/traffic for Model C;
- authority transfer or source deprecation;
- SQL `53+` allocation.

## Current decision

```text
RCA1_ENTRY_AUTHORIZED
RCA1_EXECUTION_MODEL=MODEL_A_OFFLINE_DETERMINISTIC_RECONCILIATION
IDENTITY_MODE=SYNTHETIC_ONLY
DB_CHANGE=NONE
SQL_ALLOCATION=NOT_REQUIRED
RUNTIME_WIRING=NOT_AUTHORIZED
PRODUCTION_IMPACT=NONE
PRODUCTION_ACTIVATION=NOT_AUTHORIZED
CURRENT_P1_P2_AUTHORITY_UNCHANGED
IMPLEMENTATION_REQUIRES_SEPARATE_PR
```
