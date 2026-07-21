package com.jc.backend;

import java.util.List;
import org.springframework.boot.test.util.TestPropertyValues;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.testcontainers.containers.Container;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.MountableFile;

/** Starts or connects to a canonical PostgreSQL database and applies the reviewed SQL. */
public final class CanonicalPostgresInitializer
        implements ApplicationContextInitializer<ConfigurableApplicationContext> {

    private static final List<String> SCRIPTS = List.of(
            "01_initial_schema.sql",
            "02_seed.sql",
            "03_smoke_test.sql",
            "04_admin_support.sql",
            "05_security_roles.sql",
            "06_security_smoke_test.sql",
            "07_recommendation_storage.sql",
            "08_recommendation_security_roles.sql",
            "09_recommendation_smoke_test.sql",
            "10_backend_runtime.sql",
            "11_backend_runtime_security_roles.sql",
            "12_backend_runtime_smoke_test.sql",
            "13_backend_role_routing.sql",
            "14_backend_role_routing_smoke_test.sql",
            "15_backend_role_runtime_fix.sql",
            "16_backend_role_runtime_fix_smoke_test.sql",
            "17_recommendation_run_exploration_partition_fix.sql",
            "18_recommendation_run_exploration_partition_fix_smoke_test.sql",
            "19_recommendation_replay_audit.sql",
            "20_recommendation_replay_audit_smoke_test.sql",
            "21_recommendation_behavior_runtime.sql",
            "22_recommendation_behavior_runtime_smoke_test.sql",
            "23_recommendation_p1_profile_policy.sql",
            "24_recommendation_p1_profile_policy_smoke_test.sql",
            "25_recommendation_p2_evaluation_release.sql",
            "26_recommendation_p2_evaluation_release_smoke_test.sql",
            "27_search_document_projection.sql",
            "28_search_document_projection_smoke_test.sql");

    private static final String EXTERNAL_URL = setting("jc.test.db.url", "JC_TEST_DB_URL", "");
    private static final String EXTERNAL_USERNAME = setting(
            "jc.test.db.username", "JC_TEST_DB_USERNAME", "postgres");
    private static final String EXTERNAL_PASSWORD = setting(
            "jc.test.db.password", "JC_TEST_DB_PASSWORD", "postgres");
    private static final boolean EXTERNAL_RESET = Boolean.parseBoolean(setting(
            "jc.test.db.reset", "JC_TEST_DB_RESET", "false"));
    private static final PostgreSQLContainer<?> POSTGRES = EXTERNAL_URL.isBlank() ? createContainer() : null;
    private static boolean externalReady;

    @Override
    public void initialize(ConfigurableApplicationContext context) {
        if (!EXTERNAL_URL.isBlank()) {
            ensureExternalReady();
            applyProperties(context, EXTERNAL_URL, EXTERNAL_USERNAME, EXTERNAL_PASSWORD);
            return;
        }

        ensureContainerStarted();
        applyProperties(context, POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
    }

    private static void applyProperties(
            ConfigurableApplicationContext context,
            String jdbcUrl,
            String username,
            String password) {
        TestPropertyValues.of(
                "spring.flyway.enabled=false",
                "spring.datasource.url=" + jdbcUrl,
                "spring.datasource.username=" + username,
                "spring.datasource.password=" + password,
                "spring.datasource.driver-class-name=org.postgresql.Driver",
                "app.security.jwt-secret=ip125-test-only-jwt-secret-0123456789abcdef0123456789abcdef",
                "app.cors.allowed-origins=http://localhost:5173")
                .applyTo(context.getEnvironment());
    }

    private static PostgreSQLContainer<?> createContainer() {
        String image = setting(
                "jc.test.postgres.image", "JC_TEST_POSTGRES_IMAGE", "postgres:15-alpine");
        PostgreSQLContainer<?> container = new PostgreSQLContainer<>(image)
                .withDatabaseName("journey_connect")
                .withUsername("postgres")
                .withPassword("postgres");
        for (String script : SCRIPTS) {
            container.withCopyFileToContainer(
                    MountableFile.forClasspathResource("db/canonical/" + script),
                    "/sql/" + script);
        }
        return container;
    }

    private static synchronized void ensureContainerStarted() {
        if (POSTGRES.isRunning()) {
            return;
        }
        POSTGRES.start();
        for (String script : SCRIPTS) {
            try {
                Container.ExecResult result = POSTGRES.execInContainer(
                        "psql",
                        "-v", "ON_ERROR_STOP=1",
                        "-U", POSTGRES.getUsername(),
                        "-d", POSTGRES.getDatabaseName(),
                        "-f", "/sql/" + script);
                if (result.getExitCode() != 0) {
                    throw new IllegalStateException(
                            "Canonical PostgreSQL bootstrap failed at " + script
                                    + "\nstdout:\n" + result.getStdout()
                                    + "\nstderr:\n" + result.getStderr());
                }
            } catch (Exception exception) {
                POSTGRES.stop();
                throw new IllegalStateException(
                        "Canonical PostgreSQL bootstrap failed at " + script,
                        exception);
            }
        }
    }

    private static synchronized void ensureExternalReady() {
        if (externalReady) {
            return;
        }
        if (EXTERNAL_RESET) {
            CanonicalSqlBootstrapper.resetAndApply(
                    EXTERNAL_URL, EXTERNAL_USERNAME, EXTERNAL_PASSWORD, SCRIPTS);
        }
        externalReady = true;
    }

    private static String setting(String systemProperty, String environmentVariable, String fallback) {
        String property = System.getProperty(systemProperty);
        if (property != null && !property.isBlank()) {
            return property;
        }
        String environment = System.getenv(environmentVariable);
        return environment == null || environment.isBlank() ? fallback : environment;
    }
}
