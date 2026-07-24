package com.jc.backend.recommendation.dataadoption;

import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TimeZone;

public final class Rca0ContractTestMain {
    private Rca0ContractTestMain() {
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 2) {
            throw new IllegalArgumentException("expected P1 and P2 fixture paths");
        }
        DeterministicFixtureReaderV1 reader = new DeterministicFixtureReaderV1();
        List<RecommendationDataConsumerContracts.FixtureCase> p1 = reader.read(
                Path.of(args[0]), RecommendationDataConsumerContracts.Lane.P1);
        List<RecommendationDataConsumerContracts.FixtureCase> p2 = reader.read(
                Path.of(args[1]), RecommendationDataConsumerContracts.Lane.P2);
        check(p1.size() == 12, "P1 fixture count");
        check(p2.size() == 21, "P2 fixture count");
        check(unique(p1), "P1 fixture scenarios must be unique");
        check(unique(p2), "P2 fixture scenarios must be unique");

        RecommendationDataConsumerContracts.IdentityMappingReadPort identityPort = Rca0ContractTestMain::identity;
        P1ConsumerValidatorV1 p1Validator = new P1ConsumerValidatorV1();
        P2ConsumerValidatorV1 p2Validator = new P2ConsumerValidatorV1();
        List<String> baseline = validateAll(p1, p2, identityPort, p1Validator, p2Validator);
        verifyDeterminism(p1, p2, identityPort, p1Validator, p2Validator, baseline);
        verifyMatrices();
        verifyFailClosedIdentityStates();
        verifyForbiddenResultNames();
        System.out.println("RCA-0 contract and fixture assertions: PASS");
    }

    private static List<String> validateAll(
            List<RecommendationDataConsumerContracts.FixtureCase> p1,
            List<RecommendationDataConsumerContracts.FixtureCase> p2,
            RecommendationDataConsumerContracts.IdentityMappingReadPort identityPort,
            P1ConsumerValidatorV1 p1Validator,
            P2ConsumerValidatorV1 p2Validator) {
        ArrayList<String> results = new ArrayList<>();
        for (RecommendationDataConsumerContracts.FixtureCase fixture : p1) {
            RecommendationDataConsumerContracts.CompatibilityResult result = p1Validator.validate(fixture, identityPort);
            check(result.code() == fixture.expectedCode(), fixture.scenario() + " expected "
                    + fixture.expectedCode() + " but got " + result.code());
            results.add(fixture.scenario() + "=" + result.code());
            System.out.println(fixture.scenario() + "\t" + result.code());
        }
        for (RecommendationDataConsumerContracts.FixtureCase fixture : p2) {
            RecommendationDataConsumerContracts.CompatibilityResult result = p2Validator.validate(fixture, identityPort);
            check(result.code() == fixture.expectedCode(), fixture.scenario() + " expected "
                    + fixture.expectedCode() + " but got " + result.code());
            results.add(fixture.scenario() + "=" + result.code());
            System.out.println(fixture.scenario() + "\t" + result.code());
        }
        return List.copyOf(results);
    }

    private static void verifyDeterminism(
            List<RecommendationDataConsumerContracts.FixtureCase> p1,
            List<RecommendationDataConsumerContracts.FixtureCase> p2,
            RecommendationDataConsumerContracts.IdentityMappingReadPort identityPort,
            P1ConsumerValidatorV1 p1Validator,
            P2ConsumerValidatorV1 p2Validator,
            List<String> baseline) {
        Locale previousLocale = Locale.getDefault();
        TimeZone previousZone = TimeZone.getDefault();
        try {
            Locale.setDefault(Locale.forLanguageTag("tr-TR"));
            TimeZone.setDefault(TimeZone.getTimeZone("Pacific/Honolulu"));
            List<String> changedEnvironment = validateSilently(p1, p2, identityPort, p1Validator, p2Validator);
            check(baseline.equals(changedEnvironment), "locale/timezone independence");
        } finally {
            Locale.setDefault(previousLocale);
            TimeZone.setDefault(previousZone);
        }

        List<RecommendationDataConsumerContracts.FixtureCase> reorderedP1 = reorderMaps(p1);
        List<RecommendationDataConsumerContracts.FixtureCase> reorderedP2 = reorderMaps(p2);
        check(baseline.equals(validateSilently(reorderedP1, reorderedP2, identityPort, p1Validator, p2Validator)),
                "map iteration order independence");
    }

    private static List<String> validateSilently(
            List<RecommendationDataConsumerContracts.FixtureCase> p1,
            List<RecommendationDataConsumerContracts.FixtureCase> p2,
            RecommendationDataConsumerContracts.IdentityMappingReadPort identityPort,
            P1ConsumerValidatorV1 p1Validator,
            P2ConsumerValidatorV1 p2Validator) {
        ArrayList<String> values = new ArrayList<>();
        p1.forEach(fixture -> values.add(fixture.scenario() + "=" + p1Validator.validate(fixture, identityPort).code()));
        p2.forEach(fixture -> values.add(fixture.scenario() + "=" + p2Validator.validate(fixture, identityPort).code()));
        return List.copyOf(values);
    }

    private static List<RecommendationDataConsumerContracts.FixtureCase> reorderMaps(
            List<RecommendationDataConsumerContracts.FixtureCase> fixtures) {
        ArrayList<RecommendationDataConsumerContracts.FixtureCase> values = new ArrayList<>();
        for (RecommendationDataConsumerContracts.FixtureCase fixture : fixtures) {
            ArrayList<Map.Entry<String, String>> entries = new ArrayList<>(fixture.fields().entrySet());
            Collections.reverse(entries);
            LinkedHashMap<String, String> reversed = new LinkedHashMap<>();
            entries.forEach(entry -> reversed.put(entry.getKey(), entry.getValue()));
            values.add(new RecommendationDataConsumerContracts.FixtureCase(
                    fixture.lane(), fixture.scenario(), fixture.expectedCode(), reversed));
        }
        return List.copyOf(values);
    }

    private static void verifyMatrices() {
        Map<String, RecommendationDataConsumerContracts.RequirementClassification> p1 = toMap(CompatibilityMatricesV1.p1());
        for (String missing : List.of(
                "event-grain ordering",
                "event timestamps",
                "explicit preferences",
                "BehaviorProfileEvent partition behavior",
                "feature-vocabulary transform",
                "decay inputs",
                "saturation inputs")) {
            check(p1.get(missing) == RecommendationDataConsumerContracts.RequirementClassification.MISSING,
                    "P1 missing classification: " + missing);
        }
        check(p1.get("profile snapshot fingerprint semantics")
                        == RecommendationDataConsumerContracts.RequirementClassification.AUTHORITY_PROTECTED,
                "P1 snapshot fingerprint protection");
        check(p1.get("aggregate-to-event conversion")
                        == RecommendationDataConsumerContracts.RequirementClassification.INCOMPATIBLE,
                "P1 aggregate conversion rejection");

        Map<String, RecommendationDataConsumerContracts.RequirementClassification> p2 = toMap(CompatibilityMatricesV1.p2());
        for (String protectedDimension : List.of(
                "stale unexposed assignment",
                "one-observation dedupe",
                "canonical dataset bytes/hash",
                "evaluation/release evidence")) {
            check(p2.get(protectedDimension)
                            == RecommendationDataConsumerContracts.RequirementClassification.AUTHORITY_PROTECTED,
                    "P2 protected dimension: " + protectedDimension);
        }
    }

    private static void verifyFailClosedIdentityStates() {
        RecommendationDataConsumerContracts.IdentityMappingReadPort invalid = (subject, user, asOf) ->
                RecommendationDataConsumerContracts.IdentityMappingStatus.INVALID;
        RecommendationDataConsumerContracts.IdentityMappingReadPort expired = (subject, user, asOf) ->
                RecommendationDataConsumerContracts.IdentityMappingStatus.EXPIRED;
        RecommendationDataConsumerContracts.FixtureCase fixture = new RecommendationDataConsumerContracts.FixtureCase(
                RecommendationDataConsumerContracts.Lane.P1,
                "identity_fail_closed_probe",
                RecommendationDataConsumerContracts.CompatibilityCode.IDENTITY_MAPPING_REQUIRED,
                Map.ofEntries(
                        Map.entry("contractVersion", "recommendation-profile-input-v1"),
                        Map.entry("recordRef", "projection_record:probe"),
                        Map.entry("subjectRef", "subject:opaque-valid"),
                        Map.entry("legacyUserRef", "user:42"),
                        Map.entry("projectionAsOf", "2026-07-24T00:00:00Z"),
                        Map.entry("sourceCheckpointRef", "checkpoint:probe"),
                        Map.entry("profileSchemaVersion", "recommendation-profile-input-v1"),
                        Map.entry("projectionPolicyVersion", "recommendation-profile-projection-policy-v1"),
                        Map.entry("activityWindowDays", "7"),
                        Map.entry("interactionCounts", "recommendation_click:0"),
                        Map.entry("recentRegions", "region:seoul"),
                        Map.entry("recentContentRefs", "post:1"),
                        Map.entry("recentTagRefs", "tag:food"),
                        Map.entry("engagementSignals", "recommendation_click:0"),
                        Map.entry("negativeSignals", "post_hide:0"),
                        Map.entry("sourceEventCount", "0"),
                        Map.entry("sourceLineageFingerprint", "1".repeat(64)),
                        Map.entry("projectionRecordFingerprint", "2".repeat(64))));
        P1ConsumerValidatorV1 validator = new P1ConsumerValidatorV1();
        check(validator.validate(fixture, invalid).code()
                        == RecommendationDataConsumerContracts.CompatibilityCode.IDENTITY_MAPPING_REQUIRED,
                "invalid mapping fails closed");
        check(validator.validate(fixture, expired).code()
                        == RecommendationDataConsumerContracts.CompatibilityCode.IDENTITY_MAPPING_REQUIRED,
                "expired mapping fails closed");
    }

    private static void verifyForbiddenResultNames() {
        Set<String> names = Set.of(
                "RUNTIME_READY",
                "PRODUCTION_READY",
                "AUTHORITATIVE",
                "CUTOVER_APPROVED");
        for (RecommendationDataConsumerContracts.CompatibilityCode code
                : RecommendationDataConsumerContracts.CompatibilityCode.values()) {
            check(!names.contains(code.name()), "forbidden result taxonomy value");
        }
    }

    private static RecommendationDataConsumerContracts.IdentityMappingStatus identity(
            String subject,
            String user,
            Instant asOf) {
        if (subject.endsWith("-missing")) {
            return RecommendationDataConsumerContracts.IdentityMappingStatus.ABSENT;
        }
        if (subject.endsWith("-invalid")) {
            return RecommendationDataConsumerContracts.IdentityMappingStatus.INVALID;
        }
        if (subject.endsWith("-expired")) {
            return RecommendationDataConsumerContracts.IdentityMappingStatus.EXPIRED;
        }
        if (subject.endsWith("-mismatch") || !"user:42".equals(user)) {
            return RecommendationDataConsumerContracts.IdentityMappingStatus.MISMATCHED;
        }
        return RecommendationDataConsumerContracts.IdentityMappingStatus.VALID;
    }

    private static boolean unique(List<RecommendationDataConsumerContracts.FixtureCase> fixtures) {
        return fixtures.stream().map(RecommendationDataConsumerContracts.FixtureCase::scenario).distinct().count()
                == fixtures.size();
    }

    private static Map<String, RecommendationDataConsumerContracts.RequirementClassification> toMap(
            List<RecommendationDataConsumerContracts.CompatibilityFinding> findings) {
        LinkedHashMap<String, RecommendationDataConsumerContracts.RequirementClassification> values = new LinkedHashMap<>();
        findings.forEach(finding -> values.put(finding.requirement(), finding.classification()));
        return Map.copyOf(values);
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
