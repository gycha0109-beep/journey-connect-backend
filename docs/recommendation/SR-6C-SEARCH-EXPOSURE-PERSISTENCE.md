# SR-6C Search Exposure Persistence

## 상태

```text
Stage: SR-6C
Approval: GRANTED_2026-08-05
Implementation: COMPLETE_PENDING_REMOTE_VERIFICATION
Canonical SQL: 55
Canonical smoke: 56
Runtime endpoint: POST /api/v1/search/exposures
Frontend activation: OUT_OF_SCOPE
Merge/deploy: NOT_PERFORMED
```

## 구현

```text
signed result context
→ platform subject resolution
→ visibility/binding validation
→ deterministic canonical item payload
→ append-only item exposure persistence
```

### Identity authority

- `platform_identity_mapping_v1`
- `platform_identity_mapping_invalidation_v1`
- `platform_identity_mapping_access_audit_v1`
- `resolve_platform_subject_v1`
- `invalidate_platform_subject_v1`

System Coordination의 `jc_security_owner`가 physical owner다. `jc_recommendation`은 mapping table을 직접 조회하지 않고 purpose=`search-exposure-write`, requester=`intelligence-search`로 제한된 SECURITY DEFINER 함수만 실행한다. Mapping invalidation 후 numeric 또는 새 anonymous subject fallback 없이 fail-closed한다.

### Exposure authority

`search_exposure_event_v1`은 item-level actual exposure authority다.

- exposure ID, idempotency key, natural occurrence unique
- canonical payload SHA-256 검증
- Search run/snapshot/query/policy 결속
- opaque subject + server-derived session
- post/rank/page position 결속
- `search-item-visible-v1`: viewport 50% 이상, 연속 1,000ms 이상
- `search-exposure-retention-v1`: 180일
- runtime UPDATE/DELETE/TRUNCATE 금지
- `jc_security_owner` controlled purge만 허용

일반 recommendation exposure, behavior impression, P2 experiment exposure를 authority로 재사용하지 않는다.

## Runtime

- `SearchIdentityMappingReadPort`
- `JdbcSearchIdentityMappingAdapter`
- `SearchExposureStore`
- `SearchExposureService`
- `SearchExposureController`

```text
POST /api/v1/search/exposures
Authentication: required
Success: 201
```

멱등성:

```text
same key + same canonical payload → duplicate success
same key + different payload → 409 IDEMPOTENCY_CONFLICT
batch 중 conflict → transaction 전체 rollback
```

Identity mapping 실패·무효화는 `503 SEARCH_EXPOSURE_IDENTITY_UNAVAILABLE`이다.

## Canonical package

```text
database/journey-connect-db-v2.7/55_search_exposure_persistence.sql
database/journey-connect-db-v2.7/56_search_exposure_persistence_smoke_test.sql
jc-backend/src/main/resources/db/migration/V55__search_exposure_persistence.sql
```

실제 bootstrap에 27·28과 53·54가 이미 존재하므로 신규 sequence는 55·56으로 배정했다. Flyway는 기존 저장소 정책대로 기본 비활성이고, baseline history 전환 없이 운영에서 활성화하지 않는다.

## 검증 범위

- canonical bootstrap 55·56
- role/grant·mapping audit·invalidation smoke
- authoritative insert/duplicate
- idempotency conflict와 batch rollback
- privacy boundary: numeric user ID/raw query/JWT 미저장
- controlled retention purge
- focused Search 및 full backend regression

## 비범위

- frontend IntersectionObserver와 `IMPRESSION` 전송
- browser 검증
- CTR projection/evaluation
- merge·배포
