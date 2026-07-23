package com.jc.data.contract;

import com.jc.data.contract.v1.integration.CrossTrackAuthorityRule;
import com.jc.data.contract.v1.integration.CrossTrackContractMapping;
import com.jc.data.contract.v1.integration.CrossTrackFingerprints;
import com.jc.data.contract.v1.integration.CrossTrackIdentityBinding;
import com.jc.data.contract.v1.integration.CrossTrackIntegrationCheck;
import com.jc.data.contract.v1.integration.CrossTrackIntegrationCheckStatus;
import com.jc.data.contract.v1.integration.CrossTrackIntegrationContext;
import com.jc.data.contract.v1.integration.CrossTrackIntegrationDefinition;
import com.jc.data.contract.v1.integration.CrossTrackIntegrationFailure;
import com.jc.data.contract.v1.integration.CrossTrackIntegrationPersistenceDisposition;
import com.jc.data.contract.v1.integration.CrossTrackIntegrationPersistenceOutcome;
import com.jc.data.contract.v1.integration.CrossTrackIntegrationPolicy;
import com.jc.data.contract.v1.integration.CrossTrackIntegrationResult;
import com.jc.data.contract.v1.integration.CrossTrackIntegrationScope;
import com.jc.data.contract.v1.integration.CrossTrackIntegrationVerdictStatus;
import com.jc.data.contract.v1.integration.CrossTrackPrivacyRule;
import com.jc.data.contract.v1.integration.CrossTrackQualityVerdictEvidence;
import com.jc.data.contract.v1.integration.CrossTrackQualityVerdictStatus;
import com.jc.data.contract.v1.integration.CrossTrackRetentionRule;
import com.jc.data.contract.v1.integration.CrossTrackSourceSnapshot;
import com.jc.data.contract.v1.integration.CrossTrackTargetContract;
import com.jc.data.contract.v1.integration.FullCrossTrackIntegrationValidator;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TimeZone;

public final class Dp7CrossTrackIntegrationContractTest {
    private static final Instant AS_OF = Instant.parse("2026-07-23T00:00:00Z");
    private static final FullCrossTrackIntegrationValidator VALIDATOR = new FullCrossTrackIntegrationValidator();
    private static int assertions;

    private Dp7CrossTrackIntegrationContractTest() { }

    public static void main(String[] args) throws Exception {
        recommendationFixtures();
        intelligenceFixtures();
        searchFixtures();
        commonBoundaryFixtures();
        deterministicContracts();
        persistenceOutcomes();
        failureTaxonomy();
        goldenFixture();
        System.out.println("DP-7 cross-track integration contract assertions PASS: " + assertions);
    }

    private static void recommendationFixtures() {
        CrossTrackIntegrationResult profile = VALIDATOR.validate(profileContext());
        check(profile.verdict().overallStatus() == CrossTrackIntegrationVerdictStatus.CONDITIONALLY_COMPATIBLE,
                "profile conditional compatibility");
        CrossTrackIntegrationResult outcome = VALIDATOR.validate(outcomeContext());
        check(outcome.verdict().overallStatus() == CrossTrackIntegrationVerdictStatus.CONDITIONALLY_COMPATIBLE,
                "outcome conditional compatibility");
        expectFailure(withMapping(profileContext(), mapping(profileContext(), true, true, true, false, true, true, true,
                List.of("activityWindowDays"))), CrossTrackIntegrationFailure.RECOMMENDATION_REQUIRED_FIELD_MISSING);
        expectFailure(withMapping(profileContext(), mapping(profileContext(), true, true, false, true, true, true, true,
                List.of())), CrossTrackIntegrationFailure.RECOMMENDATION_SCHEMA_UNSUPPORTED);
        expectFailure(withMapping(profileContext(), mapping(profileContext(), true, true, true, true, false, true, true,
                List.of())), CrossTrackIntegrationFailure.RECOMMENDATION_FIELD_SEMANTIC_MISMATCH);
        expectFailure(withMapping(profileContext(), mapping(profileContext(), true, true, true, true, true, false, true,
                List.of())), CrossTrackIntegrationFailure.RECOMMENDATION_UNIT_MISMATCH);
        expectFailure(withSnapshot(profileContext(), fields(profileContext(), Map.of("activityWindowDays", 14))),
                CrossTrackIntegrationFailure.RECOMMENDATION_WINDOW_MISMATCH);
        expectFailure(withSnapshot(outcomeContext(), fields(outcomeContext(), Map.of("outcomeWindowSeconds", 3600L))),
                CrossTrackIntegrationFailure.RECOMMENDATION_WINDOW_MISMATCH);
        expectFailure(withSnapshot(outcomeContext(), fields(outcomeContext(), Map.of("authoritativeP2Exposure", false))),
                CrossTrackIntegrationFailure.RECOMMENDATION_AUTHORITY_VIOLATION);
        expectFailure(withSnapshot(profileContext(), fields(profileContext(), Map.of("metricSemanticsPreserved", false))),
                CrossTrackIntegrationFailure.RECOMMENDATION_METRIC_SEMANTIC_VIOLATION);
        expectFailure(withRecommendationWrite(profileContext(), true),
                CrossTrackIntegrationFailure.RECOMMENDATION_PRODUCTION_WRITE_DETECTED);
    }

