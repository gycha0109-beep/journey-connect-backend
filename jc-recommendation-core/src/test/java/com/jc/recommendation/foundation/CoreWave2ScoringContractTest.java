package com.jc.recommendation.foundation;

import com.jc.recommendation.context.ContextEligibilityEvaluator;
import com.jc.recommendation.context.ContextMatchCalculator;
import com.jc.recommendation.freshness.FreshnessCalculator;
import com.jc.recommendation.interest.InterestMatchCalculator;
import com.jc.recommendation.model.context.CalculateContextMatchInput;
import com.jc.recommendation.model.context.ContextClause;
import com.jc.recommendation.model.context.ContextClauseEnforcement;
import com.jc.recommendation.model.context.ContextClauseSource;
import com.jc.recommendation.model.context.ContextMatchMode;
import com.jc.recommendation.model.context.ContextSchemaVersion;
import com.jc.recommendation.model.context.ContextScope;
import com.jc.recommendation.model.context.ContextSurface;
import com.jc.recommendation.model.context.EvaluateContextEligibilityInput;
import com.jc.recommendation.model.context.RecommendationContext;
import com.jc.recommendation.model.entity.RecommendationEntityType;
import com.jc.recommendation.model.feature.EntityFeature;
import com.jc.recommendation.model.feature.ExplicitPreference;
import com.jc.recommendation.model.feature.FeatureGroup;
import com.jc.recommendation.model.feature.FeatureSource;
import com.jc.recommendation.model.feature.FeatureValidationStatus;
import com.jc.recommendation.model.feature.PreferenceKind;
import com.jc.recommendation.model.freshness.CalculateFreshnessInput;
import com.jc.recommendation.model.freshness.FreshnessTimestampSource;
import com.jc.recommendation.model.interest.CalculateInterestMatchInput;
import com.jc.recommendation.model.interest.InterestMatchStatus;
import com.jc.recommendation.model.popularity.CalculatePopularityInput;
import com.jc.recommendation.model.popularity.PopularityEngagementSnapshot;
import com.jc.recommendation.model.popularity.PopularityTrustStatus;
import com.jc.recommendation.model.score.ScoreCandidateInput;
import com.jc.recommendation.popularity.PopularityCalculator;
import com.jc.recommendation.scoring.CandidateScorer;

import java.time.Instant;
import java.util.List;

public final class CoreWave2ScoringContractTest {
    private static final String REFERENCE_TIME_TEXT = "2026-07-01T00:00:00Z";
    private static final Instant REFERENCE_TIME = Instant.parse(REFERENCE_TIME_TEXT);

    private CoreWave2ScoringContractTest() {
    }

    public static void main(String[] args) {
        scoringComponentsMatchReferenceBits();
        resultsRemainImmutable();
        referenceHardInterestContractGapIsPinned();
        System.out.println("Java recommendation core Wave 2 scoring contract: PASS");
    }

    private static void scoringComponentsMatchReferenceBits() {
        var interest = scoredInterest("post:1");
        rawBits(0x3fc70a3d70a3d709L, interest.score(), "interest score");
        rawBits(0x3fd1eb851eb851ebL, interest.positiveCoverage(), "interest positive coverage");
        rawBits(0x3fb999999999999aL, interest.negativeCoverage(), "interest negative coverage");

        var freshness = freshness("post:1");
        rawBits(0x3fe6a09e667f3bccL, freshness.score(), "freshness score");
        equal(604_800_000L, freshness.ageMilliseconds(), "freshness age");

        var popularity = popularity("post:1");
        rawBits(0x3fb728d640ac387bL, popularity.score(), "popularity score");
        rawBits(0x3fb825f1ef7cd20bL, popularity.qualityScore(), "popularity quality");

        RecommendationContext context = context();
        List<EntityFeature> features = contextFeatures("post:1");
        var eligibility = new ContextEligibilityEvaluator().evaluate(new EvaluateContextEligibilityInput(
                context, "post:1", RecommendationEntityType.POST, features, REFERENCE_TIME, null, null
        ));
        var contextMatch = new ContextMatchCalculator().calculate(new CalculateContextMatchInput(
                context, eligibility, "post:1", RecommendationEntityType.POST,
                features, REFERENCE_TIME, null, null
        ));
        rawBits(0x3fc9999999999994L, contextMatch.score(), "context score");

        var candidate = new CandidateScorer().score(new ScoreCandidateInput(
                "user:1", "ctx:1", "post:1", RecommendationEntityType.POST,
                eligibility, interest, contextMatch, freshness, popularity, null
        ));
        rawBits(0x3fd084eb25ff77b3L, candidate.score(), "composed score");
        equal(List.of("context_match", "interest_match"),
                candidate.anchorComponents().stream().map(item -> item.wireValue()).toList(),
                "anchor components");
    }

