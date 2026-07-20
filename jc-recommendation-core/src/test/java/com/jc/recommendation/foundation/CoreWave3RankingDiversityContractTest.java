package com.jc.recommendation.foundation;

import com.jc.recommendation.diversity.DiversityReranker;
import com.jc.recommendation.model.diversity.DiversityCandidateMetadata;
import com.jc.recommendation.model.diversity.DiversityRerankInput;
import com.jc.recommendation.model.diversity.DiversityRerankStatus;
import com.jc.recommendation.model.diversity.DiversitySelectionReason;
import com.jc.recommendation.model.entity.RecommendationEntityType;
import com.jc.recommendation.model.ranking.RankCandidatesInput;
import com.jc.recommendation.model.ranking.RankedCandidate;
import com.jc.recommendation.model.score.CandidateScoreResult;
import com.jc.recommendation.model.score.CandidateScoreStatus;
import com.jc.recommendation.model.score.ScoreComponentName;
import com.jc.recommendation.model.score.ScoreComponentPolicyVersions;
import com.jc.recommendation.model.score.ScoreCompositionMode;
import com.jc.recommendation.ranking.CandidateRanker;

import java.util.List;

public final class CoreWave3RankingDiversityContractTest {
    private static final String EXACT_CURSOR = "eyJjdXJzb3JWZXJzaW9uIjoicmFua2luZy1jdXJzb3ItdjEiLCJyYW5raW5nU25hcHNob3RJZCI6InNuYXBzaG90LXYxLWJ5dGUtYmFzZWxpbmUiLCJyYW5raW5nUG9saWN5VmVyc2lvbiI6InJhbmtpbmctdjEiLCJzY29yZVBvbGljeVZlcnNpb24iOiJzY29yZS1jb21wb3NpdGlvbi12MSIsImNvbXBvbmVudFBvbGljeVZlcnNpb25zIjp7ImNvbnRleHRfbWF0Y2giOiJjb250ZXh0LW1hdGNoLXYxIiwiaW50ZXJlc3RfbWF0Y2giOiJpbnRlcmVzdC1tYXRjaC12MSIsImZyZXNobmVzcyI6ImZyZXNobmVzcy12MSIsInBvcHVsYXJpdHkiOiJwb3B1bGFyaXR5LXYxIn0sImxhc3RBYnNvbHV0ZVJhbmsiOjEsImxhc3RFbnRpdHlUeXBlIjoicG9zdCIsImxhc3RFbnRpdHlJZCI6InBvc3QtYSJ9";
    private static final ScoreComponentPolicyVersions VERSIONS = new ScoreComponentPolicyVersions(
            "context-match-v1", "interest-match-v1", "freshness-v1", "popularity-v1"
    );
    private static final CandidateRanker RANKER = new CandidateRanker();
    private static final DiversityReranker DIVERSITY = new DiversityReranker();

    private CoreWave3RankingDiversityContractTest() {
    }

    public static void main(String[] args) {
        rankingComparatorAndCursorAreStable();
        rankingPreservesSignedZeroAndInputIndependence();
        rankingResultsRemainImmutable();
        diversityRerankingPreservesIdentityAndProvenance();
        diversityResultsRemainImmutable();
        diversityRejectsIncompleteMetadata();
        System.out.println("Java recommendation core Wave 3 ranking/diversity contract: PASS");
    }

