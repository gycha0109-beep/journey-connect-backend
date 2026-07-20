# PostgreSQL integration test execution

The backend test suite supports two PostgreSQL modes.

## 1. Docker/Testcontainers

From `jc-backend`:

```powershell
.\gradlew.bat clean test --stacktrace
```

Optional image override:

```powershell
$env:JC_TEST_POSTGRES_IMAGE = "postgres:18-alpine"
.\gradlew.bat clean test --stacktrace
```

## 2. External PostgreSQL without Docker

Use a dedicated empty test database. The account must be able to create roles and own database objects because canonical SQL `01~16` verifies role security.

```powershell
$env:JC_TEST_DB_URL = "jdbc:postgresql://127.0.0.1:5432/journey_connect_test"
$env:JC_TEST_DB_USERNAME = "postgres"
$env:JC_TEST_DB_PASSWORD = "temporary-test-password"
$env:JC_TEST_DB_RESET = "true"

Push-Location .\jc-backend
.\gradlew.bat clean test --stacktrace
Pop-Location
```

`JC_TEST_DB_RESET=true` drops and recreates the `public` schema before applying canonical SQL `01~16`. It must never be used against a shared or production database.

When `JC_TEST_DB_RESET=false`, the test suite assumes that the canonical schema has already been applied.

## Offline PostgreSQL bundle for the assistant environment

The reliable offline handoff is a Debian PostgreSQL root filesystem exported from Docker. On the user's machine:

```powershell
docker pull postgres:15-bookworm
docker create --name jc-postgres-export postgres:15-bookworm
docker export -o postgres15-bookworm-rootfs.tar jc-postgres-export
docker rm jc-postgres-export
```

Upload `postgres15-bookworm-rootfs.tar`. The assistant can extract it, start PostgreSQL locally without Docker, and run the test suite through the external-DB environment variables above.


## Verified offline handoff

The following bundle set was verified successfully in the assistant environment:

- `postgres15-bookworm-rootfs.tar`
- Gradle 8.14.5 distribution and dependency cache
- Journey Connect source ZIP

The root filesystem was started as PostgreSQL 15.18 on an isolated local port and the backend completed all 42 PostgreSQL integration tests.
