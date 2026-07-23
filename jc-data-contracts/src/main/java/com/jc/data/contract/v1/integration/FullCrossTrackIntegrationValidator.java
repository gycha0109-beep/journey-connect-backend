package com.jc.data.contract.v1.integration;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class FullCrossTrackIntegrationValidator {
    public CrossTrackIntegrationResult validate(CrossTrackIntegrationContext context) {
        ArrayList<CrossTrackIntegrationCheck> checks = new ArrayList<>();
        int order = 0;
        List<CrossTrackIntegrationCheck> quality = new CrossTrackQualityVerdictValidator().validate(context, order);
        checks.addAll(quality); order += 10;
        List<CrossTrackIntegrationCheck> identity = new CrossTrackIdentityValidator().validate(context, order);
        checks.addAll(identity); order += 10;
        List<CrossTrackIntegrationCheck> authority = new CrossTrackAuthorityValidator().validate(context, order);
        checks.addAll(authority); order += 100;
        List<CrossTrackIntegrationCheck> privacy = new CrossTrackPrivacyValidator().validate(context, order);
        checks.addAll(privacy); order += 10;
        List<CrossTrackIntegrationCheck> retention = new CrossTrackRetentionValidator().validate(context, order);
        checks.addAll(retention); order += 10;
        List<CrossTrackIntegrationCheck> fingerprints = new CrossTrackFingerprintValidator().validate(context, order);
        checks.addAll(fingerprints); order += 10;
        checks.addAll(trackValidator(context, order));
        List<CrossTrackIntegrationCheck> ordered = checks.stream().sorted(Comparator.comparingInt(CrossTrackIntegrationCheck::order)
                .thenComparing(CrossTrackIntegrationCheck::checkCode)).toList();
        CrossTrackIntegrationVerdictStatus status = evaluate(ordered);
        long blockers = ordered.stream().filter(c -> c.checkStatus() == CrossTrackIntegrationCheckStatus.FAIL
                && c.severity() == CrossTrackIntegrationSeverity.BLOCKER).count();
        long errors = ordered.stream().filter(c -> c.checkStatus() == CrossTrackIntegrationCheckStatus.FAIL
                && c.severity() == CrossTrackIntegrationSeverity.ERROR).count();
        long warnings = ordered.stream().filter(c -> c.severity() == CrossTrackIntegrationSeverity.WARNING).count();
        long passed = ordered.stream().filter(c -> c.checkStatus() == CrossTrackIntegrationCheckStatus.PASS).count();
        long failed = ordered.stream().filter(c -> c.checkStatus() == CrossTrackIntegrationCheckStatus.FAIL).count();
        long skipped = ordered.stream().filter(c -> c.required()
                && c.checkStatus() == CrossTrackIntegrationCheckStatus.SKIPPED).count();
        long conditional = ordered.stream().filter(CrossTrackIntegrationCheck::conditionalRequirement).count();
        String verdictFingerprint = CrossTrackFingerprints.verdict(status, ordered);
        CrossTrackIntegrationVerdict verdict = new CrossTrackIntegrationVerdict(status, blockers, errors, warnings,
                passed, failed, skipped, conditional, verdictFingerprint);
        return new CrossTrackIntegrationResult(context.definition(), ordered, verdict,
                CrossTrackFingerprints.input(context), CrossTrackFingerprints.mapping(context.contractMapping()),
                CrossTrackFingerprints.contractMatrix(List.of(context.contractMapping())));
    }
    private static List<CrossTrackIntegrationCheck> trackValidator(CrossTrackIntegrationContext context, int order) {
        return switch (context.definition().integrationScope()) {
            case DATA_RECOMMENDATION_PROFILE, DATA_RECOMMENDATION_EXPERIMENT_OUTCOME ->
                    new DataRecommendationIntegrationValidator().validate(context, order);
            case DATA_INTELLIGENCE_INPUT -> new DataIntelligenceIntegrationValidator().validate(context, order);
            case DATA_SEARCH_INPUT -> new DataSearchIntegrationValidator().validate(context, order);
            default -> List.of();
        };
    }
    private static CrossTrackIntegrationVerdictStatus evaluate(List<CrossTrackIntegrationCheck> checks) {
        boolean requiredFail = checks.stream().anyMatch(c -> c.required()
                && c.checkStatus() == CrossTrackIntegrationCheckStatus.FAIL);
        boolean blocker = checks.stream().anyMatch(c -> c.checkStatus() == CrossTrackIntegrationCheckStatus.FAIL
                && c.severity() == CrossTrackIntegrationSeverity.BLOCKER);
        if (blocker || requiredFail) return CrossTrackIntegrationVerdictStatus.INCOMPATIBLE;
        boolean requiredSkipped = checks.stream().anyMatch(c -> c.required()
                && c.checkStatus() == CrossTrackIntegrationCheckStatus.SKIPPED);
        if (requiredSkipped) return CrossTrackIntegrationVerdictStatus.INCONCLUSIVE;
        boolean conditional = checks.stream().anyMatch(CrossTrackIntegrationCheck::conditionalRequirement);
        return conditional ? CrossTrackIntegrationVerdictStatus.CONDITIONALLY_COMPATIBLE
                : CrossTrackIntegrationVerdictStatus.COMPATIBLE;
    }
}
