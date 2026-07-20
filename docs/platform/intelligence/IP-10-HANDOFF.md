# IP-10 Handoff

## 문서 정보

| 항목 | 값 |
|---|---|
| 계약 ID | `ip-10-handoff-v1` |
| IP-9 상태 | `IMPLEMENTATION_COMPLETE / EXTERNAL_ATTESTATION_PENDING` |
| IP-10 상태 | `IMPLEMENTATION_COMPLETE / EXTERNAL_ATTESTATION_PENDING` |
| production shadow | `DISABLED` |
| IP-11 | `HOLD` |

## 완료

- `search-shadow-test`, `search-shadow-stage` explicit activation graph
- default/prod fail-closed disabled
- sampling 기본 0
- synthetic in-memory runtime input provider
- IP-5 Search Runtime 실제 retrieval/ranking 실행
- bounded dispatch/runtime executor와 timeout/cancellation
- IP-6/IP-7 comparison 및 bounded memory-only evidence
- production Controller 추가 변경 없음
- IP-10 Gradle task 2개
- 문서 5개와 verification evidence
- 자체 리뷰 3회 및 보완

## 검증 상태

```text
Direct Java/Search/Recommendation/IP-10 regression: PASS
Gradle/JUnit/Spring regression: NOT EXECUTED — USER-DIRECTED SKIP
PostgreSQL/Testcontainers: NOT EXECUTED — USER-DIRECTED SKIP
```

## 보호 상태

- protected Recommendation source: 320/320 exact
- canonical SQL: 26/26 exact
- PostController: IP-9 approved delta 그대로, IP-10 추가 변경 없음
- PostService/Repository/DTO/JPQL/SecurityConfig: exact
- production resources: exact, enable property 없음
- Recommendation/P2 exposure/metric: unchanged

## IP-11 HOLD

다음 owner decision 및 external attestation 전 진입 금지:

- production activation/rollback/kill-switch owner
- authoritative runtime input source
- executor/queue/latency/error budget
- sampling ceiling
- query/evidence retention과 개인정보 승인
- observability destination
- controlled cohort 및 emergency disable 절차
- Gradle 8.14.5 + backend Spring + PostgreSQL/Testcontainers PASS
