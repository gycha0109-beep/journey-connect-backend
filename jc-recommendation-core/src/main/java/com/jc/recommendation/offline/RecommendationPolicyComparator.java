package com.jc.recommendation.offline;

import com.jc.recommendation.evaluation.RecommendationBehaviorEventResolver;
import com.jc.recommendation.evaluation.RecommendationOutcomeAttributor;
import com.jc.recommendation.exposure.RecommendationExposureEventResolver;
import com.jc.recommendation.model.diversity.DiversityCandidateMetadata;
import com.jc.recommendation.model.diversity.DiversityDimension;
import com.jc.recommendation.model.diversity.DiversityDimensionCounts;
import com.jc.recommendation.model.evaluation.AttributeRecommendationOutcomesInput;
import com.jc.recommendation.model.evaluation.AttributeRecommendationOutcomesResult;
import com.jc.recommendation.model.exploration.ExplorationCandidateOrigin;
import com.jc.recommendation.model.exploration.ExplorationFinalCandidate;
import com.jc.recommendation.model.exploration.InsertedExplorationCandidate;
import com.jc.recommendation.model.exploration.PersonalizedExplorationCandidate;
import com.jc.recommendation.model.offline.OfflineEvaluationContracts.CollectedRankingV3Result;
import com.jc.recommendation.model.offline.OfflineEvaluationContracts.CompareInput;
import com.jc.recommendation.model.offline.OfflineEvaluationContracts.ComparisonMode;
import com.jc.recommendation.model.offline.OfflineEvaluationContracts.DiversityMetricsAtK;
import com.jc.recommendation.model.offline.OfflineEvaluationContracts.GlobalAttributionQuality;
import com.jc.recommendation.model.offline.OfflineEvaluationContracts.InvariantViolationCode;
import com.jc.recommendation.model.offline.OfflineEvaluationContracts.ObservationSupportScope;
import com.jc.recommendation.model.offline.OfflineEvaluationContracts.ObservedSupportMetricsAtK;
import com.jc.recommendation.model.offline.OfflineEvaluationContracts.OriginMetricsAtK;
import com.jc.recommendation.model.offline.OfflineEvaluationContracts.PolicyComparisonResult;
import com.jc.recommendation.model.offline.OfflineEvaluationContracts.PolicyVector;
import com.jc.recommendation.model.offline.OfflineEvaluationContracts.RankingV3ReplayInputSnapshot;
import com.jc.recommendation.model.offline.OfflineEvaluationContracts.ReplayStatus;
import com.jc.recommendation.model.offline.OfflineEvaluationContracts.TopKStructuralMetrics;
import com.jc.recommendation.policy.DiversityPolicy;
import com.jc.recommendation.policy.OfflineEvaluationPolicies;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

public final class RecommendationPolicyComparator {
    private final RecommendationReplayEvaluator replayEvaluator = new RecommendationReplayEvaluator();
    private final RecommendationExposureEventResolver exposureResolver = new RecommendationExposureEventResolver();
    private final RecommendationBehaviorEventResolver behaviorResolver = new RecommendationBehaviorEventResolver();
    private final RecommendationOutcomeAttributor attributor = new RecommendationOutcomeAttributor();
    private final RankingV3FullResultCollector collector = new RankingV3FullResultCollector();

