package com.jc.recommendation.model.exposure;

import com.jc.recommendation.model.event.EventSurface;
import com.jc.recommendation.model.ranking.RankingEmptyReason;
import com.jc.recommendation.model.ranking.RankingResultStatus;
import com.jc.recommendation.model.score.ScoreComponentPolicyVersions;
import java.util.List;
import java.util.Objects;

public record RecommendationExposureEventV1(
        String schemaVersion, String eventId, String idempotencyKey, String recommendationRunId,
        String userId, String sessionId, String contextId, EventSurface surface, String servedAt,
        String replayKey, String pageFingerprint, String cursorVersion, String rankingSnapshotId,
        String metadataSnapshotId, String explorationSnapshotId, String rankingPolicyVersion,
        String baseIntegrationPolicyVersion, String baseRankingPolicyVersion, String scorePolicyVersion,
        ScoreComponentPolicyVersions componentPolicyVersions, String diversityPolicyVersion,
        String explorationPolicyVersion, String explorationSeed, RankingResultStatus rankingStatus,
        RankingEmptyReason rankingEmptyReason, Integer requestedLimit, int effectiveLimit,
        Integer pageStartRank, Integer pageEndRank, int pageCandidateCount, boolean hasNextPage,
        int inputCount, int finalRankedCandidateCount, int terminalCandidateCount,
        RecommendationExposureDiversitySummary diversitySummary,
        RecommendationExposureExplorationSummary explorationSummary,
        List<RecommendationExposureCandidate> candidates
) {
    public RecommendationExposureEventV1 {
        Objects.requireNonNull(schemaVersion, "schemaVersion");
        Objects.requireNonNull(eventId, "eventId");
        Objects.requireNonNull(idempotencyKey, "idempotencyKey");
        Objects.requireNonNull(recommendationRunId, "recommendationRunId");
        Objects.requireNonNull(userId, "userId");
        Objects.requireNonNull(sessionId, "sessionId");
        Objects.requireNonNull(contextId, "contextId");
        Objects.requireNonNull(surface, "surface");
        Objects.requireNonNull(servedAt, "servedAt");
        Objects.requireNonNull(replayKey, "replayKey");
        Objects.requireNonNull(pageFingerprint, "pageFingerprint");
        Objects.requireNonNull(cursorVersion, "cursorVersion");
        Objects.requireNonNull(rankingSnapshotId, "rankingSnapshotId");
        Objects.requireNonNull(metadataSnapshotId, "metadataSnapshotId");
        Objects.requireNonNull(explorationSnapshotId, "explorationSnapshotId");
        Objects.requireNonNull(rankingPolicyVersion, "rankingPolicyVersion");
        Objects.requireNonNull(baseIntegrationPolicyVersion, "baseIntegrationPolicyVersion");
        Objects.requireNonNull(baseRankingPolicyVersion, "baseRankingPolicyVersion");
        Objects.requireNonNull(scorePolicyVersion, "scorePolicyVersion");
        Objects.requireNonNull(componentPolicyVersions, "componentPolicyVersions");
        Objects.requireNonNull(diversityPolicyVersion, "diversityPolicyVersion");
        Objects.requireNonNull(explorationPolicyVersion, "explorationPolicyVersion");
        Objects.requireNonNull(explorationSeed, "explorationSeed");
        Objects.requireNonNull(rankingStatus, "rankingStatus");
        Objects.requireNonNull(diversitySummary, "diversitySummary");
        Objects.requireNonNull(explorationSummary, "explorationSummary");
        candidates = List.copyOf(candidates);
    }
}
