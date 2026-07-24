package org.testcontainers.containers;

/**
 * Test-classpath-only compatibility bridge for Testcontainers 2.x, which moved
 * module containers to org.testcontainers.<module>. The RCA-1B runner remains
 * isolated from production classpaths and delegates all behavior to the 2.x
 * PostgreSQL module implementation.
 */
public final class PostgreSQLContainer<SELF extends PostgreSQLContainer<SELF>>
        extends org.testcontainers.postgresql.PostgreSQLContainer<SELF> {

    public PostgreSQLContainer(String dockerImageName) {
        super(dockerImageName);
    }
}
