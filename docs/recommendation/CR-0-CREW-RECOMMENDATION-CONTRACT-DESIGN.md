# CR-0 Crew Recommendation Contract Design

## 상태

```text
Stage: CR-0
Contract: crew-recommendation-contract-v1
Policy: crew-ranking-policy-v1
Surface: crew_list
Base: SR-6F-I final exact head 9e2d0b74b0a66b0c3270796f97bc2eb4571422a4
Implementation scope: CONTRACT_ONLY
DEFAULT_ENABLED=false
DB change: NONE
Runtime/API change: NONE
CR-1 migration sequence: UNASSIGNED
Exposure persistence: NOT_IMPLEMENTED
Production activation: NOT_PERFORMED
```

## 목적

탐색 추천의 repository-internal implementation/verification이 종료되고 actual stage execution만 external platform decision으로 HOLD된 상태에서, 다음 추천 surface인 Crew recommendation의 의미를 먼저 고정한다.

CR-0은 기존 `/api/v1/crews` 동작을 변경하지 않는다. 현재 `CrewService.list(Pageable)`가 제공하는 recruiting crew 최신순 목록은 `crew-service-list-v1` fallback으로 보호한다.

## 현재 기준선

현재 Crew 모델은 다음 facts를 이미 가진다.

```text
crewId
ownerId
region
travelDate
capacity
recruiting
createdAt
active member count
pending application count
```

현재 목록은 `findByRecruitingTrueOrderByCreatedAtDescIdDesc`를 사용하며 personalization/ranking을 수행하지 않는다.

현재 `CrewDtos.CreateRequest`와 `CrewDtos.View`에는 tag field가 없다. 따라서 CR-0에서 crew tag를 존재한다고 추정하거나 title/description에서 자동 추출하지 않는다.

## 보호 원칙

1. 기존 Crew API response와 newest-first list 동작을 변경하지 않는다.
2. CR-0은 Controller, CrewService, CrewRepository, CrewDtos, Crew entity를 수정하지 않는다.
3. CR-0은 DB/Flyway/canonical SQL을 변경하지 않는다.
4. CR-1 migration 번호를 과거 계획의 `V16`으로 재사용하지 않는다.
5. CR-1 DB version/sequence는 당시 authoritative merged baseline을 기준으로 System Coordination이 배정해야 한다.
6. 태그 없는 기존 Crew를 `tagMatch=0`인 완전한 feature candidate로 취급하지 않는다.
7. 사용자 profile source는 기존 P1/profile semantics를 read-only로 재사용할 수 있지만 Search 내부 service/repository에 직접 결합하지 않는다.
8. 현재 Search/P0/P1/P2 run, exposure, metric, finality 의미를 Crew recommendation에 재사용하지 않는다.

## 미래 실행 흐름

CR-0에서는 구현하지 않지만 후속 단계의 목표 흐름은 다음과 같다.

```text
GET /api/v1/crews
  → authenticated viewer resolution
  → CrewRecommendationService
  → CrewRecommendationCandidateSource
  → hard eligibility
  → CrewRecommendationProfileSource
  → crew-ranking-policy-v1
  → stable page/snapshot binding
  → crew response
```

기능 OFF, 비로그인, first-page recommendation failure에서는 다음으로 fail-open 한다.

```text
CrewService.list(Pageable)
→ crew-service-list-v1
```

continuation/snapshot이 도입된 뒤의 invalid continuation은 Search와 동일하게 silently 다른 ordering으로 fallback하지 않고 별도 conflict semantics를 가져야 한다. 정확한 cursor contract는 CR-3에서 고정한다.

## Hard eligibility contract

점수 계산 전에 다음 후보를 제외한다.

| 조건 | 결과 |
|---|---|
| `recruiting=false` | 제외 |
| `travelDate < referenceDate` | 제외 |
| `capacityRemaining <= 0` | 제외 |
| viewer가 owner | 제외 |
| viewer relation이 `PENDING` | 제외 |
| viewer relation이 `APPROVED` | 제외 |
| approved Operations visibility decision이 ineligible | 제외 |
| 과거 rejected/cancelled history only | 재신청 가능하므로 제외하지 않음 |

`travelDate=null`은 현재 schema가 허용하므로 CR-0에서 임의로 invalid 처리하지 않는다.

Operations crew visibility port가 아직 연결되지 않은 상태는 `NOT_INTEGRATED`로 구분한다. 이는 승인된 visibility라는 뜻이 아니며 현재 baseline을 그대로 유지하기 위한 compatibility state다.

## Candidate facts

후속 `CrewRecommendationCandidateSource`는 최소 다음 값을 제공해야 한다.

```text
crewId
ownerId
regionCode
travelDate
capacity
activeMemberCount
capacityRemaining
recruiting
createdAt
tagFeatureState
tagSlugs
viewerRelation
visibilityState
```

`capacityRemaining`은 `max(0, capacity - activeMemberCount)`로 계산한다.

활성 member count는 현재 Crew semantics에 따라 owner + approved member를 의미한다. pending application은 정원 분모에 합산하지 않는다.

## Tag feature coverage

태그는 CR-1 전까지 존재하지 않는다. 따라서 다음 세 상태를 구분한다.

```text
UNAVAILABLE
EMPTY
PRESENT
```

규칙:

