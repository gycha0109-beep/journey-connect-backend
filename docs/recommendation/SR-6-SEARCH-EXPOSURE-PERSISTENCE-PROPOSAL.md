# SR-6 Search Exposure Persistence Proposal

## 1. 상태

```text
Contract ID: search_exposure_v1
Stage: SR-6
Design: READY_FOR_SYSTEM_COORDINATION_REVIEW
Runtime implementation: NOT_STARTED
Database implementation: HOLD_SEQUENCE_AND_OWNER_APPROVAL
Production activation: NOT_ALLOWED
```

이 문서는 SR-3의 검색 결과 문맥과 행동 사실 저장 위에 실제 검색 노출의 authoritative source를 추가하기 위한 설계 제안이다. 아직 System Coordination, Reliability, Operations, Data 승인을 받지 않았으므로 table, migration, writer, metric은 활성 계약이 아니다.

## 2. 보호 기준선

현재 source는 다음과 같이 분리되어 있다.

| source | 의미 | SR-6 영향 |
|---|---|---|
| `recommendation_general_exposure_v1` | 일반 추천 페이지 노출 | 변경 없음 |
| `recommendation_behavior_impression_v1` | behavior impression 사실 | authoritative search exposure로 사용 금지 |
| `recommendation_p2_experiment_exposure_v1` | P2 실험 노출·평가 분모 | 변경 없음 |
| `search_exposure_v1` | 실제 검색 결과 노출 | 본 제안의 대상 |

검색 결과가 server에서 전달됐다는 사실, DOM에 렌더링됐다는 사실, 사용자가 visibility 조건을 충족해 실제로 봤다는 사실은 서로 다르다. `/api/v1/explore` 응답만으로 exposure row를 생성하지 않는다.

## 3. 소유권 제안

| 항목 | 제안 |
|---|---|
| semantic owner | Intelligence Platform / Search |
| physical writer | Search runtime application boundary의 단일 `SearchExposureStore` |
| application package | `com.jc.backend.intelligence.search` |
| 초기 DB role | 현재 recommendation backend role을 사용하는 compatibility arrangement 후보 |
| metric definition owner | Reliability |
| visibility rule owner | Operations + Reliability |
| retention/privacy owner | Data + System Coordination |
| registry/SQL sequence owner | System Coordination |

다른 트랙은 exposure table에 직접 write하지 않는다. Data는 승인된 projection 또는 snapshot만 소비하고, Reliability는 metric 계약을 소유하되 physical row를 직접 생성하지 않는다.

## 4. Identity 제안

### 4.1 V1 범위

- 인증 사용자만 허용한다.
- anonymous exposure는 V1에서 지원하지 않는다.
- 사용자·역할·계정 상태는 JWT와 보안 컨텍스트에서 서버가 결정한다.
- client가 `userId`, `subjectRef`, `sessionId`를 제출하지 않는다.

### 4.2 권장 identity scheme

신규 교차 트랙 evidence이므로 `platform_subject_v1`을 권장한다.

```text
JWT user
→ approved IdentityMappingReadPort
→ subject:<opaque-id>
→ search exposure row
```

선행 조건:

- identity mapping 단일 physical owner 지정
- Search writer read allowlist
- purpose binding=`search-exposure-write`
- mapping access audit
- invalidation·삭제 정책
- mapping 실패 시 write 거절

`legacy_user_numeric_v1`로 자동 fallback하지 않는다. mapping owner가 승인되지 않으면 SR-6 runtime 구현은 HOLD한다.

### 4.3 Session

session은 JWT token ID를 검증해 서버가 파생한다. 원문 JWT와 access token은 저장하지 않는다. token ID가 계약 형식에 맞지 않으면 Search 전용 hash session ID를 생성한다.

## 5. Authoritative item exposure

V1 authority는 item exposure row다. page delivery count를 item exposure count로 간주하지 않는다.

각 row는 다음 의미를 가진다.

```text
승인된 Search run/result snapshot의 특정 post가
특정 page occurrence와 final rank에서
승인된 visibility rule을 충족했다는 append-only evidence
```

