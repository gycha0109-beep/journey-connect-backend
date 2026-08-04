# SR-3~SR-4 Search Tracking, Performance and Pagination Stability

## 목적

SR-0~SR-2의 검색 조건 강제·추천 정렬·legacy fallback 위에 다음 경계를 추가한다.

- SR-3: 탐색 결과와 사용자 행동을 위변조 방지 문맥으로 연결한다.
- SR-4: 동일 탐색 snapshot의 페이지 분할 안정성과 후보 조회 성능을 검증한다.

비범위:

- 운영 활성화, 배포, merge
- 프론트엔드 개편
- 일반 추천 exposure 또는 P2 experiment exposure 변경
- 미승인 `search_exposure_v1` 물리 테이블·writer 생성
- Flyway 및 canonical SQL 변경

## 계약 판정

### 행동 사실

기존 `recommendation_behavior_event`는 impression, view, click 등의 사용자 행동 사실을 저장하는 범용 canonical event store다. Search는 다음 조건에서 이 저장 구조를 adapter로 사용한다.

- schema version은 `search-behavior-event-v1`
- metadata의 surface는 `search`
- 일반 추천 `run_id`는 null
- search run ID는 search metadata에만 기록
- raw query는 저장하지 않고 query fingerprint만 기록
- recommendation exposure와 P2 experiment exposure는 변경하지 않음

이 adapter는 search exposure persistence가 아니다.

### 노출 증거

`search_exposure_v1`은 계약 ID만 예약되어 있고 physical schema, writer authority, retention, deletion, attribution 규칙이 확정되지 않았다.

따라서 SR-3에서는 다음을 구현하지 않는다.

- 신규 search exposure table 또는 migration
- `recommendation_exposure_event`에 SEARCH surface를 기록하는 우회 구현
- behavior impression을 authoritative exposure로 해석
- P2 experiment exposure와 search 노출 합산

상태:

```text
HOLD_DB_CONTRACT_REQUIRED: search_exposure_v1 persistence
```

필요 후속 결정:

- event/page/candidate physical model
- anonymous exposure 허용 여부와 identity contract
- search run/snapshot foreign-key authority
- retention/deletion/privacy owner
- impression threshold와 client acknowledgement contract
- attribution consumer와 Data Platform projection owner

## SR-3 구현

### 검색 결과 문맥

검색 추천이 성공하면 응답 body는 기존 `ApiResponse<PageResponse<PostDtos.Summary>>`를 그대로 유지하고 다음 header를 제공한다.

```text
X-Search-Snapshot
X-Search-Run-Id
X-Search-Policy-Version
X-Search-Result-Context
```

`X-Search-Result-Context`는 HMAC 서명된 `search-result-context-v1` token이다.

결속 필드:

- user ID
- search run ID
- query fingerprint
- snapshot fingerprint
- policy version
- 발급·만료 시각
- 현재 페이지의 post ID와 absolute rank 목록

검색어 원문, JWT, session ID, 전체 응답 payload는 token에 포함하지 않는다.

### 행동 API

```text
POST /api/v1/search/events
```

인증 필수이며 event type은 초기 범위에서 다음만 허용한다.

```text
IMPRESSION
VIEW
CLICK
```

요청:

```text
eventId
idempotencyKey
resultContextToken
eventType
postId
absoluteRank
occurredAt
```

검증:

- token 사용자 결속
- token 만료·서명
- 해당 페이지의 post ID와 absolute rank exact binding
- event 시각 범위
- event ID와 idempotency key

저장 metadata:

```text
surface=search
source=search-result-api
searchRunId
queryFingerprint
snapshotFingerprint
policyVersion
absoluteRank
```

raw query는 저장하지 않는다.

## SR-4 pagination 안정성

### 첫 페이지

로그인 사용자이고 검색 추천이 ON이며 검색 조건이 존재할 때:

1. `referenceTime`을 millisecond 단위로 고정한다.
2. 해당 시각까지 발행된 전체 bounded 후보를 조회한다.
3. P1 profile과 후보를 결정론적으로 정렬한다.
4. 전체 순서와 점수 구성으로 snapshot fingerprint를 계산한다.
5. user, query fingerprint, page size, referenceTime, policy, snapshot을 HMAC token에 결속한다.
6. 첫 페이지와 result context를 반환한다.

첫 페이지 처리 실패는 기존 legacy 탐색으로 fail-open한다.

### 후속 페이지

`page > 0`인 추천 continuation은 첫 페이지의 `X-Search-Snapshot`을 요청 header로 전달해야 한다.

