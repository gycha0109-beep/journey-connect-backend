package com.jc.recommendation.integration;

import com.jc.recommendation.model.diversity.DiversifiedCandidate;
import com.jc.recommendation.model.diversity.DiversityCandidateMetadata;
import com.jc.recommendation.model.diversity.DiversityDimensionCounts;
import com.jc.recommendation.model.diversity.DiversityRerankResult;
import com.jc.recommendation.model.integration.DiversityRankedCandidate;
import com.jc.recommendation.model.integration.DiversityRankingSummary;
import com.jc.recommendation.model.ranking.RankingSortKey;
import com.jc.recommendation.model.ranking.TerminalCandidateAudit;
import com.jc.recommendation.model.score.CandidateScoreResult;
import com.jc.recommendation.policy.RankingPolicies;
import com.jc.recommendation.support.Utf16CodeUnitComparator;

import java.util.Comparator;
import java.util.List;

final class RankingIntegrationMapper {
    private RankingIntegrationMapper() {
    }

    static DiversityRankedCandidate mapCandidate(DiversifiedCandidate candidate) {
        return new DiversityRankedCandidate(
                candidate.diversifiedAbsoluteRank(),
                candidate.baseAbsoluteRank(),
                candidate.entityId(),
                candidate.entityType(),
                candidate.score(),
                candidate.scoredWeight(),
                candidate.neutralFilledWeight(),
                candidate.compositionMode(),
                candidate.scorePolicyVersion(),
                new RankingSortKey(
                        candidate.baseSortKey().score(),
                        candidate.baseSortKey().neutralFilledWeight(),
                        candidate.baseSortKey().entityTypeRank(),
                        candidate.baseSortKey().entityId()
                ),
                copyMetadata(candidate.diversityMetadata()),
                candidate.selectionReason(),
                candidate.appliedRelaxations(),
                candidate.violatedDimensionsAtSelection(),
                candidate.displacement(),
                candidate.promotionDistance(),
                candidate.demotionDistance()
        );
    }

    static DiversityRankingSummary mapSummary(DiversityRerankResult result) {
        return new DiversityRankingSummary(
                result.status(),
                result.movedCandidateCount(),
                result.maxPromotionObserved(),
                result.maxDemotionObserved(),
                result.movementBoundForcedCount(),
                copyCounts(result.relaxationCountByDimension()),
                copyCounts(result.violationCountByDimension()),
                copyCounts(result.missingMetadataCountByDimension())
        );
    }

    static List<TerminalCandidateAudit> terminalAudits(List<CandidateScoreResult> candidates) {
        return candidates.stream()
                .sorted(terminalComparator())
                .map(candidate -> new TerminalCandidateAudit(
                        candidate.entityId(), candidate.entityType(), candidate.status(),
                        candidate.notApplicableReason(), candidate.hardExclusionReason(), candidate.policyVersion()
                ))
                .toList();
    }

    static Comparator<CandidateScoreResult> terminalComparator() {
        return (left, right) -> {
            int typeOrder = Integer.compare(
                    RankingPolicies.V1.entityTypeOrder().indexOf(left.entityType()),
                    RankingPolicies.V1.entityTypeOrder().indexOf(right.entityType())
            );
            return typeOrder != 0 ? typeOrder
                    : Utf16CodeUnitComparator.ASCENDING.compare(left.entityId(), right.entityId());
        };
    }

    private static DiversityCandidateMetadata copyMetadata(DiversityCandidateMetadata source) {
        return new DiversityCandidateMetadata(
                source.entityId(), source.entityType(), source.authorId(),
                source.primaryRegionFeatureId(), source.primaryThemeFeatureId(), source.duplicateGroupId()
        );
    }

    private static DiversityDimensionCounts copyCounts(DiversityDimensionCounts source) {
        return new DiversityDimensionCounts(
                source.duplicateGroup(), source.author(), source.region(), source.theme()
        );
    }
}