    public PolicyComparisonResult compare(CompareInput input) {
        validateInput(input);
        var replay = replayEvaluator.evaluate(input.evaluationCase());
        if (replay.status() != ReplayStatus.EXACT_MATCH && replay.status() != ReplayStatus.PARTIAL_OBSERVATION) {
            invalid("baseline replay is not usable");
        }
        if (!replay.equals(input.baselineReplayResult())) {
            invalid("baseline replay result does not match case");
        }
        AttributeRecommendationOutcomesResult expectedAttribution = attributor.attribute(
                new AttributeRecommendationOutcomesInput(
                        input.evaluationCase().caseId(),
                        exposureResolver.resolve(input.evaluationCase().exposureEvents()),
                        behaviorResolver.resolve(input.evaluationCase().behaviorEvents()),
                        input.evaluationCase().evaluationCutoffAt()));
        if (!expectedAttribution.equals(input.attributionResult())) {
            invalid("attribution result does not match case");
        }
        if (!input.evaluationCase().caseId().equals(input.baselineReplayResult().caseId())
                || !input.evaluationCase().caseId().equals(input.attributionResult().caseId())) {
            invalid("case binding mismatch");
        }
        if (!input.baselineReplayResult().recommendationRunId()
                .equals(input.attributionResult().recommendationRunId())
                || !input.baselineReplayResult().replayKey().equals(input.attributionResult().replayKey())) {
            invalid("run binding mismatch");
        }

        RankingV3ReplayInputSnapshot baselineSnapshot = input.evaluationCase().rankingInputSnapshot();
        PolicyVector baselineVector = new PolicyVector(
                baselineSnapshot.policy(), baselineSnapshot.diversityPolicy(),
                baselineSnapshot.explorationPolicy(), baselineSnapshot.explorationSeed());
        PolicyVector treatmentVector = input.treatmentPolicyVector();
        boolean seedEqual = baselineVector.explorationSeed().equals(treatmentVector.explorationSeed());
        boolean policyEqual = baselineVector.rankingPolicy().equals(treatmentVector.rankingPolicy())
                && baselineVector.diversityPolicy().equals(treatmentVector.diversityPolicy())
                && baselineVector.explorationPolicy().equals(treatmentVector.explorationPolicy());
        ComparisonMode comparisonMode = seedEqual
                ? policyEqual ? ComparisonMode.IDENTICAL_VECTOR : ComparisonMode.POLICY_ONLY
                : ComparisonMode.POLICY_AND_SEED;

        CollectedRankingV3Result baseline = collector.collect(baselineSnapshot);
        RankingV3ReplayInputSnapshot treatmentSnapshot = new RankingV3ReplayInputSnapshot(
                baselineSnapshot.rankingSnapshotId(), baselineSnapshot.metadataSnapshotId(),
                baselineSnapshot.explorationSnapshotId(), baselineSnapshot.userId(), baselineSnapshot.contextId(),
                baselineSnapshot.scorePolicyVersion(), baselineSnapshot.componentPolicyVersions(),
                treatmentVector.explorationSeed(), baselineSnapshot.candidates(), baselineSnapshot.candidateMetadata(),
                baselineSnapshot.explorationMetadata(), treatmentVector.rankingPolicy(),
                treatmentVector.diversityPolicy(), treatmentVector.explorationPolicy());
        CollectedRankingV3Result treatment = collector.collect(treatmentSnapshot);

        Set<String> support = new HashSet<>();
        var resolved = exposureResolver.resolve(input.evaluationCase().exposureEvents());
        for (var exposure : resolved.resolvedEvents()) {
            for (var candidate : exposure.candidates()) {
                support.add(identity(candidate.entityType().wireValue(), candidate.entityId()));
            }
        }
        Function<ExplorationFinalCandidate, DiversityCandidateMetadata> metadataResolver =
                metadataResolver(baselineSnapshot);
        Map<String, Integer> baselineRanks = rankMap(baseline.finalCandidates());
        Map<String, Integer> treatmentRanks = rankMap(treatment.finalCandidates());

        List<TopKStructuralMetrics> metrics = new ArrayList<>();
        for (int cutoff : OfflineEvaluationPolicies.V1.cutoffs()) {
            List<ExplorationFinalCandidate> baselineTop = baseline.finalCandidates().stream().limit(cutoff).toList();
            List<ExplorationFinalCandidate> treatmentTop = treatment.finalCandidates().stream().limit(cutoff).toList();
            Set<String> baselineSet = identitySet(baselineTop);
            Set<String> treatmentSet = identitySet(treatmentTop);
            Set<String> intersection = new HashSet<>(baselineSet);
            intersection.retainAll(treatmentSet);
            Set<String> union = new HashSet<>(baselineSet);
            union.addAll(treatmentSet);
            int denominator = Math.min(baselineTop.size(), treatmentTop.size());
            List<Double> shifts = new ArrayList<>();
            for (String id : intersection) {
                shifts.add((double) Math.abs(treatmentRanks.get(id) - baselineRanks.get(id)));
            }
            DiversityMetricsAtK baselineDiversity = diversityMetrics(
                    baselineTop, metadataResolver, baselineVector.diversityPolicy());
            DiversityMetricsAtK treatmentDiversity = diversityMetrics(
                    treatmentTop, metadataResolver, treatmentVector.diversityPolicy());
            metrics.add(new TopKStructuralMetrics(
                    cutoff,
                    intersection.size(),
                    union.size(),
                    union.isEmpty() ? 1.0 : (double) intersection.size() / union.size(),
                    baselineTop.isEmpty() && treatmentTop.isEmpty() ? 1.0
                            : baselineTop.isEmpty() || treatmentTop.isEmpty() ? 0.0
                            : (double) intersection.size() / denominator,
                    baselineTop.size(),
                    treatmentTop.size(),
                    treatmentTop.size() - baselineTop.size(),
                    mean(shifts),
                    shifts.isEmpty() ? null : shifts.stream().mapToDouble(Double::doubleValue).max().orElseThrow(),
                    originMetrics(baselineTop),
                    originMetrics(treatmentTop),
                    baselineDiversity,
                    treatmentDiversity,
                    treatmentDiversity.uniqueAuthorCountAtK() - baselineDiversity.uniqueAuthorCountAtK(),
                    treatmentDiversity.uniqueRegionCountAtK() - baselineDiversity.uniqueRegionCountAtK(),
                    treatmentDiversity.uniqueThemeCountAtK() - baselineDiversity.uniqueThemeCountAtK(),
                    treatmentDiversity.duplicateGroupCollisionCountAtK()
                            - baselineDiversity.duplicateGroupCollisionCountAtK(),
                    outcomeMetrics(input.attributionResult(), treatmentTop, baselineTop, support)
            ));
        }

        int behaviorDenominator = input.attributionResult().resolvedBehaviorEventCount();
        GlobalAttributionQuality quality = new GlobalAttributionQuality(
                behaviorDenominator,
                input.attributionResult().attributedOutcomeEventCount(),
                input.attributionResult().ambiguousOutcomeEventCount(),
                input.attributionResult().unmatchedOutcomeEventCount(),
                input.attributionResult().runUserSessionMismatchCount(),
                behaviorDenominator == 0 ? 0.0
                        : (double) input.attributionResult().ambiguousOutcomeEventCount() / behaviorDenominator,
                behaviorDenominator == 0 ? 0.0
                        : (double) input.attributionResult().unmatchedOutcomeEventCount() / behaviorDenominator);
        LinkedHashSet<InvariantViolationCode> invariantViolations = new LinkedHashSet<>();
        invariantViolations.addAll(baseline.invariantViolations());
        invariantViolations.addAll(treatment.invariantViolations());
        ObservationSupportScope supportScope = replay.status() == ReplayStatus.EXACT_MATCH
                ? ObservationSupportScope.FULL : ObservationSupportScope.PARTIAL;

        return new PolicyComparisonResult(
                input.evaluationCase().caseId(), input.attributionResult().recommendationRunId(),
                input.attributionResult().replayKey(), comparisonMode, baselineVector, treatmentVector,
                replay.status(), supportScope,
                baseline.finalCandidates().size(), treatment.finalCandidates().size(),
                treatment.finalCandidates().size() - baseline.finalCandidates().size(),
                baseline.firstPage().explorationInsertedCandidateCount(),
                treatment.firstPage().explorationInsertedCandidateCount(),
                treatment.firstPage().explorationInsertedCandidateCount()
                        - baseline.firstPage().explorationInsertedCandidateCount(),
                baseline.terminalCandidates().size(), treatment.terminalCandidates().size(),
                treatment.terminalCandidates().size() - baseline.terminalCandidates().size(),
                metrics, quality, List.copyOf(invariantViolations));
    }