검증:

- signature
- user
- normalized keyword·region fingerprint
- page size
- TTL
- policy version
- 전체 ranking snapshot fingerprint

불일치 시:

```text
409 SEARCH_SNAPSHOT_EXPIRED
```

후속 페이지는 실패 시 legacy page와 혼합하지 않는다.

기존 프론트 호환을 위해 `page > 0`이지만 snapshot header가 없는 요청은 legacy 탐색을 사용한다.

### 안정성 효과

- 첫 페이지 이후 새로 발행된 게시물은 고정 `referenceTime` 때문에 기존 snapshot에 끼어들지 않는다.
- profile, 후보 속성, 인기 신호, 정렬 결과가 바뀌면 fingerprint가 달라져 continuation을 중단한다.
- 같은 token과 데이터는 같은 페이지 partition을 반환한다.
- token은 사용자·검색 조건·page size 사이에서 재사용할 수 없다.

## 후보 조회 성능

기존 후보 SQL의 게시물별 상관 서브쿼리를 다음 CTE 집계로 변경했다.

```text
input
eligible
tag_data
like_counts
bookmark_counts
matching
```

보호 원칙:

- public/published/moderation/active-author/region hard filter를 가장 먼저 적용
- 태그·좋아요·북마크는 eligible set에 대해서만 집계
- application candidate read 1회
- ordered summary batch read 1회
- 후보별 repository 호출 없음
- candidate limit 초과 시 첫 페이지 legacy fallback, continuation fail-closed

DB index나 migration은 추가하지 않는다. 실제 대규모 운영 성능이 부족하면 SR 후속 schema/index 계약으로 분리한다.

## API 호환성

응답 JSON body와 기존 query parameter는 변경하지 않는다.

```text
GET /api/v1/explore
keyword, region, page, size
ApiResponse<PageResponse<PostDtos.Summary>>
```

추가된 snapshot은 optional request header이며, 결과 문맥은 response header다.

CORS는 다음을 허용·노출한다.

```text
Request: X-Search-Snapshot
Response: X-Search-Snapshot, X-Search-Run-Id,
          X-Search-Policy-Version, X-Search-Result-Context
```

## DB 영향

없음.

- Flyway 변경 없음
- canonical SQL 변경 없음
- 신규 table/index/view/function/role/grant 없음
- `journey_db` 접근·변경 없음
- recommendation run/exposure/P1/P2 schema 의미 변경 없음

## 테스트 범위

- snapshot round-trip
- token tampering, wrong user, wrong query, wrong page size, expiry
- result context post/rank binding
- first/second page stable partition
- snapshot 변경 시 continuation 409
- snapshot 없는 기존 page-number 요청 legacy fallback
- search behavior stored/duplicate/idempotency
- wrong post/rank/user rejection
- recommendation exposure row 미생성
- raw query 미저장
- CTE query-shape와 단일 candidate/summary batch contract
- 기존 explore body/pageable/shadow contract
- 전체 backend, P0/P1/P2 회귀

## 프론트 연결 상태

탐색 기준 프론트는 현재 response header를 보존하지 않고 `getExplore` 결과 body만 반환하며, `SearchPage.jsx`도 size 100 단일 요청 후 client-side filtering을 수행한다.

이번 backend 범위에서는 프론트 코드를 변경하지 않는다. 실제 다중 페이지 및 행동 전송을 활성화하려면 후속 프론트 작업에서 다음이 필요하다.

- `getExplore`가 search context header를 반환
- 다음 페이지 요청에 `X-Search-Snapshot` 전달
- visible impression 기준 확정 후 `/api/v1/search/events` 호출
- card click/view 시 해당 page의 result context와 absolute rank 전달
- selected region을 backend `region` query에 일관되게 전달

## 잔여 리스크

- `search_exposure_v1` physical persistence HOLD
- anonymous search tracking identity 미확정
- candidate limit 5,000 이상 검색은 legacy fallback
- in-memory full ranking 비용
- PostgreSQL 전문검색·검색 index 미도입
- 실제 브라우저 viewport impression 기준 미확정
- 프론트가 아직 snapshot·result context를 소비하지 않음

## 완료 판정

SR-3 행동 사실 추적과 SR-4 snapshot pagination은 전체 테스트 통과 시 구현 완료로 판정한다.

`search_exposure_v1` persistence는 별도 DB/System Contract 승인 전까지 `HOLD_DB_CONTRACT_REQUIRED`다.