    private static void intelligenceFixtures() {
        CrossTrackIntegrationResult missing = VALIDATOR.validate(intelligenceContext(false));
        check(missing.verdict().overallStatus() == CrossTrackIntegrationVerdictStatus.INCONCLUSIVE,
                "generic Intelligence envelope without domain mapping is inconclusive");
        CrossTrackIntegrationResult approved = VALIDATOR.validate(intelligenceContext(true));
        check(approved.verdict().overallStatus() == CrossTrackIntegrationVerdictStatus.COMPATIBLE,
                "approved Intelligence domain mapping compatible");
        expectFailure(withSnapshot(intelligenceContext(true), fields(intelligenceContext(true),
                Map.of("qualityConfidenceSeparated", false))),
                CrossTrackIntegrationFailure.INTELLIGENCE_QUALITY_SEMANTIC_MISMATCH);
        expectFailure(withRuntime(intelligenceContext(true), true),
                CrossTrackIntegrationFailure.INTELLIGENCE_RUNTIME_ACTIVATION_DETECTED);
    }

    private static void searchFixtures() {
        CrossTrackIntegrationResult missing = VALIDATOR.validate(searchContext(false, false));
        check(missing.verdict().overallStatus() == CrossTrackIntegrationVerdictStatus.INCONCLUSIVE,
                "missing Data-to-Search contract inconclusive");
        CrossTrackIntegrationResult approved = VALIDATOR.validate(searchContext(true, false));
        check(approved.verdict().overallStatus() == CrossTrackIntegrationVerdictStatus.COMPATIBLE,
                "approved Search mapping compatible");
        expectFailure(searchContext(true, true), CrossTrackIntegrationFailure.SEARCH_DOCUMENT_IDENTITY_MISMATCH);
        expectFailure(withSearchFlags(searchContext(true, false), true, false),
                CrossTrackIntegrationFailure.SEARCH_PRODUCTION_INDEX_WRITE_DETECTED);
        expectFailure(withSearchFlags(searchContext(true, false), false, true),
                CrossTrackIntegrationFailure.SEARCH_CUTOVER_VIOLATION);
        expectFailure(withSnapshot(searchContext(true, false), fields(searchContext(true, false),
                Map.of("searchRegionSemanticsCompatible", false))),
                CrossTrackIntegrationFailure.SEARCH_REGION_SEMANTIC_MISMATCH);
        expectFailure(withSnapshot(searchContext(true, false), fields(searchContext(true, false),
                Map.of("searchContentSemanticsCompatible", false))),
                CrossTrackIntegrationFailure.SEARCH_CONTENT_SEMANTIC_MISMATCH);
        expectFailure(withSnapshot(searchContext(true, false), fields(searchContext(true, false),
                Map.of("searchTagSemanticsCompatible", false))),
                CrossTrackIntegrationFailure.SEARCH_TAG_SEMANTIC_MISMATCH);
    }

