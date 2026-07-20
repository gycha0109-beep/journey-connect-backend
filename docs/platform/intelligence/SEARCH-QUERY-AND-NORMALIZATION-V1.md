# Search Query and Normalization V1

## 1. 문서 정보

| 항목 | 값 |
|---|---|
| 계약 ID | `search-query-normalization-v1` |
| 상태 | `ACTIVE DESIGN` |
| normalization version | `search-query-normalization-v1` |

## 2. 객체

### 2.1 `SearchQueryV1`

- `queryMode`
- `originalQueryRef` 또는 protected original value
- `normalizedQuery`
- `queryFingerprint`
- `normalizationVersion`
- `languageHint` optional
- `localeHint` optional
- `codePointLength`
- `utf8SizeBytes`

### 2.2 `SearchContextV1`

- server-derived subject/session references
- surface
- entity scope
- referenceTime
- language/locale hint
- coarse region context optional
- consent/privacy context reference

정밀 위치는 raw context/log에 포함하지 않는다.

### 2.3 `SearchFilterV1`

- `filterType`
- canonical value(s)
- source: `user_selected`, `system`, `operations`
- schema/version

### 2.4 `SearchSortV1`

```text
relevance
recent
popular
distance
```

scope/strategy가 지원하지 않는 sort는 `unsupported_filter` 또는 별도 `unsupported_sort` contract extension으로 실패한다. 조용히 다른 sort로 바꾸지 않는다.

## 3. query mode

```text
text_query
browse
```

- `text_query`: normalized query가 비어 있으면 invalid request.
- `browse`: original/normalized query가 없을 수 있으며 explicit filter/sort/surface로 탐색한다.
- blank 문자열을 text query와 browse로 모호하게 해석하지 않는다.

현재 `/api/v1/explore`는 blank keyword를 null로 바꾸지만, 이는 legacy endpoint 동작이다.

## 4. V1 normalization pipeline

순서는 version 계약의 일부다.

1. null/unpaired surrogate/invalid text 검증
2. 금지 control character 거부
3. Unicode whitespace를 ASCII space 의미로 통합
4. leading/trailing whitespace 제거
5. 연속 whitespace를 하나로 축약
6. Unicode `NFKC` normalization
7. case-insensitive scope에서 `Locale.ROOT` lower-case
8. code point/UTF-8 size 재검증
9. normalized query fingerprint 생성은 승인된 canonicalization contract에 위임

원문은 그대로 별도 evidence로 보존할 수 있으나 일반 application log에 기록하지 않는다.

## 5. 길이와 문자 규칙

- 최대 256 Unicode code points
- 최대 1024 UTF-8 bytes
- NUL, bidi override, non-character, unpaired surrogate는 거부
- tab/newline 등 whitespace control은 normalization 전에 space로 변환 가능
- 일반 punctuation은 V1에서 일괄 제거하지 않는다.
- SQL wildcard 의미는 repository adapter가 parameter binding으로 격리하며 query contract가 `%`/`_`를 SQL 문법으로 해석하지 않는다.

## 6. 한국어·다국어 범위

V1 필수:

- Unicode normalization
- whitespace normalization
- locale 비종속 Latin case normalization
- 한글 완성형 원문 보존

V1 비필수/미구현:

- 한글 초성 검색
- 형태소 분석
- 동의어 expansion
- 오타 교정
- transliteration
- language model rewrite

향후 기능은 새 normalization/rewrite version과 provenance를 사용한다. 원문을 덮어쓰지 않는다.

## 7. filter canonicalization

1. filter type은 lowercase snake_case registry value다.
2. type별 value canonicalization을 명시한다.
3. multi-value filter는 canonical value 기준 정렬한다.
4. 동일 type/value 중복은 dedupe한다.
5. single-value filter의 충돌 값은 invalid request다.
6. unknown filter/enum은 fail-closed다.
7. Operations filter는 user-selected filter로 위장하지 않는다.

권장 filter type 후보:

```text
entity_scope
region
region_descendant
tag
place_category
published_after
published_before
language
```


## 8. query fingerprint

fingerprint는 raw query logging 대체 수단이지 query authority가 아니다.

입력 후보:

- normalized query
- normalization version
- canonical filters
- sort
- entity scope
- surface

새 hash/canonicalization 알고리즘은 IP-2에서 구현하지 않는다. IP-3는 승인된 canonicalization version을 사용한다.

## 9. Privacy

- raw query는 일반 structured log 금지
- analytics에는 query fingerprint, length bucket, language hint, result count bucket 등 최소 정보만 허용
- query input snapshot은 privacy class를 가진다.
- health, politics, precise location, personal name 등 민감 query classification 필요
- retention 기간은 Data/Operations/System Coordination 승인 전 확정하지 않는다.
