package com.jc.recommendation.foundation;

import com.jc.recommendation.diversity.DiversityReranker;
import com.jc.recommendation.exploration.ExplorationCandidateInserter;
import com.jc.recommendation.exploration.ExplorationSeed;
import com.jc.recommendation.model.diversity.DiversifiedCandidate;
import com.jc.recommendation.model.diversity.DiversityCandidateMetadata;
import com.jc.recommendation.model.diversity.DiversityRerankInput;
import com.jc.recommendation.model.entity.RecommendationEntityType;
import com.jc.recommendation.model.exploration.ExplorationCandidateMetadata;
import com.jc.recommendation.model.exploration.ExplorationCandidateOrigin;
import com.jc.recommendation.model.exploration.ExplorationSlotDecisionStatus;
import com.jc.recommendation.model.exploration.InsertExplorationCandidatesInput;
import com.jc.recommendation.model.exploration.InsertedExplorationCandidate;
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
import com.jc.recommendation.ranking.CandidateRanker;

import java.util.ArrayList;
import java.util.List;

public final class CoreWave3ExplorationContractTest {
    private static final ScoreComponentPolicyVersions VERSIONS = new ScoreComponentPolicyVersions(
            "context-match-v1", "interest-match-v1", "freshness-v1", "popularity-v1"
    );
    private static final ExplorationCandidateInserter INSERTER = new ExplorationCandidateInserter();

    private CoreWave3ExplorationContractTest() {
    }

    public static void main(String[] args) {
        seedAlgorithmMatchesUtf8Reference();
        insertionPreservesPersonalizedAndTerminalPartition();
        resultsRemainImmutable();
        invalidInputsAreRejected();
        System.out.println("Java recommendation core Wave 3 exploration contract: PASS");
    }

    private static void seedAlgorithmMatchesUtf8Reference() {
        equal(2_166_136_261L, ExplorationSeed.fnv1a32Utf8(""), "empty FNV-1a");
        equal(1_335_831_723L, ExplorationSeed.fnv1a32Utf8("hello"), "ASCII FNV-1a");
        equal(363_745_389L, ExplorationSeed.fnv1a32Utf8("같은-seed-🚀"), "UTF-8 FNV-1a");
        String material = ExplorationSeed.material(
                "exploration-v1", "ranking", "metadata", "exploration", "seed", "post", "e1"
        );
        equal(1_494_478_863L, ExplorationSeed.fnv1a32Utf8(material), "material FNV-1a");
    }

    private static void insertionPreservesPersonalizedAndTerminalPartition() {
        List<DiversifiedCandidate> personalized = personalized(5);
        CandidateScoreResult eligible = terminal(
                "explore-me", RecommendationEntityType.POST, CandidateScoreStatus.NOT_APPLICABLE,
                CandidateScoreNotApplicableReason.NO_ANCHOR_COMPONENT, 0.8, 0.7, false
        );
        CandidateScoreResult hardExcluded = terminal(
                "hard", RecommendationEntityType.POST, CandidateScoreStatus.HARD_EXCLUDED,
                null, 0.9, 0.9, false
        );
        var result = INSERTER.insert(input(
                personalized,
                List.of(eligible, hardExcluded),
                List.of(new ExplorationCandidateMetadata(
                        "explore-me", RecommendationEntityType.POST, "explore-author",
                        "region:busan", "theme:nature", "explore-duplicate", 0
                )),
                "seed-v1"
        ));

        equal(1, result.insertedCandidateCount(), "inserted count");
        equal(6, result.outputCount(), "output count");
        equal(1, result.remainingTerminalCount(), "remaining terminal count");
        equal("hard", result.remainingTerminalCandidates().getFirst().entityId(), "hard excluded remains terminal");
        equal(CandidateScoreStatus.HARD_EXCLUDED,
                result.remainingTerminalCandidates().getFirst().status(), "hard excluded status");
        equal(List.of("p01", "p02", "p03", "p04", "p05", "explore-me"),
                result.finalCandidates().stream().map(item -> item.entityId()).toList(),
                "final identities");
        InsertedExplorationCandidate inserted = (InsertedExplorationCandidate) result.finalCandidates().get(5);
        equal(ExplorationCandidateOrigin.EXPLORATION, inserted.origin(), "exploration origin");
        equal(6, inserted.absoluteRank(), "inserted absolute rank");
        equal(6, inserted.targetInsertionRank(), "target insertion rank");
        equal(null, inserted.score(), "exploration score impersonation forbidden");
        rawBits(0x3fe83a83a83a83a8L, inserted.explorationQualityScore(), "quality score");
        equal(ExplorationSlotDecisionStatus.INSERTED,
                result.summary().slotDecisions().getFirst().status(), "first slot inserted");
        equal(ExplorationSlotDecisionStatus.SKIPPED_DEPTH,
                result.summary().slotDecisions().get(1).status(), "second slot depth skipped");
    }

