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

class SearchCtrAggregateSqlContractTest {

    @Test
    void canonicalSqlDeclaresAggregateOnlyIdentityBoundary() throws IOException {
        String sql = resource("/db/canonical/57_search_ctr_aggregate_boundary.sql");

        for (String required : new String[] {
                "CREATE ROLE jc_reliability NOLOGIN NOSUPERUSER",
                "CREATE TABLE public.search_ctr_evaluation_access_audit_v1",
                "CREATE OR REPLACE FUNCTION public.evaluate_search_ctr_v1",
                "SECURITY DEFINER",
                "PARTITION BY behavior.event_id",
                "ORDER BY exposure.exposed_at DESC, exposure.received_at DESC, exposure.exposure_id ASC",
                "behavior.occurred_at >= exposure.exposed_at",
                "behavior.occurred_at < exposure.exposed_at + interval '30 minutes'",
                "GRANT EXECUTE ON FUNCTION public.evaluate_search_ctr_v1",
                "TO jc_reliability"
        }) {
            assertTrue(sql.contains(required), "canonical SQL missing: " + required);
        }
    }

    @Test
    void reliabilityCannotReadRawIdentityOrEvidenceTables() throws IOException {
        String sql = resource("/db/canonical/57_search_ctr_aggregate_boundary.sql");

        assertTrue(sql.contains("REVOKE ALL ON public.platform_identity_mapping_v1"));
        assertTrue(sql.contains("public.search_exposure_event_v1"));
        assertTrue(sql.contains("public.recommendation_behavior_event"));
        assertTrue(sql.contains("FROM jc_reliability"));
        assertFalse(sql.contains("GRANT SELECT ON public.platform_identity_mapping_v1 TO jc_reliability"));
        assertFalse(sql.contains("GRANT SELECT ON public.search_exposure_event_v1 TO jc_reliability"));
        assertFalse(sql.contains("GRANT SELECT ON public.recommendation_behavior_event TO jc_reliability"));
        assertFalse(sql.contains("CREATE TABLE public.search_click_attribution_v1"));
    }

    @Test
    void aggregateResultAndAuditRemainIdentityFree() throws IOException {
        String sql = resource("/db/canonical/57_search_ctr_aggregate_boundary.sql");
        String returnContract = between(sql, "RETURNS TABLE (", ")\nLANGUAGE plpgsql");
        String auditContract = between(
                sql,
                "CREATE TABLE public.search_ctr_evaluation_access_audit_v1 (",
                ");\nCREATE INDEX search_ctr_evaluation_audit_retention_idx");

        for (String prohibited : new String[] {
                "user_id", "subject_ref", "session_id", "exposure_id", "click_event_id", "raw_query"
        }) {
            assertFalse(returnContract.contains(prohibited), "result leaks: " + prohibited);
            assertFalse(auditContract.contains(prohibited), "audit leaks: " + prohibited);
        }
        assertTrue(sql.contains("RAISE EXCEPTION 'search CTR identity bridge unavailable for invalidated mapping'"));
        assertTrue(sql.contains("'PROVISIONAL'::varchar"));
        assertTrue(sql.contains("CASE WHEN counts.denominator = 0 THEN NULL"));
    }

    @Test
    void sourcePackageMatchesCanonicalBootstrap() throws IOException {
        Path root = repositoryRoot();
        assertEquals(
                normalized(Files.readString(root.resolve(
                        "database/journey-connect-db-v2.8/04_search_ctr_aggregate_boundary.sql"))),
                resource("/db/canonical/57_search_ctr_aggregate_boundary.sql"));
        assertEquals(
                normalized(Files.readString(root.resolve(
                        "database/journey-connect-db-v2.8/05_search_ctr_aggregate_boundary_smoke_test.sql"))),
                resource("/db/canonical/58_search_ctr_aggregate_boundary_smoke_test.sql"));
    }

    @Test
    void noFlywayAutodiscoveryMigrationIsIntroduced() {
        assertTrue(getClass().getResource("/db/migration/V57__search_ctr_aggregate_boundary.sql") == null);
        assertTrue(getClass().getResource("/db/migration/V58__search_ctr_aggregate_boundary_smoke_test.sql") == null);
    }

    private String resource(String path) throws IOException {
        try (InputStream input = getClass().getResourceAsStream(path)) {
            if (input == null) throw new IOException("Missing SQL resource: " + path);
            return normalized(new String(input.readAllBytes(), StandardCharsets.UTF_8));
        }
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
