# P0-2.1 Hibernate Schema Validation Hotfix

## Failure

Hibernate 6 reported:

```text
Schema-validation: wrong column type encountered in column [country_code]
in table [regions]; found [bpchar (Types#CHAR)], but expecting [char(2) (Types#VARCHAR)]
```

## Cause

`@Column(columnDefinition = "char(2)")` controls generated DDL text but did not change the
Hibernate JDBC type selected for the Java `String`. Hibernate therefore validated the mapping
as `VARCHAR`, while PostgreSQL correctly exposed the canonical `CHAR(2)` column as `bpchar` /
`Types.CHAR`.

## Fix

`Region.countryCode` now declares:

```java
@JdbcTypeCode(SqlTypes.CHAR)
@Column(name = "country_code", nullable = false, length = 2, columnDefinition = "char(2)")
private String countryCode;
```

The database schema remains unchanged. Canonical `regions.country_code CHAR(2)` is preserved.

## Verification command

```powershell
.\gradlew.bat test --tests "com.jc.backend.CanonicalSchemaJpaValidationTest" --stacktrace
.\gradlew.bat clean test --stacktrace
```

If Hibernate exposes another schema mismatch after this fix, repair the next first validator
message rather than changing all downstream tests. Application-context failures are cascading
failures from the schema gate.
