package com.jc.recommendation.foundation;

import com.jc.recommendation.canonical.CanonicalJson;
import com.jc.recommendation.diversity.DiversityReranker;
import com.jc.recommendation.exploration.ExplorationCandidateInserter;
import com.jc.recommendation.model.diversity.DiversifiedCandidate;
import com.jc.recommendation.model.diversity.DiversityCandidateMetadata;
import com.jc.recommendation.model.diversity.DiversityRerankInput;
import com.jc.recommendation.model.entity.RecommendationEntityType;
import com.jc.recommendation.model.exploration.ExplorationCandidateMetadata;
import com.jc.recommendation.model.exploration.ExplorationFinalCandidate;
import com.jc.recommendation.model.exploration.InsertExplorationCandidatesInput;
import com.jc.recommendation.model.exploration.InsertExplorationCandidatesResult;
import com.jc.recommendation.model.exploration.InsertedExplorationCandidate;
import com.jc.recommendation.model.exploration.PersonalizedExplorationCandidate;
import com.jc.recommendation.model.ranking.RankCandidatesInput;
import com.jc.recommendation.model.score.CandidateScoreHardExclusionReason;
import com.jc.recommendation.model.score.CandidateScoreNotApplicableReason;
import com.jc.recommendation.model.score.CandidateScoreResult;
import com.jc.recommendation.model.score.CandidateScoreStatus;
import com.jc.recommendation.model.score.ScoreComponentAvailability;
import com.jc.recommendation.model.score.ScoreComponentBreakdown;
import com.jc.recommendation.model.score.ScoreComponentName;
import com.jc.recommendation.model.score.ScoreComponentPolicyVersions;
import com.jc.recommendation.model.score.ScoreCompositionMode;
import com.jc.recommendation.policy.ExplorationPolicies;
import com.jc.recommendation.ranking.CandidateRanker;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class CoreWave3ExplorationGoldenOracle {
    private static final ScoreComponentPolicyVersions VERSIONS = new ScoreComponentPolicyVersions(
            "context-match-v1", "interest-match-v1", "freshness-v1", "popularity-v1"
    );
    private final CandidateRanker ranker = new CandidateRanker();
    private final DiversityReranker diversityReranker = new DiversityReranker();
    private final ExplorationCandidateInserter inserter = new ExplorationCandidateInserter();
    private final List<Map<String, Object>> records = new ArrayList<>();

    @FunctionalInterface
    private interface MetadataFactory {
        DiversityCandidateMetadata create(String entityId, int index);
    }

    public static void main(String[] args) {
        new CoreWave3ExplorationGoldenOracle().run();
    }

    private void run() {
        var policy = ExplorationPolicies.V1;
        emit("POLICY",
                policy.policyVersion(),
                policy.effectiveFrom().toString(),
                policy.expectedRankingPolicyVersion(),
                policy.expectedScorePolicyVersion(),
                policy.expectedDiversityPolicyVersion(),
                Integer.toString(policy.maximumCandidateCount()),
                String.join(",", policy.eligibleEntityTypes().stream().map(RecommendationEntityType::wireValue).toList()),
                policy.eligibleStatus().wireValue(),
                policy.eligibleNotApplicableReason().wireValue(),
                String.join(",", policy.qualityComponents().stream().map(item -> item.wireValue()).toList()),
                Integer.toString(policy.qualityWeights().freshness()),
                Integer.toString(policy.qualityWeights().popularity()),
                Integer.toString(policy.minimumAvailableQualityComponents()),
                bits(policy.minimumQualityScore()),
                Integer.toString(policy.exposureCountWindowDays()),
                Integer.toString(policy.maximumRecentExposureCount()),
                Integer.toString(policy.maximumInsertions()),
                joinIntegers(policy.insertionRanks()),
                policy.selectionOrder(),
                policy.seedAlgorithm().wireValue(),
                Integer.toString(policy.maximumSeedUtf8Bytes()),
                policy.diversityGuard(),
                policy.diversityRelaxation(),
                policy.personalizedCandidateRemoval(),
                policy.explorationScoreImpersonation(),
                policy.hardExcludedResurrection(),
                policy.paginationStage()
        );

        runScenario("insert-one", diversified(5, this::defaultDiversityMetadata), List.of(
                terminal("e1", RecommendationEntityType.POST, CandidateScoreStatus.NOT_APPLICABLE,
                        CandidateScoreNotApplicableReason.NO_ANCHOR_COMPONENT, 0.8, 0.7),
                terminal("e2", RecommendationEntityType.JOURNEY, CandidateScoreStatus.NOT_APPLICABLE,
                        CandidateScoreNotApplicableReason.NO_ANCHOR_COMPONENT, 0.9, 0.6)
        ), List.of(
                explorationMetadata("e1", RecommendationEntityType.POST, 1,
                        "explore-author:e1", "region:busan", "theme:nature", "explore-dup:e1"),
                explorationMetadata("e2", RecommendationEntityType.JOURNEY, 0,
                        "explore-author:e2", "region:busan", "theme:nature", "explore-dup:e2")
        ), "seed-v1");

        runScenario("filtered", diversified(5, this::defaultDiversityMetadata), List.of(
                terminal("exposed", RecommendationEntityType.POST, CandidateScoreStatus.NOT_APPLICABLE,
                        CandidateScoreNotApplicableReason.NO_ANCHOR_COMPONENT, 0.9, 0.9),
                terminal("low", RecommendationEntityType.POST, CandidateScoreStatus.NOT_APPLICABLE,
                        CandidateScoreNotApplicableReason.NO_ANCHOR_COMPONENT, 0.2, 0.1),
                terminal("no-evidence", RecommendationEntityType.POST, CandidateScoreStatus.NOT_APPLICABLE,
                        CandidateScoreNotApplicableReason.NO_ANCHOR_COMPONENT, null, null),
                terminal("place-no-anchor", RecommendationEntityType.PLACE, CandidateScoreStatus.NOT_APPLICABLE,
                        CandidateScoreNotApplicableReason.NO_ANCHOR_COMPONENT, 0.9, 0.9),
                terminal("expired", RecommendationEntityType.POST, CandidateScoreStatus.NOT_APPLICABLE,
                        CandidateScoreNotApplicableReason.EXPIRED_CONTEXT, 0.9, 0.9),
                terminal("hard", RecommendationEntityType.POST, CandidateScoreStatus.HARD_EXCLUDED,
                        null, 0.9, 0.9)
        ), List.of(
                explorationMetadata("exposed", RecommendationEntityType.POST, 3,
                        "explore-author:exposed", "region:busan", "theme:nature", "explore-dup:exposed"),
                explorationMetadata("low", RecommendationEntityType.POST, 0,
                        "explore-author:low", "region:busan", "theme:nature", "explore-dup:low"),
                explorationMetadata("no-evidence", RecommendationEntityType.POST, 0,
                        "explore-author:no-evidence", "region:busan", "theme:nature", "explore-dup:no-evidence")
        ), "seed-v1");

        runScenario("diversity-blocked", diversified(5, (entityId, index) -> new DiversityCandidateMetadata(
                entityId, RecommendationEntityType.POST, "same-author", "region:seoul",
                "theme:cafe", "same"
        )), List.of(
                terminal("blocked", RecommendationEntityType.POST, CandidateScoreStatus.NOT_APPLICABLE,
                        CandidateScoreNotApplicableReason.NO_ANCHOR_COMPONENT, 0.95, 0.95)
        ), List.of(
                explorationMetadata("blocked", RecommendationEntityType.POST, 0,
                        "same-author", "region:seoul", "theme:cafe", "same")
        ), "seed-v1");

        runScenario("insert-two", diversified(15, this::defaultDiversityMetadata), List.of(
                terminal("two-a", RecommendationEntityType.POST, CandidateScoreStatus.NOT_APPLICABLE,
                        CandidateScoreNotApplicableReason.NO_ANCHOR_COMPONENT, 0.85, 0.75),
                terminal("two-b", RecommendationEntityType.JOURNEY, CandidateScoreStatus.NOT_APPLICABLE,
                        CandidateScoreNotApplicableReason.NO_ANCHOR_COMPONENT, 0.75, 0.9)
        ), List.of(
                explorationMetadata("two-a", RecommendationEntityType.POST, 0,
                        "explore-author:two-a", "region:busan", "theme:nature", "explore-dup:two-a"),
                explorationMetadata("two-b", RecommendationEntityType.JOURNEY, 1,
                        "explore-author:two-b", "region:jeju", "theme:healing", "explore-dup:two-b")
        ), "같은-seed-🚀");

        Map<String, Object> document = new LinkedHashMap<>();
        document.put("fixtureVersion", "wave3-exploration-v1");
        document.put("referencePackage", "yeojeong-personalization-phase2-9b-offline-evaluation@0.1.0");
        document.put("records", records);
        try {
            System.out.write((CanonicalJson.stringify(document) + "\n").getBytes(StandardCharsets.UTF_8));
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to write Wave 3 exploration golden", exception);
        }
    }

    private void runScenario(
            String label,
            List<DiversifiedCandidate> personalized,
            List<CandidateScoreResult> terminalCandidates,
            List<ExplorationCandidateMetadata> metadata,
            String seed
    ) {
        InsertExplorationCandidatesResult result = inserter.insert(new InsertExplorationCandidatesInput(
                "ranking-snapshot",
                "metadata-snapshot",
                "exploration:" + label,
                "ranking-v2",
                "score-composition-v1",
                "diversity-v1",
                seed,
                personalized,
                terminalCandidates,
                metadata,
                null,
                null
        ));
        emitResult(label, result);
    }

    private void emitResult(String label, InsertExplorationCandidatesResult result) {
        var summary = result.summary();
        emit("RESULT", label,
                result.rankingPolicyVersion(),
                result.scorePolicyVersion(),
                result.diversityPolicyVersion(),
                result.explorationPolicyVersion(),
                result.explorationSeed(),
                Integer.toString(result.inputPersonalizedCount()),
                Integer.toString(result.inputTerminalCount()),
                Integer.toString(result.eligibleCandidateCount()),
                Integer.toString(result.insertedCandidateCount()),
                Integer.toString(result.outputCount()),
                Integer.toString(result.remainingTerminalCount()),
                summary.status().wireValue(),
                Integer.toString(summary.structurallyEligibleCandidateCount()),
                Integer.toString(summary.eligibleCandidateCount()),
                Integer.toString(summary.insertedCandidateCount()),
                Integer.toString(summary.skippedSlotCount()),
                Integer.toString(summary.statusReasonRejectedCount()),
                Integer.toString(summary.entityTypeRejectedCount()),
                Integer.toString(summary.exposureRejectedCount()),
                Integer.toString(summary.qualityEvidenceRejectedCount()),
                Integer.toString(summary.qualityFloorRejectedCount()),
                Integer.toString(summary.diversityGuardRejectedEvaluationCount()),
                joinIntegers(summary.insertedTargetRanks()),
                summary.policyVersion(),
                summary.seedAlgorithm().wireValue()
        );
        summary.slotDecisions().forEach(decision -> emit("SLOT", label,
                Integer.toString(decision.targetInsertionRank()),
                decision.status().wireValue(),
                nullable(decision.selectedEntityType() == null ? null : decision.selectedEntityType().wireValue()),
                nullable(decision.selectedEntityId())
        ));
        for (ExplorationFinalCandidate candidate : result.finalCandidates()) {
            if (candidate instanceof PersonalizedExplorationCandidate personalized) {
                emit("PERSONALIZED", label,
                        Integer.toString(personalized.absoluteRank()),
                        Integer.toString(personalized.diversifiedAbsoluteRank()),
                        Integer.toString(personalized.baseAbsoluteRank()),
                        personalized.entityId(),
                        personalized.entityType().wireValue(),
                        bits(personalized.score()),
                        bits(personalized.scoredWeight()),
                        bits(personalized.neutralFilledWeight()),
                        personalized.compositionMode().wireValue(),
                        personalized.scorePolicyVersion(),
                        personalized.selectionReason().wireValue(),
                        String.join(",", personalized.appliedRelaxations().stream().map(item -> item.wireValue()).toList()),
                        String.join(",", personalized.violatedDimensionsAtSelection().stream().map(item -> item.wireValue()).toList()),
                        Integer.toString(personalized.displacement()),
                        Integer.toString(personalized.promotionDistance()),
                        Integer.toString(personalized.demotionDistance()),
                        nullable(personalized.diversityMetadata().authorId()),
                        nullable(personalized.diversityMetadata().primaryRegionFeatureId()),
                        nullable(personalized.diversityMetadata().primaryThemeFeatureId()),
                        nullable(personalized.diversityMetadata().duplicateGroupId())
                );
            } else if (candidate instanceof InsertedExplorationCandidate inserted) {
                emit("EXPLORATION", label,
                        Integer.toString(inserted.absoluteRank()),
                        inserted.entityId(),
                        inserted.entityType().wireValue(),
                        inserted.scorePolicyVersion(),
                        inserted.sourceStatus().wireValue(),
                        inserted.sourceNotApplicableReason().wireValue(),
                        bits(inserted.explorationQualityScore()),
                        Integer.toString(inserted.availableWeightTotal()),
                        nullable(inserted.freshnessRawScore() == null ? null : bits(inserted.freshnessRawScore())),
                        nullable(inserted.popularityRawScore() == null ? null : bits(inserted.popularityRawScore())),
                        Integer.toString(inserted.recentExposureCount()),
                        Long.toString(inserted.seededTieBreakKey()),
                        Integer.toString(inserted.explorationPoolRank()),
                        Integer.toString(inserted.targetInsertionRank()),
                        inserted.explorationPolicyVersion(),
                        nullable(inserted.diversityMetadata().authorId()),
                        nullable(inserted.diversityMetadata().primaryRegionFeatureId()),
                        nullable(inserted.diversityMetadata().primaryThemeFeatureId()),
                        nullable(inserted.diversityMetadata().duplicateGroupId())
                );
                inserted.qualityEvidence().forEach(evidence -> emit("QUALITY", label,
                        inserted.entityId(),
                        evidence.component().wireValue(),
                        bits(evidence.rawScore()),
                        Integer.toString(evidence.configuredWeight()),
                        bits(evidence.weightedContribution())
                ));
            } else {
                throw new IllegalStateException("Unknown final exploration candidate");
            }
        }
        result.remainingTerminalCandidates().forEach(candidate -> emit("TERMINAL", label,
                candidate.entityId(),
                candidate.entityType().wireValue(),
                candidate.status().wireValue(),
                nullable(candidate.notApplicableReason() == null ? null : candidate.notApplicableReason().wireValue()),
                nullable(candidate.hardExclusionReason() == null ? null : candidate.hardExclusionReason().wireValue())
        ));
    }

    private List<DiversifiedCandidate> diversified(int count, MetadataFactory metadataFactory) {
        List<CandidateScoreResult> candidates = new ArrayList<>();
        List<DiversityCandidateMetadata> metadata = new ArrayList<>();
        for (int index = 0; index < count; index++) {
            String entityId = "p" + String.format(Locale.ROOT, "%02d", index + 1);
            candidates.add(scoredCandidate(entityId, 1.0d - index * 0.01d));
            metadata.add(metadataFactory.create(entityId, index));
        }
        var base = ranker.rank(new RankCandidatesInput(
                "ranking-snapshot", "ranking-user", "ranking-context", "score-composition-v1",
                VERSIONS, candidates, 30, null, null
        )).rankedCandidates();
        return diversityReranker.rerank(new DiversityRerankInput(
                "ranking-snapshot", "metadata-snapshot", "ranking-v1", "score-composition-v1",
                base, metadata, null
        )).diversifiedCandidates();
    }

    private DiversityCandidateMetadata defaultDiversityMetadata(String entityId, int index) {
        return new DiversityCandidateMetadata(
                entityId,
                RecommendationEntityType.POST,
                "author:" + entityId,
                "region:seoul",
                "theme:cafe",
                "dup:" + entityId
        );
    }

    private static CandidateScoreResult scoredCandidate(String entityId, double score) {
        return new CandidateScoreResult(
                "ranking-user", "ranking-context", entityId, RecommendationEntityType.POST,
                CandidateScoreStatus.SCORED, score, ScoreCompositionMode.PERSONALIZED_CONTEXTUAL,
                1.0d, 0.0d,
                List.of(ScoreComponentName.CONTEXT_MATCH, ScoreComponentName.INTEREST_MATCH),
                List.of(), null, null, "score-composition-v1", VERSIONS, List.of()
        );
    }

    private static CandidateScoreResult terminal(
            String entityId,
            RecommendationEntityType entityType,
            CandidateScoreStatus status,
            CandidateScoreNotApplicableReason reason,
            Double freshness,
            Double popularity
    ) {
        return new CandidateScoreResult(
                "ranking-user", "ranking-context", entityId, entityType, status,
                null, null, null, null,
                List.of(),
                status == CandidateScoreStatus.HARD_EXCLUDED
                        ? List.of(ScoreComponentName.INTEREST_MATCH) : List.of(),
                status == CandidateScoreStatus.NOT_APPLICABLE ? reason : null,
                status == CandidateScoreStatus.HARD_EXCLUDED
                        ? CandidateScoreHardExclusionReason.INTEREST_HARD_EXCLUSION : null,
                "score-composition-v1", VERSIONS,
                List.of(
                        breakdown(ScoreComponentName.CONTEXT_MATCH, null),
                        breakdown(ScoreComponentName.INTEREST_MATCH, null),
                        breakdown(ScoreComponentName.FRESHNESS, freshness),
                        breakdown(ScoreComponentName.POPULARITY, popularity)
                )
        );
    }

    private static ScoreComponentBreakdown breakdown(ScoreComponentName component, Double rawScore) {
        return rawScore == null
                ? new ScoreComponentBreakdown(
                        component, "not_applicable", "unsupported_entity_type",
                        component.wireValue() + "-v1", 1.0d, 1.0d,
                        ScoreComponentAvailability.STRUCTURALLY_EXCLUDED,
                        null, null, null
                )
                : new ScoreComponentBreakdown(
                        component, "scored", null,
                        component.wireValue() + "-v1", 1.0d, 1.0d,
                        ScoreComponentAvailability.SCORED,
                        rawScore, rawScore, rawScore
                );
    }

    private static ExplorationCandidateMetadata explorationMetadata(
            String entityId,
            RecommendationEntityType entityType,
            int recentExposureCount,
            String authorId,
            String region,
            String theme,
            String duplicateGroup
    ) {
        return new ExplorationCandidateMetadata(
                entityId, entityType, authorId, region, theme, duplicateGroup, recentExposureCount
        );
    }

    private void emit(String kind, String... fields) {
        Map<String, Object> record = new LinkedHashMap<>();
        record.put("kind", kind);
        record.put("fields", List.of(fields));
        records.add(record);
    }

    private static String joinIntegers(List<Integer> values) {
        return String.join(",", values.stream().map(String::valueOf).toList());
    }

    private static String bits(double value) {
        return String.format(Locale.ROOT, "%016x", Double.doubleToRawLongBits(value));
    }

    private static String nullable(Object value) {
        return value == null ? "null" : String.valueOf(value);
    }
}
