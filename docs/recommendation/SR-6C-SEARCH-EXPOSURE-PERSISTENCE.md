# SR-6C Search Exposure Persistence

## 상태

```text
Stage: SR-6C
Approval: GRANTED_2026-08-05
Implementation: VERIFIED
Evidence head: fa6b618f473bd6285bc3a1002c93d2646e081fbe
Runtime endpoint: POST /api/v1/search/exposures
Frontend activation: OUT_OF_SCOPE
Merge/deploy: NOT_PERFORMED
Overall: VERIFIED_RUNTIME_HOLD_FRONTEND_ACTIVATION
```

## 구현 흐름

```text
signed result context
→ platform subject resolution
→ visibility/binding validation
→ deterministic canonical item payload
→ append-only item exposure persistence
```

## Identity authority

- `platform_identity_mapping_v1`
- `platform_identity_mapping_invalidation_v1`
- `platform_identity_mapping_access_audit_v1`
- `resolve_platform_subject_v1`
- `invalidate_platform_subject_v1`

System Coordination의 `jc_security_owner`가 physical owner다. `jc_recommendation`은 mapping table을 직접 조회하지 않고 purpose=`search-exposure-write`, requester=`intelligence-search`로 제한된 `SECURITY DEFINER` 함수만 실행한다.

Mapping invalidation 이후 새 subject 발급, numeric identity, anonymous identity로 fallback하지 않고 fail-closed한다.

## Exposure authority

`search_exposure_event_v1`은 item-level actual exposure authority다.

- exposure ID, idempotency key, natural occurrence unique
- canonical payload SHA-256 검증
- Search run/snapshot/query/policy 결속
- opaque subject + server-derived session
- post/rank/page position exact binding
- `search-item-visible-v1`: viewport 50% 이상, 연속 1,000ms 이상
- `search-exposure-retention-v1`: 180일
- runtime UPDATE/DELETE/TRUNCATE 금지
- `jc_security_owner` controlled purge만 허용

일반 recommendation exposure, behavior impression, P2 experiment exposure를 authority로 재사용하지 않는다.

## Runtime

```text
SearchIdentityMappingReadPort
JdbcSearchIdentityMappingAdapter
SearchExposureValidator
SearchExposureCanonicalizer
SearchExposureStore
SearchExposureService
SearchExposureController
```

```text
POST /api/v1/search/exposures
Authentication: required
Success: 201 Created
```

멱등성:

```text
same key + same canonical payload → duplicate success
same key + different canonical payload → 409 IDEMPOTENCY_CONFLICT
batch 중 conflict → transaction 전체 rollback
```

Identity mapping 조회 실패·무효화는 `503 SEARCH_EXPOSURE_IDENTITY_UNAVAILABLE`이다.

## Canonical database package

`journey-connect-db-v2.7/01..54`는 기존 RCA·IP 보호 테스트가 고정한 동결 기준선이다. SR-6C는 이를 늘리지 않고 새 version package로 분리한다.

```text
database/journey-connect-db-v2.8/
├─ 01_search_exposure_persistence.sql
├─ 02_search_exposure_digest_privilege.sql
├─ 03_search_exposure_persistence_smoke_test.sql
└─ README.md
```

Testcontainers bootstrap 대응:

```text
01 ↔ 55_search_exposure_persistence.sql
02 ↔ 55a_search_exposure_digest_privilege.sql
03 ↔ 56_search_exposure_persistence_smoke_test.sql
```

`SearchExposureSqlContractTest`가 source package와 test bootstrap의 byte equality를 검증한다.

Flyway auto-discovery migration은 추가하지 않았다. P0 convergence contract가 canonical-only baseline을 보호하므로 별도 baseline/history 승인 없이 Flyway로 복제하지 않는다.

## 원격 검증

Exact implementation head:

```text
fa6b618f473bd6285bc3a1002c93d2646e081fbe
```

GitHub Actions `SR Search Recommendation` run `30967288235`:

```text
search-ranking-postgresql: SUCCESS
protected recommendation contracts: SUCCESS
full-backend-regression: SUCCESS
```

Focused evidence:

```text
artifact: 8915259300
digest: sha256:dcd6ccf2240d11c8db6379856a588900ba96bbf30694c806bb89e5194eb02915
14 suites / 51 tests
failures/errors/skipped: 0/0/0
SearchExposure tests: 13 PASS
```

Full backend evidence:

```text
artifact: 8915336504
digest: sha256:64e5ec631f63c3e842f25a0b91d2b89bd4ae1390a0fe7c5020d44cd82cc594a6
93 suites / 325 tests
failures/errors/skipped: 0/0/0
```

검증된 시나리오:

- canonical bootstrap과 role/grant smoke
- mapping purpose/requester audit
- mapping invalidation fail-closed
- authoritative insert와 duplicate replay
- same key/different payload conflict
- batch conflict 전체 rollback
- numeric user ID/raw query/JWT 미저장
- runtime mutation·purge 권한 차단
- controlled retention purge
- recommendation/P1/P2 및 전체 backend regression

## 비범위

- frontend `IntersectionObserver`와 `IMPRESSION` 전송
- browser validation
- CTR projection/evaluation
- merge·배포
