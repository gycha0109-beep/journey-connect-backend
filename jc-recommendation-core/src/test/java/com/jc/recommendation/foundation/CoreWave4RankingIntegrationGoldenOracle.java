package com.jc.recommendation.foundation;

import com.jc.recommendation.canonical.CanonicalJson;
import com.jc.recommendation.integration.DiversityEnabledRanker;
import com.jc.recommendation.integration.ExplorationEnabledRanker;
import com.jc.recommendation.model.diversity.DiversityCandidateMetadata;
import com.jc.recommendation.model.entity.RecommendationEntityType;
import com.jc.recommendation.model.exploration.ExplorationCandidateMetadata;
import com.jc.recommendation.model.exploration.ExplorationFinalCandidate;
import com.jc.recommendation.model.exploration.InsertedExplorationCandidate;
import com.jc.recommendation.model.exploration.PersonalizedExplorationCandidate;
import com.jc.recommendation.model.integration.DiversityRankedCandidate;
import com.jc.recommendation.model.integration.DiversityRankingSummary;
import com.jc.recommendation.model.integration.RankCandidatesWithDiversityInput;
import com.jc.recommendation.model.integration.RankCandidatesWithDiversityResult;
import com.jc.recommendation.model.integration.RankCandidatesWithExplorationInput;
import com.jc.recommendation.model.integration.RankCandidatesWithExplorationResult;
import com.jc.recommendation.model.ranking.TerminalCandidateAudit;
import com.jc.recommendation.model.score.CandidateScoreNotApplicableReason;
import com.jc.recommendation.model.score.CandidateScoreResult;
import com.jc.recommendation.model.score.CandidateScoreStatus;
import com.jc.recommendation.model.score.ScoreComponentAvailability;
import com.jc.recommendation.model.score.ScoreComponentBreakdown;
import com.jc.recommendation.model.score.ScoreComponentName;
import com.jc.recommendation.model.score.ScoreComponentPolicyVersions;
import com.jc.recommendation.model.score.ScoreCompositionMode;
import com.jc.recommendation.policy.RankingIntegrationPolicies;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class CoreWave4RankingIntegrationGoldenOracle {
    private static final ScoreComponentPolicyVersions VERSIONS = new ScoreComponentPolicyVersions(
            "context-match-v1", "interest-match-v1", "freshness-v1", "popularity-v1"
    );
    private final DiversityEnabledRanker v2Ranker = new DiversityEnabledRanker();
    private final ExplorationEnabledRanker v3Ranker = new ExplorationEnabledRanker();
    private final List<Map<String, Object>> records = new ArrayList<>();

    public static void main(String[] args) {
        new CoreWave4RankingIntegrationGoldenOracle().run();
    }

    private void run() {
        var v2 = RankingIntegrationPolicies.V2;
        emit("POLICY_V2", v2.policyVersion(), v2.effectiveFrom().toString(), v2.baseRankingPolicyVersion(),
                v2.expectedScorePolicyVersion(), v2.expectedDiversityPolicyVersion(), v2.cursorVersion(),
                v2.metadataCoverage(), v2.finalRankSource());
        var v3 = RankingIntegrationPolicies.V3;
        emit("POLICY_V3", v3.policyVersion(), v3.effectiveFrom().toString(), v3.baseRankingPolicyVersion(),
                v3.baseIntegrationPolicyVersion(), v3.expectedScorePolicyVersion(), v3.expectedDiversityPolicyVersion(),
                v3.expectedExplorationPolicyVersion(), v3.cursorVersion(), v3.terminalMigration(), v3.finalRankSource());

        List<CandidateScoreResult> v2Candidates = List.of(
                scored("a", 0.9, RecommendationEntityType.POST),
                scored("b", 0.8, RecommendationEntityType.POST),
                scored("c", 0.7, RecommendationEntityType.POST),
                scored("d", 0.6, RecommendationEntityType.POST),
                terminal("z", RecommendationEntityType.POST, 0.8, 0.7)
        );
        List<DiversityCandidateMetadata> v2Metadata = List.of(
                metadata("a", RecommendationEntityType.POST, 0),
                metadata("b", RecommendationEntityType.POST, 1),
                metadata("c", RecommendationEntityType.POST, 2),
                metadata("d", RecommendationEntityType.POST, 3)
        );
        RankCandidatesWithDiversityResult v2Page1 = v2Ranker.rank(new RankCandidatesWithDiversityInput(
                "rank-v2", "meta-v2", "wave4-user", "wave4-context", "score-composition-v1",
                VERSIONS, v2Candidates, v2Metadata, 2, null, null, null
        ));
        emitV2("page1", v2Page1);
        RankCandidatesWithDiversityResult v2Page2 = v2Ranker.rank(new RankCandidatesWithDiversityInput(
                "rank-v2", "meta-v2", "wave4-user", "wave4-context", "score-composition-v1",
                VERSIONS, v2Candidates, v2Metadata, 2, v2Page1.nextCursor(), null, null
        ));
        emitV2("page2", v2Page2);

        List<CandidateScoreResult> v3Candidates = new ArrayList<>();
        List<DiversityCandidateMetadata> v3Metadata = new ArrayList<>();
        for (int index = 0; index < 7; index++) {
            String id = "p" + (index + 1);
            v3Candidates.add(scored(id, 1.0 - index * 0.05, RecommendationEntityType.POST));
            v3Metadata.add(metadata(id, RecommendationEntityType.POST, index));
        }
        v3Candidates.add(terminal("e1", RecommendationEntityType.POST, 0.9, 0.8));
        v3Candidates.add(terminal("e2", RecommendationEntityType.JOURNEY, 0.85, 0.9));
        List<ExplorationCandidateMetadata> explorationMetadata = List.of(
                explorationMetadata("e1", RecommendationEntityType.POST, 0),
                explorationMetadata("e2", RecommendationEntityType.JOURNEY, 1)
        );
        RankCandidatesWithExplorationResult v3Page1 = v3Ranker.rank(new RankCandidatesWithExplorationInput(
                "rank-v3", "meta-v3", "explore-v3", "wave4-user", "wave4-context",
                "score-composition-v1", VERSIONS, "서울🌏wave4", v3Candidates, v3Metadata,
                explorationMetadata, 4, null, null, null, null
        ));
        emitV3("page1", v3Page1);
        RankCandidatesWithExplorationResult v3Page2 = v3Ranker.rank(new RankCandidatesWithExplorationInput(
                "rank-v3", "meta-v3", "explore-v3", "wave4-user", "wave4-context",
                "score-composition-v1", VERSIONS, "서울🌏wave4", v3Candidates, v3Metadata,
                explorationMetadata, 4, v3Page1.nextCursor(), null, null, null
        ));
        emitV3("page2", v3Page2);

        Map<String, Object> document = new LinkedHashMap<>();
        document.put("fixtureVersion", "wave4-ranking-integration-v1");
        document.put("referencePackage", "yeojeong-personalization-phase2-9b-offline-evaluation@0.1.0");
        document.put("records", records);
        try {
            System.out.write((CanonicalJson.stringify(document) + "\n").getBytes(StandardCharsets.UTF_8));
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to write Wave 4 golden", exception);
        }
    }

    private void emitV2(String label, RankCandidatesWithDiversityResult result) {
        emit("V2_RESULT", label, result.status().wireValue(), nullable(result.emptyReason()), result.policyVersion(),
                result.baseRankingPolicyVersion(), result.scorePolicyVersion(), result.diversityPolicyVersion(),
                result.inputCount(), result.scoredCandidateCount(), result.terminalCandidateCount(),
                nullable(result.requestedLimit()), result.effectiveLimit(), nullable(result.pageStartRank()),
                nullable(result.pageEndRank()), result.hasNextPage(), nullable(result.nextCursor()));
        emitSummary("V2_SUMMARY:" + label, result.diversitySummary());
        for (DiversityRankedCandidate candidate : result.rankedCandidates()) {
            emit("V2_CANDIDATE", label, candidate.absoluteRank(), candidate.baseAbsoluteRank(), candidate.entityId(),
                    candidate.entityType().wireValue(), bits(candidate.score()), bits(candidate.scoredWeight()),
                    bits(candidate.neutralFilledWeight()), candidate.selectionReason().wireValue(),
                    join(candidate.appliedRelaxations().stream().map(item -> item.wireValue()).toList()),
                    join(candidate.violatedDimensionsAtSelection().stream().map(item -> item.wireValue()).toList()),
                    candidate.displacement(), candidate.promotionDistance(), candidate.demotionDistance());
        }
        for (TerminalCandidateAudit candidate : result.terminalCandidates()) {
            emit("V2_TERMINAL", label, candidate.entityId(), candidate.entityType().wireValue(),
                    candidate.status().wireValue(), nullable(candidate.notApplicableReason()),
                    nullable(candidate.hardExclusionReason()));
        }
    }

    private void emitV3(String label, RankCandidatesWithExplorationResult result) {
        emit("V3_RESULT", label, result.status().wireValue(), nullable(result.emptyReason()), result.policyVersion(),
                result.baseIntegrationPolicyVersion(), result.baseRankingPolicyVersion(), result.scorePolicyVersion(),
                result.diversityPolicyVersion(), result.explorationPolicyVersion(), result.explorationSeed(),
                result.inputCount(), result.scoredCandidateCount(), result.sourceTerminalCandidateCount(),
                result.personalizedCandidateCount(), result.structurallyEligibleExplorationCandidateCount(),
                result.explorationEligibleCandidateCount(), result.explorationInsertedCandidateCount(),
                result.finalRankedCandidateCount(), result.terminalCandidateCount(), nullable(result.requestedLimit()),
                result.effectiveLimit(), nullable(result.pageStartRank()), nullable(result.pageEndRank()),
                result.hasNextPage(), nullable(result.nextCursor()));
        emitSummary("V3_SUMMARY:" + label, result.diversitySummary());
        var summary = result.explorationSummary();
        emit("V3_EXPLORATION_SUMMARY", label, summary.status().wireValue(),
                summary.structurallyEligibleCandidateCount(), summary.eligibleCandidateCount(),
                summary.insertedCandidateCount(), summary.skippedSlotCount(), join(summary.insertedTargetRanks()));
        for (ExplorationFinalCandidate candidate : result.rankedCandidates()) {
            if (candidate instanceof PersonalizedExplorationCandidate personalized) {
                emit("V3_PERSONALIZED", label, personalized.absoluteRank(), personalized.diversifiedAbsoluteRank(),
                        personalized.baseAbsoluteRank(), personalized.entityId(), personalized.entityType().wireValue(),
                        bits(personalized.score()), personalized.selectionReason().wireValue());
            } else if (candidate instanceof InsertedExplorationCandidate exploration) {
                emit("V3_EXPLORATION", label, exploration.absoluteRank(), exploration.entityId(),
                        exploration.entityType().wireValue(), bits(exploration.explorationQualityScore()),
                        exploration.recentExposureCount(), exploration.seededTieBreakKey(),
                        exploration.explorationPoolRank(), exploration.targetInsertionRank());
            }
        }
        for (TerminalCandidateAudit candidate : result.terminalCandidates()) {
            emit("V3_TERMINAL", label, candidate.entityId(), candidate.entityType().wireValue(),
                    candidate.status().wireValue(), nullable(candidate.notApplicableReason()),
                    nullable(candidate.hardExclusionReason()));
        }
    }

    private void emitSummary(String kind, DiversityRankingSummary summary) {
        var relax = summary.relaxationCountByDimension();
        var violations = summary.violationCountByDimension();
        var missing = summary.missingMetadataCountByDimension();
        emit(kind, summary.status().wireValue(), summary.movedCandidateCount(), summary.maxPromotionObserved(),
                summary.maxDemotionObserved(), summary.movementBoundForcedCount(),
                relax.duplicateGroup(), relax.author(), relax.region(), relax.theme(),
                violations.duplicateGroup(), violations.author(), violations.region(), violations.theme(),
                missing.duplicateGroup(), missing.author(), missing.region(), missing.theme());
    }

    private static CandidateScoreResult scored(String id, double score, RecommendationEntityType type) {
        return new CandidateScoreResult("wave4-user", "wave4-context", id, type, CandidateScoreStatus.SCORED,
                score, ScoreCompositionMode.PERSONALIZED_CONTEXTUAL, 1.0, 0.0,
                List.of(ScoreComponentName.CONTEXT_MATCH, ScoreComponentName.INTEREST_MATCH), List.of(),
                null, null, "score-composition-v1", VERSIONS, List.of());
    }

    private static CandidateScoreResult terminal(String id, RecommendationEntityType type, double freshness, double popularity) {
        return new CandidateScoreResult("wave4-user", "wave4-context", id, type,
                CandidateScoreStatus.NOT_APPLICABLE, null, null, null, null, List.of(), List.of(),
                CandidateScoreNotApplicableReason.NO_ANCHOR_COMPONENT, null, "score-composition-v1", VERSIONS,
                List.of(row(ScoreComponentName.CONTEXT_MATCH, null), row(ScoreComponentName.INTEREST_MATCH, null),
                        row(ScoreComponentName.FRESHNESS, freshness), row(ScoreComponentName.POPULARITY, popularity)));
    }

    private static ScoreComponentBreakdown row(ScoreComponentName component, Double rawScore) {
        String version = switch (component) {
            case CONTEXT_MATCH -> VERSIONS.contextMatch();
            case INTEREST_MATCH -> VERSIONS.interestMatch();
            case FRESHNESS -> VERSIONS.freshness();
            case POPULARITY -> VERSIONS.popularity();
        };
        if (rawScore == null) {
            return new ScoreComponentBreakdown(component, "not_applicable", "not_available", version,
                    1.0, 1.0, ScoreComponentAvailability.NEUTRAL_FILLED, null, 0.5, null);
        }
        return new ScoreComponentBreakdown(component, "scored", null, version, 1.0, 1.0,
                ScoreComponentAvailability.SCORED, rawScore, rawScore, rawScore * 0.1);
    }

    private static DiversityCandidateMetadata metadata(String id, RecommendationEntityType type, int index) {
        return new DiversityCandidateMetadata(id, type, "author-" + (index % 3),
                index % 2 == 0 ? "region:seoul" : "region:busan",
                index % 2 == 0 ? "theme:cafe" : "theme:nature", "dup:" + id);
    }

    private static ExplorationCandidateMetadata explorationMetadata(String id, RecommendationEntityType type, int index) {
        DiversityCandidateMetadata base = metadata(id, type, index);
        return new ExplorationCandidateMetadata(base.entityId(), base.entityType(), base.authorId(),
                base.primaryRegionFeatureId(), base.primaryThemeFeatureId(), base.duplicateGroupId(), index);
    }

    private void emit(String kind, Object... values) {
        List<String> fields = new ArrayList<>();
        for (Object value : values) fields.add(String.valueOf(value));
        Map<String, Object> record = new LinkedHashMap<>();
        record.put("kind", kind);
        record.put("fields", fields);
        records.add(record);
    }

    private static String bits(double value) {
        return String.format(java.util.Locale.ROOT, "%016x", Double.doubleToRawLongBits(value));
    }

    private static String nullable(Object value) {
        if (value == null) return "null";
        if (value instanceof Enum<?> enumeration) {
            try {
                return String.valueOf(enumeration.getClass().getMethod("wireValue").invoke(enumeration));
            } catch (ReflectiveOperationException exception) {
                throw new IllegalStateException("Enum lacks wireValue", exception);
            }
        }
        return String.valueOf(value);
    }

    private static String join(List<?> values) {
        return String.join(",", values.stream().map(String::valueOf).toList());
    }
}
