# SC Decision Register

## 문서 정보

| 항목 | 값 |
|---|---|
| 계약 ID | `sc-decision-register-v1` |
| canonical path | `docs/platform/governance/SC-DECISION-REGISTER.md` |
| 상태 | `ACTIVE` |
| 기준 main HEAD | `f38cf56b34ff23fbd5cb20b9013444a8cb2d29f4` |
| 갱신일 | `2026-07-21` |

| Decision ID | 결정 | 상태 | 근거/제약 |
|---|---|---|---|
| `SC-DP1-001` | canonical DB는 `journey-connect-db-v2.7/01..28` | APPROVED | actual SQL 27/28 존재 |
| `SC-DP1-002` | authoritative sequence는 IP 종결 → DP → OP → RP → cross-track validation | APPROVED | 현재 실행 계획 |
| `SC-DP1-003` | 과거 DP-1/IP-1 병렬 권고는 historical recommendation | APPROVED | 현재 sequence를 덮어쓰지 않음 |
| `SC-DP1-004` | module `jc-data-contracts` 예약 | APPROVED / NOT IMPLEMENTED | DP-1에서 생성 |
| `SC-DP1-005` | package `com.jc.data.contract` 예약 | APPROVED / NOT IMPLEMENTED | DP-1에서 생성 |
| `SC-DP1-006` | producerVersion/consumerVersion/producerBuildId 의미 분리 | APPROVED | System Contract |
| `SC-DP1-007` | SQL 27 Search projection, Operations eligibility ownership | APPROVED | actual SQL |
| `SC-DP1-008` | DP-2 SQL은 28 이후 SC가 별도 배정 | APPROVED | sequence collision 방지 |
| `SC-DP1-009` | 신규 Data fingerprint algorithm/encoding/exact field set | SC DECISION REQUIRED / DP-1 FINGERPRINT BLOCKER | 원 계약에서 미확정 |
| `SC-DP1-010` | PR #3 `aaea95946133f518996b7e57c7f5a657e8f161b9`는 merge commit `f38cf56b34ff23fbd5cb20b9013444a8cb2d29f4`로 main authority가 됨 | APPROVED / MERGED | traffic activation은 별도 승인 필요 |
| `SC-DP1-011` | identity mapping physical owner/deletion policy | UNRESOLVED | owner 임의 배정 금지 |
| `SC-DP1-012` | Decision Register canonical path | APPROVED | 이 문서 |
| `SC-DP1-013` | RACI canonical path | APPROVED | `docs/platform/governance/SC-RACI.md` |
| `SC-DP1-014` | DP-1 start SHA는 PR #3+SC PR 모두 포함한 최초 main HEAD | APPROVED | PR #4 merge 전 exact SHA 미확정 |