    private static void commonBoundaryFixtures() {
        expectFailure(withQuality(profileContext(), null), CrossTrackIntegrationFailure.QUALITY_VERDICT_MISSING);
        expectFailure(withQuality(profileContext(), quality(profileContext(), CrossTrackQualityVerdictStatus.REJECTED)),
                CrossTrackIntegrationFailure.QUALITY_VERDICT_REJECTED);
        CrossTrackIntegrationResult inconclusive = VALIDATOR.validate(withQuality(profileContext(),
                quality(profileContext(), CrossTrackQualityVerdictStatus.INCONCLUSIVE)));
        check(inconclusive.verdict().overallStatus() == CrossTrackIntegrationVerdictStatus.INCONCLUSIVE,
                "inconclusive quality remains inconclusive");
        expectFailure(withIdentity(profileContext(), null), CrossTrackIntegrationFailure.CROSS_TRACK_IDENTITY_BINDING_MISSING);
        String automaticFingerprint = bindingFingerprint("user:42", "subject:fixture", "fixture");
        CrossTrackIdentityBinding auto = new CrossTrackIdentityBinding("user:42", "subject:fixture",
                "cross-track-identity-binding-v1", "fixture", automaticFingerprint, automaticFingerprint,
                "cross-track-integration", "Data", true);
        expectFailure(withIdentity(profileContext(), auto),
                CrossTrackIntegrationFailure.CROSS_TRACK_IDENTITY_AUTOMATIC_MERGE_DETECTED);
        CrossTrackPrivacyRule privacy = new CrossTrackPrivacyRule("pseudonymous", "pseudonymous", false, true,
                false, false, true, true, false);
        expectFailure(withPrivacy(profileContext(), privacy), CrossTrackIntegrationFailure.CROSS_TRACK_PII_EXPOSURE);
        CrossTrackRetentionRule retention = new CrossTrackRetentionRule(90, 180, 90, true, false, false);
        expectFailure(withRetention(profileContext(), retention), CrossTrackIntegrationFailure.CROSS_TRACK_RETENTION_CONFLICT);
        List<CrossTrackAuthorityRule> authority = List.of(new CrossTrackAuthorityRule("Recommendation decision",
                "Recommendation", Set.of("Data"), Set.of("Recommendation"), Set.of("Data"), "Recommendation",
                "Data", true, true, true, false));
        expectFailure(withAuthority(profileContext(), authority),
                CrossTrackIntegrationFailure.CROSS_TRACK_WRITE_AUTHORITY_VIOLATION);
        expectFailure(withExpectedSnapshotFingerprint(profileContext(), hex("tampered")),
                CrossTrackIntegrationFailure.CROSS_TRACK_FINGERPRINT_INVALID);
    }

    private static void deterministicContracts() {
        CrossTrackIntegrationContext original = profileContext();
        LinkedHashMap<String, Object> reversed = new LinkedHashMap<>();
        ArrayList<Map.Entry<String, Object>> entries = new ArrayList<>(original.sourceSnapshot().fields().entrySet());
        Collections.reverse(entries);
        for (Map.Entry<String, Object> entry : entries) reversed.put(entry.getKey(), entry.getValue());
        CrossTrackIntegrationContext reordered = withSnapshot(original, new CrossTrackSourceSnapshot(
                original.sourceSnapshot().snapshotRef(), original.sourceSnapshot().projectionName(),
                original.sourceSnapshot().sourceContract(), original.sourceSnapshot().sourceSchemaVersion(),
                original.sourceSnapshot().projectionPolicyVersion(), original.sourceSnapshot().contentFingerprint(),
                original.sourceSnapshot().lineageFingerprint(), original.sourceSnapshot().identityNamespace(),
                "git:9999999999999999999999999999999999999999", original.sourceSnapshot().snapshotAsOf(), original.sourceSnapshot().sourceRetentionDays(),
                List.of("event:b", "event:a"), reversed));
        Locale oldLocale = Locale.getDefault();
        TimeZone oldZone = TimeZone.getDefault();
        try {
            Locale.setDefault(Locale.forLanguageTag("tr-TR"));
            TimeZone.setDefault(TimeZone.getTimeZone("Asia/Seoul"));
            CrossTrackIntegrationResult first = VALIDATOR.validate(original);
            CrossTrackIntegrationResult second = VALIDATOR.validate(reordered);
            check(first.integrationInputFingerprint().equals(second.integrationInputFingerprint()),
                    "map/source insertion order and build ID independent input fingerprint");
            check(first.verdict().verdictFingerprint().equals(second.verdict().verdictFingerprint()),
                    "locale/timezone independent verdict fingerprint");
            check(first.checks().stream().map(CrossTrackIntegrationCheck::checkCode).toList()
                    .equals(second.checks().stream().map(CrossTrackIntegrationCheck::checkCode).toList()),
                    "stable check ordering");
        } finally {
            Locale.setDefault(oldLocale);
            TimeZone.setDefault(oldZone);
        }
    }

