package com.jc.backend.recommendation.dataadoption;

import static com.jc.backend.recommendation.dataadoption.RecommendationDataConsumerContracts.CompatibilityCode.COMPATIBLE_FOR_FIXTURE_VALIDATION;
import static com.jc.backend.recommendation.dataadoption.RecommendationDataConsumerContracts.CompatibilityCode.EXPOSURE_AUTHORITY_MISMATCH;
import static com.jc.backend.recommendation.dataadoption.RecommendationDataConsumerContracts.CompatibilityCode.IDENTITY_MAPPING_REQUIRED;
import static com.jc.backend.recommendation.dataadoption.RecommendationDataConsumerContracts.CompatibilityCode.IDENTITY_SCHEME_MISMATCH;
import static com.jc.backend.recommendation.dataadoption.RecommendationDataConsumerContracts.CompatibilityCode.INCOMPATIBLE_REQUIRED_ENUM;
import static com.jc.backend.recommendation.dataadoption.RecommendationDataConsumerContracts.CompatibilityCode.INCOMPATIBLE_REQUIRED_FIELD;
import static com.jc.backend.recommendation.dataadoption.RecommendationDataConsumerContracts.CompatibilityCode.MIGRATION_REQUIRED;
import static com.jc.backend.recommendation.dataadoption.RecommendationDataConsumerContracts.CompatibilityCode.OUTCOME_WINDOW_MISMATCH;
import static com.jc.backend.recommendation.dataadoption.RecommendationDataConsumerContracts.CompatibilityCode.PROTECTED_AUTHORITY_CHANGE_REQUIRED;
import static com.jc.backend.recommendation.dataadoption.RecommendationDataConsumerContracts.CompatibilityCode.UNSUPPORTED_CONTRACT_VERSION;
import static com.jc.backend.recommendation.dataadoption.RecommendationDataConsumerContracts.RequirementClassification.AUTHORITY_PROTECTED;
import static com.jc.backend.recommendation.dataadoption.RecommendationDataConsumerContracts.RequirementClassification.MISSING;
import static com.jc.backend.recommendation.dataadoption.RecommendationDataConsumerContracts.finding;
import static com.jc.backend.recommendation.dataadoption.RecommendationDataConsumerContracts.result;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Map;
import java.util.Set;

public final class P2ConsumerValidatorV1 {
    private static final Set<String> REQUIRED_FIELDS = Set.of(
            "contractVersion",
            "recordRef",
            "experimentRef",
            "experimentVersion",
            "variantRef",
            "exposureAuthority",
            "exposureKind",
            "exposureRef",
            "expectedExposureRef",
            "runRef",
            "expectedRunRef",
            "subjectRef",
            "expectedSubjectRef",
            "legacyUserRef",
            "sessionRef",
            "expectedSessionRef",
            "exposedAt",
            "outcomeWindowSeconds",
            "clicked",
            "liked",
            "saved",
            "shared",
            "fallbackObserved",
            "fallbackRunBound",
            "sourceCheckpointRef",
            "sourceEventCount",
            "sourceLineageFingerprint",
            "projectionRecordFingerprint");
    private static final Set<String> ALLOWED_OUTCOMES = Set.of("", "click", "like", "save", "share");

