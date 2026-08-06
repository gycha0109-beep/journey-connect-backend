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

class SearchCtrManualActivationSqlContractTest {

    @Test
    void canonicalSqlProvidesIdentityFreeHeadAndAppendOnlyRunAudit() throws IOException {
        String sql = resource("/db/canonical/61_search_ctr_nonprod_manual_activation_foundation.sql");

        for (String required : new String[] {
                "CREATE TABLE public.search_ctr_manual_run_audit_v1",
                "CREATE TRIGGER search_ctr_manual_run_append_only",
                "CREATE OR REPLACE FUNCTION public.read_search_ctr_projection_head_v1",
                "CREATE OR REPLACE FUNCTION public.execute_search_ctr_manual_v1",
                "NONPRODUCTION_MANUAL",
                "finality_write_attempted = false",
                "GRANT EXECUTE ON FUNCTION public.read_search_ctr_projection_head_v1",
                "GRANT EXECUTE ON FUNCTION public.execute_search_ctr_manual_v1",
                "TO jc_reliability"
        }) {
            assertTrue(sql.contains(required), "manual activation SQL missing: " + required);
        }

        String headResult = between(
                sql,
                "CREATE OR REPLACE FUNCTION public.read_search_ctr_projection_head_v1(",
                ")\nLANGUAGE plpgsql");
        for (String prohibited : new String[] {
                "user_id", "subject_ref", "session_id", "exposure_id", "click_event_id", "raw_query"
        }) {
            assertFalse(headResult.contains(prohibited), "head boundary leaks: " + prohibited);
        }
        assertTrue(sql.contains("REVOKE ALL ON public.search_ctr_manual_run_audit_v1"));
        assertFalse(sql.contains("GRANT SELECT ON public.search_ctr_manual_run_audit_v1 TO jc_reliability"));
        assertFalse(sql.contains("GRANT INSERT ON public.search_ctr_manual_run_audit_v1 TO jc_reliability"));
    }

    @Test
    void JavaRunnerRemainsDefaultOffAndHasNoEndpointOrScheduler() throws IOException {
        String properties = readSource(
                "jc-backend/src/main/java/com/jc/backend/intelligence/search/SearchCtrManualActivationProperties.java");
        String configuration = readSource(
                "jc-backend/src/main/java/com/jc/backend/intelligence/search/SearchCtrManualActivationConfiguration.java");
        String runner = readSource(
                "jc-backend/src/main/java/com/jc/backend/intelligence/search/SearchCtrManualActivationRunner.java");
        String verifier = readSource(
                "jc-backend/src/main/java/com/jc/backend/database/DatabaseRoleCapabilityVerifier.java");

        assertTrue(properties.contains("private boolean killSwitch = true"));
        assertTrue(configuration.contains("havingValue = \"true\""));
        assertTrue(verifier.contains("app.database.role-routing.require-reliability:false"));
        assertTrue(runner.contains("case STORED, DUPLICATE"));
        assertTrue(runner.contains("blind retry is forbidden"));
        for (String prohibited : new String[] {"@RestController", "@Controller", "@Scheduled"}) {
            assertFalse(configuration.contains(prohibited));
            assertFalse(runner.contains(prohibited));
        }
    }

    @Test
    void sourcePackageMatchesCanonicalBootstrap() throws IOException {
        assertEquals(
                normalized(readSource(
                        "database/journey-connect-db-v2.8/08_search_ctr_nonprod_manual_activation_foundation.sql")),
                resource("/db/canonical/61_search_ctr_nonprod_manual_activation_foundation.sql"));
        assertEquals(
                normalized(readSource(
                        "database/journey-connect-db-v2.8/09_search_ctr_nonprod_manual_activation_smoke_test.sql")),
                resource("/db/canonical/62_search_ctr_nonprod_manual_activation_smoke_test.sql"));
    }

    @Test
    void noFlywayAutodiscoveryMigrationIsIntroduced() {
        assertTrue(getClass().getResource(
                "/db/migration/V61__search_ctr_nonprod_manual_activation_foundation.sql") == null);
        assertTrue(getClass().getResource(
                "/db/migration/V62__search_ctr_nonprod_manual_activation_smoke_test.sql") == null);
    }

    private String resource(String path) throws IOException {
        try (InputStream input = getClass().getResourceAsStream(path)) {
            if (input == null) throw new IOException("Missing SQL resource: " + path);
            return normalized(new String(input.readAllBytes(), StandardCharsets.UTF_8));
        }
    }

    private static String readSource(String relativePath) throws IOException {
        return Files.readString(repositoryRoot().resolve(relativePath));
    }

    private static String between(String value, String start, String end) {
        int startIndex = value.indexOf(start);
        int endIndex = startIndex < 0 ? -1 : value.indexOf(end, startIndex + start.length());
        if (startIndex < 0 || endIndex < 0) {
            throw new IllegalArgumentException("SQL contract section not found");
        }
        return value.substring(startIndex + start.length(), endIndex);
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
