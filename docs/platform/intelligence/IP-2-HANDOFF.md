# IP-2 Handoff

## 문서 정보

| 항목 | 값 |
|---|---|
| 계약 ID | `ip-2-handoff-v1` |
| 상태 | `COMPLETE / CONTRACT_ONLY` |
| Search production implementation | `NOT STARTED` |
| Search exposure persistence | `NOT IMPLEMENTED` |
| DB/SQL | `UNCHANGED` |
| 다음 후보 | `IP-3 Search Domain Types & Validation` |

## 1. 완료

- current search/explore/region/crew/user/place/tag/frontend/DB inventory
- Search 책임/비책임
- request/query/context/filter/sort/scope/surface contract
- query/normalization V1
- retrieval/filtering/eligibility/ranking/reranking contract
- snapshot-bound pagination/cursor contract
- SearchRun/common contract adapter mapping
- `search_exposure_v1` semantic contract와 activation gate
- explanation/failure/fallback/no-result contract
- exact/semantic/evidence replay 요구사항
- query privacy/logging boundary
- 문서 구조/링크/ID/source hash 검증
- 2회 independent self-review와 보완

## 2. 핵심 확정

1. 현재 `/api/v1/explore`는 Search Intelligence runtime이 아니라 legacy post DB browse query다.
2. Search와 Recommendation은 policy/candidate/score/exposure/metric을 공유하지 않는다.
3. Search V1 pagination은 snapshot-bound cursor다.
4. original query와 normalized query는 분리한다.
5. final rank는 1-based이며 complete deterministic tie-break가 필요하다.
6. visibility authority는 Operations이며 Search는 read/consume만 한다.
7. `search_exposure_v1`은 SC-1 reserved ID를 사용하지만 persistence activation 전 authority는 비활성이다.
8. no-results는 valid succeeded empty output이다.
9. provider/source snapshot이 없으면 exact replay를 선언하지 않는다.

## 3. Current implementation inventory

- post keyword/region: 구현됨, JPQL substring + latest order + offset page
- region keyword: 구현됨, 최대 50 substring list
- region nearby: 구현됨, geo lookup
- tag/place/user/crew text search: 미구현
- integrated search API: 미구현
- SearchRun/snapshot/ranking/cursor/exposure: 미구현
- frontend search UI: 미구현
- dedicated search index/provider: 미구현
- public search behavior producer: 확인되지 않음

## 4. 보호 결과

- production Java/Kotlin 변경: 없음
- build file 변경: 없음
- existing recommendation production 변경: 없음
- `jc-recommendation-core` 변경: 없음
- canonical SQL `01..26` 변경: 없음
- P2 source authority/metric 의미 변경: 없음
- Data shadow cutover: 없음
- identity join: 없음

## 5. 검증

| 대상 | 결과 |
|---|---|
| 필수 문서 8개 | PASS |
| README links | PASS |
| 상대 링크 | PASS |
| contract ID 형식/중복 | PASS |
| wire enum lowercase_snake_case | PASS |
| Instant/UTC/version 규칙 | PASS |
| `search_exposure_v1` registry 정합성 | PASS |
| recommendation exposure 분리 | PASS |
| current inventory fact check | PASS |
| protected source 320 list | PASS, current project exact match |
| canonical SQL `01..26` | PASS, unchanged |
| production/build file diff | PASS, none |
| Gradle/PostgreSQL rerun | 미실행, 문서-only 변경 |

## 6. 자체 리뷰

### 리뷰 1 — 계약/아키텍처

발견 4 / 수정 4 / 보류 0

1. common `IntelligenceRunV1`에 candidateSnapshotRef가 있다고 오해할 위험 → Search domain extension mapping으로 수정
2. reserved search exposure를 active authority로 표현할 위험 → activation gate 추가
3. persistence enum `search`를 current public search logging으로 오인할 위험 → public producer 부재 명시
4. `/explore` offset pagination을 Search V1로 오인할 위험 → legacy compatibility로 고정

### 리뷰 2 — 안정성/보안/사실성

발견 5 / 수정 5 / 보류 0

1. visibility 변경 시 page backfill 규칙 모호 → omit-without-backfill 고정
2. raw query observability 노출 위험 → fingerprint/bucket only 원칙 강화
3. no-results가 failure/fallback 목록에 혼재 → normal outcome으로 분리
4. NFKC normalization이 초성/형태소/오타 기능까지 암시 → V1 제외 범위 명시
5. filter 후보의 `tag` 앞 공백으로 wire value 혼동 가능 → canonical `tag`로 수정

보완 후 구조·링크·ID·hash 검증 재실행 PASS.

## 7. 문제/승인 필요

1. active runtime에 named IP-1.10 final archive가 직접 마운트되지 않아 84번째 backend test artifact는 IP-2에서 독립 재열람하지 못했다. 이관 baseline은 변경하지 않았다.
2. search query retention/access/deletion policy owner approval 필요
3. Operations visibility/eligibility port contract 필요
4. cursor key owner/rotation policy 필요
5. `search_exposure_v1` physical writer/schema/role/grant는 IP-6 전 승인 필요
6. actual retrieval/index strategy는 미선정

## 8. IP-3 진입 판정

`READY — DB-FREE CONTRACT/DOMAIN TYPE SCOPE ONLY`

허용:

- Java Search domain records/enums/value objects
- validators
- normalization canonicalizer contract implementation
- cursor payload/codec interface와 tamper-protection test double
- JSON fixtures/contract tests
- current `/explore` read-adapter input fixture

HOLD:

- production controller cutover
- DB/index/search provider
- search exposure persistence
- Operations visibility integration 없이 candidate delivery
- raw query retention 확정
- cursor production key management
- integrated search runtime
