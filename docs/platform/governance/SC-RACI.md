# SC RACI

## Document identity

| Field | Value |
|---|---|
| contract ID | `sc-raci-v1` |
| status | `ACTIVE / SC-2 RCA-0 ALIGNED` |
| authoritative main | `95dad33fd56a54d69e2497c11dc4e2e77d8d3a77` |

| Area | Responsible | Accountable | Consulted | Informed |
|---|---|---|---|---|
| Data contract and canonical evidence | Data | SC | Intelligence/Reliability/Operations | Backend |
| DB sequence | SC | SC | Data/Intelligence/Operations/Reliability | Backend |
| Search projection | Intelligence/Search | SC | Operations/Data | Backend |
| operational eligibility | Operations | SC | Search/Data | Backend |
| P1 profile consumer meaning | Intelligence | Intelligence | Data/SC | Reliability/Operations |
| P2 experiment authority | Reliability | SC | Intelligence/Data | Operations |
| RCA-0 shared fixture implementation | Intelligence lead | SC | Reliability for P2, Data | Operations |
| RCA-0 P2 semantic fixture approval | Reliability | Reliability | Intelligence/Data/SC | Operations |
| identity mapping | `UNRESOLVED OWNER` | SC | Data/Intelligence/Reliability/Privacy/Security | Operations |
| Operations runtime execution | Operations | Operations | Data/Intelligence/Reliability/SC | Team |
| production release/rollback | Reliability + Operations | SC | Intelligence/Security/Privacy | Team |

## Restrictions

- `UNRESOLVED OWNER` must not be silently assigned to Data, Intelligence, Reliability or Operations.
- physical writer and semantic owner may differ only under an SC-approved compatibility arrangement.
- Data must not write Search projection, operational eligibility or P2 experiment evidence.
- RCA is a workstream, not a platform.
- `RP` means Reliability Platform.
- RCA-0 has no DB, runtime, production or authority-transfer responsibility.
