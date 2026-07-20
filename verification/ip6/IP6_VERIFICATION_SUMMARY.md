# IP-6 Verification Summary

| 검증 대상 | 결과 |
|---|---|
| `jc-search-integration` main/test Java 21 compile | PASS |
| `-Xlint:all -Werror` | PASS |
| IP-6 integration contract | 972 assertions PASS |
| IP-5 runtime regression | 850 assertions PASS |
| IP-4 compatibility regression | 584 assertions PASS |
| IP-3 Search contract regression | 425 assertions PASS |
| IP-1 common contract regression | 739 assertions PASS |
| JSON fixture | 12/12 PASS |
| disabled-by-default / unknown mode | PASS |
| fail-open / timeout isolation | PASS |
| original legacy response identity/order/page envelope | PASS |
| deterministic mismatch/top-K/zero denominator | PASS |
| privacy-safe memory evidence | PASS |
| persistence/exposure/release/metric/cursor/API authority | all false |
| forbidden dependency and Spring activation scan | PASS |
| Recommendation Foundation/Wave1~7/golden/isolation | PASS |
| P1 Core | 17 scenarios PASS |
| P2 Core | 23 scenarios PASS |
| protected source | 320/320 exact match |
| canonical SQL | 26/26 exact match |
| production Controller/Service/Repository/DTO/JPQL/SecurityConfig | unchanged |
| DB migration/new SQL/index | none |
| Markdown links/contract declarations/wire form | PASS |
| Gradle Wrapper | BLOCKED before tasks: services.gradle.org DNS resolution failure |

Gradle PASS는 선언하지 않는다. 동일 소스는 Java 21 직접 컴파일과 독립 contract runner로 검증했다.
