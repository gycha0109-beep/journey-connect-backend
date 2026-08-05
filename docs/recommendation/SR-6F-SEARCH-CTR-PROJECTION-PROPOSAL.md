# SR-6F Search CTR Projection Proposal

## 1. 상태

```text
Metric ID: search-click-through-rate-v1
Stage: SR-6F
Design: READY_FOR_SYSTEM_COORDINATION_REVIEW
Runtime projection: NOT_STARTED
Database implementation: HOLD_OWNER_IDENTITY_AND_SEQUENCE_APPROVAL
Production evaluation: NOT_ALLOWED
```

이 문서는 `search_exposure_event_v1`과 `search-behavior-event-v1`의 `CLICK`을 결속해 검색 CTR을 계산하기 위한 설계 제안이다. SR-6C 저장/API 구현과 SR-6E 프론트 노출 수집은 별도 단계이며, 이 문서는 metric authority·identity bridge·attribution·finality를 승인받기 전 SQL, projection writer, endpoint를 활성화하지 않는다.

## 2. 현재 authority

| source | authority | 현재 identity |
|---|---|---|
| `search_exposure_event_v1` | 실제 검색 item exposure | `subject_ref` / `platform_subject_v1` |
| `recommendation_behavior_event` + `search-behavior-event-v1` | 검색 CLICK/VIEW 행동 사실 | numeric `user_id` + server-derived `session_id` |
| `platform_identity_mapping_v1` | numeric user ↔ opaque subject mapping | System Coordination / `jc_security_owner` |

Reliability가 `platform_identity_mapping_v1`을 직접 조회하거나 raw identity pair를 수신하는 방식은 허용하지 않는다.

## 3. metric 정의 후보

```text
metric_id: search-click-through-rate-v1
unit: authoritative item exposure occurrence
value: attributed_exposure_count / eligible_exposure_count
```

### 3.1 denominator

다음 조건을 모두 만족하는 `search_exposure_event_v1` row 수다.

- `schema_version = 'search-exposure-v1'`
- `surface = 'search'`
- `result_entity_type = 'post'`
- `visibility_rule_version = 'search-item-visible-v1'`
- `visible_ratio_basis_points >= 5000`
- `dwell_milliseconds >= 1000`
- 평가 window의 `exposed_at`에 포함
- 동일 `exposure_id`는 한 번만 계산

behavior `IMPRESSION`, 일반 recommendation exposure, P2 experiment exposure는 denominator가 아니다.

### 3.2 numerator

eligible exposure에 결속된 `CLICK`이 하나 이상 존재하는 exposure 수다. click row 수가 아니다.

CLICK 후보 조건:

- `schema_version = 'search-behavior-event-v1'`
- `event_type = 'click'`
- `entity_type = 'post'`
- `metadata.surface = 'search'`
- exposure 이후 30분 이내
- 동일 user/subject, session, search run, post, absolute rank 결속
- invalid binding, direct detail, fallback/legacy, anonymous source 제외

## 4. identity privacy 경계

### 4.1 권장 방식

`jc_security_owner` 소유의 aggregate-only `SECURITY DEFINER` projection boundary를 권장한다.

```text
search exposure subject_ref
+ private identity mapping
+ search CLICK numeric user_id
→ 내부에서만 attribution
→ identity 없는 aggregate result 또는 opaque attribution key
```

`jc_reliability`와 일반 application role에는 다음을 금지한다.

- mapping table SELECT
- numeric user ID와 subject_ref 동시 반환
- raw exposure/CLICK identity join 결과 반환
- user-level metric export

### 4.2 대안

append-only `search_click_attribution_v1` ledger를 별도 authority로 둘 수 있다. 이 경우 row는 `exposure_id`, `click_event_id`, attribution version, attributed_at만 저장하고 numeric user ID·subject_ref를 저장하지 않는다. ledger writer와 SQL sequence는 별도 승인 대상이다.

### 4.3 승인 전 결론

aggregate-only function과 attribution ledger 중 하나를 System Coordination·Privacy/Security·Reliability가 선택하기 전 구현은 HOLD한다.

## 5. deterministic attribution

### 5.1 후보 집합

CLICK 발생 시각 이전 30분 내 exposure 중 다음 값이 모두 같은 row를 후보로 한다.

```text
mapped user/subject
session_id
search_run_id
result_entity_id
absolute_rank
```

`query_fingerprint`, `result_snapshot_ref`, `ranking_policy_version`은 consistency verification에 사용하며 불일치 후보는 제외한다.

