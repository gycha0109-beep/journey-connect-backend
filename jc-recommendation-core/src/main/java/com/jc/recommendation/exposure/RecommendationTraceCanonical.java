package com.jc.recommendation.exposure;

import com.jc.recommendation.canonical.JsonWire;
import com.jc.recommendation.model.diversity.DiversityDimensionCounts;
import com.jc.recommendation.model.exposure.RecommendationExposureCandidate;
import com.jc.recommendation.model.exposure.RecommendationExposureDiversitySummary;
import com.jc.recommendation.model.exposure.RecommendationExposureEventV1;
import com.jc.recommendation.model.exposure.RecommendationExposureExplorationSlotDecision;
import com.jc.recommendation.model.exposure.RecommendationExposureExplorationSummary;
import com.jc.recommendation.model.score.ScoreComponentPolicyVersions;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class RecommendationTraceCanonical {
    private RecommendationTraceCanonical() {
    }

    public static String hashCanonical(Object value) {
        try {
            byte[] bytes = JsonWire.stringify(value).getBytes(StandardCharsets.UTF_8);
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(bytes);
            return java.util.HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 must be available", exception);
        }
    }

    public static Map<String, Object> canonicalComponents(ScoreComponentPolicyVersions versions) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("context_match", versions.contextMatch());
        result.put("interest_match", versions.interestMatch());
        result.put("freshness", versions.freshness());
        result.put("popularity", versions.popularity());
        return result;
    }

    private static Map<String, Object> dimensions(DiversityDimensionCounts counts) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("duplicate_group", counts.duplicateGroup());
        result.put("author", counts.author());
        result.put("region", counts.region());
        result.put("theme", counts.theme());
        return result;
    }

    public static Map<String, Object> canonicalDiversity(RecommendationExposureDiversitySummary summary) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("status", summary.status().wireValue());
        result.put("movedCandidateCount", summary.movedCandidateCount());
        result.put("maxPromotionObserved", summary.maxPromotionObserved());
        result.put("maxDemotionObserved", summary.maxDemotionObserved());
        result.put("movementBoundForcedCount", summary.movementBoundForcedCount());
        result.put("relaxationCountByDimension", dimensions(summary.relaxationCountByDimension()));
        result.put("violationCountByDimension", dimensions(summary.violationCountByDimension()));
        result.put("missingMetadataCountByDimension", dimensions(summary.missingMetadataCountByDimension()));
        return result;
    }

    public static Map<String, Object> canonicalExploration(RecommendationExposureExplorationSummary summary) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("status", summary.status().wireValue());
        result.put("structurallyEligibleCandidateCount", summary.structurallyEligibleCandidateCount());
        result.put("eligibleCandidateCount", summary.eligibleCandidateCount());
        result.put("insertedCandidateCount", summary.insertedCandidateCount());
        result.put("skippedSlotCount", summary.skippedSlotCount());
        result.put("statusReasonRejectedCount", summary.statusReasonRejectedCount());
        result.put("entityTypeRejectedCount", summary.entityTypeRejectedCount());
        result.put("exposureRejectedCount", summary.exposureRejectedCount());
        result.put("qualityEvidenceRejectedCount", summary.qualityEvidenceRejectedCount());
        result.put("qualityFloorRejectedCount", summary.qualityFloorRejectedCount());
        result.put("diversityGuardRejectedEvaluationCount", summary.diversityGuardRejectedEvaluationCount());
        result.put("insertedTargetRanks", summary.insertedTargetRanks());
        result.put("policyVersion", summary.policyVersion());
        result.put("seedAlgorithm", summary.seedAlgorithm().wireValue());
        List<Map<String, Object>> slots = new ArrayList<>();
        for (RecommendationExposureExplorationSlotDecision decision : summary.slotDecisions()) {
            Map<String, Object> slot = new LinkedHashMap<>();
            slot.put("targetInsertionRank", decision.targetInsertionRank());
            slot.put("status", decision.status().wireValue());
            slots.add(slot);
        }
        result.put("slotDecisions", slots);
        return result;
    }

    public static Map<String, Object> canonicalCandidate(RecommendationExposureCandidate candidate) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("entityId", candidate.entityId());
        result.put("entityType", candidate.entityType().wireValue());
        result.put("absoluteRank", candidate.absoluteRank());
        result.put("pagePosition", candidate.pagePosition());
        result.put("origin", candidate.origin().wireValue());
        result.put("score", candidate.score());
        result.put("scoreIsNegativeZero", candidate.scoreIsNegativeZero());
        result.put("baseAbsoluteRank", candidate.baseAbsoluteRank());
        result.put("diversifiedAbsoluteRank", candidate.diversifiedAbsoluteRank());
        result.put("explorationQualityScore", candidate.explorationQualityScore());
        result.put("recentExposureCount", candidate.recentExposureCount());
        result.put("seededTieBreakKey", candidate.seededTieBreakKey());
        result.put("explorationPoolRank", candidate.explorationPoolRank());
        result.put("targetInsertionRank", candidate.targetInsertionRank());
        return result;
    }

    private static List<Map<String, Object>> candidates(RecommendationExposureEventV1 event) {
        List<Map<String, Object>> result = new ArrayList<>();
        for (RecommendationExposureCandidate candidate : event.candidates()) {
            result.add(canonicalCandidate(candidate));
        }
        return result;
    }

    public static Map<String, Object> replayProjection(RecommendationExposureEventV1 event) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("schemaVersion", event.schemaVersion());
        result.put("userId", event.userId());
        result.put("contextId", event.contextId());
        result.put("cursorVersion", event.cursorVersion());
        result.put("rankingSnapshotId", event.rankingSnapshotId());
        result.put("metadataSnapshotId", event.metadataSnapshotId());
        result.put("explorationSnapshotId", event.explorationSnapshotId());
        result.put("rankingPolicyVersion", event.rankingPolicyVersion());
        result.put("baseIntegrationPolicyVersion", event.baseIntegrationPolicyVersion());
        result.put("baseRankingPolicyVersion", event.baseRankingPolicyVersion());
        result.put("scorePolicyVersion", event.scorePolicyVersion());
        result.put("componentPolicyVersions", canonicalComponents(event.componentPolicyVersions()));
        result.put("diversityPolicyVersion", event.diversityPolicyVersion());
        result.put("explorationPolicyVersion", event.explorationPolicyVersion());
        result.put("explorationSeed", event.explorationSeed());
        return result;
    }

    public static Map<String, Object> pageProjection(RecommendationExposureEventV1 event) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("replayKey", event.replayKey());
        result.put("rankingStatus", event.rankingStatus().wireValue());
        result.put("rankingEmptyReason", event.rankingEmptyReason() == null ? null : event.rankingEmptyReason().wireValue());
        result.put("requestedLimit", event.requestedLimit());
        result.put("effectiveLimit", event.effectiveLimit());
        result.put("pageStartRank", event.pageStartRank());
        result.put("pageEndRank", event.pageEndRank());
        result.put("pageCandidateCount", event.pageCandidateCount());
        result.put("hasNextPage", event.hasNextPage());
        result.put("inputCount", event.inputCount());
        result.put("finalRankedCandidateCount", event.finalRankedCandidateCount());
        result.put("terminalCandidateCount", event.terminalCandidateCount());
        result.put("diversitySummary", canonicalDiversity(event.diversitySummary()));
        result.put("explorationSummary", canonicalExploration(event.explorationSummary()));
        result.put("candidates", candidates(event));
        return result;
    }

    public static Map<String, Object> eventSignatureProjection(RecommendationExposureEventV1 event) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("schemaVersion", event.schemaVersion());
        result.put("recommendationRunId", event.recommendationRunId());
        result.put("userId", event.userId());
        result.put("sessionId", event.sessionId());
        result.put("contextId", event.contextId());
        result.put("surface", event.surface().wireValue());
        result.put("servedAt", event.servedAt());
        result.put("replayKey", event.replayKey());
        result.put("pageFingerprint", event.pageFingerprint());
        result.put("cursorVersion", event.cursorVersion());
        result.put("rankingSnapshotId", event.rankingSnapshotId());
        result.put("metadataSnapshotId", event.metadataSnapshotId());
        result.put("explorationSnapshotId", event.explorationSnapshotId());
        result.put("rankingPolicyVersion", event.rankingPolicyVersion());
        result.put("baseIntegrationPolicyVersion", event.baseIntegrationPolicyVersion());
        result.put("baseRankingPolicyVersion", event.baseRankingPolicyVersion());
        result.put("scorePolicyVersion", event.scorePolicyVersion());
        result.put("componentPolicyVersions", canonicalComponents(event.componentPolicyVersions()));
        result.put("diversityPolicyVersion", event.diversityPolicyVersion());
        result.put("explorationPolicyVersion", event.explorationPolicyVersion());
        result.put("explorationSeed", event.explorationSeed());
        result.put("rankingStatus", event.rankingStatus().wireValue());
        result.put("rankingEmptyReason", event.rankingEmptyReason() == null ? null : event.rankingEmptyReason().wireValue());
        result.put("requestedLimit", event.requestedLimit());
        result.put("effectiveLimit", event.effectiveLimit());
        result.put("pageStartRank", event.pageStartRank());
        result.put("pageEndRank", event.pageEndRank());
        result.put("pageCandidateCount", event.pageCandidateCount());
        result.put("hasNextPage", event.hasNextPage());
        result.put("inputCount", event.inputCount());
        result.put("finalRankedCandidateCount", event.finalRankedCandidateCount());
        result.put("terminalCandidateCount", event.terminalCandidateCount());
        result.put("diversitySummary", canonicalDiversity(event.diversitySummary()));
        result.put("explorationSummary", canonicalExploration(event.explorationSummary()));
        result.put("candidates", candidates(event));
        return result;
    }
}