- `PRESENT`: 1개 이상의 canonical tag slug가 존재
- `EMPTY`: tag model은 적용됐지만 해당 Crew가 의도적으로 tag를 선택하지 않음
- `UNAVAILABLE`: legacy Crew 또는 tag model 적용 전 candidate
- `EMPTY/UNAVAILABLE`은 tag list를 가질 수 없음
- title/description AI extraction으로 state를 임의 승격하지 않음

coverage mode:

```text
PRESENT       → full_featured
EMPTY         → legacy_tagless
UNAVAILABLE   → legacy_tagless
```

즉 기존 Crew는 tag data 부재 자체로 40% component를 잃는 후보가 되지 않는다.

## Full-featured ranking components

CR-3에서 구현할 `crew-ranking-policy-v1`의 component allocation은 다음으로 고정한다.

| component | weight |
|---|---:|
| 관심 태그 일치 | 0.40 |
| 지역 관심 일치 | 0.30 |
| 여행 날짜 적합성 | 0.10 |
| 모집 여유 | 0.10 |
| 최신성 | 0.10 |

합계는 정확히 `1.00`이다.

가입 가능 여부는 점수가 아니라 hard eligibility다.

정확한 normalization/decay 함수와 deterministic comparator는 CR-3에서 구현하되, 동일 `crew-ranking-policy-v1` 안에서 위 component 의미와 weight를 바꾸지 않는다.

## Legacy tagless ranking path

태그가 없는 기존 Crew는 다음 compatibility weighting을 사용한다.

```text
regionInterest = 0.75
freshness      = 0.25
```

`recruiting`, 여행일 경과, 정원, viewer relation은 이미 hard filter로 처리한다.

이 경로는 태그가 없는 기존 Crew의 호환 처리를 위한 것이며 tag match를 0으로 간주한 full-featured score와 동일하지 않다.

Tie-break는 후속 구현에서 최소 다음 순서를 유지한다.

```text
score DESC
createdAt DESC
crewId DESC
```

동점에서 DB row order에 의존하지 않는다.

## User interest source boundary

Crew recommendation은 기존 recommendation P1 profile semantics와 explicit preference를 read-only input으로 재사용할 수 있다.

다만 다음 coupling은 금지한다.

```text
Crew → RecommendationSearchService
Crew → RecommendationSearchCandidateSource
Crew → SearchRankingPolicy
```

CR-2/CR-3에서는 `CrewRecommendationProfileSource` 또는 승인된 common read port를 사용한다. Search-specific ranking/exposure/cursor 의미를 Crew에 복사하지 않는다.

## Entity reference

cross-track reference는 System Contract의 기존 형식을 따른다.

```text
crew:<positive-id>
```

numeric crew PK 자체를 새 cross-track identity space로 취급하지 않는다.

## Exposure / behavior boundary

현재 System exposure registry에는 Crew recommendation exposure의 authoritative source가 등록되어 있지 않다.

CR-0은 다음 식별자를 제안만 한다.

```text
crew_recommendation_exposure_v1
status=PROPOSED_NOT_REGISTERED
```

System Coordination 승인 전:

- Crew exposure table 생성 금지
- 기존 recommendation/search exposure table 재사용 금지
- 일반 list view를 P2 experiment exposure로 기록 금지
- `recommendation_behavior_event`의 impression을 Crew exposure 분모로 재해석 금지

행동 추적은 후속 CR-4에서 별도 승인된 event/exposure contract에 따라 구현한다. 기존 Data 문서에 `crew_join`, `crew_leave`가 존재하더라도 producer와 metric semantics를 확인하지 않고 자동 연결하지 않는다.

## CR-1 데이터 보강 gate

CR-1은 crew tag physical model과 DTO 확장을 담당한다.

진입 전 확인:

```text
CR-1 DB version: UNASSIGNED
CR-1 DB sequence: UNASSIGNED
Tag authority/table identity: MUST_BE_VERIFIED
Migration rollback/forward-fix: REQUIRED
Existing Crew response compatibility: REQUIRED
Existing Crew create/list/join/review regression: REQUIRED
```

logical target은 게시물 tag vocabulary를 재사용하는 Crew↔Tag association이다. 그러나 실제 table/constraint/sequence는 repository의 authoritative merged DB baseline을 다시 확인한 후 결정한다.

## 단계 분리

```text
CR-0 Contract Design
→ CR-1 Crew Tag DB / DTO Extension
→ CR-2 Candidate Source / Hard Filter
→ CR-3 Ranking / Service / Cursor
→ CR-4 Exposure / Behavior Tracking
→ CR-5 Integration Verification
```

## CR-0 완료 조건

- [x] current Crew list fallback 보호
- [x] hard eligibility contract
- [x] tag coverage state 분리
- [x] full-featured component weight 고정
- [x] legacy tagless compatibility path 고정
- [x] entityRef contract
- [x] Search direct dependency 금지
- [x] Operations visibility compatibility state 정의
- [x] exposure registry gap 명시
- [x] CR-1 DB sequence 미배정 유지
- [x] DB/API/runtime 비변경

## Non-scope

```text
crew_tag migration
Crew DTO tags
candidate query
ranking implementation
recommendation service
controller wiring
cursor/snapshot implementation
exposure persistence
behavior producer
P1/P2 cutover
merge/deploy
production activation
```
