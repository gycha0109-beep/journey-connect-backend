package com.jc.recommendation.foundation;

import com.jc.recommendation.integration.DiversityEnabledRanker;
import com.jc.recommendation.integration.ExplorationEnabledRanker;
import com.jc.recommendation.model.diversity.DiversityCandidateMetadata;
import com.jc.recommendation.model.entity.RecommendationEntityType;
import com.jc.recommendation.model.exploration.ExplorationCandidateMetadata;
import com.jc.recommendation.model.integration.RankCandidatesWithDiversityInput;
import com.jc.recommendation.model.integration.RankCandidatesWithExplorationInput;
import com.jc.recommendation.model.score.CandidateScoreNotApplicableReason;
import com.jc.recommendation.model.score.CandidateScoreResult;
import com.jc.recommendation.model.score.CandidateScoreStatus;
import com.jc.recommendation.model.score.ScoreComponentAvailability;
import com.jc.recommendation.model.score.ScoreComponentBreakdown;
import com.jc.recommendation.model.score.ScoreComponentName;
import com.jc.recommendation.model.score.ScoreComponentPolicyVersions;
import com.jc.recommendation.model.score.ScoreCompositionMode;

import java.util.ArrayList;
import java.util.List;

public final class CoreWave4RankingIntegrationContractTest {
    private static final ScoreComponentPolicyVersions VERSIONS = new ScoreComponentPolicyVersions(
            "context-match-v1", "interest-match-v1", "freshness-v1", "popularity-v1"
    );

    public static void main(String[] args) {
        verifiesV2PaginationBinding();
        verifiesV3TerminalMigrationAndPagination();
        System.out.println("Java recommendation core Wave 4 ranking integration contract: PASS");
    }

    private static void verifiesV2PaginationBinding() {
        var ranker = new DiversityEnabledRanker();
        List<CandidateScoreResult> candidates = List.of(scored("a", 0.9), scored("b", 0.8), scored("c", 0.7));
        List<DiversityCandidateMetadata> metadata = List.of(meta("a", 0), meta("b", 1), meta("c", 2));
        var first = ranker.rank(new RankCandidatesWithDiversityInput(
                "r", "m", "u", "c", "score-composition-v1", VERSIONS,
                candidates, metadata, 2, null, null, null
        ));
        check(first.hasNextPage() && first.nextCursor() != null, "v2 first page cursor");
        var second = ranker.rank(new RankCandidatesWithDiversityInput(
                "r", "m", "u", "c", "score-composition-v1", VERSIONS,
                candidates, metadata, 2, first.nextCursor(), null, null
        ));
        check(second.pageStartRank() == 3 && second.rankedCandidates().size() == 1, "v2 page continuity");
        expectFailure(() -> ranker.rank(new RankCandidatesWithDiversityInput(
                "r", "changed", "u", "c", "score-composition-v1", VERSIONS,
                candidates, metadata, 2, first.nextCursor(), null, null
        )), "cursor metadata snapshot mismatch");
    }

    private static void verifiesV3TerminalMigrationAndPagination() {
        var ranker = new ExplorationEnabledRanker();
        List<CandidateScoreResult> candidates = new ArrayList<>();
        List<DiversityCandidateMetadata> metadata = new ArrayList<>();
        for (int index = 0; index < 7; index++) {
            String id = "p" + index;
            candidates.add(scored(id, 1.0 - index * 0.05));
            metadata.add(meta(id, index));
        }
        candidates.add(terminal("e"));
        var first = ranker.rank(new RankCandidatesWithExplorationInput(
                "r3", "m3", "e3", "u", "c", "score-composition-v1", VERSIONS,
                "seed", candidates, metadata, List.of(exploreMeta("e")), 4,
                null, null, null, null
        ));
        check(first.explorationInsertedCandidateCount() == 1, "v3 exploration insertion");
        check(first.finalRankedCandidateCount() == 8 && first.terminalCandidateCount() == 0, "v3 terminal migration");
        check(first.nextCursor() != null, "v3 first page cursor");
        expectFailure(() -> ranker.rank(new RankCandidatesWithExplorationInput(
                "r3", "m3", "e3", "u", "c", "score-composition-v1", VERSIONS,
                "changed-seed", candidates, metadata, List.of(exploreMeta("e")), 4,
                first.nextCursor(), null, null, null
        )), "cursor Exploration seed mismatch");
    }

    private static CandidateScoreResult scored(String id, double score) {
        return new CandidateScoreResult("u", "c", id, RecommendationEntityType.POST,
                CandidateScoreStatus.SCORED, score, ScoreCompositionMode.PERSONALIZED_CONTEXTUAL,
                1.0, 0.0, List.of(ScoreComponentName.CONTEXT_MATCH), List.of(), null, null,
                "score-composition-v1", VERSIONS, List.of());
    }

    private static CandidateScoreResult terminal(String id) {
        return new CandidateScoreResult("u", "c", id, RecommendationEntityType.POST,
                CandidateScoreStatus.NOT_APPLICABLE, null, null, null, null, List.of(), List.of(),
                CandidateScoreNotApplicableReason.NO_ANCHOR_COMPONENT, null, "score-composition-v1", VERSIONS,
                List.of(row(ScoreComponentName.CONTEXT_MATCH, null), row(ScoreComponentName.INTEREST_MATCH, null),
                        row(ScoreComponentName.FRESHNESS, 0.9), row(ScoreComponentName.POPULARITY, 0.8)));
    }

    private static ScoreComponentBreakdown row(ScoreComponentName component, Double score) {
        String version = switch (component) {
            case CONTEXT_MATCH -> VERSIONS.contextMatch();
            case INTEREST_MATCH -> VERSIONS.interestMatch();
            case FRESHNESS -> VERSIONS.freshness();
            case POPULARITY -> VERSIONS.popularity();
        };
        if (score == null) {
            return new ScoreComponentBreakdown(component, "not_applicable", "not_available", version,
                    1.0, 1.0, ScoreComponentAvailability.NEUTRAL_FILLED, null, 0.5, null);
        }
        return new ScoreComponentBreakdown(component, "scored", null, version, 1.0, 1.0,
                ScoreComponentAvailability.SCORED, score, score, score * 0.1);
    }

    private static DiversityCandidateMetadata meta(String id, int index) {
        String region = index % 2 == 0 ? "region:seoul" : "region:busan";
        String theme = index % 2 == 0 ? "theme:cafe" : "theme:nature";
        return new DiversityCandidateMetadata(id, RecommendationEntityType.POST, "author:" + index,
                region, theme, "dup:" + id);
    }

    private static ExplorationCandidateMetadata exploreMeta(String id) {
        return new ExplorationCandidateMetadata(id, RecommendationEntityType.POST, "author:e",
                "region:jeju", "theme:healing", "dup:e", 0);
    }

    private static void check(boolean condition, String label) {
        if (!condition) throw new AssertionError(label);
    }

    private static void expectFailure(Runnable operation, String expectedMessage) {
        try {
            operation.run();
            throw new AssertionError("Expected failure: " + expectedMessage);
        } catch (IllegalArgumentException exception) {
            if (!exception.getMessage().contains(expectedMessage)) {
                throw new AssertionError("Unexpected error: " + exception.getMessage(), exception);
            }
        }
    }
}