    private static void resultsRemainImmutable() {
        var result = INSERTER.insert(input(
                personalized(5),
                List.of(terminal(
                        "immutable", RecommendationEntityType.POST, CandidateScoreStatus.NOT_APPLICABLE,
                        CandidateScoreNotApplicableReason.NO_ANCHOR_COMPONENT, 0.8, 0.7, false
                )),
                List.of(new ExplorationCandidateMetadata(
                        "immutable", RecommendationEntityType.POST, null,
                        "region:busan", "theme:nature", null, 0
                )),
                "immutable-seed"
        ));
        expectUnsupported(() -> result.finalCandidates().clear());
        expectUnsupported(() -> result.remainingTerminalCandidates().clear());
        expectUnsupported(() -> result.summary().slotDecisions().clear());
        InsertedExplorationCandidate inserted = (InsertedExplorationCandidate) result.finalCandidates().get(5);
        expectUnsupported(() -> inserted.qualityEvidence().clear());
    }

    private static void invalidInputsAreRejected() {
        CandidateScoreResult candidate = terminal(
                "coverage", RecommendationEntityType.POST, CandidateScoreStatus.NOT_APPLICABLE,
                CandidateScoreNotApplicableReason.NO_ANCHOR_COMPONENT, 0.8, 0.7, false
        );
        expectIllegalArgumentContains(() -> INSERTER.insert(input(
                personalized(5), List.of(candidate), List.of(), "seed"
        )), "exploration metadata coverage mismatch");

        expectIllegalArgumentContains(() -> INSERTER.insert(input(
                personalized(5), List.of(candidate), List.of(new ExplorationCandidateMetadata(
                        "coverage", RecommendationEntityType.POST, null,
                        "region:busan", "theme:nature", null, 0
                )), "가".repeat(43)
        )), "explorationSeed must be 1..128 UTF-8 bytes");

        CandidateScoreResult malformed = terminal(
                "malformed", RecommendationEntityType.POST, CandidateScoreStatus.NOT_APPLICABLE,
                CandidateScoreNotApplicableReason.NO_ANCHOR_COMPONENT, 0.8, 0.7, true
        );
        expectIllegalArgumentContains(() -> INSERTER.insert(input(
                personalized(5), List.of(malformed), List.of(new ExplorationCandidateMetadata(
                        "malformed", RecommendationEntityType.POST, null,
                        "region:busan", "theme:nature", null, 0
                )), "seed"
        )), "has malformed scored freshness evidence");
    }

    private static InsertExplorationCandidatesInput input(
            List<DiversifiedCandidate> personalized,
            List<CandidateScoreResult> terminal,
            List<ExplorationCandidateMetadata> metadata,
            String seed
    ) {
        return new InsertExplorationCandidatesInput(
                "ranking-snapshot", "metadata-snapshot", "exploration-snapshot",
                "ranking-v2", "score-composition-v1", "diversity-v1", seed,
                personalized, terminal, metadata, null, null
        );
    }

    private static List<DiversifiedCandidate> personalized(int count) {
        List<CandidateScoreResult> candidates = new ArrayList<>();
        List<DiversityCandidateMetadata> metadata = new ArrayList<>();
        for (int index = 0; index < count; index++) {
            String id = "p" + String.format(java.util.Locale.ROOT, "%02d", index + 1);
            candidates.add(scored(id, 1.0d - index * 0.01d));
            metadata.add(new DiversityCandidateMetadata(
                    id, RecommendationEntityType.POST, "author:" + id,
                    "region:seoul", "theme:cafe", "dup:" + id
            ));
        }
        var ranked = new CandidateRanker().rank(new RankCandidatesInput(
                "ranking-snapshot", "ranking-user", "ranking-context",
                "score-composition-v1", VERSIONS, candidates, 30, null, null
        )).rankedCandidates();
        return new DiversityReranker().rerank(new DiversityRerankInput(
                "ranking-snapshot", "metadata-snapshot", "ranking-v1",
                "score-composition-v1", ranked, metadata, null
        )).diversifiedCandidates();
    }