    private static void rankingComparatorAndCursorAreStable() {
        List<CandidateScoreResult> candidates = List.of(
                candidate("post-a", RecommendationEntityType.POST, 0.9, 1.0, 0.0),
                candidate("post-b", RecommendationEntityType.POST, 0.8, 1.0, 0.0),
                candidate("post-c", RecommendationEntityType.POST, 0.7, 1.0, 0.0)
        );
        var page1 = RANKER.rank(input(candidates, "snapshot-v1-byte-baseline", 1, null));
        equal(EXACT_CURSOR, page1.nextCursor(), "exact v1 cursor bytes");
        equal(List.of("post-a"), ids(page1.rankedCandidates()), "page 1 identity");

        var page2 = RANKER.rank(input(
                List.of(candidates.get(2), candidates.get(0), candidates.get(1)),
                "snapshot-v1-byte-baseline", 1, page1.nextCursor()
        ));
        equal(List.of("post-b"), ids(page2.rankedCandidates()), "page 2 identity");
        equal(2, page2.pageStartRank(), "page 2 start rank");
        equal(2, page2.pageEndRank(), "page 2 end rank");

        expectIllegalArgumentContains(() -> RANKER.rank(input(
                candidates, "different-snapshot", 1, page1.nextCursor()
        )), "cursor snapshot mismatch");
    }

    private static void rankingPreservesSignedZeroAndInputIndependence() {
        CandidateScoreResult positive = candidate("B", RecommendationEntityType.POST, 0.0, 1.0, 0.0);
        CandidateScoreResult negative = candidate("A", RecommendationEntityType.POST, -0.0, 1.0, 0.0);

        var first = RANKER.rank(input(List.of(positive, negative), "zero-snapshot", null, null));
        var second = RANKER.rank(input(List.of(negative, positive), "zero-snapshot", null, null));
        equal(List.of("A", "B"), ids(first.rankedCandidates()), "zero tie order");
        equal(ids(first.rankedCandidates()), ids(second.rankedCandidates()), "permutation independence");
        rawBits(0x8000000000000000L, first.rankedCandidates().getFirst().score(), "source negative zero");
        rawBits(0x0000000000000000L, first.rankedCandidates().getFirst().sortKey().score(), "sort key canonical zero");
    }

    private static void rankingResultsRemainImmutable() {
        var result = RANKER.rank(input(List.of(
                candidate("immutable", RecommendationEntityType.POST, 0.5, 1.0, 0.0)
        ), "immutable-snapshot", null, null));
        expectUnsupported(() -> result.rankedCandidates().clear());
        expectUnsupported(() -> result.terminalCandidates().clear());
    }

    private static void diversityRerankingPreservesIdentityAndProvenance() {
        List<CandidateScoreResult> scored = List.of(
                candidate("x1", RecommendationEntityType.POST, 0.9, 1.0, 0.0),
                candidate("x2", RecommendationEntityType.POST, 0.8, 1.0, 0.0),
                candidate("y1", RecommendationEntityType.POST, 0.7, 1.0, 0.0)
        );
        List<RankedCandidate> ranked = RANKER.rank(input(scored, "div-ranking", 30, null)).rankedCandidates();
        var result = DIVERSITY.rerank(new DiversityRerankInput(
                "div-ranking", "div-metadata", "ranking-v1", "score-composition-v1", ranked,
                List.of(
                        metadata("x1", "author:x1", "region:seoul", "theme:cafe", "x"),
                        metadata("x2", "author:x2", "region:seoul", "theme:cafe", "x"),
                        metadata("y1", "author:y1", "region:seoul", "theme:cafe", "y")
                ),
                null
        ));

        equal(DiversityRerankStatus.RERANKED, result.status(), "diversity status");
        equal(List.of("x1", "y1", "x2"),
                result.diversifiedCandidates().stream().map(item -> item.entityId()).toList(),
                "strict diversity order");
        equal(2, result.movedCandidateCount(), "moved count");
        equal(DiversitySelectionReason.STRICT_DIVERSITY,
                result.diversifiedCandidates().get(1).selectionReason(), "strict selection reason");
        equal(3, result.inputCount(), "identity input count");
        equal(3, result.outputCount(), "identity output count");

        List<CandidateScoreResult> zero = List.of(
                candidate("m1", RecommendationEntityType.POST, -0.0, 1.0, 0.0),
                candidate("m2", RecommendationEntityType.POST, 0.0, 1.0, 0.0)
        );
        List<RankedCandidate> zeroRanked = RANKER.rank(input(zero, "zero-div-ranking", 30, null)).rankedCandidates();
        var zeroResult = DIVERSITY.rerank(new DiversityRerankInput(
                "zero-div-ranking", "zero-div-metadata", "ranking-v1", "score-composition-v1", zeroRanked,
                List.of(metadata("m1", null, null, null, null), metadata("m2", null, null, null, null)),
                null
        ));
        var negativeZero = zeroResult.diversifiedCandidates().stream()
                .filter(item -> item.entityId().equals("m1"))
                .findFirst()
                .orElseThrow();
        rawBits(0x8000000000000000L, negativeZero.score(), "diversity signed zero");
        equal(2, zeroResult.missingMetadataCountByDimension().author(), "missing author count");
        equal(2, zeroResult.missingMetadataCountByDimension().duplicateGroup(), "missing duplicate count");
    }