    private static void persistenceOutcomes() {
        String fp = VALIDATOR.validate(profileContext()).verdict().verdictFingerprint();
        CrossTrackIntegrationPersistenceOutcome fresh = new CrossTrackIntegrationPersistenceOutcome(
                CrossTrackIntegrationPersistenceDisposition.NEW, "integration_run:new", fp, null);
        CrossTrackIntegrationPersistenceOutcome duplicate = new CrossTrackIntegrationPersistenceOutcome(
                CrossTrackIntegrationPersistenceDisposition.DUPLICATE, "integration_run:new", fp, null);
        CrossTrackIntegrationPersistenceOutcome conflict = new CrossTrackIntegrationPersistenceOutcome(
                CrossTrackIntegrationPersistenceDisposition.CONFLICT, "integration_run:new", fp,
                CrossTrackIntegrationFailure.CROSS_TRACK_INTEGRATION_VERDICT_CONFLICT);
        check(fresh.disposition() == CrossTrackIntegrationPersistenceDisposition.NEW, "NEW outcome");
        check(duplicate.disposition() == CrossTrackIntegrationPersistenceDisposition.DUPLICATE, "DUPLICATE outcome");
        check(conflict.failure() == CrossTrackIntegrationFailure.CROSS_TRACK_INTEGRATION_VERDICT_CONFLICT,
                "stable CONFLICT outcome");
    }

    private static void failureTaxonomy() {
        Set<String> values = new java.util.HashSet<>();
        for (CrossTrackIntegrationFailure failure : CrossTrackIntegrationFailure.values()) {
            check(values.add(failure.wireValue()), "unique failure code " + failure.wireValue());
        }
    }

    private static void goldenFixture() throws Exception {
        Path path = Path.of("..", "jc-data-contracts", "src", "test", "resources", "dp7-cross-track-golden-v1.tsv");
        if (!Files.isRegularFile(path)) {
            path = Path.of("src", "test", "resources", "dp7-cross-track-golden-v1.tsv");
        }
        List<String> lines = Files.readAllLines(path);
        check(lines.size() >= 9, "golden fixture coverage");
        Map<String, CrossTrackIntegrationVerdictStatus> expected = new HashMap<>();
        for (String line : lines.subList(1, lines.size())) {
            String[] fields = line.split("\\t", -1);
            expected.put(fields[0], CrossTrackIntegrationVerdictStatus.valueOf(fields[1]));
        }
        check(expected.get("recommendation_profile_valid") == VALIDATOR.validate(profileContext()).verdict().overallStatus(),
                "golden recommendation profile");
        check(expected.get("recommendation_outcome_valid") == VALIDATOR.validate(outcomeContext()).verdict().overallStatus(),
                "golden recommendation outcome");
        check(expected.get("intelligence_mapping_missing") == VALIDATOR.validate(intelligenceContext(false)).verdict().overallStatus(),
                "golden Intelligence missing mapping");
        check(expected.get("search_contract_missing") == VALIDATOR.validate(searchContext(false, false)).verdict().overallStatus(),
                "golden Search missing contract");
    }

    private static CrossTrackIntegrationContext profileContext() {
        return context(CrossTrackIntegrationScope.DATA_RECOMMENDATION_PROFILE,
                "recommendation-profile-input-v1", "recommendation-profile-input-v1", true, true,
                Map.of("activityWindowDays", 30, "metricSemanticsPreserved", true,
                        "qualityConfidenceSeparated", true, "searchRegionSemanticsCompatible", true,
                        "searchContentSemanticsCompatible", true, "searchTagSemanticsCompatible", true,
                        "stableSearchDocumentIdentity", true));
    }

