# IP-11 Privacy, Security, Retention and Observability

## 상태

`POLICY_APPROVED_WITH_CONDITIONS / STORAGE_AND_EXTERNAL_OPERATIONS_PENDING`

- Raw query, raw/normalized query 원문, raw identity, JWT subject, session ID, full request/response payload: `PROHIBITED`
- Aggregate/bucket evidence: 최대 `14일`; persistence 구현 시 생성 시각 기반 자동 삭제 필수
- Error summary: 최대 `30일`; enum category, bucket, version만 허용
- 접근: `Project Owner`, `Backend Owner`
- 개인정보·raw data·오기록·정책 위반·잘못된 결속 발견 시 즉시 삭제
- 현재 production sink는 no-op이고 persistent storage/cleanup job은 없음
- 따라서 `POLICY_APPROVED / STORAGE_IMPLEMENTATION_PENDING`; production persistence는 계속 `DISABLED`

- 승인된 instrumentation target: Spring Boot structured logs + Micrometer metrics
- 현재 확인된 구현: privacy-safe metric abstraction, no-op/default sink, in-memory test sink, Spring Boot SLF4J 기반
- Micrometer production binding과 metric 확인 절차는 IP-12 또는 별도 integration 단계에서 검증 필요
- Prometheus, Grafana, 외부 APM, 중앙 로그 플랫폼: `DEFERRED`
- automated paging/on-call channel: `OPEN_OPERATIONAL_DETAIL`
- external dashboard 부재는 instrumentation policy 승인을 취소하지 않지만 controlled production activation 전 수동 확인 절차와 책임자 지정이 필수

## Security controls

- private/deleted/unpublished/moderation-blocked content prohibited
- aggregate/bucket-only metrics and evidence
- high-cardinality identity/query/post/document tags prohibited
- persistence introduction triggers Security/Privacy re-review