    private static void resultsRemainImmutable() {
        var interest = scoredInterest("post:1");
        expectUnsupported(() -> interest.consideredFeatureIds().add("theme:history"));
        expectUnsupported(() -> interest.breakdown().clear());

        RecommendationContext context = context();
        expectUnsupported(() -> context.clauses().add(context.clauses().getFirst()));
    }

    private static void referenceHardInterestContractGapIsPinned() {
        var hardInterest = InterestMatchCalculator.calculate(new CalculateInterestMatchInput(
                "user:1",
                "post:hard",
                List.of(new ExplicitPreference(
                        "user:1", "activity:hiking", PreferenceKind.AVOID, 1.0,
                        Instant.parse("2026-06-30T00:00:00Z")
                )),
                List.of(),
                List.of(feature("post:hard", "activity:hiking", 0.8, FeatureSource.SYSTEM))
        ));
        equal(InterestMatchStatus.HARD_EXCLUDED, hardInterest.status(), "hard interest status");
        rawBits(0x0000000000000000L, hardInterest.score(), "reference hard interest score");

        RecommendationContext context = context();
        List<EntityFeature> features = contextFeatures("post:hard");
        var eligibility = new ContextEligibilityEvaluator().evaluate(new EvaluateContextEligibilityInput(
                context, "post:hard", RecommendationEntityType.POST, features, REFERENCE_TIME, null, null
        ));
        var contextMatch = new ContextMatchCalculator().calculate(new CalculateContextMatchInput(
                context, eligibility, "post:hard", RecommendationEntityType.POST,
                features, REFERENCE_TIME, null, null
        ));

        expectIllegalArgumentContains(() -> new CandidateScorer().score(new ScoreCandidateInput(
                "user:1", "ctx:1", "post:hard", RecommendationEntityType.POST,
                eligibility, hardInterest, contextMatch,
                freshness("post:hard"), popularity("post:hard"), null
        )), "interest hard_excluded contract invalid");
    }

    private static com.jc.recommendation.model.interest.InterestMatchResult scoredInterest(String entityId) {
        return InterestMatchCalculator.calculate(new CalculateInterestMatchInput(
                "user:1",
                entityId,
                List.of(
                        new ExplicitPreference("user:1", "theme:cafe", PreferenceKind.PREFER, 0.8,
                                Instant.parse("2026-06-30T00:00:00Z")),
                        new ExplicitPreference("user:1", "mood:lively", PreferenceKind.AVOID, 0.4,
                                Instant.parse("2026-06-30T00:00:00Z"))
                ),
                List.of(),
                List.of(
                        feature(entityId, "theme:cafe", 0.7, FeatureSource.BEHAVIOR),
                        feature(entityId, "mood:lively", 0.5, FeatureSource.ADMIN),
                        feature(entityId, "region:seoul", 0.8, FeatureSource.SYSTEM)
                )
        ));
    }

    private static com.jc.recommendation.model.freshness.FreshnessResult freshness(String entityId) {
        return FreshnessCalculator.calculate(new CalculateFreshnessInput(
                entityId, RecommendationEntityType.POST, REFERENCE_TIME_TEXT,
                "2026-06-24T00:00:00Z", FreshnessTimestampSource.PUBLISHED_AT
        ));
    }