    private static CrossTrackIntegrationContext outcomeContext() {
        return context(CrossTrackIntegrationScope.DATA_RECOMMENDATION_EXPERIMENT_OUTCOME,
                "experiment-outcome-input-v1", "recommendation-evaluation-dataset-v1", true, true,
                Map.of("outcomeWindowSeconds", 604800L, "metricSemanticsPreserved", true,
                        "authoritativeP2Exposure", true, "qualityConfidenceSeparated", true,
                        "searchRegionSemanticsCompatible", true, "searchContentSemanticsCompatible", true,
                        "searchTagSemanticsCompatible", true, "stableSearchDocumentIdentity", true));
    }

    private static CrossTrackIntegrationContext intelligenceContext(boolean domainMapping) {
        return context(CrossTrackIntegrationScope.DATA_INTELLIGENCE_INPUT,
                "recommendation-profile-input-v1", "intelligence-input-snapshot-v1", true, domainMapping,
                Map.of("activityWindowDays", 30, "metricSemanticsPreserved", true,
                        "qualityConfidenceSeparated", true, "searchRegionSemanticsCompatible", true,
                        "searchContentSemanticsCompatible", true, "searchTagSemanticsCompatible", true,
                        "stableSearchDocumentIdentity", true));
    }

    private static CrossTrackIntegrationContext searchContext(boolean contractPresent, boolean directDocumentMapping) {
        return context(CrossTrackIntegrationScope.DATA_SEARCH_INPUT,
                "recommendation-profile-input-v1", "search-document-projection-v1", contractPresent, contractPresent,
                Map.of("activityWindowDays", 30, "metricSemanticsPreserved", true,
                        "qualityConfidenceSeparated", true, "searchRegionSemanticsCompatible", true,
                        "searchContentSemanticsCompatible", true, "searchTagSemanticsCompatible", true,
                        "stableSearchDocumentIdentity", !directDocumentMapping));
    }

    private static CrossTrackIntegrationContext context(CrossTrackIntegrationScope scope, String sourceContract,
            String targetContract, boolean targetPresent, boolean domainMapping, Map<String, Object> fields) {
        String snapshotFp = hex("snapshot-" + scope.name());
        String lineageFp = hex("lineage-" + scope.name());
        String verdictFp = hex("quality-" + scope.name());
        CrossTrackIntegrationDefinition definition = new CrossTrackIntegrationDefinition(scope, "Data",
                targetTrack(scope), sourceContract, sourceContract, targetContract, targetContract,
                "data-cross-track-mapping-policy-v1", CrossTrackIntegrationPolicy.VERSION,
                "data-cross-track-integration-validator-v1", List.of("quality", "identity", "authority", "privacy", "retention", "fingerprint"));
        CrossTrackSourceSnapshot snapshot = new CrossTrackSourceSnapshot("snapshot:" + scope.name().toLowerCase(Locale.ROOT),
                sourceContract, sourceContract, sourceContract, "data-projection-policy-v1", snapshotFp, lineageFp,
                "subject:fixture", "git:1111111111111111111111111111111111111111", AS_OF, 90,
                List.of("event:a", "event:b"), fields);
        CrossTrackTargetContract target = new CrossTrackTargetContract(targetTrack(scope), targetContract,
                targetContract, targetPresent, targetPresent, domainMapping, "subject:fixture", "pseudonymous", 90,
                List.of("snapshotRef", "schemaVersion"), Map.of("window", scope.name()));
        CrossTrackContractMapping mapping = new CrossTrackContractMapping(sourceContract, sourceContract,
                targetContract, targetContract, "data-cross-track-mapping-policy-v1", targetPresent, targetPresent,
                true, true, true, true, domainMapping, List.of(), Map.of("subjectRef", "subjectRef"), Map.of("window", "days"));
        String authoritativeBindingFingerprint = bindingFingerprint("user:42", "subject:fixture", "approved-fixture");
        CrossTrackIdentityBinding binding = new CrossTrackIdentityBinding("user:42", "subject:fixture",
                "cross-track-identity-binding-v1", "approved-fixture", authoritativeBindingFingerprint,
                authoritativeBindingFingerprint, "cross-track-integration", "Data", false);
        List<CrossTrackAuthorityRule> authority = List.of(
                new CrossTrackAuthorityRule("canonical event", "Data", Set.of("Data"), Set.of("Data"), Set.of("Data"),
                        "Data", "Data", true, false, true, false),
                new CrossTrackAuthorityRule("target authoritative object", targetTrack(scope), Set.of("Data", targetTrack(scope)),
                        Set.of(targetTrack(scope)), Set.of("Data", targetTrack(scope)), targetTrack(scope), "Data",
                        true, false, true, false));
        CrossTrackPrivacyRule privacy = new CrossTrackPrivacyRule("pseudonymous", "pseudonymous", false, false,
                false, false, true, true, false);
        CrossTrackRetentionRule retention = new CrossTrackRetentionRule(90, 90, 90, true, false, false);
        CrossTrackQualityVerdictEvidence quality = new CrossTrackQualityVerdictEvidence("quality_verdict:" + scope.name().toLowerCase(Locale.ROOT),
                snapshot.snapshotRef(), "data-quality-policy-v1", CrossTrackQualityVerdictStatus.VALIDATED,
                verdictFp, false, true);
        return new CrossTrackIntegrationContext(definition, snapshot, quality, target, mapping, binding, authority,
                privacy, retention, CrossTrackIntegrationPolicy.v1(), AS_OF, snapshotFp, lineageFp, verdictFp,
                false, false, false, false, scope == CrossTrackIntegrationScope.DATA_SEARCH_INPUT && !Boolean.TRUE.equals(fields.get("stableSearchDocumentIdentity")));
    }

