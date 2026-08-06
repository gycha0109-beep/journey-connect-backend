# SR-6F Search CTR Approval Record

## 상태

```text
Initial decision date: 2026-08-05 KST
SR-6F-C authorization date: 2026-08-06 KST
SR-6F-D authorization date: 2026-08-06 KST
SR-6F-E governance date: 2026-08-06 KST
SR-6F-F foundation date: 2026-08-06 KST
Decision: APPROVED_BY_PROJECT_OWNER
Metric: search-click-through-rate-v1
SR-6F-A design: APPROVED
SR-6F-B Java contracts: VERIFIED
SR-6F-C aggregate-only DB boundary: VERIFIED
SR-6F-D projection snapshot/single writer: VERIFIED
SR-6F-E activation/finality governance: VERIFIED
Verified SR-6F-E implementation head: 20022a39d740cee8052e2b5c113d99a759e343d6
SR-6F-F non-production manual foundation: VERIFIED
Verified SR-6F-F implementation head: 349aef3f489cddb9190856dac734be41a3086afc
Runtime mode: DISABLED
Manual runner: DEFAULT_OFF
Finality write: NOT_AUTHORIZED
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
- `jc_security_owner`에는 aggregate 함수 내부 호출을 위한 최소 `EXECUTE`만 부여하며 raw table 권한은 추가하지 않는다.
- canonical package는 `journey-connect-db-v2.8/06`, `06a`, `07`, Testcontainers global labels는 `59`, `59a`, `60`이다.
- `jc_reliability`는 허용된 routed role이지만 writer 활성화 전에는 startup 필수 capability가 아니다.
- 실제 writer 사용 전 restricted backend login에 대한 `jc_reliability` membership은 별도 운영 승인으로 활성화해야 한다.

## SR-6F-E 승인값

- projection window는 정확히 1시간이고 UTC 정각에 정렬한다.
- provisional 검토 가능 시각은 `windowEnd + 35분`이다.
- 35분은 30분 attribution window와 5분 future-skew 계약의 합이다.
- settlement 검토 threshold는 `windowEnd + 30일 35분`이다.
- threshold와 같은 시각에는 settlement를 허용하지 않고 그 이후만 후보로 본다.
- 현재 runtime mode는 `DISABLED`다.
- 다음에 승인 가능한 최초 mode는 `NONPRODUCTION_MANUAL`뿐이다.
- 최초 activation 경로로 HTTP endpoint를 허용하지 않는다.
- scheduler, production mode, dashboard, alert로 직접 건너뛰지 않는다.
- `SUPERSEDED` 상태를 추가하지 않고 predecessor lineage를 current-head 교체 근거로 사용한다.
- 향후 `SETTLED`는 기존 row UPDATE가 아니라 별도 승인된 append-only finality writer로만 생성한다.
- settlement threshold 이후 최소 1시간 간격의 두 평가가 동일 fingerprint를 가져야 finality 후보가 된다.
- Search 행동 replay age, attribution window, future-skew가 변경되면 activation policy version을 재검토한다.
- rollback은 new write 중단과 capability 제거이며 기존 append-only evidence를 삭제하지 않는다.

## SR-6F-F 승인값

- identity-free current-head read boundary는 `read_search_ctr_projection_head_v1`이다.
- `jc_reliability`는 projection table을 직접 읽지 않고 함수 결과만 소비한다.
- manual 실행과 audit는 `execute_search_ctr_manual_v1` 단일 transaction으로 결속한다.
- operational authority는 append-only `search_ctr_manual_run_audit_v1`이다.
- manual audit에는 user, subject, session, exposure, click, raw query를 저장하지 않는다.
- `finality_write_attempted`는 항상 `false`다.
- 허용 environment는 `local`, `dev`, `test`, `stage`다.
- `prod`, `production` 및 기타 environment는 거절한다.
- 한 실행은 정확히 하나의 UTC 정렬 1시간 window만 처리한다.
- provisional eligibility 이전 호출은 거절한다.
- explicit startup flag는 `app.database.role-routing.require-reliability=false`가 기본이다.
- one-shot runner는 `enabled=false`, kill switch `true`가 기본이다.
- runner는 HTTP endpoint나 scheduler를 제공하지 않는다.
- `STORED`와 `DUPLICATE`만 정상 종료하고 conflict는 자동 재시도 없이 중단한다.
- current authorized runtime mode가 `DISABLED`이므로 foundation 구현만으로 실제 write가 활성화되지 않는다.
- canonical package는 `journey-connect-db-v2.8/08..09`, Testcontainers global labels는 `61..62`다.

## SR-6F-F 검증 증거

```text
SR Search Recommendation: 31089405826 — SUCCESS
Recommendation P0 Database CI: 31089402997 — SUCCESS
Backend PR CI: 31089403711 — SUCCESS
Focused: 26 suites / 96 tests / failures 0 / errors 0 / skipped 0
Full backend: 105 suites / 370 tests / failures 0 / errors 0 / skipped 0
PostgreSQL 15: SUCCESS
PostgreSQL 18: SUCCESS
IP-12.5 protected readiness: SUCCESS
Focused artifact: sha256:a5dbd11c951973fc3900be925bf929fe8da6dd99eb4856d95c3bf991081a4fe3
Full artifact: sha256:c9d80cabab98e1ba2f9e545da90bfe8cb15f1215b09ea4a3e8f0f7a2d7317e85
```

## SR-6F-G 진입 전 필수 항목

- exact non-production environment 승인
- restricted login `jc_reliability` membership grant/revoke 절차
- 실행 window와 operator approval reference
- 실행 전/후 evidence checklist
- disable drill
- runtime mode `NONPRODUCTION_MANUAL` 승인 commit

## 계속 금지

- public/internal HTTP evaluation endpoint
- scheduler/cron activation
- production writer activation
- dashboard, alert
- `SETTLED` finality writer
- user/subject/session/raw query segment
- Reliability의 raw identity/evidence/projection/audit table 직접 접근
- merge, deploy, production activation