    private static CandidateScoreResult scored(String id, double score) {
        return new CandidateScoreResult(
                "ranking-user", "ranking-context", id, RecommendationEntityType.POST,
                CandidateScoreStatus.SCORED, score, ScoreCompositionMode.PERSONALIZED_CONTEXTUAL,
                1.0d, 0.0d,
                List.of(ScoreComponentName.CONTEXT_MATCH, ScoreComponentName.INTEREST_MATCH),
                List.of(), null, null, "score-composition-v1", VERSIONS, List.of()
        );
    }

    private static CandidateScoreResult terminal(
            String id,
            RecommendationEntityType type,
            CandidateScoreStatus status,
            CandidateScoreNotApplicableReason reason,
            Double freshness,
            Double popularity,
            boolean malformedFreshness
    ) {
        List<ScoreComponentBreakdown> breakdown = List.of(
                breakdown(ScoreComponentName.CONTEXT_MATCH, null, false),
                breakdown(ScoreComponentName.INTEREST_MATCH, null, false),
                breakdown(ScoreComponentName.FRESHNESS, freshness, malformedFreshness),
                breakdown(ScoreComponentName.POPULARITY, popularity, false)
        );
        return new CandidateScoreResult(
                "ranking-user", "ranking-context", id, type, status,
                null, null, null, null, List.of(),
                status == CandidateScoreStatus.HARD_EXCLUDED
                        ? List.of(ScoreComponentName.INTEREST_MATCH) : List.of(),
                status == CandidateScoreStatus.NOT_APPLICABLE ? reason : null,
                status == CandidateScoreStatus.HARD_EXCLUDED
                        ? CandidateScoreHardExclusionReason.INTEREST_HARD_EXCLUSION : null,
                "score-composition-v1", VERSIONS, breakdown
        );
    }

    private static ScoreComponentBreakdown breakdown(
            ScoreComponentName component,
            Double rawScore,
            boolean malformed
    ) {
        if (rawScore == null) {
            return new ScoreComponentBreakdown(
                    component, "not_applicable", "unsupported_entity_type",
                    component.wireValue() + "-v1", 1.0d, 1.0d,
                    ScoreComponentAvailability.STRUCTURALLY_EXCLUDED,
                    null, null, null
            );
        }
        return new ScoreComponentBreakdown(
                component, malformed ? "not_applicable" : "scored", malformed ? "broken" : null,
                component.wireValue() + "-v1", 1.0d, 1.0d,
                ScoreComponentAvailability.SCORED,
                rawScore, rawScore, rawScore
        );
    }

    private static void rawBits(long expected, double actual, String label) {
        long actualBits = Double.doubleToRawLongBits(actual);
        if (actualBits != expected) {
            throw new AssertionError(label + ": expected raw bits " + Long.toHexString(expected)
                    + " but was " + Long.toHexString(actualBits));
        }
    }

    private static void equal(Object expected, Object actual, String label) {
        if (!java.util.Objects.equals(expected, actual)) {
            throw new AssertionError(label + ": expected " + expected + " but was " + actual);
        }
    }

    private static void expectUnsupported(Runnable action) {
        try {
            action.run();
            throw new AssertionError("Expected UnsupportedOperationException");
        } catch (UnsupportedOperationException expected) {
            // Expected.
        }
    }

    private static void expectIllegalArgumentContains(Runnable action, String expectedText) {
        try {
            action.run();
            throw new AssertionError("Expected IllegalArgumentException containing: " + expectedText);
        } catch (IllegalArgumentException exception) {
            if (!exception.getMessage().contains(expectedText)) {
                throw new AssertionError("Expected IllegalArgumentException containing '" + expectedText
                        + "' but was '" + exception.getMessage() + "'", exception);
            }
        }
    }
}
