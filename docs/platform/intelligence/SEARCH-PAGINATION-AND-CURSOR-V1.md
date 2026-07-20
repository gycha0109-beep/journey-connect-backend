# Search Pagination and Cursor V1

## 1. 문서 정보

| 항목 | 값 |
|---|---|
| 계약 ID | `search-pagination-cursor-v1` |
| 상태 | `ACTIVE DESIGN` |
| V1 decision | `SNAPSHOT_BOUND_CURSOR` |

## 2. 현재와 목표 분리

| 경로 | 현재 pagination | IP-2 판정 |
|---|---|---|
| `/api/v1/explore` | Spring Data offset/page | legacy compatibility |
| `/api/v1/feed` | `(publishedAt,id)` Base64 feed cursor | feed-specific |
| Search Intelligence V1 | run/result-snapshot-bound cursor | future contract |

현재 `CursorCodec`는 단순 Base64이고 tamper protection, query binding, policy binding이 없다. Search cursor로 재사용하지 않는다.

## 3. 첫 Search request

1. request/query/filter/sort/context validation
2. immutable input snapshot
3. retrieval/candidate snapshot
4. deterministic ranking/output snapshot
5. SearchRun terminal evidence
6. first rank window delivery
7. next cursor 발급

후속 cursor 요청은 알고리즘을 재실행하지 않고 같은 result snapshot의 rank window를 읽는다.

## 4. Cursor payload

최소 논리 필드:

- `cursorVersion`
- `searchRunId`
- `resultSnapshotRef`
- `queryFingerprint`
- `filterFingerprint`
- `sortPolicyVersion`
- `rankingPolicyVersion`
- `referenceTime`
- `nextRank`
- `lastOrderingTuple`
- `surface`
- `entityScope`
- `subjectBindingRef` optional
- `sessionBindingRef` optional
- `issuedAt`
- `expiresAt`

cursor field는 JSON camelCase, wire enum은 lowercase snake_case다.

## 5. 보호

- HMAC authenticated cursor 또는 AEAD encryption
- key version 포함
- user/session binding은 authenticated/personalized surface에서 필수
- anonymous surface는 server-derived session binding 권고
- query/filter/surface/scope mismatch fail-closed
- unknown cursor version fail-closed
- expired/stale cursor 구분
- raw query와 private feature를 cursor에 넣지 않는다.

key owner와 rotation은 Security/Operations/System Coordination 후속 승인 사항이다.

## 6. Rank window

- final rank는 1-based
- `nextRank`는 다음 읽을 original snapshot rank
- page size는 contract max를 가진다.
- 동일 rank를 중복 반환하지 않는다.
- snapshot candidate/result 순서를 Set/Map iteration으로 복원하지 않는다.

## 7. Mutation 동작

### 7.1 동일 점수 다수

snapshot 생성 시 complete tie-break로 순서를 고정한다.

### 7.2 중간 데이터 추가

기존 SearchRun에 삽입하지 않는다. 새 request에서만 새 run/snapshot에 반영한다.

### 7.3 중간 데이터 삭제 또는 visibility 변경

- 과거 output evidence 보존
- delivery 시 current visibility 재검증
- invisible item omit
- original rank window 내에서 backfill하지 않음
- 결과 수가 부족해도 cursor의 nextRank는 original snapshot 기준으로 전진

### 7.4 policy version 변경

유효 cursor는 발급 당시 policy/snapshot에 결속된다. 해당 version/evidence를 읽을 수 없으면 `cursor_expired` 또는 `cursor_mismatch`가 아니라 `cursor_stale` extension으로 처리할 수 있다. 새 정책을 기존 run에 적용하지 않는다.

### 7.5 cursor 재사용

동일 binding에서 동일 original rank window를 요청할 수 있다. visibility가 같으면 result refs는 동일해야 한다. exposure event는 idempotency/occurrence contract로 별도 처리한다.

### 7.6 다른 query/user/session 사용

`cursor_mismatch`로 거부한다. anonymous fallback이나 새 query로 조용히 실행하지 않는다.

## 8. Error codes

```text
cursor_invalid
cursor_expired
cursor_mismatch
cursor_stale
```

- invalid: syntax/signature/version 구조 불량
- expired: expiresAt 경과
- mismatch: query/filter/user/session/surface/scope 불일치
- stale: 필요한 immutable snapshot/policy dependency를 더 이상 안전하게 읽을 수 없음

## 9. Offset pagination

offset pagination은 admin/bounded legacy list에 유지할 수 있으나 Search Intelligence stable result contract로 사용하지 않는다.

V1 runtime이 offset을 외부 cursor 문자열로 포장하는 것은 금지한다.