    public RecommendationDataConsumerContracts.CompatibilityResult validate(
            RecommendationDataConsumerContracts.FixtureCase fixture,
            RecommendationDataConsumerContracts.IdentityMappingReadPort identityPort) {
        requireLane(fixture);
        String missing = firstMissing(fixture.fields(), REQUIRED_FIELDS);
        if (missing != null) {
            return result(fixture, INCOMPATIBLE_REQUIRED_FIELD,
                    finding(missing, MISSING, "Required outcome consumer field is absent."));
        }
        if (!RecommendationDataConsumerContracts.OUTCOME_SOURCE_CONTRACT.equals(fixture.field("contractVersion"))) {
            return result(fixture, UNSUPPORTED_CONTRACT_VERSION,
                    finding("outcome contract version", MISSING, "Only experiment-outcome-input-v1 is supported."));
        }
        if (!Set.of("baseline", "treatment").contains(fixture.field("variantRef"))) {
            return result(fixture, INCOMPATIBLE_REQUIRED_ENUM,
                    finding("variant", MISSING, "Only baseline and treatment are valid P2 variants."));
        }
        if (!RecommendationDataConsumerContracts.P2_EXPOSURE_AUTHORITY.equals(fixture.field("exposureAuthority"))
                || !"p2_experiment_exposure".equals(fixture.field("exposureKind"))) {
            return result(fixture, EXPOSURE_AUTHORITY_MISMATCH,
                    finding("exposure authority", AUTHORITY_PROTECTED,
                            "Only recommendation_p2_experiment_exposure can supply P2 denominator evidence."));
        }
        long window = parseLong(fixture.field("outcomeWindowSeconds"), -1L);
        if (window != RecommendationDataConsumerContracts.OUTCOME_WINDOW_SECONDS) {
            return result(fixture, OUTCOME_WINDOW_MISMATCH,
                    finding("outcome window", AUTHORITY_PROTECTED, "P2 attribution window must be exactly 604800 seconds."));
        }
        String unsupportedOutcome = unsupportedOutcome(fixture.field("outcomeTypes"));
        if (unsupportedOutcome != null) {
            return result(fixture, INCOMPATIBLE_REQUIRED_ENUM,
                    finding("engagement event", MISSING,
                            unsupportedOutcome + " is not a protected P2 engagement event."));
        }
        Instant exposedAt = parseInstant(fixture.field("exposedAt"));
        if (exposedAt == null
                || !isReference(fixture.field("recordRef"))
                || !isReference(fixture.field("experimentRef"))
                || !isReference(fixture.field("exposureRef"))
                || !isReference(fixture.field("runRef"))
                || !isSubject(fixture.field("subjectRef"))
                || !isUser(fixture.field("legacyUserRef"))
                || !isReference(fixture.field("sessionRef"))
                || !isReference(fixture.field("sourceCheckpointRef"))
                || parseLong(fixture.field("sourceEventCount"), -1L) < 1L
                || !isFingerprint(fixture.field("sourceLineageFingerprint"))
                || !isFingerprint(fixture.field("projectionRecordFingerprint"))) {
            return result(fixture, INCOMPATIBLE_REQUIRED_FIELD,
                    finding("outcome field validation", MISSING, "One or more outcome fields are malformed."));
        }
        if (!fixture.field("subjectRef").equals(fixture.field("expectedSubjectRef"))) {
            return result(fixture, IDENTITY_SCHEME_MISMATCH,
                    finding("subject binding", MISSING, "Outcome subject does not match the exposure assignment subject."));
        }
        if (!fixture.field("sessionRef").equals(fixture.field("expectedSessionRef"))) {
            return result(fixture, INCOMPATIBLE_REQUIRED_FIELD,
                    finding("session binding", MISSING, "Outcome session does not match the exposure session."));
        }
        if (!fixture.field("runRef").equals(fixture.field("expectedRunRef"))) {
            return result(fixture, INCOMPATIBLE_REQUIRED_FIELD,
                    finding("run binding", AUTHORITY_PROTECTED, "Outcome run does not match the bound exposed run."));
        }
        if (!fixture.field("exposureRef").equals(fixture.field("expectedExposureRef"))) {
            return result(fixture, EXPOSURE_AUTHORITY_MISMATCH,
                    finding("exposure binding", AUTHORITY_PROTECTED, "Outcome exposure does not match authoritative exposure."));
        }
        RecommendationDataConsumerContracts.IdentityMappingStatus identity = identityPort.resolve(
                fixture.field("subjectRef"), fixture.field("legacyUserRef"), exposedAt);
        if (identity == RecommendationDataConsumerContracts.IdentityMappingStatus.MISMATCHED) {
            return result(fixture, IDENTITY_SCHEME_MISMATCH,
                    finding("identity mapping", MISSING, "Opaque subject and legacy user binding mismatch."));
        }
        if (identity != RecommendationDataConsumerContracts.IdentityMappingStatus.VALID) {
            return result(fixture, IDENTITY_MAPPING_REQUIRED,
                    finding("identity mapping", MISSING, "Absent, invalid or expired identity mapping fails closed."));
        }
        if (fixture.flag("fallbackObserved") && !fixture.flag("fallbackRunBound")) {
            return result(fixture, PROTECTED_AUTHORITY_CHANGE_REQUIRED,
                    finding("fallback binding", AUTHORITY_PROTECTED,
                            "Fallback may only come from the recommendation run bound to the P2 exposure."));
        }
        if (fixture.flag("staleAssignment") || fixture.flag("datasetHashMigration")) {
            return result(fixture, MIGRATION_REQUIRED,
                    finding("protected migration dimension", AUTHORITY_PROTECTED,
                            "Stale assignment, one-observation dedupe or canonical dataset hash needs a separate migration."));
        }
        return new RecommendationDataConsumerContracts.CompatibilityResult(
                fixture.lane(), fixture.scenario(), COMPATIBLE_FOR_FIXTURE_VALIDATION, CompatibilityMatricesV1.p2());
    }

    private static void requireLane(RecommendationDataConsumerContracts.FixtureCase fixture) {
        if (fixture.lane() != RecommendationDataConsumerContracts.Lane.P2) {
            throw new IllegalArgumentException("P2 fixture required");
        }
    }

    private static String firstMissing(Map<String, String> fields, Set<String> required) {
        return required.stream().sorted().filter(field -> fields.getOrDefault(field, "").isBlank()).findFirst().orElse(null);
    }

    private static String unsupportedOutcome(String source) {
        if (source == null || source.isBlank()) {
            return null;
        }
        for (String event : source.split(",", -1)) {
            if (!ALLOWED_OUTCOMES.contains(event)) {
                return event;
            }
        }
        return null;
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

    private static long parseLong(String value, long fallback) {
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException exception) {
            return fallback;
        }
    }
}
