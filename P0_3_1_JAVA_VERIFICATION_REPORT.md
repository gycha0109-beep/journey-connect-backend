# P0-3.1 Java-only verification conversion

## Changed

- Removed backend/recommendation Python, Node, TypeScript-oracle, and shell verification executables.
- Replaced P0, P0-2, and P0-3 static gates with JUnit Java tests.
- Added Gradle task: `p0Verification`.
- Added Java-owned golden-fixture and framework-isolation gates to `:jc-recommendation-core:check`.
- Updated GitHub Actions to Java 21 + Gradle only.
- Added external PostgreSQL mode for environments without Docker.

## Verified

- `testClasses`: PASS
- `p0Verification`: PASS — 6 tests, 0 failures
- `:jc-recommendation-core:check`: PASS
- Canonical SQL 01~14 Java splitter test: PASS
- Python/Node/shell verifier residue gate: PASS

PostgreSQL runtime integration was not executed in this environment because neither Docker nor a PostgreSQL server was available.

## Commands

```powershell
Push-Location .\jc-backend
.\gradlew.bat p0Verification --stacktrace
.\gradlew.bat :jc-recommendation-core:check --stacktrace
.\gradlew.bat clean test --stacktrace
Pop-Location
```

For Docker-free PostgreSQL execution, see `POSTGRES_INTEGRATION_TEST.md`.