### 5.2 1:1 규칙

하나의 CLICK은 후보 중 `exposed_at`이 가장 최근인 exposure 하나에만 귀속한다.

동률 정렬:

```text
exposed_at DESC
received_at DESC
exposure_id ASC
```

하나의 exposure에 여러 CLICK이 있어도 numerator는 1이다. 하나의 CLICK이 여러 exposure numerator를 증가시키지 않는다.

### 5.3 시간 경계

```text
click.occurred_at >= exposure.exposed_at
click.occurred_at < exposure.exposed_at + interval '30 minutes'
```

상한은 exclusive다.

## 6. 평가 window

권장 query window:

```text
[window_start, window_end)
```

- denominator는 `exposed_at` 기준
- numerator는 해당 exposure의 30분 attribution window까지 조회
- timezone은 UTC
- 최소 granularity 후보는 1시간
- segment 없는 global aggregate를 V1 기본으로 한다

초기 segment 후보는 별도 승인 없이는 활성화하지 않는다.

- ranking policy version
- region bucket
- query class
- device class

raw query, user, subject, session segment는 금지한다.

## 7. finality와 late arrival

현재 behavior API는 과거 event replay를 허용하므로 단순히 `window_end + 30분`만으로 final 판정할 수 없다.

필수 결정:

- CLICK late-arrival 허용 최대 age
- provisional metric 표시 기간
- finalization watermark
- late correction 방식

권장 V1 상태:

```text
PROVISIONAL
SETTLED
SUPERSEDED
```

finality 정책 승인 전에는 projection snapshot을 `SETTLED`로 기록하지 않는다.

## 8. output contract 후보

```text
metricId
metricVersion
windowStart
windowEnd
status
eligibleExposureCount
attributedExposureCount
ctrBasisPoints
computedAt
sourceMaxReceivedAt
projectionFingerprint
```

계산:

```text
ctrBasisPoints =
  denominator = 0 ? null
  : floor(attributedExposureCount * 10000 / eligibleExposureCount)
```

0 denominator를 0%로 표현하지 않는다.

## 9. retention 후보

| evidence | 후보 |
|---|---|
| raw exposure | 기존 180일 |
| raw CLICK | 기존 behavior retention에 따름 |
| attribution ledger | raw source 중 짧은 retention과 동일 이하 |
| aggregate snapshot | 400일 후보 |
| operational logs | 30일 이하 |

aggregate snapshot retention은 Reliability·Data 승인 전 확정하지 않는다.

## 10. role/ownership 후보

| 항목 | 후보 owner |
|---|---|
| metric semantic definition | Reliability |
| identity bridge physical owner | System Coordination / `jc_security_owner` |
| projection implementation | Intelligence/Search 또는 Reliability 전용 projector 중 승인된 단일 writer |
| aggregate snapshot physical owner | Reliability |
| retention | Data + Reliability |
| registry/SQL sequence | System Coordination |

## 11. 구현 단계

```text
SR-6F-A metric/identity/finality decision
SR-6F-B Java metric contract + deterministic fixture, DB 비변경
SR-6F-C SC-assigned SQL/function or attribution ledger
SR-6F-D projection store/evaluator
SR-6F-E PostgreSQL integration and privacy negative tests
SR-6F-F full regression and independent review
```

## 12. 필수 검증

- denominator가 authoritative search exposure만 사용
- behavior IMPRESSION 제외
- CLICK 30분 하한 inclusive / 상한 exclusive
- one click → one exposure
- multiple clicks → exposure numerator 최대 1
- same post라도 session/run/rank 불일치 시 attribution 제외
- mapping invalidation 후 신규 attribution fail-closed 정책
- Reliability role의 mapping table 직접 접근 거절
- raw identity pair가 query/result/log에 노출되지 않음
- zero denominator null 처리
- deterministic ordering/fingerprint
- late-arrival provisional/final transition
- P0/P1/P2 및 SR 전체 regression

## 13. 승인 전 금지

- Reliability role에 identity mapping SELECT 부여
- numeric user ID와 subject_ref를 metric snapshot에 저장
- behavior IMPRESSION을 denominator로 사용
- CLICK 수를 numerator로 직접 합산
- 하나의 CLICK을 여러 exposure에 중복 귀속
- raw query/user/session segment 생성
- SQL sequence 또는 DB version 자체 배정
- production dashboard·alert 활성화
