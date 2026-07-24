package com.jc.backend.recommendation.dataadoption;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

public final class RecommendationDataConsumerContracts {
    public static final String ALIGNMENT_CONTRACT_ID = "recommendation-data-consumer-alignment-v1";
    public static final String PROFILE_CONSUMER_CONTRACT_ID = "recommendation-profile-input-consumer-v1";
    public static final String OUTCOME_CONSUMER_CONTRACT_ID = "experiment-outcome-input-consumer-v1";
    public static final String FIXTURE_CONTRACT_ID = "recommendation-data-consumer-fixture-v1";

    public static final String PROFILE_SOURCE_CONTRACT = "recommendation-profile-input-v1";
    public static final String PROFILE_POLICY_VERSION = "recommendation-profile-projection-policy-v1";
    public static final String OUTCOME_SOURCE_CONTRACT = "experiment-outcome-input-v1";
    public static final String OUTCOME_POLICY_VERSION = "experiment-outcome-projection-policy-v1";
    public static final String P2_EXPOSURE_AUTHORITY = "recommendation_p2_experiment_exposure";
    public static final long OUTCOME_WINDOW_SECONDS = 604_800L;

    private RecommendationDataConsumerContracts() {
    }

    public enum Lane {
        P1,
        P2
    }

    public enum CompatibilityCode {
        COMPATIBLE_FOR_FIXTURE_VALIDATION,
        CONDITIONALLY_COMPATIBLE,
        MIGRATION_REQUIRED,
        INCOMPATIBLE_SCHEMA,
        INCOMPATIBLE_REQUIRED_FIELD,
        INCOMPATIBLE_REQUIRED_ENUM,
        UNSUPPORTED_CONTRACT_VERSION,
        IDENTITY_MAPPING_REQUIRED,
        IDENTITY_SCHEME_MISMATCH,
        EXPOSURE_AUTHORITY_MISMATCH,
        OUTCOME_WINDOW_MISMATCH,
        PROTECTED_AUTHORITY_CHANGE_REQUIRED
    }

    public enum RequirementClassification {
        EXACT,
        DERIVABLE,
        MISSING,
        INCOMPATIBLE,
        AUTHORITY_PROTECTED
    }

    public enum IdentityMappingStatus {
        VALID,
        ABSENT,
        INVALID,
        EXPIRED,
        MISMATCHED
    }

    public record CompatibilityFinding(
            String requirement,
            RequirementClassification classification,
            String detail) {
        public CompatibilityFinding {
            requirement = requireText(requirement, "requirement");
            Objects.requireNonNull(classification, "classification");
            detail = requireText(detail, "detail");
        }
    }

    public record CompatibilityResult(
            Lane lane,
            String scenario,
            CompatibilityCode code,
            List<CompatibilityFinding> findings) {
        public CompatibilityResult {
            Objects.requireNonNull(lane, "lane");
            scenario = requireText(scenario, "scenario");
            Objects.requireNonNull(code, "code");
            findings = List.copyOf(Objects.requireNonNull(findings, "findings"));
        }
    }

    public record FixtureCase(
            Lane lane,
            String scenario,
            CompatibilityCode expectedCode,
            Map<String, String> fields) {
        public FixtureCase {
            Objects.requireNonNull(lane, "lane");
            scenario = requireText(scenario, "scenario");
            Objects.requireNonNull(expectedCode, "expectedCode");
            Objects.requireNonNull(fields, "fields");
            TreeMap<String, String> sorted = new TreeMap<>();
            fields.forEach((key, value) -> sorted.put(requireText(key, "field key"), value == null ? "" : value));
            fields = Collections.unmodifiableMap(sorted);
        }

        public String field(String name) {
            return fields.getOrDefault(name, "");
        }

        public boolean flag(String name) {
            return Boolean.parseBoolean(field(name));
        }
    }

    @FunctionalInterface
    public interface IdentityMappingReadPort {
        IdentityMappingStatus resolve(String opaqueSubjectRef, String legacyUserRef, Instant asOf);
    }

    public static CompatibilityFinding finding(
            String requirement,
            RequirementClassification classification,
            String detail) {
        return new CompatibilityFinding(requirement, classification, detail);
    }

    public static CompatibilityResult result(
            FixtureCase fixture,
            CompatibilityCode code,
            CompatibilityFinding... findings) {
        ArrayList<CompatibilityFinding> values = new ArrayList<>(List.of(findings));
        return new CompatibilityResult(fixture.lane(), fixture.scenario(), code, values);
    }

    static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
        return value;
    }
}