    private static String targetTrack(CrossTrackIntegrationScope scope) {
        return switch (scope) {
            case DATA_RECOMMENDATION_PROFILE, DATA_RECOMMENDATION_EXPERIMENT_OUTCOME -> "Recommendation";
            case DATA_INTELLIGENCE_INPUT -> "Intelligence";
            case DATA_SEARCH_INPUT -> "Search";
            default -> "Data";
        };
    }

    private static CrossTrackContractMapping mapping(CrossTrackIntegrationContext context, boolean present,
            boolean authority, boolean schema, boolean required, boolean semantics, boolean units,
            boolean domain, List<String> missing) {
        CrossTrackContractMapping old = context.contractMapping();
        return new CrossTrackContractMapping(old.sourceContract(), old.sourceSchemaVersion(), old.targetContract(),
                old.targetSchemaVersion(), old.mappingPolicyVersion(), present, authority, schema, required,
                semantics, units, domain, missing, old.semanticMappings(), old.unitMappings());
    }

    private static CrossTrackIntegrationContext withMapping(CrossTrackIntegrationContext c, CrossTrackContractMapping m) {
        return copy(c, c.sourceSnapshot(), c.qualityVerdict(), c.identityBinding(), c.authorityRules(), c.privacyRule(),
                c.retentionRule(), m, c.recommendationProductionWriteAttempted(), c.intelligenceRuntimeActivationAttempted(),
                c.searchIndexWriteAttempted(), c.searchCutoverAttempted(), c.searchDocumentMappingAttempted(),
                c.expectedSnapshotFingerprint());
    }

    private static CrossTrackIntegrationContext withSnapshot(CrossTrackIntegrationContext c, CrossTrackSourceSnapshot s) {
        return copy(c, s, c.qualityVerdict(), c.identityBinding(), c.authorityRules(), c.privacyRule(), c.retentionRule(),
                c.contractMapping(), c.recommendationProductionWriteAttempted(), c.intelligenceRuntimeActivationAttempted(),
                c.searchIndexWriteAttempted(), c.searchCutoverAttempted(), c.searchDocumentMappingAttempted(),
                c.expectedSnapshotFingerprint());
    }

