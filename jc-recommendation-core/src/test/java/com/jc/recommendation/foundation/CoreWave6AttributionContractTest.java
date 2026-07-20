package com.jc.recommendation.foundation;

import com.jc.recommendation.evaluation.RecommendationBehaviorEventResolver;
import com.jc.recommendation.evaluation.RecommendationOutcomeAttributor;
import com.jc.recommendation.exposure.RecommendationExposureEventBuilder;
import com.jc.recommendation.exposure.RecommendationExposureEventResolver;
import com.jc.recommendation.integration.ExplorationEnabledRanker;
import com.jc.recommendation.model.diversity.DiversityCandidateMetadata;
import com.jc.recommendation.model.entity.RecommendationEntityType;
import com.jc.recommendation.model.evaluation.AttributeRecommendationOutcomesInput;
import com.jc.recommendation.model.evaluation.RecommendationAttributionAuditCategory;
import com.jc.recommendation.model.event.EventSurface;
import com.jc.recommendation.model.event.EventType;
import com.jc.recommendation.model.event.UserBehaviorEvent;
import com.jc.recommendation.model.event.UserBehaviorEventMetadata;
import com.jc.recommendation.model.exposure.BuildRecommendationExposureEventInput;
import com.jc.recommendation.model.integration.RankCandidatesWithExplorationInput;
import com.jc.recommendation.model.score.CandidateScoreResult;
import com.jc.recommendation.model.score.CandidateScoreStatus;
import com.jc.recommendation.model.score.ScoreComponentName;
import com.jc.recommendation.model.score.ScoreComponentPolicyVersions;
import com.jc.recommendation.model.score.ScoreCompositionMode;

import java.util.List;

public final class CoreWave6AttributionContractTest {
    private static final ScoreComponentPolicyVersions VERSIONS = new ScoreComponentPolicyVersions(
            "context-match-v1", "interest-match-v1", "freshness-v1", "popularity-v1"
    );

    public static void main(String[] args) {
        verifiesBehaviorDedupeAndAttributionWindow();
        System.out.println("Java recommendation core Wave 6 behavior and attribution contract: PASS");
    }

    private static void verifiesBehaviorDedupeAndAttributionWindow() {
        CandidateScoreResult scored = new CandidateScoreResult(
                "user", "context", "p1", RecommendationEntityType.POST, CandidateScoreStatus.SCORED,
                0.9d, ScoreCompositionMode.PERSONALIZED_CONTEXTUAL, 1.0d, 0.0d,
                List.of(ScoreComponentName.CONTEXT_MATCH), List.of(), null, null,
                "score-composition-v1", VERSIONS, List.of()
        );
        var ranking = new ExplorationEnabledRanker().rank(new RankCandidatesWithExplorationInput(
                "rank", "meta", "explore", "user", "context", "score-composition-v1", VERSIONS,
                "seed", List.of(scored), List.of(new DiversityCandidateMetadata(
                        "p1", RecommendationEntityType.POST, "author", "region:seoul", "theme:cafe", "dup:p1")),
                List.of(), 1, null, null, null, null
        ));
        var exposure = new RecommendationExposureEventBuilder().build(new BuildRecommendationExposureEventInput(
                "exposure", "exposure-key", "run", "session", EventSurface.HOME,
                "2026-07-18T01:00:00Z", ranking
        ));
        var exposures = new RecommendationExposureEventResolver().resolve(List.of(exposure));
        UserBehaviorEvent click = behavior("click", "click-key", "2026-07-18T01:01:00Z");
        UserBehaviorEvent before = behavior("before", "before-key", "2026-07-18T00:59:00Z");
        var behaviors = new RecommendationBehaviorEventResolver().resolve(List.of(click, before, click));
        check(behaviors.resolvedCount() == 2 && behaviors.duplicateCount() == 1, "behavior dedupe");
        var result = new RecommendationOutcomeAttributor().attribute(new AttributeRecommendationOutcomesInput(
                "case", exposures, behaviors, "2026-07-18T02:00:00Z"
        ));
        check(result.attributedOutcomeEventCount() == 1, "attributed count");
        check(result.clickCount() == 1 && result.associatedOutcomeValue() == 0.5d, "click value");
        check(result.auditCounts().get(RecommendationAttributionAuditCategory.BEFORE_EXPOSURE) == 1,
                "before exposure audit");
        check(result.attributions().getFirst().exposureEventId().equals("exposure"), "exposure binding");
    }

    private static UserBehaviorEvent behavior(String eventId, String key, String occurredAt) {
        return new UserBehaviorEvent(eventId, key, "user", "session", EventType.CLICK, "p1", "run",
                UserBehaviorEventMetadata.empty(), occurredAt);
    }

    private static void check(boolean condition, String label) {
        if (!condition) throw new AssertionError(label);
    }
}