    private static void validateInput(CompareInput input) {
        if (input.treatmentPolicyVector().explorationSeed().isBlank()) {
            invalid("treatment policy vector seed");
        }
        int bytes = input.treatmentPolicyVector().explorationSeed().getBytes(StandardCharsets.UTF_8).length;
        if (bytes < 1 || bytes > 128) {
            invalid("treatment seed byte length");
        }
    }

    private static Function<ExplorationFinalCandidate, DiversityCandidateMetadata> metadataResolver(
            RankingV3ReplayInputSnapshot input
    ) {
        Map<String, DiversityCandidateMetadata> personalized = new HashMap<>();
        for (DiversityCandidateMetadata item : input.candidateMetadata()) {
            String id = identity(item.entityType().wireValue(), item.entityId());
            if (personalized.put(id, item) != null) {
                throw new IllegalStateException("METADATA_RESOLUTION_FAILURE");
            }
        }
        Map<String, DiversityCandidateMetadata> exploration = new HashMap<>();
        for (var item : input.explorationMetadata()) {
            String id = identity(item.entityType().wireValue(), item.entityId());
            DiversityCandidateMetadata converted = new DiversityCandidateMetadata(
                    item.entityId(), item.entityType(), item.authorId(), item.primaryRegionFeatureId(),
                    item.primaryThemeFeatureId(), item.duplicateGroupId());
            if (exploration.put(id, converted) != null) {
                throw new IllegalStateException("METADATA_RESOLUTION_FAILURE");
            }
        }
        return candidate -> {
            Map<String, DiversityCandidateMetadata> source = candidate.origin() == ExplorationCandidateOrigin.PERSONALIZED
                    ? personalized : exploration;
            DiversityCandidateMetadata item = source.get(
                    identity(candidate.entityType().wireValue(), candidate.entityId()));
            if (item == null) {
                throw new IllegalStateException("METADATA_RESOLUTION_FAILURE");
            }
            return item;
        };
    }

