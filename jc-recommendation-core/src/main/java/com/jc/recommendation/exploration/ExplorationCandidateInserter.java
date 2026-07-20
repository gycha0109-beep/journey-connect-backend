package com.jc.recommendation.exploration;

import com.jc.recommendation.diversity.DiversityContracts;
import com.jc.recommendation.model.diversity.DiversifiedCandidate;
import com.jc.recommendation.model.diversity.DiversityCandidateMetadata;
import com.jc.recommendation.model.diversity.DiversityDimension;
import com.jc.recommendation.model.entity.RecommendationEntityType;
import com.jc.recommendation.model.exploration.ExplorationCandidateMetadata;
import com.jc.recommendation.model.exploration.ExplorationCandidateOrigin;
import com.jc.recommendation.model.exploration.ExplorationFinalCandidate;
import com.jc.recommendation.model.exploration.ExplorationQualityComponent;
import com.jc.recommendation.model.exploration.ExplorationQualityEvidence;
import com.jc.recommendation.model.exploration.ExplorationResultStatus;
import com.jc.recommendation.model.exploration.ExplorationSlotDecision;
import com.jc.recommendation.model.exploration.ExplorationSlotDecisionStatus;
import com.jc.recommendation.model.exploration.ExplorationSummary;
import com.jc.recommendation.model.exploration.InsertExplorationCandidatesInput;
import com.jc.recommendation.model.exploration.InsertExplorationCandidatesResult;
import com.jc.recommendation.model.exploration.InsertedExplorationCandidate;
import com.jc.recommendation.model.exploration.PersonalizedExplorationCandidate;
import com.jc.recommendation.model.ranking.RankingSortKey;
import com.jc.recommendation.model.score.CandidateScoreHardExclusionReason;
import com.jc.recommendation.model.score.CandidateScoreNotApplicableReason;
import com.jc.recommendation.model.score.CandidateScoreResult;
import com.jc.recommendation.model.score.CandidateScoreStatus;
import com.jc.recommendation.model.score.ScoreComponentAvailability;
import com.jc.recommendation.model.score.ScoreComponentBreakdown;
import com.jc.recommendation.model.score.ScoreComponentName;
import com.jc.recommendation.model.score.ScoreComponentPolicyVersions;
import com.jc.recommendation.policy.DiversityPolicies;
import com.jc.recommendation.policy.DiversityPolicy;
import com.jc.recommendation.policy.ExplorationPolicies;
import com.jc.recommendation.policy.ExplorationPolicy;
import com.jc.recommendation.support.Utf16CodeUnitComparator;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public final class ExplorationCandidateInserter {
    private static final double EPSILON = 1e-12d;

    private record Quality(
            List<ExplorationQualityEvidence> evidence,
            Double score,
            int availableWeightTotal,
            Double freshnessRawScore,
            Double popularityRawScore
    ) {
        private Quality {
            evidence = List.copyOf(evidence);
        }
    }

    private record EligibleCandidate(
            CandidateScoreResult source,
            ExplorationCandidateMetadata metadata,
            double qualityScore,
            List<ExplorationQualityEvidence> evidence,
            int availableWeightTotal,
            Double freshnessRawScore,
            Double popularityRawScore,
            long seededTieBreakKey,
            int explorationPoolRank
    ) {
        private EligibleCandidate {
            evidence = List.copyOf(evidence);
        }

        private EligibleCandidate withPoolRank(int poolRank) {
            return new EligibleCandidate(
                    source, metadata, qualityScore, evidence, availableWeightTotal,
                    freshnessRawScore, popularityRawScore, seededTieBreakKey, poolRank
            );
        }
    }

    private interface Provisional {
        DiversityCandidateMetadata metadata();
    }

    private record ProvisionalPersonalized(
            DiversifiedCandidate source,
            DiversityCandidateMetadata metadata
    ) implements Provisional {
    }

    private record ProvisionalExploration(
            EligibleCandidate candidate,
            int targetInsertionRank,
            DiversityCandidateMetadata metadata
    ) implements Provisional {
    }

    public InsertExplorationCandidatesResult insert(InsertExplorationCandidatesInput input) {
        validateTopLevel(input);
        ExplorationPolicy policy = input.policy() == null ? ExplorationPolicies.V1 : input.policy();
        ExplorationContracts.validatePolicy(policy);
        DiversityPolicy diversityPolicy = input.diversityPolicy() == null ? DiversityPolicies.V1 : input.diversityPolicy();
        DiversityContracts.validatePolicy(diversityPolicy);
        validateCompatibility(input, policy, diversityPolicy);
        ExplorationContracts.validateSeed(input.explorationSeed(), policy.maximumSeedUtf8Bytes());
        if (input.diversifiedCandidates().size() + input.terminalCandidates().size()
                > policy.maximumCandidateCount()) {
            throw new IllegalArgumentException("full candidate snapshot exceeds maximumCandidateCount");
        }

        List<DiversifiedCandidate> diversified = validateDiversifiedCandidates(
                input.diversifiedCandidates(), input.scorePolicyVersion()
        );
        Set<String> personalizedIds = identitySet(diversified);
        List<CandidateScoreResult> terminals = validateTerminalCandidates(
                input.terminalCandidates(), input.scorePolicyVersion(), personalizedIds
        );

        List<CandidateScoreResult> structurallyEligible = terminals.stream()
                .filter(ExplorationCandidateInserter::isStructurallyEligible)
                .toList();
        Map<String, ExplorationCandidateMetadata> metadataById = validateMetadataCoverage(
                structurallyEligible, input.explorationMetadata()
        );

        int statusReasonRejectedCount = 0;
        int entityTypeRejectedCount = 0;
        for (CandidateScoreResult candidate : terminals) {
            if (!isNoAnchor(candidate)) {
                statusReasonRejectedCount++;
            } else if (!isExplorationEntityType(candidate.entityType())) {
                entityTypeRejectedCount++;
            }
        }

        int exposureRejectedCount = 0;
        int qualityEvidenceRejectedCount = 0;
        int qualityFloorRejectedCount = 0;
        List<EligibleCandidate> prePool = new ArrayList<>();
        for (CandidateScoreResult candidate : structurallyEligible) {
            ExplorationCandidateMetadata metadata = metadataById.get(identity(candidate));
            if (metadata == null) {
                throw new IllegalStateException("exploration metadata coverage invariant failed");
            }
            if (metadata.recentExposureCount() > policy.maximumRecentExposureCount()) {
                exposureRejectedCount++;
                continue;
            }
            Quality quality = qualityFor(candidate, policy);
            if (quality.evidence().size() < policy.minimumAvailableQualityComponents()
                    || quality.score() == null) {
                qualityEvidenceRejectedCount++;
                continue;
            }
            if (quality.score().doubleValue() < policy.minimumQualityScore()) {
                qualityFloorRejectedCount++;
                continue;
            }
            long seededTieBreakKey = ExplorationSeed.fnv1a32Utf8(ExplorationSeed.material(
                    policy.policyVersion(),
                    input.rankingSnapshotId(),
                    input.metadataSnapshotId(),
                    input.explorationSnapshotId(),
                    input.explorationSeed(),
                    candidate.entityType().wireValue(),
                    candidate.entityId()
            ));
            prePool.add(new EligibleCandidate(
                    candidate,
                    metadata,
                    quality.score().doubleValue(),
                    quality.evidence(),
                    quality.availableWeightTotal(),
                    quality.freshnessRawScore(),
                    quality.popularityRawScore(),
                    seededTieBreakKey,
                    0
            ));
        }
        prePool.sort(eligibleComparator());
        List<EligibleCandidate> pool = new ArrayList<>();
        for (int index = 0; index < prePool.size(); index++) {
            pool.add(prePool.get(index).withPoolRank(index + 1));
        }
        pool = List.copyOf(pool);

        List<Provisional> provisional = new ArrayList<>();
        for (DiversifiedCandidate source : diversified) {
            provisional.add(new ProvisionalPersonalized(source, copyMetadata(source.diversityMetadata())));
        }
        Set<String> insertedIds = new HashSet<>();
        List<ExplorationSlotDecision> slotDecisions = new ArrayList<>();
        List<Integer> insertedTargetRanks = new ArrayList<>();
        int diversityGuardRejectedEvaluationCount = 0;

        List<Integer> activeRanks = policy.insertionRanks().subList(0, policy.maximumInsertions());
        for (Integer rankValue : activeRanks) {
            int targetInsertionRank = rankValue.intValue();
            if (provisional.size() < targetInsertionRank - 1) {
                slotDecisions.add(slotDecision(
                        targetInsertionRank, ExplorationSlotDecisionStatus.SKIPPED_DEPTH, null, null
                ));
                continue;
            }
            List<EligibleCandidate> remaining = pool.stream()
                    .filter(candidate -> !insertedIds.contains(identity(candidate.source())))
                    .toList();
            if (remaining.isEmpty()) {
                slotDecisions.add(slotDecision(
                        targetInsertionRank, ExplorationSlotDecisionStatus.SKIPPED_NO_CANDIDATE, null, null
                ));
                continue;
            }

            EligibleCandidate selected = null;
            for (EligibleCandidate candidate : remaining) {
                List<Provisional> trial = new ArrayList<>(provisional);
                trial.add(targetInsertionRank - 1, new ProvisionalExploration(
                        candidate, targetInsertionRank, copyMetadata(candidate.metadata())
                ));
                if (guardPasses(trial, targetInsertionRank, diversityPolicy)) {
                    selected = candidate;
                    provisional.add(targetInsertionRank - 1, new ProvisionalExploration(
                            candidate, targetInsertionRank, copyMetadata(candidate.metadata())
                    ));
                    insertedIds.add(identity(candidate.source()));
                    insertedTargetRanks.add(targetInsertionRank);
                    slotDecisions.add(slotDecision(
                            targetInsertionRank,
                            ExplorationSlotDecisionStatus.INSERTED,
                            candidate.source().entityType(),
                            candidate.source().entityId()
                    ));
                    break;
                }
                diversityGuardRejectedEvaluationCount++;
            }
            if (selected == null) {
                slotDecisions.add(slotDecision(
                        targetInsertionRank, ExplorationSlotDecisionStatus.SKIPPED_DIVERSITY, null, null
                ));
            }
        }

        List<ExplorationFinalCandidate> finalCandidates = new ArrayList<>();
        for (int index = 0; index < provisional.size(); index++) {
            Provisional item = provisional.get(index);
            if (item instanceof ProvisionalPersonalized personalized) {
                finalCandidates.add(clonePersonalized(personalized.source(), index + 1));
            } else if (item instanceof ProvisionalExploration exploration) {
                finalCandidates.add(cloneExploration(exploration, index + 1, policy));
            } else {
                throw new IllegalStateException("Unknown provisional Exploration candidate");
            }
        }
        finalCandidates = List.copyOf(finalCandidates);
        validateFinalIdentities(finalCandidates, personalizedIds);

        List<CandidateScoreResult> remainingTerminalCandidates = terminals.stream()
                .filter(candidate -> !insertedIds.contains(identity(candidate)))
                .sorted(terminalComparator())
                .map(ExplorationCandidateInserter::cloneTerminal)
                .toList();
        if (finalCandidates.size() + remainingTerminalCandidates.size()
                != diversified.size() + terminals.size()) {
            throw new IllegalStateException("Exploration count invariant failed");
        }

        int insertedCandidateCount = insertedIds.size();
        ExplorationSummary summary = new ExplorationSummary(
                insertedCandidateCount == 0 ? ExplorationResultStatus.UNCHANGED : ExplorationResultStatus.INSERTED,
                structurallyEligible.size(),
                pool.size(),
                insertedCandidateCount,
                (int) slotDecisions.stream()
                        .filter(decision -> decision.status() != ExplorationSlotDecisionStatus.INSERTED)
                        .count(),
                statusReasonRejectedCount,
                entityTypeRejectedCount,
                exposureRejectedCount,
                qualityEvidenceRejectedCount,
                qualityFloorRejectedCount,
                diversityGuardRejectedEvaluationCount,
                insertedTargetRanks,
                policy.policyVersion(),
                policy.seedAlgorithm(),
                slotDecisions
        );

        return new InsertExplorationCandidatesResult(
                input.rankingSnapshotId(),
                input.metadataSnapshotId(),
                input.explorationSnapshotId(),
                input.rankingPolicyVersion(),
                input.scorePolicyVersion(),
                diversityPolicy.policyVersion(),
                policy.policyVersion(),
                input.explorationSeed(),
                diversified.size(),
                terminals.size(),
                pool.size(),
                insertedCandidateCount,
                finalCandidates.size(),
                remainingTerminalCandidates.size(),
                summary,
                finalCandidates,
                remainingTerminalCandidates
        );
    }

    private static void validateTopLevel(InsertExplorationCandidatesInput input) {
        ExplorationContracts.nonBlank(input.rankingSnapshotId(), "rankingSnapshotId");
        ExplorationContracts.nonBlank(input.metadataSnapshotId(), "metadataSnapshotId");
        ExplorationContracts.nonBlank(input.explorationSnapshotId(), "explorationSnapshotId");
        ExplorationContracts.nonBlank(input.rankingPolicyVersion(), "rankingPolicyVersion");
        ExplorationContracts.nonBlank(input.scorePolicyVersion(), "scorePolicyVersion");
        ExplorationContracts.nonBlank(input.diversityPolicyVersion(), "diversityPolicyVersion");
        ExplorationContracts.nonBlank(input.explorationSeed(), "explorationSeed");
    }

    private static void validateCompatibility(
            InsertExplorationCandidatesInput input,
            ExplorationPolicy policy,
            DiversityPolicy diversityPolicy
    ) {
        if (!input.rankingPolicyVersion().equals(policy.expectedRankingPolicyVersion())) {
            throw new IllegalArgumentException("rankingPolicyVersion is incompatible with Exploration policy");
        }
        if (!input.scorePolicyVersion().equals(policy.expectedScorePolicyVersion())) {
            throw new IllegalArgumentException("scorePolicyVersion is incompatible with Exploration policy");
        }
        if (!input.diversityPolicyVersion().equals(diversityPolicy.policyVersion())) {
            throw new IllegalArgumentException("diversityPolicyVersion does not match effective Diversity policy");
        }
        if (!policy.expectedDiversityPolicyVersion().equals(diversityPolicy.policyVersion())) {
            throw new IllegalArgumentException("Exploration policy is incompatible with effective Diversity policy");
        }
    }

    private static List<DiversifiedCandidate> validateDiversifiedCandidates(
            List<DiversifiedCandidate> candidates,
            String scorePolicyVersion
    ) {
        Set<String> identities = new HashSet<>();
        List<DiversifiedCandidate> result = new ArrayList<>();
        for (int index = 0; index < candidates.size(); index++) {
            DiversifiedCandidate candidate = candidates.get(index);
            String label = "diversifiedCandidates[" + index + "]";
            if (candidate.diversifiedAbsoluteRank() != index + 1) {
                throw new IllegalArgumentException(label + ".absoluteRank must equal " + (index + 1));
            }
            if (candidate.baseAbsoluteRank() < 1) {
                throw new IllegalArgumentException(label + ".baseAbsoluteRank must be positive");
            }
            validateRankingType(candidate.entityType(), label + ".entityType");
            ExplorationContracts.nonBlank(candidate.entityId(), label + ".entityId");
            ExplorationContracts.finiteUnit(candidate.score(), label + ".score");
            if (!Double.isFinite(candidate.scoredWeight()) || candidate.scoredWeight() <= 0.0d
                    || candidate.scoredWeight() > 1.0d) {
                throw new IllegalArgumentException(label + ".scoredWeight must be finite in (0,1]");
            }
            ExplorationContracts.finiteUnit(candidate.neutralFilledWeight(), label + ".neutralFilledWeight");
            if (Math.abs(candidate.scoredWeight() + candidate.neutralFilledWeight() - 1.0d) > EPSILON) {
                throw new IllegalArgumentException(label + " weights must sum to 1");
            }
            if (!candidate.scorePolicyVersion().equals(scorePolicyVersion)) {
                throw new IllegalArgumentException(label + ".scorePolicyVersion mismatch");
            }
            validateSortKey(candidate, label);
            validateMetadata(candidate.diversityMetadata(), false, label + ".diversityMetadata");
            if (!candidate.diversityMetadata().entityId().equals(candidate.entityId())
                    || candidate.diversityMetadata().entityType() != candidate.entityType()) {
                throw new IllegalArgumentException(label + ".diversityMetadata identity mismatch");
            }
            if (candidate.displacement()
                    != candidate.diversifiedAbsoluteRank() - candidate.baseAbsoluteRank()) {
                throw new IllegalArgumentException(label + ".displacement mismatch");
            }
            if (candidate.promotionDistance() != Math.max(0, -candidate.displacement())
                    || candidate.demotionDistance() != Math.max(0, candidate.displacement())) {
                throw new IllegalArgumentException(label + " movement provenance mismatch");
            }
            String identity = identity(candidate.entityType(), candidate.entityId());
            if (!identities.add(identity)) {
                throw new IllegalArgumentException("duplicate personalized identity: " + identity);
            }
            result.add(candidate);
        }
        return List.copyOf(result);
    }

    private static void validateSortKey(DiversifiedCandidate candidate, String label) {
        RankingSortKey sortKey = candidate.baseSortKey();
        ExplorationContracts.finiteUnit(sortKey.score(), label + ".baseSortKey.score");
        ExplorationContracts.finiteUnit(
                sortKey.neutralFilledWeight(), label + ".baseSortKey.neutralFilledWeight"
        );
        if (sortKey.entityTypeRank() < 0 || sortKey.entityTypeRank() > 3) {
            throw new IllegalArgumentException(label + ".baseSortKey.entityTypeRank must be in 0..3");
        }
        if (!sortKey.entityId().equals(candidate.entityId())) {
            throw new IllegalArgumentException(label + ".baseSortKey.entityId mismatch");
        }
        if (Double.doubleToRawLongBits(sortKey.neutralFilledWeight())
                != Double.doubleToRawLongBits(candidate.neutralFilledWeight())) {
            throw new IllegalArgumentException(label + ".baseSortKey.neutralFilledWeight mismatch");
        }
        double expectedScore = candidate.score() == 0.0d ? 0.0d : candidate.score();
        if (Double.doubleToRawLongBits(sortKey.score()) != Double.doubleToRawLongBits(expectedScore)) {
            throw new IllegalArgumentException(label + ".baseSortKey.score mismatch");
        }
    }

    private static List<CandidateScoreResult> validateTerminalCandidates(
            List<CandidateScoreResult> candidates,
            String scorePolicyVersion,
            Set<String> personalizedIds
    ) {
        Set<String> identities = new HashSet<>();
        List<CandidateScoreResult> result = new ArrayList<>();
        for (int index = 0; index < candidates.size(); index++) {
            CandidateScoreResult candidate = candidates.get(index);
            String label = "terminalCandidates[" + index + "]";
            ExplorationContracts.nonBlank(candidate.userId(), label + ".userId");
            ExplorationContracts.nonBlank(candidate.contextId(), label + ".contextId");
            ExplorationContracts.nonBlank(candidate.entityId(), label + ".entityId");
            validateRankingType(candidate.entityType(), label + ".entityType");
            if (candidate.status() != CandidateScoreStatus.NOT_APPLICABLE
                    && candidate.status() != CandidateScoreStatus.HARD_EXCLUDED) {
                throw new IllegalArgumentException(label + ".status has unknown value");
            }
            if (candidate.score() != null || candidate.compositionMode() != null
                    || candidate.scoredWeight() != null || candidate.neutralFilledWeight() != null) {
                throw new IllegalArgumentException(label + " terminal numeric contract is invalid");
            }
            if (candidate.status() == CandidateScoreStatus.NOT_APPLICABLE) {
                if (candidate.notApplicableReason() == null) {
                    throw new IllegalArgumentException(label + ".notApplicableReason has unknown value");
                }
                if (candidate.hardExclusionReason() != null) {
                    throw new IllegalArgumentException(label + ".hardExclusionReason must be null");
                }
                if (candidate.notApplicableReason() == CandidateScoreNotApplicableReason.NO_ANCHOR_COMPONENT
                        && (!candidate.anchorComponents().isEmpty()
                        || !candidate.hardGateComponents().isEmpty())) {
                    throw new IllegalArgumentException(
                            label + " no_anchor_component must have empty anchorComponents and hardGateComponents"
                    );
                }
            } else {
                if (candidate.notApplicableReason() != null) {
                    throw new IllegalArgumentException(label + ".notApplicableReason must be null");
                }
                CandidateScoreHardExclusionReason reason = candidate.hardExclusionReason();
                if (reason == null) {
                    throw new IllegalArgumentException(label + ".hardExclusionReason has unknown value");
                }
            }
            if (!candidate.policyVersion().equals(scorePolicyVersion)) {
                throw new IllegalArgumentException(label + ".policyVersion mismatch");
            }
            validateComponentVersions(candidate.componentPolicyVersions(), label + ".componentPolicyVersions");
            validateBreakdown(candidate.breakdown(), label + ".breakdown");
            String identity = identity(candidate);
            if (!identities.add(identity)) {
                throw new IllegalArgumentException("duplicate terminal identity: " + identity);
            }
            if (personalizedIds.contains(identity)) {
                throw new IllegalArgumentException(
                        "identity appears in personalized and terminal sets: " + identity
                );
            }
            result.add(candidate);
        }
        return List.copyOf(result);
    }

    private static void validateComponentVersions(ScoreComponentPolicyVersions versions, String label) {
        ExplorationContracts.nonBlank(versions.contextMatch(), label + ".context_match");
        ExplorationContracts.nonBlank(versions.interestMatch(), label + ".interest_match");
        ExplorationContracts.nonBlank(versions.freshness(), label + ".freshness");
        ExplorationContracts.nonBlank(versions.popularity(), label + ".popularity");
    }

    private static void validateBreakdown(List<ScoreComponentBreakdown> breakdown, String label) {
        EnumSet<ScoreComponentName> seen = EnumSet.noneOf(ScoreComponentName.class);
        for (int index = 0; index < breakdown.size(); index++) {
            ScoreComponentBreakdown row = breakdown.get(index);
            String rowLabel = label + "[" + index + "]";
            if (!seen.add(row.component())) {
                throw new IllegalArgumentException(
                        label + " contains duplicate component: " + row.component().wireValue()
                );
            }
            ExplorationContracts.nonBlank(row.resultStatus(), rowLabel + ".resultStatus");
            ExplorationContracts.nullableNonBlank(
                    row.resultNotApplicableReason(), rowLabel + ".resultNotApplicableReason"
            );
            ExplorationContracts.nonBlank(row.resultPolicyVersion(), rowLabel + ".resultPolicyVersion");
            if (!Double.isFinite(row.globalBaseWeight()) || row.globalBaseWeight() < 0.0d) {
                throw new IllegalArgumentException(rowLabel + ".globalBaseWeight is invalid");
            }
            if (!Double.isFinite(row.entityEffectiveWeight()) || row.entityEffectiveWeight() < 0.0d) {
                throw new IllegalArgumentException(rowLabel + ".entityEffectiveWeight is invalid");
            }
            ExplorationContracts.nullableFiniteUnit(row.rawScore(), rowLabel + ".rawScore");
            ExplorationContracts.nullableFiniteUnit(row.valueUsed(), rowLabel + ".valueUsed");
            if (row.weightedContribution() != null
                    && (!Double.isFinite(row.weightedContribution().doubleValue())
                    || row.weightedContribution().doubleValue() < 0.0d)) {
                throw new IllegalArgumentException(rowLabel + ".weightedContribution is invalid");
            }
        }
        if (seen.size() != ScoreComponentName.values().length) {
            throw new IllegalArgumentException(
                    label + " must contain every score component exactly once"
            );
        }
    }

    private static Map<String, ExplorationCandidateMetadata> validateMetadataCoverage(
            List<CandidateScoreResult> structurallyEligible,
            List<ExplorationCandidateMetadata> metadataRows
    ) {
        Set<String> structuralIds = new HashSet<>();
        for (CandidateScoreResult candidate : structurallyEligible) {
            structuralIds.add(identity(candidate));
        }
        Map<String, ExplorationCandidateMetadata> metadataById = new HashMap<>();
        for (int index = 0; index < metadataRows.size(); index++) {
            ExplorationCandidateMetadata metadata = metadataRows.get(index);
            String label = "explorationMetadata[" + index + "]";
            validateMetadata(metadata, true, label);
            if (!isExplorationEntityType(metadata.entityType())) {
                throw new IllegalArgumentException(label + ".entityType must be post or journey");
            }
            String identity = identity(metadata.entityType(), metadata.entityId());
            if (metadataById.putIfAbsent(identity, metadata) != null) {
                throw new IllegalArgumentException("duplicate exploration metadata identity: " + identity);
            }
        }
        if (metadataById.size() != structuralIds.size()) {
            throw new IllegalArgumentException("exploration metadata coverage mismatch");
        }
        for (String identity : structuralIds) {
            if (!metadataById.containsKey(identity)) {
                throw new IllegalArgumentException("missing exploration metadata: " + identity);
            }
        }
        for (String identity : metadataById.keySet()) {
            if (!structuralIds.contains(identity)) {
                throw new IllegalArgumentException("extra exploration metadata: " + identity);
            }
        }
        return Map.copyOf(metadataById);
    }

    private static void validateMetadata(
            ExplorationCandidateMetadata metadata,
            boolean includeExposure,
            String label
    ) {
        ExplorationContracts.nonBlank(metadata.entityId(), label + ".entityId");
        validateRankingType(metadata.entityType(), label + ".entityType");
        ExplorationContracts.nullableNonBlank(metadata.authorId(), label + ".authorId");
        ExplorationContracts.validateRegionFeature(
                metadata.primaryRegionFeatureId(), label + ".primaryRegionFeatureId"
        );
        ExplorationContracts.validateThemeFeature(
                metadata.primaryThemeFeatureId(), label + ".primaryThemeFeatureId"
        );
        ExplorationContracts.nullableNonBlank(
                metadata.duplicateGroupId(), label + ".duplicateGroupId"
        );
        if (includeExposure) {
            ExplorationContracts.nonNegative(metadata.recentExposureCount(), label + ".recentExposureCount");
        }
    }

    private static void validateMetadata(
            DiversityCandidateMetadata metadata,
            boolean includeExposure,
            String label
    ) {
        validateMetadata(new ExplorationCandidateMetadata(
                metadata.entityId(), metadata.entityType(), metadata.authorId(),
                metadata.primaryRegionFeatureId(), metadata.primaryThemeFeatureId(),
                metadata.duplicateGroupId(), 0
        ), includeExposure, label);
    }

    private static Quality qualityFor(CandidateScoreResult candidate, ExplorationPolicy policy) {
        EnumMap<ScoreComponentName, ScoreComponentBreakdown> rows = new EnumMap<>(ScoreComponentName.class);
        for (ScoreComponentBreakdown row : candidate.breakdown()) {
            rows.put(row.component(), row);
        }
        List<ExplorationQualityEvidence> evidence = new ArrayList<>();
        double weightedTotal = 0.0d;
        int weightTotal = 0;
        Double freshnessRawScore = null;
        Double popularityRawScore = null;
        for (ExplorationQualityComponent component : ExplorationContracts.QUALITY_COMPONENTS) {
            ScoreComponentName scoreComponent = scoreComponent(component);
            ScoreComponentBreakdown row = rows.get(scoreComponent);
            if (row != null && row.availability() == ScoreComponentAvailability.SCORED
                    && (!row.resultStatus().equals("scored") || row.rawScore() == null)) {
                throw new IllegalArgumentException(
                        candidate.entityType().wireValue() + ":" + candidate.entityId()
                                + " has malformed scored " + component.wireValue() + " evidence"
                );
            }
            if (row == null || row.availability() != ScoreComponentAvailability.SCORED
                    || !row.resultStatus().equals("scored") || row.rawScore() == null) {
                continue;
            }
            int weight = policy.qualityWeights().get(component);
            double contribution = weight * row.rawScore().doubleValue();
            evidence.add(new ExplorationQualityEvidence(
                    component, row.rawScore().doubleValue(), weight, contribution
            ));
            weightedTotal += contribution;
            weightTotal += weight;
            if (component == ExplorationQualityComponent.FRESHNESS) {
                freshnessRawScore = row.rawScore();
            } else {
                popularityRawScore = row.rawScore();
            }
        }
        return new Quality(
                evidence,
                weightTotal == 0 ? null : weightedTotal / weightTotal,
                weightTotal,
                freshnessRawScore,
                popularityRawScore
        );
    }

    private static Comparator<EligibleCandidate> eligibleComparator() {
        return (left, right) -> {
            int exposureOrder = Integer.compare(
                    left.metadata().recentExposureCount(), right.metadata().recentExposureCount()
            );
            if (exposureOrder != 0) {
                return exposureOrder;
            }
            int qualityOrder = Double.compare(right.qualityScore(), left.qualityScore());
            if (qualityOrder != 0) {
                return qualityOrder;
            }
            int seedOrder = Long.compare(left.seededTieBreakKey(), right.seededTieBreakKey());
            if (seedOrder != 0) {
                return seedOrder;
            }
            int typeOrder = Integer.compare(
                    qualityEntityTypeRank(left.source().entityType()),
                    qualityEntityTypeRank(right.source().entityType())
            );
            if (typeOrder != 0) {
                return typeOrder;
            }
            return Utf16CodeUnitComparator.ASCENDING.compare(
                    left.source().entityId(), right.source().entityId()
            );
        };
    }

    private static boolean guardPasses(
            List<Provisional> provisional,
            int insertionRank,
            DiversityPolicy policy
    ) {
        Provisional inserted = provisional.get(insertionRank - 1);
        if (!(inserted instanceof ProvisionalExploration)) {
            throw new IllegalStateException("Exploration guard insertion invariant failed");
        }
        int finalLength = provisional.size();
        int lastEndRank = Math.min(
                finalLength, insertionRank + policy.exposureWindowSize() - 1
        );
        for (int endRank = insertionRank; endRank <= lastEndRank; endRank++) {
            int startRank = Math.max(1, endRank - policy.exposureWindowSize() + 1);
            List<Provisional> window = provisional.subList(startRank - 1, endRank);
            for (DiversityDimension dimension : DiversityDimension.values()) {
                String key = dimensionValue(inserted.metadata(), dimension);
                if (key == null) {
                    continue;
                }
                int count = 0;
                for (Provisional item : window) {
                    if (Objects.equals(dimensionValue(item.metadata(), dimension), key)) {
                        count++;
                    }
                }
                if (count > policy.exposureCaps().get(dimension)) {
                    return false;
                }
            }
        }
        return true;
    }

    private static PersonalizedExplorationCandidate clonePersonalized(
            DiversifiedCandidate source,
            int absoluteRank
    ) {
        return new PersonalizedExplorationCandidate(
                ExplorationCandidateOrigin.PERSONALIZED,
                absoluteRank,
                source.diversifiedAbsoluteRank(),
                source.baseAbsoluteRank(),
                source.entityId(),
                source.entityType(),
                source.score(),
                source.scoredWeight(),
                source.neutralFilledWeight(),
                source.compositionMode(),
                source.scorePolicyVersion(),
                copySortKey(source.baseSortKey()),
                copyMetadata(source.diversityMetadata()),
                source.selectionReason(),
                source.appliedRelaxations(),
                source.violatedDimensionsAtSelection(),
                source.displacement(),
                source.promotionDistance(),
                source.demotionDistance()
        );
    }

    private static InsertedExplorationCandidate cloneExploration(
            ProvisionalExploration item,
            int absoluteRank,
            ExplorationPolicy policy
    ) {
        EligibleCandidate candidate = item.candidate();
        return new InsertedExplorationCandidate(
                ExplorationCandidateOrigin.EXPLORATION,
                absoluteRank,
                null,
                null,
                candidate.source().entityId(),
                candidate.source().entityType(),
                null,
                null,
                null,
                null,
                candidate.source().policyVersion(),
                CandidateScoreStatus.NOT_APPLICABLE,
                CandidateScoreNotApplicableReason.NO_ANCHOR_COMPONENT,
                candidate.qualityScore(),
                candidate.evidence(),
                candidate.availableWeightTotal(),
                candidate.freshnessRawScore(),
                candidate.popularityRawScore(),
                candidate.metadata().recentExposureCount(),
                candidate.seededTieBreakKey(),
                candidate.explorationPoolRank(),
                item.targetInsertionRank(),
                policy.policyVersion(),
                copyMetadata(candidate.metadata())
        );
    }

    private static CandidateScoreResult cloneTerminal(CandidateScoreResult source) {
        List<ScoreComponentBreakdown> breakdown = source.breakdown().stream()
                .map(row -> new ScoreComponentBreakdown(
                        row.component(),
                        row.resultStatus(),
                        row.resultNotApplicableReason(),
                        row.resultPolicyVersion(),
                        row.globalBaseWeight(),
                        row.entityEffectiveWeight(),
                        row.availability(),
                        row.rawScore(),
                        row.valueUsed(),
                        row.weightedContribution()
                ))
                .toList();
        ScoreComponentPolicyVersions versions = source.componentPolicyVersions();
        return new CandidateScoreResult(
                source.userId(),
                source.contextId(),
                source.entityId(),
                source.entityType(),
                source.status(),
                source.score(),
                source.compositionMode(),
                source.scoredWeight(),
                source.neutralFilledWeight(),
                source.anchorComponents(),
                source.hardGateComponents(),
                source.notApplicableReason(),
                source.hardExclusionReason(),
                source.policyVersion(),
                new ScoreComponentPolicyVersions(
                        versions.contextMatch(),
                        versions.interestMatch(),
                        versions.freshness(),
                        versions.popularity()
                ),
                breakdown
        );
    }

    private static void validateFinalIdentities(
            List<ExplorationFinalCandidate> finalCandidates,
            Set<String> personalizedIds
    ) {
        Set<String> finalIds = new HashSet<>();
        for (ExplorationFinalCandidate candidate : finalCandidates) {
            if (!finalIds.add(identity(candidate.entityType(), candidate.entityId()))) {
                throw new IllegalStateException("final Exploration identity invariant failed");
            }
        }
        for (String personalizedId : personalizedIds) {
            if (!finalIds.contains(personalizedId)) {
                throw new IllegalStateException(
                        "personalized identity was removed: " + personalizedId
                );
            }
        }
    }

    private static Set<String> identitySet(List<DiversifiedCandidate> candidates) {
        Set<String> result = new HashSet<>();
        for (DiversifiedCandidate candidate : candidates) {
            result.add(identity(candidate.entityType(), candidate.entityId()));
        }
        return Set.copyOf(result);
    }

    private static Comparator<CandidateScoreResult> terminalComparator() {
        return (left, right) -> {
            int typeOrder = Integer.compare(
                    ExplorationContracts.entityTypeRank(left.entityType()),
                    ExplorationContracts.entityTypeRank(right.entityType())
            );
            if (typeOrder != 0) {
                return typeOrder;
            }
            return Utf16CodeUnitComparator.ASCENDING.compare(
                    left.entityId(), right.entityId()
            );
        };
    }

    private static ExplorationSlotDecision slotDecision(
            int rank,
            ExplorationSlotDecisionStatus status,
            RecommendationEntityType type,
            String entityId
    ) {
        return new ExplorationSlotDecision(rank, status, type, entityId);
    }

    private static boolean isNoAnchor(CandidateScoreResult candidate) {
        return candidate.status() == CandidateScoreStatus.NOT_APPLICABLE
                && candidate.notApplicableReason()
                == CandidateScoreNotApplicableReason.NO_ANCHOR_COMPONENT;
    }

    private static boolean isStructurallyEligible(CandidateScoreResult candidate) {
        return isNoAnchor(candidate) && isExplorationEntityType(candidate.entityType());
    }

    private static boolean isExplorationEntityType(RecommendationEntityType type) {
        return type == RecommendationEntityType.POST || type == RecommendationEntityType.JOURNEY;
    }

    private static void validateRankingType(RecommendationEntityType type, String label) {
        if (!ExplorationContracts.RANKING_TYPES.contains(type)) {
            throw new IllegalArgumentException(label + " has unknown value");
        }
    }

    private static int qualityEntityTypeRank(RecommendationEntityType type) {
        return type == RecommendationEntityType.POST ? 0 : 1;
    }

    private static ScoreComponentName scoreComponent(ExplorationQualityComponent component) {
        return switch (component) {
            case FRESHNESS -> ScoreComponentName.FRESHNESS;
            case POPULARITY -> ScoreComponentName.POPULARITY;
        };
    }

    private static String dimensionValue(
            DiversityCandidateMetadata metadata,
            DiversityDimension dimension
    ) {
        return switch (dimension) {
            case DUPLICATE_GROUP -> metadata.duplicateGroupId();
            case AUTHOR -> metadata.authorId();
            case REGION -> metadata.primaryRegionFeatureId();
            case THEME -> metadata.primaryThemeFeatureId();
        };
    }

    private static RankingSortKey copySortKey(RankingSortKey source) {
        return new RankingSortKey(
                source.score(),
                source.neutralFilledWeight(),
                source.entityTypeRank(),
                source.entityId()
        );
    }

    private static DiversityCandidateMetadata copyMetadata(DiversityCandidateMetadata source) {
        return new DiversityCandidateMetadata(
                source.entityId(),
                source.entityType(),
                source.authorId(),
                source.primaryRegionFeatureId(),
                source.primaryThemeFeatureId(),
                source.duplicateGroupId()
        );
    }

    private static DiversityCandidateMetadata copyMetadata(ExplorationCandidateMetadata source) {
        return new DiversityCandidateMetadata(
                source.entityId(),
                source.entityType(),
                source.authorId(),
                source.primaryRegionFeatureId(),
                source.primaryThemeFeatureId(),
                source.duplicateGroupId()
        );
    }

    private static String identity(CandidateScoreResult candidate) {
        return identity(candidate.entityType(), candidate.entityId());
    }

    private static String identity(RecommendationEntityType type, String entityId) {
        return ExplorationContracts.identity(type, entityId);
    }
}
