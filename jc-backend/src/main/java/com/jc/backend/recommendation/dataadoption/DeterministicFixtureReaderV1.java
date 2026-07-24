package com.jc.backend.recommendation.dataadoption;

import static com.jc.backend.recommendation.dataadoption.RecommendationDataConsumerContracts.CompatibilityCode;
import static com.jc.backend.recommendation.dataadoption.RecommendationDataConsumerContracts.FixtureCase;
import static com.jc.backend.recommendation.dataadoption.RecommendationDataConsumerContracts.Lane;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class DeterministicFixtureReaderV1 {
    private static final String HEADER = "scenario\texpected\tfields";
    private static final String DELETE = "__DELETE__";

    public List<FixtureCase> read(Path path, Lane lane) throws IOException {
        try (InputStream input = Files.newInputStream(path)) {
            return read(input, lane);
        }
    }

    public List<FixtureCase> read(InputStream input, Lane lane) throws IOException {
        if (input == null) {
            throw new IllegalArgumentException("fixture input is required");
        }
        List<FixtureCase> fixtures = new ArrayList<>();
        Set<String> scenarios = new HashSet<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8))) {
            String header = reader.readLine();
            if (!HEADER.equals(header)) {
                throw new IllegalArgumentException("unexpected fixture header");
            }
            String line;
            int lineNumber = 1;
            while ((line = reader.readLine()) != null) {
                lineNumber++;
                if (line.isBlank() || line.startsWith("#")) {
                    continue;
                }
                String[] columns = line.split("\\t", -1);
                if (columns.length != 3) {
                    throw new IllegalArgumentException("fixture line " + lineNumber + " must have 3 columns");
                }
                String scenario = columns[0];
                if (!scenarios.add(scenario)) {
                    throw new IllegalArgumentException("duplicate fixture scenario: " + scenario);
                }
                CompatibilityCode expected;
                try {
                    expected = CompatibilityCode.valueOf(columns[1]);
                } catch (IllegalArgumentException exception) {
                    throw new IllegalArgumentException(
                            "unsupported expected classification at line " + lineNumber, exception);
                }
                LinkedHashMap<String, String> fields = new LinkedHashMap<>(baseFields(lane));
                applyOverrides(fields, columns[2], lineNumber);
                fixtures.add(new FixtureCase(lane, scenario, expected, fields));
            }
        }
        return List.copyOf(fixtures);
    }

    private static void applyOverrides(Map<String, String> fields, String source, int lineNumber) {
        if (source.isBlank()) {
            return;
        }
        Set<String> overridden = new HashSet<>();
        for (String entry : source.split(";", -1)) {
            int separator = entry.indexOf('=');
            if (separator < 1) {
                throw new IllegalArgumentException("invalid fixture field at line " + lineNumber);
            }
            String key = entry.substring(0, separator);
            String value = entry.substring(separator + 1);
            if (!overridden.add(key)) {
                throw new IllegalArgumentException("duplicate fixture override " + key + " at line " + lineNumber);
            }
            if (DELETE.equals(value)) {
                fields.remove(key);
            } else {
                fields.put(key, value);
            }
        }
    }

    private static Map<String, String> baseFields(Lane lane) {
        return lane == Lane.P1 ? p1BaseFields() : p2BaseFields();
    }

    private static Map<String, String> p1BaseFields() {
        return Map.ofEntries(
                Map.entry("contractVersion", "recommendation-profile-input-v1"),
                Map.entry("recordRef", "projection_record:p1"),
                Map.entry("subjectRef", "subject:opaque-valid"),
                Map.entry("legacyUserRef", "user:42"),
                Map.entry("projectionAsOf", "2026-07-24T00:00:00Z"),
                Map.entry("sourceCheckpointRef", "checkpoint:p1"),
                Map.entry("profileSchemaVersion", "recommendation-profile-input-v1"),
                Map.entry("projectionPolicyVersion", "recommendation-profile-projection-policy-v1"),
                Map.entry("activityWindowDays", "7"),
                Map.entry("interactionCounts", "recommendation_click:3,post_like:2"),
                Map.entry("recentRegions", "region:seoul,region:busan"),
                Map.entry("recentContentRefs", "post:10,post:20"),
                Map.entry("recentTagRefs", "tag:food,tag:nature"),
                Map.entry("engagementSignals", "recommendation_click:3,post_like:2"),
                Map.entry("negativeSignals", "post_hide:1"),
                Map.entry("sourceEventCount", "6"),
                Map.entry("sourceLineageFingerprint", "1".repeat(64)),
                Map.entry("projectionRecordFingerprint", "2".repeat(64)),
                Map.entry("attemptAggregateEventStream", "false"),
                Map.entry("requiresExplicitPreference", "false"));
    }

    private static Map<String, String> p2BaseFields() {
        return Map.ofEntries(
                Map.entry("contractVersion", "experiment-outcome-input-v1"),
                Map.entry("recordRef", "projection_record:p2"),
                Map.entry("experimentRef", "experiment:ranking"),
                Map.entry("experimentVersion", "experiment-ranking-v1"),
                Map.entry("variantRef", "baseline"),
                Map.entry("exposureAuthority", "recommendation_p2_experiment_exposure"),
                Map.entry("exposureKind", "p2_experiment_exposure"),
                Map.entry("exposureRef", "exposure:p2"),
                Map.entry("expectedExposureRef", "exposure:p2"),
                Map.entry("runRef", "recommendation_run:p2"),
                Map.entry("expectedRunRef", "recommendation_run:p2"),
                Map.entry("subjectRef", "subject:opaque-valid"),
                Map.entry("expectedSubjectRef", "subject:opaque-valid"),
                Map.entry("legacyUserRef", "user:42"),
                Map.entry("sessionRef", "session:p2"),
                Map.entry("expectedSessionRef", "session:p2"),
                Map.entry("exposedAt", "2026-07-24T00:00:00Z"),
                Map.entry("outcomeWindowSeconds", "604800"),
                Map.entry("clicked", "false"),
                Map.entry("liked", "false"),
                Map.entry("saved", "false"),
                Map.entry("shared", "false"),
                Map.entry("fallbackObserved", "false"),
                Map.entry("fallbackRunBound", "true"),
                Map.entry("outcomeTypes", ""),
                Map.entry("outcomeEventRefs", ""),
                Map.entry("sourceCheckpointRef", "checkpoint:p2"),
                Map.entry("sourceEventCount", "1"),
                Map.entry("sourceLineageFingerprint", "1".repeat(64)),
                Map.entry("projectionRecordFingerprint", "2".repeat(64)),
                Map.entry("staleAssignment", "false"),
                Map.entry("datasetHashMigration", "false"));
    }
}
