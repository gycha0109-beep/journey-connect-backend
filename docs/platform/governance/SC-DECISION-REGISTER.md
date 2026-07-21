# SC Decision Register

## 문서 정보

| 항목 | 값 |
|---|---|
| 계약 ID | `sc-decision-register-v1` |
| canonical path | `docs/platform/governance/SC-DECISION-REGISTER.md` |
| 상태 | `ACTIVE` |
| 기준 main HEAD | `bdce7de5ef6be31f8da6a8a349424be8f06a87a1` |
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
| `SC-DP1-009` | 신규 Data fingerprint exact contract | APPROVED / RESOLVED | `SC-DP2-001` 및 `SC-DP2-ENTRY-DECISIONS.md` |
| `SC-DP1-010` | PR #3 merge commit `f38cf56b34ff23fbd5cb20b9013444a8cb2d29f4`는 main authority | APPROVED / MERGED | traffic activation은 별도 승인 필요 |
| `SC-DP1-011` | identity mapping physical owner/deletion policy | UNRESOLVED / OUTSIDE DP-2 | DP-2는 mapping 저장소·join을 구현하지 않음 |
| `SC-DP1-012` | Decision Register canonical path | APPROVED | 이 문서 |
| `SC-DP1-013` | RACI canonical path | APPROVED | `docs/platform/governance/SC-RACI.md` |
| `SC-DP1-014` | 공식 DP-1 Baseline SHA | APPROVED | `9d84f630e87d54f780e332eead0c1f8df6a51d0b` |
| `SC-DP2-001` | fingerprint algorithm `SHA-256`, output lowercase hex 64자, wire ID `platform-event-fingerprint-sha256-v1` | APPROVED | 신규 Data 전용; P0 fingerprint와 분리 |
| `SC-DP2-002` | DP-2 canonical DB target은 `journey-connect-db-v2.7`; SQL `29` event store, `30` idempotency/atomic ingest, `31` smoke/contract verification | APPROVED | SQL 01..28 보호; 32+ 미배정 |
| `SC-DP2-003` | physical writer는 `jc_data_event_writer`; reader `jc_data_event_reader`; replay executor `jc_data_replay_executor` | APPROVED | 최소권한, canonical tables 직접 UPDATE/DELETE 금지 |
| `SC-DP2-004` | idempotency binding online retention 30일; attempt/conflict/quarantine technical retention 90일 | APPROVED / TECHNICAL BASELINE | production purge는 OP/Security/Privacy 승인 전 비활성 |
| `SC-DP2-005` | canonical event retention class 기본 365일, `expires_at` 기록 가능, 자동 purge 비활성 | APPROVED / TECHNICAL BASELINE | 법적 보존·삭제 정책 확정 전 물리 삭제 실행 금지 |
| `SC-DP2-006` | DP-2는 identity mapping, runtime API, projection cutover를 포함하지 않음 | APPROVED | 순수 event store/idempotency persistence 경계 |
