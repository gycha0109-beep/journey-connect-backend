# Search Exposure Contract V1

## 1. 문서 정보

| 항목 | 값 |
|---|---|
| 계약/registry ID | `search-exposure-v1` |
| 상태 | `ACTIVE DESIGN / RESERVED SOURCE / PERSISTENCE NOT IMPLEMENTED` |
| semantic owner | Intelligence Platform / Search |
| metric owner | Reliability |
| physical writer | 미구현; future Search runtime application boundary 후보 |

## 2. Source separation

| source ID | 의미 |
|---|---|
| `recommendation_general_exposure_v1` | 일반 추천 페이지 노출 |
| `recommendation_behavior_impression_v1` | recommendation behavior fact |
| `recommendation_p2_experiment_exposure_v1` | P2 experiment denominator authority |
| `search_exposure_v1` | Search result actual exposure |

네 source는 합산하거나 서로 대체하지 않는다.

## 3. Authority 상태

SC-1은 `search_exposure_v1` ID를 예약했다. IP-2는 semantic contract를 확정한다.

아직 다음이 없으므로 runtime authoritative source는 활성화되지 않았다.

- table/event schema
- physical writer
- API/command
- run/result binding validator
- idempotency/dedupe persistence
- role/grant
- PostgreSQL validation

IP-6에서 구현·검증·System Coordination 승인 후 `RESERVED`에서 `ACTIVE`로 전환할 수 있다.

## 4. Exposure 객체

최소 필드:

- `exposureId`
- `exposureSchemaVersion`
- `searchRunId`
- `resultSnapshotRef`
- `subjectRef` 또는 privacy-safe actorRef
- `identityScheme` 조건부
- `sessionId`
- `surface`
- `queryFingerprint`
- `pageRef` 또는 cursor occurrence ref
- `resultEntityRef`
- `position`
- `exposedAt`
- `producerBuildId`
- `idempotencyKey`
- `exposureFingerprint`

position은 Search output의 1-based final rank와 page 내 position을 별도 필드로 구분할 수 있다.

## 5. Delivered/rendered/exposed 분리

- delivered: server가 result를 응답함
- rendered: client가 DOM/view tree에 그렸음
- exposed: 승인된 visibility 조건을 충족해 사용자에게 실제 노출된 것으로 검증됨

server response만으로 item exposure를 자동 생성하지 않는다.

V1 actual exposure 권장 조건:

- server-issued SearchRun/result snapshot에 존재
- current user/session/surface binding 일치
- client occurrence/idempotency 검증
- visibility threshold/dwell rule version 기록
- event time 허용 범위

threshold/dwell 수치는 Reliability/Operations와 별도 metric/evidence contract에서 결정한다.

## 6. Page와 item exposure

- page exposure envelope은 한 page/cursor occurrence를 나타낸다.
- item exposure는 실제 노출된 entity와 position을 나타낸다.
- metric이 item 단위라면 item row가 authority다.
- page delivered count를 item exposure count로 간주하지 않는다.

## 7. Append-only와 dedupe

- exposure evidence는 append-only
- 같은 idempotencyKey + same fingerprint: dedupe
- same key + different fingerprint: conflict
- 기본 occurrence identity 후보:

```text
searchRunId
+ resultSnapshotRef
+ session/subject binding
+ page/cursor occurrence
+ resultEntityRef
+ finalRank
```

동일 화면의 rerender는 같은 occurrence key면 dedupe한다. 사용자가 결과 화면을 명시적으로 다시 열어 새 occurrence가 생성되면 별도 exposure가 가능하다. Reliability metric은 자체 dedupe rule을 추가로 명시한다.

## 8. Visibility 변경

과거 exposure는 당시 evidence로 보존한다. 이후 item이 hide/remove되어도 row를 수정·삭제하지 않는다. 새 delivery/exposure는 current visibility를 통과해야 한다.

## 9. Reliability 연결

Search metric이 exposure를 사용하려면 반드시 다음을 고정한다.

- metricDefinitionVersion
- authoritative source=`search_exposure_v1`
- delivered/rendered/exposed 정의
- item/page unit
- eligibility
- dedupe key
- attribution window
- result selection/outcome source

Recommendation P2의 `engagement_rate` 또는 `fallback_rate`에 Search exposure를 추가하지 않는다.

## 10. Privacy/logging

- raw query 금지; queryFingerprint 사용
- raw subject numeric ID 일반 로그 복제 금지
- result candidate payload 전체 금지
- precise location 금지
- exposure operational log는 ID/version/status 중심