    private static OriginMetricsAtK originMetrics(List<ExplorationFinalCandidate> list) {
        if (list.isEmpty()) {
            return new OriginMetricsAtK(null, null, null, null, null, null);
        }
        List<PersonalizedExplorationCandidate> personalized = list.stream()
                .filter(item -> item instanceof PersonalizedExplorationCandidate)
                .map(item -> (PersonalizedExplorationCandidate) item).toList();
        List<InsertedExplorationCandidate> exploration = list.stream()
                .filter(item -> item instanceof InsertedExplorationCandidate)
                .map(item -> (InsertedExplorationCandidate) item).toList();
        return new OriginMetricsAtK(
                (double) personalized.size() / list.size(),
                (double) exploration.size() / list.size(),
                mean(personalized.stream().map(item -> normalizeZero(item.score())).toList()),
                mean(exploration.stream().map(InsertedExplorationCandidate::explorationQualityScore).toList()),
                mean(exploration.stream().map(item -> (double) item.recentExposureCount()).toList()),
                exploration.isEmpty() ? null
                        : (double) exploration.stream().filter(item -> item.recentExposureCount() == 0).count()
                        / exploration.size());
    }

    private static DiversityMetricsAtK diversityMetrics(
            List<ExplorationFinalCandidate> list,
            Function<ExplorationFinalCandidate, DiversityCandidateMetadata> resolver,
            DiversityPolicy policy
    ) {
        List<DiversityCandidateMetadata> metadata = list.stream().map(resolver).toList();
        int uniqueAuthors = uniqueCount(metadata.stream().map(DiversityCandidateMetadata::authorId).toList());
        int uniqueRegions = uniqueCount(metadata.stream()
                .map(DiversityCandidateMetadata::primaryRegionFeatureId).toList());
        int uniqueThemes = uniqueCount(metadata.stream()
                .map(DiversityCandidateMetadata::primaryThemeFeatureId).toList());
        Map<String, Integer> duplicateCounts = countMap(metadata.stream()
                .map(DiversityCandidateMetadata::duplicateGroupId).toList());
        int collisions = duplicateCounts.values().stream().mapToInt(count -> Math.max(0, count - 1)).sum();
        int duplicateViolations = 0;
        int authorViolations = 0;
        int regionViolations = 0;
        int themeViolations = 0;
        for (int end = 0; end < metadata.size(); end++) {
            int start = Math.max(0, end - policy.exposureWindowSize() + 1);
            List<DiversityCandidateMetadata> window = metadata.subList(start, end + 1);
            if (overCap(window.stream().map(DiversityCandidateMetadata::duplicateGroupId).toList(),
                    policy.exposureCaps().get(DiversityDimension.DUPLICATE_GROUP))) duplicateViolations++;
            if (overCap(window.stream().map(DiversityCandidateMetadata::authorId).toList(),
                    policy.exposureCaps().get(DiversityDimension.AUTHOR))) authorViolations++;
            if (overCap(window.stream().map(DiversityCandidateMetadata::primaryRegionFeatureId).toList(),
                    policy.exposureCaps().get(DiversityDimension.REGION))) regionViolations++;
            if (overCap(window.stream().map(DiversityCandidateMetadata::primaryThemeFeatureId).toList(),
                    policy.exposureCaps().get(DiversityDimension.THEME))) themeViolations++;
        }
        DiversityDimensionCounts counts = new DiversityDimensionCounts(
                duplicateViolations, authorViolations, regionViolations, themeViolations);
        return new DiversityMetricsAtK(uniqueAuthors, uniqueRegions, uniqueThemes, collisions,
                duplicateViolations + authorViolations + regionViolations + themeViolations, counts);
    }