    private static void diversityResultsRemainImmutable() {
        List<RankedCandidate> ranked = RANKER.rank(input(List.of(
                candidate("immutable", RecommendationEntityType.POST, 0.5, 1.0, 0.0)
        ), "immutable-div-ranking", 30, null)).rankedCandidates();
        var result = DIVERSITY.rerank(new DiversityRerankInput(
                "immutable-div-ranking", "immutable-div-metadata", "ranking-v1", "score-composition-v1", ranked,
                List.of(metadata("immutable", null, null, null, null)), null
        ));
        expectUnsupported(() -> result.diversifiedCandidates().clear());
        expectUnsupported(() -> result.diversifiedCandidates().getFirst().appliedRelaxations().clear());
        expectUnsupported(() -> result.diversifiedCandidates().getFirst().violatedDimensionsAtSelection().clear());
    }

    private static void diversityRejectsIncompleteMetadata() {
        List<RankedCandidate> ranked = RANKER.rank(input(List.of(
                candidate("one", RecommendationEntityType.POST, 0.6, 1.0, 0.0),
                candidate("two", RecommendationEntityType.POST, 0.5, 1.0, 0.0)
        ), "coverage-ranking", 30, null)).rankedCandidates();
        expectIllegalArgumentContains(() -> DIVERSITY.rerank(new DiversityRerankInput(
                "coverage-ranking", "coverage-metadata", "ranking-v1", "score-composition-v1", ranked,
                List.of(metadata("one", null, null, null, null)), null
        )), "candidate metadata coverage mismatch");
    }

    private static RankCandidatesInput input(
            List<CandidateScoreResult> candidates,
            String snapshotId,
            Integer limit,
            String cursor
    ) {
        return new RankCandidatesInput(
                snapshotId, "ranking-user", "ranking-context", "score-composition-v1", VERSIONS,
                candidates, limit, cursor, null
        );
    }

    private static CandidateScoreResult candidate(
            String entityId,
            RecommendationEntityType entityType,
            double score,
            double scoredWeight,
            double neutralFilledWeight
    ) {
        return new CandidateScoreResult(
                "ranking-user", "ranking-context", entityId, entityType, CandidateScoreStatus.SCORED,
                score, ScoreCompositionMode.PERSONALIZED_CONTEXTUAL, scoredWeight, neutralFilledWeight,
                List.of(ScoreComponentName.CONTEXT_MATCH, ScoreComponentName.INTEREST_MATCH), List.of(),
                null, null, "score-composition-v1", VERSIONS, List.of()
        );
    }

    private static DiversityCandidateMetadata metadata(
            String entityId,
            String authorId,
            String region,
            String theme,
            String duplicateGroup
    ) {
        return new DiversityCandidateMetadata(
                entityId, RecommendationEntityType.POST, authorId, region, theme, duplicateGroup
        );
    }

    private static List<String> ids(List<RankedCandidate> candidates) {
        return candidates.stream().map(RankedCandidate::entityId).toList();
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
