package com.jc.backend.intelligence.search;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class SearchCtrReliabilityRoleConvergenceSqlContractTest {

    @Test
    void convergenceMakesReliabilityNoLoginNoInheritWithoutMemberships() throws IOException {
        String convergence = resource(
                "/db/canonical/63_search_ctr_reliability_role_noinherit_convergence.sql");
        String smoke = resource(
                "/db/canonical/64_search_ctr_reliability_role_noinherit_smoke_test.sql");

        for (String required : new String[] {
                "ALTER ROLE jc_reliability NOINHERIT",
                "reliability_role.rolinherit",
                "pg_catalog.pg_auth_members",
                "jc_reliability memberships must be empty before convergence",
                "jc_reliability NOLOGIN NOINHERIT convergence failed"
        }) {
            assertTrue(convergence.contains(required), "convergence SQL missing: " + required);
        }
        assertTrue(smoke.contains("jc_reliability does not satisfy NOLOGIN NOINHERIT isolation"));
        assertTrue(smoke.contains("execute_search_ctr_manual_v1"));
        assertFalse(convergence.contains("GRANT jc_reliability"));
        assertFalse(convergence.contains("REVOKE ALL ON"));
        assertFalse(convergence.contains("DROP ROLE"));
    }

    @Test
    void sourcePackageMatchesCanonicalBootstrap() throws IOException {
        Path root = repositoryRoot();
        assertEquals(
                normalized(Files.readString(root.resolve(
                        "database/journey-connect-db-v2.8/10_search_ctr_reliability_role_noinherit_convergence.sql"))),
                resource("/db/canonical/63_search_ctr_reliability_role_noinherit_convergence.sql"));
        assertEquals(
                normalized(Files.readString(root.resolve(
                        "database/journey-connect-db-v2.8/11_search_ctr_reliability_role_noinherit_smoke_test.sql"))),
                resource("/db/canonical/64_search_ctr_reliability_role_noinherit_smoke_test.sql"));
    }

    @Test
    void noFlywayAutodiscoveryMigrationIsIntroduced() {
        assertTrue(getClass().getResource(
                "/db/migration/V63__search_ctr_reliability_role_noinherit_convergence.sql") == null);
        assertTrue(getClass().getResource(
                "/db/migration/V64__search_ctr_reliability_role_noinherit_smoke_test.sql") == null);
    }

    private String resource(String path) throws IOException {
        try (InputStream input = getClass().getResourceAsStream(path)) {
            if (input == null) throw new IOException("Missing SQL resource: " + path);
            return normalized(new String(input.readAllBytes(), StandardCharsets.UTF_8));
        }
    }

    private static String normalized(String value) {
        return value.replace("\r\n", "\n").replace('\r', '\n');
    }

    private static Path repositoryRoot() {
        Path current = Path.of("").toAbsolutePath().normalize();
        for (Path candidate = current; candidate != null; candidate = candidate.getParent()) {
            if (Files.isDirectory(candidate.resolve("database/journey-connect-db-v2.8"))
                    && Files.isRegularFile(candidate.resolve("jc-backend/build.gradle.kts"))) {
                return candidate;
            }
        }
        throw new IllegalStateException("repository root not found from " + current);
    }
}
