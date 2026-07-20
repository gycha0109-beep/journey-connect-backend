# IP-11.5 Resource Budget and Observability Contract

## IP-11.75 governance amendment

| 항목 | 승인된 initial pilot 상한 | 현재 상태 |
|---|---:|---|
| core concurrency | 1 | capability present; activation disconnected |
| maximum concurrency | 2 | capability present; activation disconnected |
| queue capacity | 8 | capability present; activation disconnected |
| runtime timeout | 200ms | capability present |
| hard cancellation timeout | 300ms 이하 | current provisional implementation 250ms; 승인 상한 이내 |
| maximum candidate count | 100 | capability present |
| effective sample | 0 BPS | 강제 유지 |
| initial pilot ceiling | 10 BPS | 승인된 상한; 활성화 값 아님 |

`50 BPS`, `100 BPS`는 향후 제안값이며 각각 별도 승인과 관찰 증거가 필요하다. 현재 production controls의 provisional technical policy가 100 BPS를 표현할 수 있으므로 IP-12에서는 승인 ceiling 10 BPS와의 `min()`/validation 결속을 외부 attestation 전에 검증해야 한다.

- 승인된 instrumentation target: Spring Boot structured logs + Micrometer metrics
- 현재 확인된 구현: privacy-safe metric abstraction, no-op/default sink, in-memory test sink, Spring Boot SLF4J 기반
- Micrometer production binding과 metric 확인 절차는 IP-12 또는 별도 integration 단계에서 검증 필요
- Prometheus, Grafana, 외부 APM, 중앙 로그 플랫폼: `DEFERRED`
- automated paging/on-call channel: `OPEN_OPERATIONAL_DETAIL`
- external dashboard 부재는 instrumentation policy 승인을 취소하지 않지만 controlled production activation 전 수동 확인 절차와 책임자 지정이 필수

The IP-11.5 technical implementation remains unchanged. Governance ceiling is 10 BPS while current effective sample remains 0 BPS. IP-12 must verify that any activation configuration cannot exceed the approved 10 BPS ceiling even though the provisional technical policy can represent a broader value.
