# SR-6F Search CTR Approval Record

## 상태

```text
Initial decision date: 2026-08-05 KST
SR-6F-C authorization date: 2026-08-06 KST
SR-6F-D authorization date: 2026-08-06 KST
Decision: APPROVED_BY_PROJECT_OWNER
Metric: search-click-through-rate-v1
SR-6F-A design: APPROVED
SR-6F-B Java contracts: VERIFIED
SR-6F-C aggregate-only DB boundary: VERIFIED
SR-6F-D projection snapshot/single writer: AUTHORIZED
Endpoint/finality: NOT_AUTHORIZED_IN_THIS_STAGE
Merge/deploy/production activation: NOT_AUTHORIZED
```

## 승인된 계약

- denominator는 authoritative `search_exposure_event_v1` item occurrence다.
- numerator는 CLICK row 수가 아니라 CLICK이 하나 이상 귀속된 exposure 수다.
- attribution window는 exposure 이후 30분이며 하한 inclusive, 상한 exclusive다.
- 하나의 CLICK은 가장 최근 eligible exposure 하나에만 귀속한다.
- 동률은 `exposedAt DESC`, `receivedAt DESC`, `exposureId ASC` 순서로 결정한다.
- 하나의 exposure에 여러 CLICK이 있어도 numerator는 최대 1이다.
- zero denominator는 0%가 아니라 `null`이다.
- V1 상태는 `PROVISIONAL`만 허용하며 finality 승인 전 `SETTLED`를 생성하지 않는다.
- Reliability는 identity mapping table이나 numeric user ID ↔ subject pair를 직접 읽지 않는다.

## SR-6F-C 승인값

- identity bridge 방식은 attribution ledger가 아닌 aggregate-only `SECURITY DEFINER` 함수다.
- 함수 physical owner는 System Coordination의 `jc_security_owner`다.
- 전용 consumer role은 `jc_reliability`이며 NOLOGIN·무상속·무우회 속성을 사용한다.
- `jc_reliability`에는 mapping, raw exposure, raw behavior, access-audit table 권한을 부여하지 않는다.
- 함수 반환은 metric/window/status/count/basis-points/watermark로 제한하고 identity-bearing key를 반환하지 않는다.
- 평가 window에 invalidated mapping exposure가 있으면 부분 집계하지 않고 fail-closed한다.
- aggregate 접근 감사는 identity 없이 30일 보존한다.
- 신규 raw attribution ledger는 만들지 않는다.
- canonical package는 `journey-connect-db-v2.8/04..05`, Testcontainers global labels는 `57..58`이다.
- Flyway auto-discovery migration은 추가하지 않는다.

## SR-6F-D 승인값

- projection authority는 append-only `search_ctr_projection_snapshot_v1`이다.
- 유일한 write 경로는 `jc_security_owner` 소유 `write_search_ctr_projection_v1`이다.
- 애플리케이션은 window, idempotency key, expected predecessor, producer build만 전달한다.
- denominator, numerator, CTR, canonical payload, fingerprint, projection ID는 writer 내부에서 계산한다.
- 동일 semantic payload는 새 row를 만들지 않고 `DUPLICATE`를 반환한다.
- 동일 idempotency key와 다른 semantic payload는 `IDEMPOTENCY_CONFLICT`다.
- 변경된 payload는 현재 head와 expected predecessor가 정확히 일치할 때만 새 append-only row로 저장한다.
- predecessor 불일치는 `PREDECESSOR_CONFLICT`다.
- predecessor lineage는 replacement 계보일 뿐 `SUPERSEDED` finality 상태를 생성하지 않는다.
- projection payload와 writer 반환에는 user/subject/session/exposure/click/raw-query 식별자를 넣지 않는다.
- canonical package는 `journey-connect-db-v2.8/06..07`, Testcontainers global labels는 `59..60`이다.
- runtime DB login role은 실제 writer 사용 전 `jc_reliability` membership을 별도 운영 설정으로 가져야 한다.

## 계속 금지

- public/internal evaluation endpoint
- dashboard, alert
- `SETTLED`·`SUPERSEDED` finality state
- user/subject/session/raw query segment
- Reliability의 raw identity/evidence/projection table 직접 접근
- merge, deploy, production activation
