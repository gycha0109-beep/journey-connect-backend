# SC RACI

## Document identity

| Field | Value |
|---|---|
| contract ID | `sc-raci-v1` |
| status | `ACTIVE / SC-3 RCA-1 ALIGNED` |
| authoritative main | `f802a105e46a62718616acaa7a3db6c172e7ed10` |
| RCA-0 exact-final-head | `d33f7e152d0e40999ed8dc3f16c0a3f0bb980a9d` |

| Area | Responsible | Accountable | Consulted | Informed |
|---|---|---|---|---|
| Data candidate contracts/checkpoint/lineage | Data | Data | Intelligence/Reliability/SC | Operations |
| P1 comparison implementation | Intelligence | Intelligence | Data/SC | Reliability/Operations |
| P1 expected-gap interpretation and acceptance | Intelligence | Intelligence | Data/SC | Reliability |
| P2 comparison implementation | Intelligence lead permitted | Reliability | Data/SC | Operations |
| P2 exposure/window/event/fallback acceptance | Reliability | Reliability | Intelligence/Data/SC | Operations |
| P2 dedupe/hash/release protection | Reliability | Reliability | SC/Intelligence | Operations |
| RCA-1 evidence taxonomy and integrity | Intelligence + Reliability | SC | Data/Privacy | Operations |
| synthetic fixture identity | implementation team | SC | Data/Privacy | Operations |
| real identity mapping | `UNRESOLVED OWNER` | SC | Data/Intelligence/Reliability/Privacy/Security | Operations |
| phase entry/exit and registry | SC | SC | all tracks | team |
| Model A execution environment | implementation CI | SC | Operations | team |
| Model B read-only environment | Operations | Operations + SC | Data/Intelligence/Reliability/Security | team |
| Model C runtime dark read | Operations + Intelligence | SC | Reliability/Security/Privacy | team |
| production release/rollback | Reliability + Operations | SC | Intelligence/Security/Privacy | team |

## RCA-1 responsibility rules

- P1 and P2 produce separate verdicts.
- Intelligence may lead shared pure Java implementation but cannot approve P2 semantics.
- Reliability approval is required for P2 mismatch acceptance and evidence integrity.
- Operations is not an execution prerequisite for Model A; it remains consulted for production-control protection.
- a real identity mapping owner is not assigned by this phase.
- System Coordination controls entry, exit, breaking changes and authority transfer.

## Restrictions

- `UNRESOLVED OWNER` must not be silently assigned.
- physical location does not transfer semantic authority.
- Data candidate projections remain non-authoritative.
- RCA is a workstream, not a platform.
- `RP` means Reliability Platform.
- no DB, runtime, production or authority-transfer responsibility is allocated to RCA-1 Model A.
