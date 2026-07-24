package com.jc.backend.recommendation.dataadoption.reconciliation.database;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

final class Rca1bQueryRegistry {
    static final String CONTRACT_VERSION = "rca1b-query-registry-v1";
    static final String VERIFIER_VERSION = "rca1b-database-verifier-v1";
    static final int MAX_ROWS = 1_000;

    enum Lane { P1, P2, SHARED }

    record QueryDefinition(
            String id,
            Lane lane,
            String resource,
            String expectedFingerprint,
            List<String> parameterNames,
            String deterministicOrderKey,
            Set<String> allowedObjects,
            Set<String> prohibitedObjects) {}

    private static final String ROOT = "recommendation-data-adoption/rca1b/";
    private static final Map<String, QueryDefinition> DEFINITIONS = definitions();

    QueryDefinition require(String id) {
        QueryDefinition definition = DEFINITIONS.get(id);
        if (definition == null) {
            throw new IllegalArgumentException("unknown query id");
        }
        String actual = sha256(canonicalBytes(read(definition.resource())));
        if (!actual.equals(definition.expectedFingerprint())) {
            throw new IllegalStateException("query fingerprint mismatch");
        }
        return definition;
    }

    QueryDefinition requireWithFingerprint(String id, String fingerprint) {
        QueryDefinition definition = require(id);
        if (!definition.expectedFingerprint().equals(fingerprint)) {
            throw new IllegalArgumentException("unregistered query fingerprint");
        }
        return definition;
    }

    String sql(QueryDefinition definition) {
        return canonicalText(read(definition.resource()));
    }

    Map<String, QueryDefinition> inventory() {
        return DEFINITIONS;
    }

    static byte[] canonicalBytes(String raw) {
        return canonicalText(raw).getBytes(StandardCharsets.UTF_8);
    }

    static String canonicalText(String raw) {
        String normalized = raw.replace("\r\n", "\n").replace('\r', '\n');
        String[] lines = normalized.split("\n", -1);
        StringBuilder out = new StringBuilder();
        int last = lines.length;
        while (last > 0 && lines[last - 1].isBlank()) {
            last--;
        }
        for (int i = 0; i < last; i++) {
            out.append(lines[i].stripTrailing()).append('\n');
        }
        return out.toString();
    }

    static String sha256(byte[] bytes) {
        try {
            return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static String read(String resource) {
        try (InputStream input = Rca1bQueryRegistry.class.getClassLoader().getResourceAsStream(ROOT + resource)) {
            if (input == null) {
                throw new IllegalStateException("missing query resource");
            }
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static Map<String, QueryDefinition> definitions() {
        Map<String, QueryDefinition> definitions = new LinkedHashMap<>();
        register(definitions, new QueryDefinition(
                "P1_AUTHORITATIVE_REFERENCE_V1", Lane.P1, "queries/p1-authoritative-reference-v1.sql",
                "fb0db145de19695f4cbffb5aacf12b7715e291d2e2b8fcce520828fd18201dd7",
                List.of("caseId", "maxRows"), "window_days,profile_snapshot_id",
                Set.of("rca1b_fixture.p1_case_map", "public.recommendation_p1_profile_snapshot",
                        "public.recommendation_user_preference", "public.tags"), prohibited()));
        register(definitions, new QueryDefinition(
                "P1_DATA_CANDIDATE_V1", Lane.P1, "queries/p1-data-candidate-v1.sql",
                "2fb6c99c1adcfbc5c3558466a98166dca63b1415f74a2dc61767591868462e22",
                List.of("caseId", "maxRows"), "activity_window_days,projection_record_ref",
                Set.of("rca1b_fixture.p1_case_map", "public.data_recommendation_profile_input_projection_v1",
                        "public.data_source_checkpoint_v1", "public.data_projection_snapshot_v1"), prohibited()));
        register(definitions, new QueryDefinition(
                "P2_AUTHORITATIVE_EXPOSURE_OUTCOME_V1", Lane.P2, "queries/p2-authoritative-exposure-outcome-v1.sql",
                "1db567e30490f90b33a1c55b0f10d38d7120ab0c360bb15bbb20dbbf5387840f",
                List.of("caseId", "maxRows"), "experiment_id,experiment_version,exposure_id",
                Set.of("rca1b_fixture.p2_case_map", "public.recommendation_p2_experiment_assignment",
                        "public.recommendation_p2_experiment_exposure", "public.recommendation_run",
                        "public.recommendation_behavior_event"), prohibited()));
        register(definitions, new QueryDefinition(
                "P2_DATA_CANDIDATE_V1", Lane.P2, "queries/p2-data-candidate-v1.sql",
                "d9be182a258d408a18fa129a49b2ec18d3f7933551431c20e31e5832223fa8d6",
                List.of("caseId", "maxRows"), "experiment_ref,experiment_version,exposure_ref",
                Set.of("rca1b_fixture.p2_case_map", "public.data_experiment_outcome_input_projection_v1",
                        "public.data_source_checkpoint_v1", "public.data_projection_snapshot_v1"), prohibited()));
        register(definitions, new QueryDefinition(
                "SOURCE_CHECKPOINT_V1", Lane.SHARED, "queries/source-checkpoint-v1.sql",
                "e9f9c96647c6fed1d06222d9c7550e90c3320fe0253511f051c994ef83161c43",
                List.of("checkpointRef", "maxRows"), "checkpoint_ref",
                Set.of("public.data_source_checkpoint_v1"), prohibited()));
        register(definitions, new QueryDefinition(
                "SOURCE_LINEAGE_V1", Lane.SHARED, "queries/source-lineage-v1.sql",
                "18d10c6074d83e91c1377e741930ef2f17b7059985200d117faf08a28435e91d",
                List.of("snapshotRef", "maxRows"), "projection_record_ref,source_event_ref,lineage_entry_fingerprint",
                Set.of("public.data_projection_snapshot_v1", "public.data_projection_lineage_v1"), prohibited()));
        register(definitions, new QueryDefinition(
                "BOUNDED_ROW_COUNT_V1", Lane.SHARED, "queries/bounded-row-count-v1.sql",
                "96f12cdca4bd08a6219eedf070397874678ca47e6585fdd9b63309f4695384eb",
                List.of("maxRows"), "ordinal", Set.of("rca1b_fixture.row_limit_probe"), prohibited()));
        if (definitions.size() != 7) {
            throw new IllegalStateException("query registry must contain exactly seven entries");
        }
        return Map.copyOf(definitions);
    }

    private static void register(Map<String, QueryDefinition> definitions, QueryDefinition definition) {
        if (definitions.putIfAbsent(definition.id(), definition) != null) {
            throw new IllegalStateException("duplicate query id");
        }
        if (definitions.values().stream().filter(value -> value.expectedFingerprint().equals(definition.expectedFingerprint())).count() > 1) {
            throw new IllegalStateException("duplicate query fingerprint");
        }
    }

    private static Set<String> prohibited() {
        return Set.of(
                "public.recommendation_p2_dataset_snapshot",
                "public.recommendation_p2_release_decision",
                "public.refresh_tokens",
                "pg_authid",
                "pg_shadow");
    }
}
