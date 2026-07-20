# IP-1 Verification Summary

- Contract Java compile: PASS (`--release 21 -Xlint:all -Werror`)
- Contract executable: PASS, 739 assertions
- Recommendation adapter Java compile: PASS (`--release 21 -Xlint:all -Werror`)
- Recommendation adapter executable: PASS, 226 assertions
- Recommendation Core foundation/Wave1..7/golden/isolation: PASS
- P1 Core: PASS, 17 scenarios
- P2 Core: PASS, 23 scenarios
- Protected SHA-256: PASS, 320/320 exact, diff 0
- Canonical SQL 01..26: unchanged
- JSON fixture syntax/round-trip contract: PASS, 11 fixtures
- Document structure/relative links: PASS
- Gradle/Spring backend/root regression: BLOCKED; Gradle 8.14.5 distribution could not be downloaded because `services.gradle.org` DNS access was unavailable
- PostgreSQL runtime: not rerun; no DB or SQL change
- Self-review 1: found 4 / fixed 4 / held 0
- Self-review 2: found 3 / fixed 3 / held 0
