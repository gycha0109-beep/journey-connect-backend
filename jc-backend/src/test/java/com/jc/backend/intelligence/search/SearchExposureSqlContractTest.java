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

class SearchExposureSqlContractTest {

    @Test
    void canonicalSqlDeclaresApprovedAuthorityAndRoleBoundaries() throws IOException {
        String sql = resource("/db/canonical/55_search_exposure_persistence.sql");

        for (String required : new String[] {
                "CREATE TABLE public.platform_identity_mapping_v1",
                "CREATE TABLE public.platform_identity_mapping_invalidation_v1",
                "CREATE TABLE public.platform_identity_mapping_access_audit_v1",
                "CREATE OR REPLACE FUNCTION public.resolve_platform_subject_v1",
                "CREATE TABLE public.search_exposure_event_v1",
                "CREATE TRIGGER search_exposure_append_only",
                "CREATE OR REPLACE FUNCTION public.purge_expired_search_exposure_v1",
                "GRANT EXECUTE ON FUNCTION public.resolve_platform_subject_v1",
                "GRANT SELECT,INSERT ON public.search_exposure_event_v1 TO jc_recommendation",
                "REVOKE UPDATE,DELETE,TRUNCATE ON public.search_exposure_event_v1 FROM jc_recommendation"
        }) {
            assertTrue(sql.contains(required), "canonical SQL missing: " + required);
        }
    }

    @Test
    void sqlKeepsSearchExposureSeparateAndPrivacyBounded() throws IOException {
        String sql = resource("/db/canonical/55_search_exposure_persistence.sql");

        assertTrue(sql.contains("identity_scheme='platform_subject_v1'"));
        assertTrue(sql.contains("visibility_rule_version='search-item-visible-v1'"));
        assertTrue(sql.contains("retention_until=exposed_at+interval '180 days'"));
        assertFalse(sql.contains("INSERT INTO public.recommendation_exposure_event"));
        assertFalse(sql.contains("ALTER TABLE public.recommendation_p2_experiment_exposure"));
        assertFalse(sql.contains("CREATE TABLE public.search_exposure_event_v1 ( user_id"));
    }

    @Test
    void databaseV28PackageMatchesCanonicalTestBootstrap() throws IOException {
        Path root = repositoryRoot();
        assertEquals(
                normalized(Files.readString(root.resolve(
                        "database/journey-connect-db-v2.8/01_search_exposure_persistence.sql"))),
                resource("/db/canonical/55_search_exposure_persistence.sql"));
        assertEquals(
                normalized(Files.readString(root.resolve(
                        "database/journey-connect-db-v2.8/02_search_exposure_digest_privilege.sql"))),
                resource("/db/canonical/55a_search_exposure_digest_privilege.sql"));
        assertEquals(
                normalized(Files.readString(root.resolve(
                        "database/journey-connect-db-v2.8/03_search_exposure_persistence_smoke_test.sql"))),
                resource("/db/canonical/56_search_exposure_persistence_smoke_test.sql"));
    }

    @Test
    void noFlywayAutodiscoveryMigrationIsIntroduced() {
        assertTrue(getClass().getResource("/db/migration/V55__search_exposure_persistence.sql") == null);
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