page-level envelope은 `pageOccurrenceId`로 item rows를 그룹화한다. 별도 page exposure table은 V1 필수 대상이 아니다. page delivery·render telemetry가 필요하면 non-authoritative event 또는 후속 version으로 분리한다.

## 6. Visibility rule 제안

계약 후보:

```text
search-item-visible-v1
```

권장 초기 조건이며 아직 ACTIVE가 아니다.

- document가 visible 상태
- result card의 viewport 교차 비율이 50% 이상
- 위 조건이 연속 1,000ms 이상 유지
- card가 현재 Search result context의 post/rank binding에 포함
- 같은 page occurrence의 rerender는 동일 idempotency key 사용
- 화면을 명시적으로 다시 열어 새 page occurrence가 생성되면 새 exposure 허용

수집 evidence 후보:

- `visibleRatioBasisPoints` — 최소 5,000
- `dwellMilliseconds` — 최소 1,000
- `visibilityRuleVersion`
- `exposedAt`

client 값은 exposure 충족 주장에 불과하다. 서버는 rule version, 범위, result binding과 시간 허용 범위를 검증한다.

## 7. API 제안

```text
POST /api/v1/search/exposures
Authentication: required
Response: ApiResponse<SearchExposureBatchResponse>
```

요청 envelope:

```text
pageOccurrenceId
resultContextToken
visibilityRuleVersion
producerBuildId
items[]
```

item:

```text
exposureId
idempotencyKey
postId
absoluteRank
pagePosition
visibleRatioBasisPoints
dwellMilliseconds
exposedAt
```

제약:

- batch 1..100
- 모든 item은 같은 signed result context에 존재
- `absoluteRank`와 `postId` exact binding
- `pagePosition`은 1..page size
- event time은 현재보다 5분 이상 미래일 수 없음
- replay 허용 최대 age는 승인된 retention/retry 계약에 결속
- raw query 제출 금지

batch는 단일 transaction으로 처리한다. same idempotency key + same fingerprint는 duplicate, same key + different fingerprint는 conflict다. conflict가 하나라도 있으면 batch 전체를 rollback한다.

응답:

```text
acceptedCount
duplicateCount
status
```

오류 코드 제안:

| HTTP | code | 의미 |
|---:|---|---|
| 400 | `SEARCH_EXPOSURE_BATCH_INVALID` | batch·필드 형식 오류 |
| 400 | `SEARCH_EXPOSURE_TIME_INVALID` | 발생 시각 범위 오류 |
| 403 | `SEARCH_EXPOSURE_CONTEXT_INVALID` | token 사용자·서명·만료 오류 |
| 403 | `SEARCH_EXPOSURE_BINDING_INVALID` | post/rank/page binding 오류 |
| 409 | `IDEMPOTENCY_CONFLICT` | 같은 key의 다른 payload |
| 422 | `SEARCH_EXPOSURE_RULE_UNSUPPORTED` | 비활성 rule version |

## 8. Logical persistence model

System Coordination이 SQL sequence와 target DB version을 배정하기 전에는 executable DDL을 작성하지 않는다.

논리 필드:

| 필드 | 의미 |
|---|---|
| `exposure_id` | producer exposure ID |
| `exposure_schema_version` | `search-exposure-v1` |
| `idempotency_key` | occurrence command dedupe |
| `payload_fingerprint` | canonical payload SHA-256 |
| `canonical_payload` | replay·conflict 판정용 canonical bytes |
| `search_run_id` | Search 실행 ID |
| `result_snapshot_ref` | ranking snapshot fingerprint |
| `subject_ref` | `platform_subject_v1` opaque subject |
| `identity_scheme` | `platform_subject_v1` |
| `session_id` | server-derived session |
| `surface` | `search` |
| `query_fingerprint` | raw query가 아닌 SHA-256 |
| `page_occurrence_id` | page render occurrence |
| `result_entity_type` | `post` |
| `result_entity_id` | post ID |
| `absolute_rank` | 전체 result의 1-based rank |
| `page_position` | page 내부 1-based position |
| `visibility_rule_version` | exposure 판정 규칙 |
| `visible_ratio_basis_points` | client evidence |
| `dwell_milliseconds` | client evidence |
| `exposed_at` | 실제 조건 충족 시각, `TIMESTAMPTZ` |
| `received_at` | server 수신 시각, `TIMESTAMPTZ` |
| `producer_build_id` | frontend build 식별자 |