    private static CrossTrackSourceSnapshot fields(CrossTrackIntegrationContext c, Map<String, Object> overrides) {
        LinkedHashMap<String, Object> fields = new LinkedHashMap<>(c.sourceSnapshot().fields());
        fields.putAll(overrides);
        CrossTrackSourceSnapshot s = c.sourceSnapshot();
        return new CrossTrackSourceSnapshot(s.snapshotRef(), s.projectionName(), s.sourceContract(), s.sourceSchemaVersion(),
                s.projectionPolicyVersion(), s.contentFingerprint(), s.lineageFingerprint(), s.identityNamespace(),
                s.producerBuildId(), s.snapshotAsOf(), s.sourceRetentionDays(), s.sourceEventRefs(), fields);
    }

    private static CrossTrackIntegrationContext withQuality(CrossTrackIntegrationContext c, CrossTrackQualityVerdictEvidence q) {
        return copy(c, c.sourceSnapshot(), q, c.identityBinding(), c.authorityRules(), c.privacyRule(), c.retentionRule(),
                c.contractMapping(), c.recommendationProductionWriteAttempted(), c.intelligenceRuntimeActivationAttempted(),
                c.searchIndexWriteAttempted(), c.searchCutoverAttempted(), c.searchDocumentMappingAttempted(),
                c.expectedSnapshotFingerprint());
    }

    private static CrossTrackQualityVerdictEvidence quality(CrossTrackIntegrationContext c, CrossTrackQualityVerdictStatus status) {
        return new CrossTrackQualityVerdictEvidence(c.qualityVerdict().verdictRef(), c.sourceSnapshot().snapshotRef(),
                "data-quality-policy-v1", status, c.qualityVerdict().verdictFingerprint(), false, true);
    }

    private static CrossTrackIntegrationContext withIdentity(CrossTrackIntegrationContext c, CrossTrackIdentityBinding identity) {
        return copy(c, c.sourceSnapshot(), c.qualityVerdict(), identity, c.authorityRules(), c.privacyRule(), c.retentionRule(),
                c.contractMapping(), c.recommendationProductionWriteAttempted(), c.intelligenceRuntimeActivationAttempted(),
                c.searchIndexWriteAttempted(), c.searchCutoverAttempted(), c.searchDocumentMappingAttempted(),
                c.expectedSnapshotFingerprint());
    }

    private static CrossTrackIntegrationContext withPrivacy(CrossTrackIntegrationContext c, CrossTrackPrivacyRule privacy) {
        return copy(c, c.sourceSnapshot(), c.qualityVerdict(), c.identityBinding(), c.authorityRules(), privacy, c.retentionRule(),
                c.contractMapping(), c.recommendationProductionWriteAttempted(), c.intelligenceRuntimeActivationAttempted(),
                c.searchIndexWriteAttempted(), c.searchCutoverAttempted(), c.searchDocumentMappingAttempted(),
                c.expectedSnapshotFingerprint());
    }

    private static CrossTrackIntegrationContext withRetention(CrossTrackIntegrationContext c, CrossTrackRetentionRule retention) {
        return copy(c, c.sourceSnapshot(), c.qualityVerdict(), c.identityBinding(), c.authorityRules(), c.privacyRule(), retention,
                c.contractMapping(), c.recommendationProductionWriteAttempted(), c.intelligenceRuntimeActivationAttempted(),
                c.searchIndexWriteAttempted(), c.searchCutoverAttempted(), c.searchDocumentMappingAttempted(),
                c.expectedSnapshotFingerprint());
    }

    private static CrossTrackIntegrationContext withAuthority(CrossTrackIntegrationContext c, List<CrossTrackAuthorityRule> authority) {
        return copy(c, c.sourceSnapshot(), c.qualityVerdict(), c.identityBinding(), authority, c.privacyRule(), c.retentionRule(),
                c.contractMapping(), c.recommendationProductionWriteAttempted(), c.intelligenceRuntimeActivationAttempted(),
                c.searchIndexWriteAttempted(), c.searchCutoverAttempted(), c.searchDocumentMappingAttempted(),
                c.expectedSnapshotFingerprint());
    }

