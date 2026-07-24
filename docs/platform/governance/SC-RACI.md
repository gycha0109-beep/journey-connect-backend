# SC RACI

## Document identity

| Field | Value |
|---|---|
| contract ID | `sc-raci-v1` |
| status | `ACTIVE / SC-4 RCA-1B ALIGNED` |
| authoritative main | `b2e7a5c316c6f6ee543ccedf35bca65353ab3aa4` |
| RCA-1 exact-final-head | `38896b2a37180633870282e9d9e305d9c9fbbf8a` |

## RCA-1B RACI

| Area | Responsible | Accountable | Consulted | Informed | Approval state |
|---|---|---|---|---|---|
| P1 authoritative/candidate query and dimensions | Intelligence | Intelligence | Data/SC | Reliability/Operations | `BLOCKING_APPROVAL` |
| P1 expected-gap interpretation and exit | Intelligence | Intelligence | Data/SC | Reliability | `BLOCKING_APPROVAL` |
| P2 exposure/window/event/fallback query | Reliability/shared implementation permitted | Reliability | Intelligence/Data/SC | Operations | `BLOCKING_APPROVAL` |
| P2 migration-gap acceptance and evidence integrity | Reliability | Reliability | Intelligence/Data/SC | Operations/Privacy | `BLOCKING_APPROVAL` |
| candidate object/checkpoint/lineage inventory | Data | Data | Intelligence/Reliability/SC | Operations | `REQUIRED` |
| deterministic synthetic seed interpretation | Data + lane owners | Data | Operations/Privacy/SC | team | `REQUIRED` |
| CI PostgreSQL 15/18 environment | Operations | Operations | Data/SC | team | `BLOCKING_APPROVAL` |
| credential/network/read-only role boundary | Operations | Operations | Security/SC | lane owners | `BLOCKING_APPROVAL` |
| timeout/row/resource/teardown | Operations | Operations | SC | team | `BLOCKING_APPROVAL` |
| synthetic identity and raw-data prohibition | Privacy/Security | Privacy/Security | SC/lane owners | Operations | `BLOCKING_APPROVAL` |
| evidence redaction and retention | Privacy/Security + Reliability | Privacy/Security | Operations/SC | team | `BLOCKING_APPROVAL` |
| query registry, phase entry/exit and SQL allocation | SC | SC | all tracks | team | `BLOCKING_APPROVAL` |
| RCA-2 runtime dark read | NOT ALLOCATED IN RCA-1B | SC | Operations/Reliability/Privacy | team | `NOT_AUTHORIZED` |
| production release/rollback | NOT ALLOCATED IN RCA-1B | SC | all tracks | team | `NOT_AUTHORIZED` |

## Responsibility rules

- P1 and P2 produce separate verdicts and mismatch inventories.
- Intelligence cannot approve P2 semantics; Reliability cannot transfer P1 authority.
- Operations approval is mandatory because RCA-1B introduces DB credentials and execution, despite being ephemeral and non-production.
- Privacy/Security approval is mandatory before any DB artifact is accepted.
- Data candidate projections remain non-authoritative.
- System Coordination controls entry, exit, breaking changes, SQL allocation and authority transfer.
- Physical implementation location does not transfer semantic ownership.

## Environment and DB boundary

```text
RCA1B_EXECUTION_ENVIRONMENT=CI_EPHEMERAL_POSTGRESQL
POSTGRESQL_VERSION_MATRIX=15,18
TRANSACTION_READ_ONLY=REQUIRED
PRODUCTION_DB=FORBIDDEN
DB_WRITE=FORBIDDEN
```

The bootstrap owner is not the reconciliation identity. `rca1b_readonly` is ephemeral test-only and has explicit least-privilege grants. Persistent roles/grants are not allocated.

## Identity and evidence boundary

```text
IDENTITY_MODE=SYNTHETIC_ONLY
RAW_RESULT_RETENTION=NONE
CI_EVIDENCE_RETENTION_DAYS=90
```

Actual identity owner remains unresolved and is not silently assigned.

## Restrictions

- `RP` means Reliability Platform and is reserved for Reliability Platform.
- RCA is a workstream, not a platform.
- no production DB, runtime, traffic, actual identity mapping, canonical SQL or authority-transfer responsibility is allocated by SC-4.
- no blocking approval is inferred from SC-4 documentation; the RCA-1B implementation PR must collect exact-head evidence.
