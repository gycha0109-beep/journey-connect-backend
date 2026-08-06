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

class SearchCtrProjectionWriterSqlContractTest {

    @Test
    void canonicalSqlDeclaresAppendOnlySingleWriterBoundary() throws IOException {
        String sql = resource("/db/canonical/59_search_ctr_projection_writer.sql");

        for (String required : new String[] {
                "CREATE TABLE public.search_ctr_projection_snapshot_v1",
                "CREATE TRIGGER search_ctr_projection_append_only",
                "CREATE OR REPLACE FUNCTION public.write_search_ctr_projection_v1",
                "SECURITY DEFINER",
                "pg_advisory_xact_lock",
                "search-ctr-single-writer-v1",
                "'DUPLICATE'::varchar",
                "'IDEMPOTENCY_CONFLICT'::varchar",
                "'PREDECESSOR_CONFLICT'::varchar",
                "'STORED'::varchar",
                "GRANT EXECUTE ON FUNCTION public.write_search_ctr_projection_v1",
                "TO jc_reliability"
        }) {
            assertTrue(sql.contains(required), "canonical SQL missing: " + required);
        }
    }

    @Test
    void writerComputesCanonicalPayloadInsideSecurityBoundary() throws IOException {
        String sql = resource("/db/canonical/59_search_ctr_projection_writer.sql");
        String parameters = between(
                sql,
                "CREATE OR REPLACE FUNCTION public.write_search_ctr_projection_v1(",
                ")\nRETURNS TABLE");

        assertTrue(sql.contains("FROM public.evaluate_search_ctr_v1("));
        assertTrue(sql.contains("v_payload := convert_to(v_payload_text, 'UTF8')"));
        assertTrue(sql.contains("v_fingerprint := public.recommendation_sha256_hex(v_payload)"));
        assertFalse(parameters.contains("eligible_exposure_count"));
        assertFalse(parameters.contains("attributed_exposure_count"));
        assertFalse(parameters.contains("ctr_basis_points"));
        assertFalse(parameters.contains("canonical_payload"));
        assertFalse(parameters.contains("payload_fingerprint"));
    }

    @Test
    void snapshotAndWriterResultRemainIdentityFree() throws IOException {
        String sql = resource("/db/canonical/59_search_ctr_projection_writer.sql");
        String table = between(
                sql,
                "CREATE TABLE public.search_ctr_projection_snapshot_v1 (",
                ");\n\nCREATE INDEX search_ctr_projection_window_idx");
        String result = between(sql, "RETURNS TABLE (", ")\nLANGUAGE plpgsql");

        for (String prohibited : new String[] {
                "user_id", "subject_ref", "session_id", "exposure_id", "click_event_id", "raw_query"
        }) {
            assertFalse(table.contains(prohibited), "snapshot leaks: " + prohibited);
            assertFalse(result.contains(prohibited), "writer result leaks: " + prohibited);
        }
        assertTrue(sql.contains("REVOKE ALL ON public.search_ctr_projection_snapshot_v1"));
        assertTrue(sql.contains("FROM PUBLIC, jc_app, jc_auth, jc_admin, jc_recommendation, jc_reliability"));
        assertFalse(sql.contains("GRANT SELECT ON public.search_ctr_projection_snapshot_v1 TO jc_reliability"));
        assertFalse(sql.contains("GRANT INSERT ON public.search_ctr_projection_snapshot_v1 TO jc_reliability"));
    }

    @Test
    void sourcePackageMatchesCanonicalBootstrap() throws IOException {
        Path root = repositoryRoot();
        assertEquals(
                normalized(Files.readString(root.resolve(
                        "database/journey-connect-db-v2.8/06_search_ctr_projection_writer.sql"))),
                resource("/db/canonical/59_search_ctr_projection_writer.sql"));
        assertEquals(
                normalized(Files.readString(root.resolve(
                        "database/journey-connect-db-v2.8/07_search_ctr_projection_writer_smoke_test.sql"))),
                resource("/db/canonical/60_search_ctr_projection_writer_smoke_test.sql"));
    }

    @Test
    void noFlywayAutodiscoveryMigrationIsIntroduced() {
        assertTrue(getClass().getResource("/db/migration/V59__search_ctr_projection_writer.sql") == null);
        assertTrue(getClass().getResource("/db/migration/V60__search_ctr_projection_writer_smoke_test.sql") == null);
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
