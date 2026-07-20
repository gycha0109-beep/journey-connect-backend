# IP-11.5 External Attestation and Regression

## Executed directly

- Java 21 `javac --release 21 -Xlint:all -Werror`
- IP-1, IP-3..IP-8 Search contract mains
- IP-11.5 technical contract main
- Recommendation Foundation/Wave1..7, Golden, Isolation, P1 17, P2 23
- protected SHA, SQL 01..26 SHA, static security/activation/privacy scans

## Not executed

Gradle 8.14.5, JUnit/Spring context, PostgreSQL/Testcontainers, P0/P1/P2 backend tasks and `check` are `NOT EXECUTED — USER-DIRECTED SKIP`. Migration 27/28 therefore has static evidence only, not PostgreSQL replay attestation.

## Required external closure

Run exact final ZIP SHA with Java 21/Gradle 8.14.5, tasks `ip9*`, `ip10*`, `ip115*`, backend test/check/P0/P1/P2 and PostgreSQL 15 migration replay. Submit command logs, JUnit XML, DB route and source manifest. Any source change invalidates attestation.

## 보호 결론

- Production shadow: `DISABLED`
- Effective production sampling: `0 BPS`
- Legacy `/api/v1/explore` response authority: `legacy`
- Search response cutover: `NOT STARTED`
- Recommendation exposure/persistence/release authority: `NONE`
- IP-12: `HOLD`
- Gradle/Spring/PostgreSQL: `NOT EXECUTED — USER-DIRECTED SKIP`
