# SC Decision Register

| 항목 | 값 |
|---|---|
| 계약 ID | `sc-decision-register-v1` |
| 상태 | `ACTIVE / DATA PLATFORM TECHNICAL CLOSURE` |
| 기준 main HEAD | `c528f6fb0942389b70a348cb9aa672eb7819a392` |
| 갱신일 | `2026-07-24` |

| Decision ID | 결정 | 상태 | 제약 |
|---|---|---|---|
| `SC-DP1-001` | historical DB `01..28` | APPROVED / HISTORICAL | current와 구분 |
| `SC-DP1-004` | `jc-data-contracts` | APPROVED / IMPLEMENTED | DP1~7 |
| `SC-DP1-005` | `com.jc.data.contract.v1` | APPROVED / IMPLEMENTED | current convention |
| `SC-DP1-011` | identity mapping owner/deletion | UNRESOLVED | automatic join 금지 |
| `SC-DP2-001` | `platform-event-fingerprint-sha256-v1` | APPROVED / IMPLEMENTED | exact version |
| `SC-DP2-002` | SQL `29..31` | APPROVED / IMPLEMENTED / MERGED | DP2 |
| `SC-DP2-003` | event roles; replay no execute | APPROVED | least privilege |
| `SC-DP3-001` | SQL `32..34` | APPROVED / IMPLEMENTED / MERGED | DP3 |
| `SC-DP3-002` | `data-projection-retry-v1` | APPROVED / IMPLEMENTED | scheduler disabled |
| `SC-DP45-001` | SQL `35..37` | APPROVED / IMPLEMENTED / MERGED | DP4.5 |
| `SC-DP5-001` | SQL `38..42` | APPROVED / IMPLEMENTED / MERGED | DP5 |
| `SC-DP5-003` | profile/outcome shadow-only | APPROVED / ACTIVE | no cutover |
| `SC-DP5-004` | P2 exposure authority protected | APPROVED | exact source |
| `SC-DP6-001` | SQL `43..47` | APPROVED / IMPLEMENTED / MERGED | DP6 |
| `SC-DP6-003` | `data-quality-policy-v1` | APPROVED / IMPLEMENTED | v1 immutable |
| `SC-DP6-004` | VALIDATED/REJECTED/INCONCLUSIVE | APPROVED | release 의미 없음 |
| `SC-DP7-001` | SQL `48..52` | APPROVED / IMPLEMENTED / MERGED | PR20 `c528f6fb0942389b70a348cb9aa672eb7819a392` |
| `SC-DP7-002` | integration roles | APPROVED / IMPLEMENTED | least privilege |
| `SC-DP7-003` | integration policy/fingerprints | APPROVED / IMPLEMENTED | no production authority |
| `SC-DP7-004` | Recommendation conditional compatibility | APPROVED / VALIDATED | P1/P2 authority 유지 |
| `SC-DP7-005` | Intelligence Data mapping | UNRESOLVED / INCONCLUSIVE | Intelligence owner |
| `SC-DP7-006` | Data-to-Search contract | UNRESOLVED / INCONCLUSIVE | Search owner |
| `SC-DPCLOSE-001` | DP0~7 technical closure | CANDIDATE / PR MERGE REQUIRED | exact-head CI |
| `SC-DPCLOSE-002` | SQL01..52 immutable, 53+ unallocated | APPROVED | rewrite 금지 |
| `SC-DPCLOSE-003` | closure != production approval | APPROVED | gates remain |
| `SC-DPCLOSE-004` | runtime handoff to Operations | APPROVED HANDOFF | implementation pending |
| `SC-DPCLOSE-005` | SLO/recovery handoff to Reliability | APPROVED HANDOFF | targets TBD |
| `SC-DPCLOSE-006` | separate target-track tasks | APPROVED HANDOFF | no DP8 implication |
| `SC-DPCLOSE-007` | main push workflows unavailable | RECORDED | MAIN_CI_PASS 금지 |