    private static ObservedSupportMetricsAtK outcomeMetrics(
            AttributeRecommendationOutcomesResult attribution,
            List<ExplorationFinalCandidate> treatmentTop,
            List<ExplorationFinalCandidate> baselineTop,
            Set<String> support
    ) {
        Set<String> treatmentIds = identitySet(treatmentTop);
        Set<String> baselineIds = identitySet(baselineTop);
        Set<String> supportedTreatment = new HashSet<>(treatmentIds);
        supportedTreatment.retainAll(support);
        Set<String> supportedBaseline = new HashSet<>(baselineIds);
        supportedBaseline.retainAll(support);
        var treatmentRecords = attribution.attributions().stream()
                .filter(record -> supportedTreatment.contains(
                        identity(record.entityType().wireValue(), record.entityId()))).toList();
        var baselineRecords = attribution.attributions().stream()
                .filter(record -> supportedBaseline.contains(
                        identity(record.entityType().wireValue(), record.entityId()))).toList();
        double value = normalizeZero(treatmentRecords.stream()
                .mapToDouble(record -> record.associatedOutcomeValue() == null
                        ? 0.0 : record.associatedOutcomeValue()).sum());
        int severe = (int) treatmentRecords.stream().filter(record -> record.isSevereReport()).count();
        int baselineSevere = (int) baselineRecords.stream().filter(record -> record.isSevereReport()).count();
        return new ObservedSupportMetricsAtK(
                supportedTreatment.size(), treatmentTop.size(),
                treatmentTop.isEmpty() ? 1.0 : (double) supportedTreatment.size() / treatmentTop.size(),
                treatmentRecords.size(), value,
                (int) treatmentRecords.stream().filter(record -> record.behaviorEventType().wireValue()
                        .equals("click")).count(),
                (int) treatmentRecords.stream().filter(record -> record.isPositive()).count(),
                (int) treatmentRecords.stream().filter(record -> record.isNegative()).count(),
                severe, baselineSevere, severe - baselineSevere);
    }

    private static Map<String, Integer> rankMap(List<ExplorationFinalCandidate> candidates) {
        Map<String, Integer> result = new HashMap<>();
        for (ExplorationFinalCandidate candidate : candidates) {
            result.put(identity(candidate.entityType().wireValue(), candidate.entityId()), candidate.absoluteRank());
        }
        return result;
    }

    private static Set<String> identitySet(List<ExplorationFinalCandidate> candidates) {
        Set<String> result = new HashSet<>();
        for (ExplorationFinalCandidate candidate : candidates) {
            result.add(identity(candidate.entityType().wireValue(), candidate.entityId()));
        }
        return result;
    }

    private static Map<String, Integer> countMap(List<String> values) {
        Map<String, Integer> result = new HashMap<>();
        for (String value : values) {
            if (value != null) result.merge(value, 1, Integer::sum);
        }
        return result;
    }

    private static int uniqueCount(List<String> values) {
        return (int) values.stream().filter(java.util.Objects::nonNull).distinct().count();
    }

    private static boolean overCap(List<String> values, int cap) {
        return countMap(values).values().stream().anyMatch(count -> count > cap);
    }

    private static Double mean(List<Double> values) {
        if (values.isEmpty()) return null;
        return values.stream().mapToDouble(Double::doubleValue).sum() / values.size();
    }

    private static double normalizeZero(double value) {
        return value == 0.0d ? 0.0d : value;
    }

    private static String identity(String entityType, String entityId) {
        return entityType + '\0' + entityId;
    }

    private static void invalid(String detail) {
        throw new IllegalArgumentException("INVALID_POLICY_COMPARISON_INPUT: " + detail);
    }
}
