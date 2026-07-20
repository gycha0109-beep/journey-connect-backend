package com.jc.recommendation.policy;

import com.jc.recommendation.model.context.ContextClauseSource;
import com.jc.recommendation.model.context.ContextSchemaVersion;
import com.jc.recommendation.model.entity.RecommendationEntityType;
import com.jc.recommendation.model.feature.FeatureGroup;
import com.jc.recommendation.model.feature.FeatureSource;
import com.jc.recommendation.model.freshness.FreshnessTimestampSource;
import com.jc.recommendation.model.score.ScoreComponentName;
import com.jc.recommendation.model.score.ScoreComponentPolicyVersions;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class ScoringPolicies {
    private static final Instant EFFECTIVE_FROM = Instant.parse("2026-07-01T00:00:00Z");
    private static final List<RecommendationEntityType> RANKABLE_ENTITY_TYPES = List.of(
            RecommendationEntityType.POST,
            RecommendationEntityType.JOURNEY,
            RecommendationEntityType.PLACE,
            RecommendationEntityType.CREW
    );
    private static final List<FeatureGroup> ALL_FEATURE_GROUPS = List.of(
            FeatureGroup.REGION,
            FeatureGroup.THEME,
            FeatureGroup.PACE,
            FeatureGroup.BUDGET,
            FeatureGroup.COMPANION,
            FeatureGroup.ENVIRONMENT,
            FeatureGroup.TRANSPORT,
            FeatureGroup.TIME,
            FeatureGroup.MOOD,
            FeatureGroup.ACTIVITY
    );

    public static final InterestMatchPolicy INTEREST_MATCH_V1 = new InterestMatchPolicy(
            "interest-match-v1",
            EFFECTIVE_FROM,
            0.8,
            List.of(FeatureSource.EXPLICIT),
            true,
            0.0,
            1.0
    );

    public static final FreshnessPolicy FRESHNESS_V1 = new FreshnessPolicy(
            "freshness-v1",
            EFFECTIVE_FROM,
            List.of(RecommendationEntityType.POST, RecommendationEntityType.JOURNEY),
            linkedMap(
                    RecommendationEntityType.POST, 14.0,
                    RecommendationEntityType.JOURNEY, 30.0
            ),
            0.0,
            1.0,
            86_400_000L,
            List.of(
                    FreshnessTimestampSource.PUBLISHED_AT,
                    FreshnessTimestampSource.CREATED_AT,
                    FreshnessTimestampSource.MEANINGFUL_UPDATED_AT
            )
    );

    public static final PopularityPolicy POPULARITY_V1 = new PopularityPolicy(
            "popularity-v1",
            EFFECTIVE_FROM,
            List.of(RecommendationEntityType.POST, RecommendationEntityType.JOURNEY),
            linkedMap(
                    RecommendationEntityType.POST, 14,
                    RecommendationEntityType.JOURNEY, 30
            ),
            20,
            1.96,
            new PopularitySignalWeights(0.25, 0.5, 0.25),
            linkedMap(
                    RecommendationEntityType.POST, 500,
                    RecommendationEntityType.JOURNEY, 200
            ),
            0.75,
            0.25,
            0.0,
            1.0,
            86_400_000L
    );

    public static final ContextPolicy CONTEXT_MATCH_V1 = new ContextPolicy(
            "context-match-v1",
            EFFECTIVE_FROM,
            ContextSchemaVersion.V1,
            RANKABLE_ENTITY_TYPES,
            List.of(FeatureGroup.REGION, FeatureGroup.ENVIRONMENT, FeatureGroup.TRANSPORT),
            ALL_FEATURE_GROUPS,
            List.of(ContextClauseSource.EXPLICIT, ContextClauseSource.VALIDATED_QUERY),
            List.of(FeatureSource.EXPLICIT, FeatureSource.ADMIN, FeatureSource.SYSTEM),
            0.5,
            ALL_FEATURE_GROUPS,
            List.of(
                    ContextClauseSource.EXPLICIT,
                    ContextClauseSource.VALIDATED_QUERY,
                    ContextClauseSource.SYSTEM,
                    ContextClauseSource.AI
            ),
            List.of(
                    FeatureSource.EXPLICIT,
                    FeatureSource.ADMIN,
                    FeatureSource.SYSTEM,
                    FeatureSource.BEHAVIOR,
                    FeatureSource.AI
            ),
            true,
            86_400_000L,
            86_400_000L,
            0.0,
            1.0
    );

    public static final ScoreCompositionPolicy SCORE_COMPOSITION_V1 = new ScoreCompositionPolicy(
            "score-composition-v1",
            EFFECTIVE_FROM,
            RANKABLE_ENTITY_TYPES,
            List.of(
                    ScoreComponentName.CONTEXT_MATCH,
                    ScoreComponentName.INTEREST_MATCH,
                    ScoreComponentName.FRESHNESS,
                    ScoreComponentName.POPULARITY
            ),
            linkedMap(
                    ScoreComponentName.CONTEXT_MATCH, 0.40,
                    ScoreComponentName.INTEREST_MATCH, 0.35,
                    ScoreComponentName.FRESHNESS, 0.15,
                    ScoreComponentName.POPULARITY, 0.10
            ),
            entityEligibility(),
            List.of(ScoreComponentName.CONTEXT_MATCH, ScoreComponentName.INTEREST_MATCH),
            new ScoreComponentPolicyVersions(
                    "context-match-v1",
                    "interest-match-v1",
                    "freshness-v1",
                    "popularity-v1"
            ),
            0.5,
            0.0,
            1.0
    );

    private ScoringPolicies() {
    }

    private static Map<RecommendationEntityType, List<ScoreComponentName>> entityEligibility() {
        Map<RecommendationEntityType, List<ScoreComponentName>> result = new LinkedHashMap<>();
        List<ScoreComponentName> allComponents = List.of(
                ScoreComponentName.CONTEXT_MATCH,
                ScoreComponentName.INTEREST_MATCH,
                ScoreComponentName.FRESHNESS,
                ScoreComponentName.POPULARITY
        );
        List<ScoreComponentName> anchorOnly = List.of(
                ScoreComponentName.CONTEXT_MATCH,
                ScoreComponentName.INTEREST_MATCH
        );
        result.put(RecommendationEntityType.POST, allComponents);
        result.put(RecommendationEntityType.JOURNEY, allComponents);
        result.put(RecommendationEntityType.PLACE, anchorOnly);
        result.put(RecommendationEntityType.CREW, anchorOnly);
        return result;
    }

    private static <K, V> Map<K, V> linkedMap(K key1, V value1, K key2, V value2) {
        Map<K, V> result = new LinkedHashMap<>();
        result.put(key1, value1);
        result.put(key2, value2);
        return result;
    }

    private static <K, V> Map<K, V> linkedMap(
            K key1,
            V value1,
            K key2,
            V value2,
            K key3,
            V value3,
            K key4,
            V value4
    ) {
        Map<K, V> result = new LinkedHashMap<>();
        result.put(key1, value1);
        result.put(key2, value2);
        result.put(key3, value3);
        result.put(key4, value4);
        return result;
    }
}
