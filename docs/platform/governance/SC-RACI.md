# SC RACI

## 문서 정보

| 항목 | 값 |
|---|---|
| 계약 ID | `sc-raci-v1` |
| canonical path | `docs/platform/governance/SC-RACI.md` |
| 상태 | `ACTIVE` |

| 영역 | Responsible | Accountable | Consulted | Informed |
|---|---|---|---|---|
| Data contract | Data | SC | IP/RP/OP | Backend |
| DB sequence | SC | SC | Data/IP/OP/RP | Backend |
| Search projection | IP/Search | SC | Operations/Data | Backend |
| Operational eligibility | Operations | SC | Search/Data | Backend |
| P2 experiment authority | Reliability | SC | Intelligence/Data | Operations |
| Identity mapping | `UNRESOLVED OWNER` | SC | Data/IP/RP/Privacy | Operations |
| DP-1 contract module | Data | SC | IP/RP/OP/Backend | Team |
| production pilot approval | Operations/Reliability | SC | IP/Security/Privacy | Team |

## 제한

- `UNRESOLVED OWNER`를 Data, IP, RP 또는 Operations로 임의 배정하지 않는다.
- physical writer와 semantic owner가 다르면 SC compatibility arrangement가 필요하다.
- Data는 Search projection, operational eligibility, P2 experiment evidence에 direct write하지 않는다.
