package com.jc.backend.recommendation.dataadoption;

import static com.jc.backend.recommendation.dataadoption.RecommendationDataConsumerContracts.CompatibilityCode.CONDITIONALLY_COMPATIBLE;
import static com.jc.backend.recommendation.dataadoption.RecommendationDataConsumerContracts.CompatibilityCode.IDENTITY_MAPPING_REQUIRED;
import static com.jc.backend.recommendation.dataadoption.RecommendationDataConsumerContracts.CompatibilityCode.IDENTITY_SCHEME_MISMATCH;
import static com.jc.backend.recommendation.dataadoption.RecommendationDataConsumerContracts.CompatibilityCode.INCOMPATIBLE_REQUIRED_ENUM;
import static com.jc.backend.recommendation.dataadoption.RecommendationDataConsumerContracts.CompatibilityCode.INCOMPATIBLE_REQUIRED_FIELD;
import static com.jc.backend.recommendation.dataadoption.RecommendationDataConsumerContracts.CompatibilityCode.MIGRATION_REQUIRED;
import static com.jc.backend.recommendation.dataadoption.RecommendationDataConsumerContracts.CompatibilityCode.PROTECTED_AUTHORITY_CHANGE_REQUIRED;
import static com.jc.backend.recommendation.dataadoption.RecommendationDataConsumerContracts.CompatibilityCode.UNSUPPORTED_CONTRACT_VERSION;
import static com.jc.backend.recommendation.dataadoption.RecommendationDataConsumerContracts.RequirementClassification.AUTHORITY_PROTECTED;
import static com.jc.backend.recommendation.dataadoption.RecommendationDataConsumerContracts.RequirementClassification.MISSING;
import static com.jc.backend.recommendation.dataadoption.RecommendationDataConsumerContracts.finding;
import static com.jc.backend.recommendation.dataadoption.RecommendationDataConsumerContracts.result;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class P1ConsumerValidatorV1 {
    private static final Set<String> REQUIRED_FIELDS = Set.of(
            "contractVersion",
            "recordRef",
            "subjectRef",
            "legacyUserRef",
            "projectionAsOf",
            "sourceCheckpointRef",
            "profileSchemaVersion",
            "projectionPolicyVersion",
            "activityWindowDays",
            "interactionCounts",
            "recentRegions",
            "recentContentRefs",
            "recentTagRefs",
            "engagementSignals",
            "negativeSignals",
            "sourceEventCount",
            "sourceLineageFingerprint",
            "projectionRecordFingerprint");

    public RecommendationDataConsumerContracts.CompatibilityResult validate(
            RecommendationDataConsumerContracts.FixtureCase fixture,
            RecommendationDataConsumerContracts.IdentityMappingReadPort identityPort) {
        requireLane(fixture);
        String missing = firstMissing(fixture.fields(), REQUIRED_FIELDS);
        if (missing != null) {
            return result(fixture, INCOMPATIBLE_REQUIRED_FIELD,
                    finding(missing, MISSING, "Required profile consumer field is absent."));
        }
        if (!RecommendationDataConsumerContracts.PROFILE_SOURCE_CONTRACT.equals(fixture.field("contractVersion"))
                || !RecommendationDataConsumerContracts.PROFILE_SOURCE_CONTRACT.equals(fixture.field("profileSchemaVersion"))) {
            return result(fixture, UNSUPPORTED_CONTRACT_VERSION,
                    finding("profile schema version", MISSING, "Only recommendation-profile-input-v1 is supported."));
        }
        if (!RecommendationDataConsumerContracts.PROFILE_POLICY_VERSION.equals(fixture.field("projectionPolicyVersion"))) {
            return result(fixture, UNSUPPORTED_CONTRACT_VERSION,
                    finding("projection policy version", MISSING,
                            "Only recommendation-profile-projection-policy-v1 is supported."));
        }
        int window = parseInteger(fixture.field("activityWindowDays"), -1);
        if (!List.of(7, 30, 90).contains(window)) {
            return result(fixture, INCOMPATIBLE_REQUIRED_ENUM,
                    finding("activity window", MISSING, "Only 7, 30 and 90 day windows are accepted."));
        }
        Instant asOf = parseInstant(fixture.field("projectionAsOf"));
        if (asOf == null
                || !isReference(fixture.field("recordRef"))
                || !isSubject(fixture.field("subjectRef"))
                || !isUser(fixture.field("legacyUserRef"))
                || !isReference(fixture.field("sourceCheckpointRef"))
                || !validCounts(fixture.field("interactionCounts"))
                || !validCounts(fixture.field("engagementSignals"))
                || !validCounts(fixture.field("negativeSignals"))
                || parseLong(fixture.field("sourceEventCount"), -1L) < 0L
                || !isFingerprint(fixture.field("sourceLineageFingerprint"))
                || !isFingerprint(fixture.field("projectionRecordFingerprint"))) {
            return result(fixture, INCOMPATIBLE_REQUIRED_FIELD,
                    finding("profile field validation", MISSING, "One or more profile fields are malformed."));
        }
        RecommendationDataConsumerContracts.IdentityMappingStatus identity = identityPort.resolve(
                fixture.field("subjectRef"), fixture.field("legacyUserRef"), asOf);
        if (identity == RecommendationDataConsumerContracts.IdentityMappingStatus.MISMATCHED) {
            return result(fixture, IDENTITY_SCHEME_MISMATCH,
                    finding("identity mapping", MISSING, "Opaque subject and legacy user binding mismatch."));
        }
        if (identity != RecommendationDataConsumerContracts.IdentityMappingStatus.VALID) {
            return result(fixture, IDENTITY_MAPPING_REQUIRED,
                    finding("identity mapping", MISSING, "Absent, invalid or expired identity mapping fails closed."));
        }
        if (fixture.flag("attemptAggregateEventStream")) {
            return result(fixture, PROTECTED_AUTHORITY_CHANGE_REQUIRED,
                    finding("aggregate-to-event conversion", AUTHORITY_PROTECTED,
                            "Aggregate counts cannot be expanded into synthetic BehaviorProfileEvent rows."));
        }
        if (fixture.flag("requiresExplicitPreference")) {
            return result(fixture, MIGRATION_REQUIRED,
                    finding("explicit preferences", MISSING,
                            "Current recommendation_user_preference semantics are not present in the Data aggregate."));
        }
        return new RecommendationDataConsumerContracts.CompatibilityResult(
                fixture.lane(), fixture.scenario(), CONDITIONALLY_COMPATIBLE, CompatibilityMatricesV1.p1());
    }

    private static void requireLane(RecommendationDataConsumerContracts.FixtureCase fixture) {
        if (fixture.lane() != RecommendationDataConsumerContracts.Lane.P1) {
            throw new IllegalArgumentException("P1 fixture required");
        }
    }

    private static String firstMissing(Map<String, String> fields, Set<String> required) {
        return required.stream().sorted().filter(field -> fields.getOrDefault(field, "").isBlank()).findFirst().orElse(null);
    }

    private static boolean isReference(String value) {
        return value != null && value.matches("[a-z][a-z0-9_]*:[^\\s:][^\\s]*");
    }

    private static boolean isSubject(String value) {
        return value != null && value.matches("subject:[^\\s:][^\\s]*");
    }

    private static boolean isUser(String value) {
        return value != null && value.matches("user:[1-9][0-9]*");
    }

    private static boolean isFingerprint(String value) {
        return value != null && value.matches("[0-9a-f]{64}");
    }

    private static Instant parseInstant(String value) {
        try {
            return Instant.parse(value);
        } catch (DateTimeParseException exception) {
            return null;
        }
    }

    private static int parseInteger(String value, int fallback) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException exception) {
            return fallback;
        }
    }

    private static long parseLong(String value, long fallback) {
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException exception) {
            return fallback;
        }
    }

    private static boolean validCounts(String source) {
        if (source == null || source.isBlank()) {
            return true;
        }
        for (String entry : source.split(",", -1)) {
            String[] pair = entry.split(":", 2);
            if (pair.length != 2 || pair[0].isBlank() || parseLong(pair[1], -1L) < 0L) {
                return false;
            }
        }
        return true;
    }
}
