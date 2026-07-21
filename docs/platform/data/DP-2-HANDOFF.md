# DP-2 Handoff

## 문서 정보

| 항목 | 값 |
|---|---|
| 계약 ID | `dp-2-handoff-v1` |
| 상태 | `DP2_IMPLEMENTATION_COMPLETE` |
| 작업 시작 HEAD | `c3f791c6c6eaa12b2aba3a1dbe686cb0b3d3cc80` |
| branch | `codex/dp-2-postgresql-event-store` |
| PR | `#8` |
| next | `DP-3 Retry, Quarantine & Processing State` |

## 1. 완료

- SC-approved `platform-event-fingerprint-sha256-v1` 구현
- exact 11-field canonical fingerprint bytes 및 lowercase hex SHA-256 구현
- SQL 29 canonical event와 append-only evidence 구현
- SQL 30 atomic idempotency와 least-privilege roles 구현
- SQL 31 smoke/contract validation 구현
- 병렬 PostgreSQL concurrency fixture 구현
- PostgreSQL 15/18 Data DB workflow 구현
- Data contract 및 Recommendation Java Core gate 구성
- Recommendation P0/P1/P2 DB regression matrix 연동
- Backend/IP-12.5 및 SC 보호 gate 정합화
- DP-2 static verifier와 machine-readable evidence 작성

## 2. 구현 경계

### 포함

- canonical event store
- idempotency binding
- ingest attempt
- duplicate observation
- conflict observation
- atomic `NEW / DUPLICATE / CONFLICT`
- append-only enforcement
- writer/reader/replay capability roles
- retention metadata

### 제외

- application/runtime integration
- Controller/API/Spring Service/JPA
- retry/quarantine/replay processor
- replay executor procedure grant
- purge/delete executor
- identity mapping/join
- Recommendation/Search projection cutover
- production traffic/control 변경

## 3. Fingerprint

- wire ID: `platform-event-fingerprint-sha256-v1`
- canonicalization: `platform-event-canonical-json-v1`
- algorithm: SHA-256
- encoding: lowercase hexadecimal
- length: 64
- golden fixture: `f6ca2be46c46150e7c26d82e5c22974b1fd53dc54b500a0fccba29e23450f09d`
- DP-1 unresolved implementation은 compatibility boundary로 유지
- 기존 Recommendation P0 canonical bytes/fingerprint는 변경·호출·재사용하지 않음

## 4. PostgreSQL objects

SQL 29:

- `data_platform_event_v1`
- `data_event_ingest_attempt_v1`
- `data_event_duplicate_observation_v1`
- `data_event_conflict_observation_v1`
- append-only/validation/canonical JSON helper functions

SQL 30:

- `data_event_idempotency_binding_v1`
- `ingest_data_platform_event_v1(...)`
- Data reader views
- `jc_data_event_writer`
- `jc_data_event_reader`
- `jc_data_replay_executor`

SQL 31:

- NEW/DUPLICATE/CONFLICT
- malformed input denial
- append-only denial
- role/grant denial
- retention/no-purge validation

## 5. 검증 결과

| Gate | 결과 | GitHub Actions run |
|---|---|---|
| Data Contract CI | PASS | `29854424891` |
| Data PostgreSQL 15 | PASS | `29854424964` |
| Data PostgreSQL 18 | PASS | `29854424964` |
| Recommendation P0 DB PostgreSQL 15 | PASS | `29854424898` |
| Recommendation P0 DB PostgreSQL 18 | PASS | `29854424898` |
| Backend/IP-12.5 `verifyIp125` | PASS | `29854424935` |
| SC Baseline Reconciliation | PASS | `29854424897` |
| protected diff | PASS | GitHub compare + SC gate |

Data PostgreSQL CI는 fresh database에서 state-producing canonical SQL을 SQL 30까지 적용하고 SQL 31을 실행한다. 기존 rollback-only smoke scripts와 P0/P1/P2 protected regression은 Recommendation P0 Database CI가 별도로 검증한다.

Concurrency 결과:

- same key + same fingerprint: `1 NEW + 1 DUPLICATE`
- same key + different fingerprint: `1 NEW + 1 CONFLICT`
- persisted total: `2 events + 2 bindings + 1 duplicate + 1 conflict`
- deadlock/transaction abort: 없음

## 6. 발견 및 보완

1. DP-1의 pre-approval fingerprint guard가 승인된 SHA-256 구현까지 차단했다.
   - 승인 구현에는 SHA-256을 요구하고 unresolved boundary에는 digest를 금지하도록 분리했다.
2. generic canonicalization의 payload denylist가 top-level `actorRef`를 차단했다.
   - 정확한 11개 fingerprint top-level key만 허용하는 전용 canonicalization 경계를 추가했다.
3. 최초 fixture가 `post_view` taxonomy에 없는 `position`을 포함했다.
   - `surface + viewEpisodeRef` fixture로 교체하고 golden hash를 재고정했다.
4. 기존 SQL 28 smoke는 rollback-only test라 순차 state bootstrap에 사용할 수 없었다.
   - 보호 SQL을 수정하지 않고 state bootstrap과 protected smoke regression을 분리했다.
5. IP-12 static test가 SQL inventory를 28개로 고정했다.
   - 31개와 정확한 SQL 29/30/31 이름을 검증하도록 test-only 최소 보정했다.
6. SC allowlist는 위 exact test file 하나와 SQL 29..31만 허용하도록 유지했다.

## 7. 보호 상태

- SQL `01..28`: 변경 없음
- production Java/Kotlin: 변경 없음
- Recommendation P0/P1/P2 source authority: 변경 없음
- P2 experiment exposure/metric/evaluation evidence: 변경 없음
- Search runtime/projection ownership: 변경 없음
- `/api/v1/explore`: 변경 없음
- production shadow: `DISABLED`
- kill switch: `ENABLED`
- effective sampling: `0 BPS`
- cohort: `EMPTY`
- Search cutover: `NOT STARTED`

## 8. 잔여 리스크

- 실제 application DB login과 role membership routing은 미구현
- retry/quarantine/replay state machine과 processor는 미구현
- replay executor는 role만 존재하며 실행 권한 없음
- retention executor와 physical deletion은 미구현
- identity mapping/join은 미구현

이는 DP-2 비책임이며 DP-2 완료 blocker가 아니다.

## 9. 다음 재개 지점

1. PR #8 최종 exact-head CI 확인
2. 사용자 승인 후에만 PR #8 merge
3. merged main HEAD를 DP-2 기준선으로 확정
4. DP-3 retry/quarantine state machine 및 processor role 계약 승인
5. Operations/Security/Privacy observability review

사용자 명시 승인 전 main merge 금지.
