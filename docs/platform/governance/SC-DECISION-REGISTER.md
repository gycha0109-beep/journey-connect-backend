# SC Decision Register

## 문서 정보

| 항목 | 값 |
|---|---|
| 계약 ID | `sc-decision-register-v1` |
| canonical path | `docs/platform/governance/SC-DECISION-REGISTER.md` |
| 상태 | `ACTIVE` |
| 기준 main HEAD | `67a9b7515dbfd41360160c8059ac387e74cbdf6b` |
| 갱신일 | `2026-07-22` |

| Decision ID | 결정 | 상태 | 근거/제약 |
|---|---|---|---|
| `SC-DP1-001` | canonical DB는 `journey-connect-db-v2.7/01..28` | APPROVED | actual SQL 27/28 존재 |
| `SC-DP1-002` | authoritative sequence는 IP 종결 → DP → OP → RP → cross-track validation | APPROVED | 현재 실행 계획 |
| `SC-DP1-003` | 과거 DP-1/IP-1 병렬 권고는 historical recommendation | APPROVED | 현재 sequence를 덮어쓰지 않음 |
| `SC-DP1-004` | module `jc-data-contracts` | APPROVED / IMPLEMENTED | PR #6 merge commit `bdce7de5ef6be31f8da6a8a349424be8f06a87a1` |
| `SC-DP1-005` | package root `com.jc.data.contract.v1` | APPROVED / IMPLEMENTED | DP-1 actual package convention |
| `SC-DP1-006` | producerVersion/consumerVersion/producerBuildId 의미 분리 | APPROVED | System Contract |
| `SC-DP1-007` | SQL 27 Search projection, Operations eligibility ownership | APPROVED | actual SQL |
| `SC-DP1-008` | DP-2 SQL은 28 이후 SC가 별도 배정 | SUPERSEDED BY SC-DP2-002 | sequence collision 방지 |
| `SC-DP1-009` | 신규 Data fingerprint exact contract | APPROVED / RESOLVED | `SC-DP2-001` |
| `SC-DP1-010` | PR #3 merge commit `f38cf56b34ff23fbd5cb20b9013444a8cb2d29f4`는 main authority | APPROVED / MERGED | traffic activation은 별도 승인 필요 |
| `SC-DP1-011` | identity mapping physical owner/deletion policy | UNRESOLVED | 자동 join 및 mapping repository 구현 금지 |
| `SC-DP1-012` | Decision Register canonical path | APPROVED | 이 문서 |
| `SC-DP1-013` | RACI canonical path | APPROVED | `docs/platform/governance/SC-RACI.md` |
| `SC-DP1-014` | 공식 DP-1 Baseline SHA | APPROVED | `9d84f630e87d54f780e332eead0c1f8df6a51d0b` |
| `SC-DP2-001` | fingerprint `platform-event-fingerprint-sha256-v1`, SHA-256/lowercase hex 64자 | APPROVED / IMPLEMENTED | DP-2 PR #8 |
| `SC-DP2-002` | SQL `29..31`을 event store/idempotency/verification에 배정 | APPROVED / IMPLEMENTED | PR #8 merge `0ff67aaf9a86b61be2b41c431a570a9f0d460f7c` |
| `SC-DP2-003` | `jc_data_event_writer`, `jc_data_event_reader`, `jc_data_replay_executor` | APPROVED / IMPLEMENTED OR RESERVED | replay execute는 미부여 |
| `SC-DP2-004` | idempotency 30일, attempt/conflict/quarantine 90일 | APPROVED / TECHNICAL BASELINE | 자동 purge 비활성 |
| `SC-DP2-005` | canonical event 기본 365일 retention metadata | APPROVED / TECHNICAL BASELINE | 물리 삭제 실행 금지 |
| `SC-DP2-006` | DP-2는 identity mapping, runtime API, projection cutover 제외 | APPROVED | 보호 경계 |
| `SC-DP3-001` | SQL `32..34` retry/quarantine/processing/validation | APPROVED / IMPLEMENTED | SQL 01..31 보호 |
| `SC-DP3-002` | retry policy `data-projection-retry-v1`: initial 1회 + 최대 5 retries | APPROVED / IMPLEMENTED | production scheduler 비활성 |
| `SC-DP3-003` | validation/privacy/integrity failure fail-closed quarantine | APPROVED / IMPLEMENTED | automatic retry 금지 |
| `SC-DP3-004` | processor/reviewer role 및 replay no-execute | APPROVED / IMPLEMENTED | 최소권한 |
| `SC-DP3-005` | lease 60초, heartbeat 20초, batch 최대 100 | APPROVED / IMPLEMENTED | atomic claim/reclaim |
| `SC-DP3-006` | retry/quarantine/review evidence 90일 metadata | APPROVED / TECHNICAL BASELINE | purge 비활성 |
| `SC-DP3-007` | production scheduler/alert/replay 실행 비활성 | APPROVED | 운영 gate 별도 |
| `SC-DP45-001` | SQL `35..37` adapter shadow evidence persistence | APPROVED / IMPLEMENTED | PR #14 merge `de4e9f308130e10948edb69ceb1b2bba0eebcd2e` |
| `SC-DP45-002` | adapter evidence writer/reader/function owner | APPROVED / IMPLEMENTED | execute-only/safe-view-only/NOLOGIN |
| `SC-DP45-003` | DP-4.5는 DP-5 기술 선행 조건 | APPROVED / SATISFIED | PostgreSQL 15/18 및 protected regression PASS |
| `SC-DP5-001` | SQL `38..42`을 projection/snapshot foundation에 배정 | APPROVED / IMPLEMENTED | PR #16 code HEAD `1dad0d84ffcfacfc56a880e1296ef9430c2d43ed`, PostgreSQL 15/18 PASS |
| `SC-DP5-002` | `jc_data_projection_writer`, `jc_data_projection_reader`, `jc_data_projection_function_owner` | APPROVED / IMPLEMENTED | execute-only / safe-view-only / NOLOGIN owner |
| `SC-DP5-003` | `recommendation-profile-input-v1`과 `experiment-outcome-input-v1`은 shadow-only | APPROVED | production/P1/P2 authority 비변경 |
| `SC-DP5-004` | P2 outcome exposure authority는 `recommendation_p2_experiment_exposure` | APPROVED | general exposure/impression 대체 금지 |
| `SC-DP5-005` | projection/snapshot/lineage/validation/conflict evidence 90일 metadata | APPROVED / TECHNICAL BASELINE | purge 및 physical delete 비활성 |
| `SC-DP5-006` | source timestamp, identity, as-of, lineage 및 outcome aggregation은 fail-closed authority 검증 대상 | APPROVED / IMPLEMENTED | independent review correction, exact-head gates PASS |
