package com.jc.recommendation.foundation;

import com.jc.recommendation.canonical.CanonicalJson;
import com.jc.recommendation.model.entity.RecommendationEntityType;
import com.jc.recommendation.model.ranking.RankCandidatesInput;
import com.jc.recommendation.model.ranking.RankCandidatesResult;
import com.jc.recommendation.model.score.CandidateScoreHardExclusionReason;
import com.jc.recommendation.model.score.CandidateScoreNotApplicableReason;
import com.jc.recommendation.model.score.CandidateScoreResult;
import com.jc.recommendation.model.score.CandidateScoreStatus;
import com.jc.recommendation.model.score.ScoreComponentName;
import com.jc.recommendation.model.score.ScoreComponentPolicyVersions;
import com.jc.recommendation.model.score.ScoreCompositionMode;
import com.jc.recommendation.policy.RankingPolicies;
import com.jc.recommendation.ranking.CandidateRanker;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class CoreWave3BaseRankingGoldenOracle {
    private static final ScoreComponentPolicyVersions VERSIONS = new ScoreComponentPolicyVersions(
            "context-match-v1", "interest-match-v1", "freshness-v1", "popularity-v1"
    );
    private final CandidateRanker ranker = new CandidateRanker();
    private final List<Map<String, Object>> records = new ArrayList<>();

    public static void main(String[] args) {
        new CoreWave3BaseRankingGoldenOracle().run();
    }

    private void run() {
        var policy = RankingPolicies.V1;
        emit("POLICY", policy.policyVersion(), policy.effectiveFrom().toString(), policy.expectedScorePolicyVersion(),
                Integer.toString(policy.maxInputCandidates()), Integer.toString(policy.defaultResultLimit()),
                Integer.toString(policy.hardResultLimit()), wireTypes(policy.eligibleEntityTypes()),
                wireTypes(policy.entityTypeOrder()), policy.cursorVersion());

        emitResult("ordered", ranker.rank(input(List.of(
                candidate("B", RecommendationEntityType.POST, 0.7, 1.0, 0.0),
                candidate("A", RecommendationEntityType.POST, Double.parseDouble("0.8000000000000001"), 1.0, 0.0),
                candidate("C", RecommendationEntityType.POST, 0.8, 1.0, 0.0)
        ), "ranking-snapshot-1", null, null)));
        emitResult("tie", ranker.rank(input(List.of(
                candidate("post-10", RecommendationEntityType.POST, 0.5, 0.8, 0.2),
                candidate("crew-01", RecommendationEntityType.CREW, 0.5, 1.0, 0.0),
                candidate("place-01", RecommendationEntityType.PLACE, 0.5, 1.0, 0.0),
                candidate("journey-01", RecommendationEntityType.JOURNEY, 0.5, 1.0, 0.0),
                candidate("post-02", RecommendationEntityType.POST, 0.5, 1.0, 0.0)
        ), "ranking-snapshot-1", null, null)));
        emitResult("zero-terminal", ranker.rank(input(List.of(
                candidate("B", RecommendationEntityType.POST, 0.0, 1.0, 0.0),
                terminal("z", RecommendationEntityType.POST, CandidateScoreStatus.NOT_APPLICABLE),
                candidate("A", RecommendationEntityType.POST, -0.0, 1.0, 0.0),
                terminal("a", RecommendationEntityType.JOURNEY, CandidateScoreStatus.HARD_EXCLUDED)
        ), "ranking-snapshot-1", null, null)));

        List<CandidateScoreResult> pageCandidates = List.of(
                candidate("post-a", RecommendationEntityType.POST, 0.9, 1.0, 0.0),
                candidate("post-b", RecommendationEntityType.POST, 0.8, 1.0, 0.0),
                candidate("post-c", RecommendationEntityType.POST, 0.7, 1.0, 0.0)
        );
        RankCandidatesResult page1 = ranker.rank(input(
                pageCandidates, "snapshot-v1-byte-baseline", 1, null
        ));
        emitResult("page1", page1);
        emitResult("page2", ranker.rank(input(
                List.of(pageCandidates.get(2), pageCandidates.get(0), pageCandidates.get(1)),
                "snapshot-v1-byte-baseline", 1, page1.nextCursor()
        )));
        emitResult("empty", ranker.rank(input(List.of(
                terminal("x", RecommendationEntityType.POST, CandidateScoreStatus.NOT_APPLICABLE)
        ), "ranking-snapshot-1", null, null)));

        Map<String, Object> document = new LinkedHashMap<>();
        document.put("fixtureVersion", "wave3-base-ranking-v1");
        document.put("referencePackage", "yeojeong-personalization-phase2-9b-offline-evaluation@0.1.0");
        document.put("records", records);
        try {
            System.out.write((CanonicalJson.stringify(document) + "\n").getBytes(StandardCharsets.UTF_8));
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to write Wave 3 base ranking golden", exception);
        }
    }

    private void emitResult(String label, RankCandidatesResult result) {
        emit("RESULT", label, result.status().wireValue(), nullable(result.emptyReason() == null ? null : result.emptyReason().wireValue()),
                result.policyVersion(), result.scorePolicyVersion(), Integer.toString(result.inputCount()),
                Integer.toString(result.scoredCandidateCount()), Integer.toString(result.terminalCandidateCount()),
                nullable(result.requestedLimit()), Integer.toString(result.effectiveLimit()),
                nullable(result.pageStartRank()), nullable(result.pageEndRank()), Boolean.toString(result.hasNextPage()),
                nullable(result.nextCursor()));
        result.rankedCandidates().forEach(item -> emit("RANKED", label, Integer.toString(item.absoluteRank()), item.entityId(),
                item.entityType().wireValue(), bits(item.score()), bits(item.scoredWeight()), bits(item.neutralFilledWeight()),
                item.compositionMode().wireValue(), item.scorePolicyVersion(), bits(item.sortKey().score()),
                bits(item.sortKey().neutralFilledWeight()), Integer.toString(item.sortKey().entityTypeRank()),
                item.sortKey().entityId()));
        result.terminalCandidates().forEach(item -> emit("TERMINAL", label, item.entityId(), item.entityType().wireValue(),
                item.status().wireValue(), nullable(item.notApplicableReason() == null ? null : item.notApplicableReason().wireValue()),
                nullable(item.hardExclusionReason() == null ? null : item.hardExclusionReason().wireValue()),
                item.scorePolicyVersion()));
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
            double neutralWeight
    ) {
        return new CandidateScoreResult(
                "ranking-user", "ranking-context", entityId, entityType, CandidateScoreStatus.SCORED,
                score, ScoreCompositionMode.PERSONALIZED_CONTEXTUAL, scoredWeight, neutralWeight,
                List.of(ScoreComponentName.CONTEXT_MATCH, ScoreComponentName.INTEREST_MATCH), List.of(),
                null, null, "score-composition-v1", VERSIONS, List.of()
        );
    }

    private static CandidateScoreResult terminal(
            String entityId,
            RecommendationEntityType entityType,
            CandidateScoreStatus status
    ) {
        return new CandidateScoreResult(
                "ranking-user", "ranking-context", entityId, entityType, status,
                null, null, null, null, List.of(),
                status == CandidateScoreStatus.HARD_EXCLUDED ? List.of(ScoreComponentName.INTEREST_MATCH) : List.of(),
                status == CandidateScoreStatus.NOT_APPLICABLE ? CandidateScoreNotApplicableReason.NO_ANCHOR_COMPONENT : null,
                status == CandidateScoreStatus.HARD_EXCLUDED ? CandidateScoreHardExclusionReason.INTEREST_HARD_EXCLUSION : null,
                "score-composition-v1", VERSIONS, List.of()
        );
    }

    private void emit(String kind, String... fields) {
        Map<String, Object> record = new LinkedHashMap<>();
        record.put("kind", kind);
        record.put("fields", List.of(fields));
        records.add(record);
    }

    private static String bits(double value) {
        return String.format(Locale.ROOT, "%016x", Double.doubleToRawLongBits(value));
    }

    private static String nullable(Object value) {
        return value == null ? "null" : String.valueOf(value);
    }

    private static String wireTypes(List<RecommendationEntityType> values) {
        return String.join(",", values.stream().map(RecommendationEntityType::wireValue).toList());
    }
}