필수 uniqueness 후보:

```text
UNIQUE(exposure_id)
UNIQUE(idempotency_key)
```

server-derived fingerprint material:

```text
schemaVersion
+ searchRunId
+ resultSnapshotRef
+ subjectRef
+ sessionId
+ pageOccurrenceId
+ resultEntityRef
+ absoluteRank
+ pagePosition
+ visibilityRuleVersion
+ exposedAt
```

같은 화면 rerender는 같은 occurrence key를 사용한다. raw query, JWT, 전체 candidate payload, precise location은 저장하지 않는다.

## 9. Retention·삭제 제안

기술 기본안이며 production 확정값이 아니다.

| 데이터 | 제안 |
|---|---|
| raw item exposure | 180일 |
| idempotency/fingerprint material | raw row와 동일 기간 |
| operational logs | ID/version/status만, 30일 이하 후보 |
| aggregate metric snapshot | metric contract별 별도 retention |

privacy deletion은 exposure row를 user numeric ID로 직접 찾아 rewrite하는 방식이 아니다. `platform_subject_v1` mapping invalidation 이후 row를 개인 계정으로 재결합할 수 없도록 하는 방식을 권장한다. 법적 삭제가 raw evidence 삭제를 요구하는 경우 append-only 원칙과 충돌하므로 Data/System Coordination이 tombstone, crypto-shredding 또는 physical delete 중 하나를 별도 승인해야 한다.

## 10. Search metric 연결 제안

초기 metric 후보:

```text
search-click-through-rate-v1
```

| 항목 | 정의 후보 |
|---|---|
| authority | `search_exposure_v1` |
| unit | item occurrence |
| denominator | eligible·deduped item exposure |
| numerator | 같은 subject/session/run/post/rank에 결속된 `CLICK` |
| attribution window | exposure 이후 30분 |
| behavior source | `search-behavior-event-v1` |
| exclusions | legacy/fallback/direct detail/anonymous/invalid binding |

`VIEW`는 별도 `search-detail-view-rate-v1` 후보로 분리한다. 일반 recommendation metric이나 `recommendation-metrics-v1`에 search exposure를 추가하지 않는다.

## 11. Retry·replay

- network retry는 동일 idempotency key를 재사용한다.
- 같은 key + 같은 canonical fingerprint는 duplicate success다.
- 같은 key + 다른 fingerprint는 409 conflict다.
- expired result context는 replay를 허용하지 않는다.
- accepted row는 append-only다.
- correction은 새 superseding evidence version 없이 기존 row를 수정하지 않는다.

## 12. 구현 단계

```text
SR-6A System Coordination decision
SR-6B Java contract type/validator/canonicalizer — DB 비변경
SR-6C SC-assigned canonical SQL + role/grant
SR-6D SearchExposureStore + API integration
SR-6E frontend IntersectionObserver acknowledgement
SR-6F Reliability metric projection/evaluation
SR-6G PostgreSQL/full regression/independent review
```

## 13. 필수 검증

- identity mapping allowlist·failure rejection
- token user/run/snapshot/post/rank binding
- visibility threshold boundary
- batch size·page position validation
- same key/same fingerprint duplicate
- same key/different fingerprint conflict and transaction rollback
- raw query/JWT/precise location 미저장
- recommendation general exposure row 미생성
- P2 experiment exposure row 미생성
- search behavior impression을 exposure denominator로 사용하지 않음
- PostgreSQL role/grant 및 unauthorized writer 거절
- retention fixture와 deletion/invalidation 시나리오
- full backend P0/P1/P2 regression
- replay/golden canonical payload

## 14. 승인 전 금지

- 신규 Flyway 또는 canonical SQL 번호 자체 배정
- `recommendation_exposure_event`에 Search row 삽입
- behavior `IMPRESSION`을 authoritative exposure로 승격
- anonymous identity 임의 생성
- `legacy_user_numeric_v1` 자동 fallback
- raw query 또는 JWT 저장
- Search exposure를 P2 engagement denominator에 합산
- frontend에서 visibility rule version 없이 impression 전송