    private static CrossTrackIntegrationContext withExpectedSnapshotFingerprint(CrossTrackIntegrationContext c, String fp) {
        return copy(c, c.sourceSnapshot(), c.qualityVerdict(), c.identityBinding(), c.authorityRules(), c.privacyRule(),
                c.retentionRule(), c.contractMapping(), c.recommendationProductionWriteAttempted(),
                c.intelligenceRuntimeActivationAttempted(), c.searchIndexWriteAttempted(), c.searchCutoverAttempted(),
                c.searchDocumentMappingAttempted(), fp);
    }

    private static CrossTrackIntegrationContext withRecommendationWrite(CrossTrackIntegrationContext c, boolean attempted) {
        return copy(c, c.sourceSnapshot(), c.qualityVerdict(), c.identityBinding(), c.authorityRules(), c.privacyRule(),
                c.retentionRule(), c.contractMapping(), attempted, c.intelligenceRuntimeActivationAttempted(),
                c.searchIndexWriteAttempted(), c.searchCutoverAttempted(), c.searchDocumentMappingAttempted(),
                c.expectedSnapshotFingerprint());
    }

    private static CrossTrackIntegrationContext withRuntime(CrossTrackIntegrationContext c, boolean attempted) {
        return copy(c, c.sourceSnapshot(), c.qualityVerdict(), c.identityBinding(), c.authorityRules(), c.privacyRule(),
                c.retentionRule(), c.contractMapping(), c.recommendationProductionWriteAttempted(), attempted,
                c.searchIndexWriteAttempted(), c.searchCutoverAttempted(), c.searchDocumentMappingAttempted(),
                c.expectedSnapshotFingerprint());
    }

    private static CrossTrackIntegrationContext withSearchFlags(CrossTrackIntegrationContext c, boolean index, boolean cutover) {
        return copy(c, c.sourceSnapshot(), c.qualityVerdict(), c.identityBinding(), c.authorityRules(), c.privacyRule(),
                c.retentionRule(), c.contractMapping(), c.recommendationProductionWriteAttempted(),
                c.intelligenceRuntimeActivationAttempted(), index, cutover, c.searchDocumentMappingAttempted(),
                c.expectedSnapshotFingerprint());
    }

    private static CrossTrackIntegrationContext copy(CrossTrackIntegrationContext c, CrossTrackSourceSnapshot snapshot,
            CrossTrackQualityVerdictEvidence quality, CrossTrackIdentityBinding identity,
            List<CrossTrackAuthorityRule> authority, CrossTrackPrivacyRule privacy, CrossTrackRetentionRule retention,
            CrossTrackContractMapping mapping, boolean recommendationWrite, boolean intelligenceRuntime,
            boolean searchIndex, boolean searchCutover, boolean searchDocumentMapping, String expectedSnapshotFp) {
        return new CrossTrackIntegrationContext(c.definition(), snapshot, quality, c.targetContract(), mapping, identity,
                authority, privacy, retention, c.integrationPolicy(), c.validationAsOf(), expectedSnapshotFp,
                c.expectedLineageFingerprint(), c.expectedQualityVerdictFingerprint(), recommendationWrite,
                intelligenceRuntime, searchIndex, searchCutover, searchDocumentMapping);
    }

    private static void expectFailure(CrossTrackIntegrationContext context, CrossTrackIntegrationFailure failure) {
        CrossTrackIntegrationResult result = VALIDATOR.validate(context);
        check(result.checks().stream().anyMatch(c -> c.failure() == failure), "expected failure " + failure.wireValue());
        if (failure != CrossTrackIntegrationFailure.QUALITY_VERDICT_MISSING
                && failure != CrossTrackIntegrationFailure.QUALITY_VERDICT_INCONCLUSIVE) {
            check(result.verdict().overallStatus() == CrossTrackIntegrationVerdictStatus.INCOMPATIBLE,
                    "failure must fail closed " + failure.wireValue());
        }
    }

    private static String bindingFingerprint(String source, String target, String bindingSource) {
        return hex("authoritative-binding:" + source + ":" + target + ":" + bindingSource);
    }

    private static String hex(String value) {
        return CrossTrackFingerprints.fingerprint("fixture-sha256-v1", Map.of("value", value));
    }

    private static void check(boolean condition, String message) {
        assertions++;
        if (!condition) throw new AssertionError(message);
    }
}