    private static com.jc.recommendation.model.popularity.PopularityResult popularity(String entityId) {
        long exposure = 180;
        long like = 22;
        long save = 35;
        long share = 4;
        long accepted = exposure + like + save + share;
        return PopularityCalculator.calculate(new CalculatePopularityInput(
                entityId,
                RecommendationEntityType.POST,
                REFERENCE_TIME_TEXT,
                new PopularityEngagementSnapshot(
                        "snapshot:" + entityId, entityId, RecommendationEntityType.POST,
                        "2026-06-17T00:00:00Z", REFERENCE_TIME_TEXT,
                        exposure, like, save, share, accepted, accepted, 0,
                        PopularityTrustStatus.TRUSTED, "aggregation-v1", "anti-abuse-v1"
                )
        ));
    }

    private static RecommendationContext context() {
        return new RecommendationContext(
                "ctx:1", ContextSurface.HOME_FEED, ContextScope.REQUEST,
                REFERENCE_TIME, null,
                List.of(
                        clause("hard-region", FeatureGroup.REGION, "region:seoul",
                                ContextClauseEnforcement.HARD_REQUIRED, 1.0, ContextClauseSource.EXPLICIT),
                        clause("hard-outdoor", FeatureGroup.ENVIRONMENT, "environment:outdoor",
                                ContextClauseEnforcement.HARD_EXCLUDED, 1.0, ContextClauseSource.VALIDATED_QUERY),
                        clause("soft-cafe", FeatureGroup.THEME, "theme:cafe",
                                ContextClauseEnforcement.SOFT_PREFERRED, 0.8, ContextClauseSource.EXPLICIT),
                        clause("soft-lively", FeatureGroup.MOOD, "mood:lively",
                                ContextClauseEnforcement.SOFT_AVOIDED, 0.4, ContextClauseSource.AI)
                ),
                ContextSchemaVersion.V1
        );
    }

    private static List<EntityFeature> contextFeatures(String entityId) {
        return List.of(
                feature(entityId, "region:seoul", 1.0, FeatureSource.SYSTEM),
                feature(entityId, "environment:indoor", 0.9, FeatureSource.SYSTEM),
                feature(entityId, "theme:cafe", 0.7, FeatureSource.BEHAVIOR),
                feature(entityId, "mood:lively", 0.5, FeatureSource.BEHAVIOR)
        );
    }

    private static ContextClause clause(
            String clauseId,
            FeatureGroup group,
            String featureId,
            ContextClauseEnforcement enforcement,
            double strength,
            ContextClauseSource source
    ) {
        return new ContextClause(
                clauseId, group, List.of(featureId), enforcement, ContextMatchMode.ANY,
                strength, source, FeatureValidationStatus.ACCEPTED
        );
    }

    private static EntityFeature feature(
            String entityId,
            String featureId,
            double weight,
            FeatureSource source
    ) {
        return new EntityFeature(
                entityId, featureId, weight, null, source,
                FeatureValidationStatus.ACCEPTED, Instant.parse("2026-06-30T00:00:00Z")
        );
    }

    private static void expectIllegalArgumentContains(Runnable runnable, String expectedMessagePart) {
        try {
            runnable.run();
            throw new AssertionError("Expected IllegalArgumentException containing: " + expectedMessagePart);
        } catch (IllegalArgumentException exception) {
            if (exception.getMessage() == null || !exception.getMessage().contains(expectedMessagePart)) {
                throw new AssertionError("Exception message did not contain: " + expectedMessagePart, exception);
            }
        }
    }

    private static void expectUnsupported(Runnable runnable) {
        try {
            runnable.run();
            throw new AssertionError("Expected UnsupportedOperationException");
        } catch (UnsupportedOperationException ignored) {
        }
    }

    private static void rawBits(long expectedBits, Double actual, String label) {
        if (actual == null) {
            throw new AssertionError(label + " expected non-null score");
        }
        long actualBits = Double.doubleToRawLongBits(actual);
        if (expectedBits != actualBits) {
            throw new AssertionError(label + " expectedBits=" + Long.toHexString(expectedBits)
                    + " actualBits=" + Long.toHexString(actualBits));
        }
    }

    private static void equal(Object expected, Object actual, String label) {
        if (!java.util.Objects.equals(expected, actual)) {
            throw new AssertionError(label + " expected=" + expected + " actual=" + actual);
        }
    }
}
