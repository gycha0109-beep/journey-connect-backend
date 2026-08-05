package com.jc.backend.intelligence.search;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class SearchExposureSqlContractTest {

    @Test
    void canonicalAndFlywayDeclareTheSameAuthorityBoundaries() throws IOException {
        String canonical = resource("/db/canonical/55_search_exposure_persistence.sql");
        String flyway = resource("/db/migration/V55__search_exposure_persistence.sql");

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
            assertTrue(canonical.contains(required), "canonical missing: " + required);
            assertTrue(flyway.contains(required), "Flyway missing: " + required);
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
        assertFalse(sql.contains(" user_id bigint NOT NULL REFERENCES public.app_users(id) ON DELETE RESTRICT, identity_scheme"));
    }

    private String resource(String path) throws IOException {
        try (InputStream input = getClass().getResourceAsStream(path)) {
            if (input == null) throw new IOException("Missing SQL resource: " + path);
            return new String(input.readAllBytes(), StandardCharsets.UTF_8).replace("\r\n", "\n");
        }
    }
}
