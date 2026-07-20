# IP-11.75 Observability and Operational Readiness

## 승인 범위

- 승인된 instrumentation target: Spring Boot structured logs + Micrometer metrics
- 현재 확인된 구현: privacy-safe metric abstraction, no-op/default sink, in-memory test sink, Spring Boot SLF4J 기반
- Micrometer production binding과 metric 확인 절차는 IP-12 또는 별도 integration 단계에서 검증 필요
- Prometheus, Grafana, 외부 APM, 중앙 로그 플랫폼: `DEFERRED`
- automated paging/on-call channel: `OPEN_OPERATIONAL_DETAIL`
- external dashboard 부재는 instrumentation policy 승인을 취소하지 않지만 controlled production activation 전 수동 확인 절차와 책임자 지정이 필수

## 최소 수동 확인 절차

controlled pilot 전에 Backend Owner는 다음을 확인해야 한다.

1. eligible/sampled/dispatched/completed/skipped/killed 카운터
2. timeout/rejection/runtime/comparison/evidence failure 카운터
3. queue depth와 executor active count
4. runtime/total latency bucket
5. projection stale/ineligible 카운터
6. raw query, identity, session, JWT, post/document ID tag 부재
7. legacy endpoint latency/error 변화 부재

## 아직 준비되지 않은 항목

- Prometheus/Grafana/APM/central log destination
- automated paging
- on-call channel/account
- actual production environment metric binding verification

Instrumentation policy는 승인됐지만 operational destination과 external verification은 미완료다.
