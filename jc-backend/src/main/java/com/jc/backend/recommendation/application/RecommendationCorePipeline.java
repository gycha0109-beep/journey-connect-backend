package com.jc.backend.recommendation.application;

import com.jc.backend.recommendation.RecommendationCoreCandidate;
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
import com.jc.recommendation.model.diversity.DiversityCandidateMetadata;
import com.jc.recommendation.model.entity.EngagementRawData;
import com.jc.recommendation.model.entity.RecommendationEntityType;
import com.jc.recommendation.model.exploration.ExplorationCandidateMetadata;
import com.jc.recommendation.model.feature.FeatureDefinition;
import com.jc.recommendation.model.feature.FeatureGroup;
import com.jc.recommendation.model.feature.FeatureValidationStatus;
import com.jc.recommendation.model.freshness.CalculateFreshnessInput;
import com.jc.recommendation.model.freshness.FreshnessTimestampSource;
import com.jc.recommendation.model.interest.CalculateInterestMatchInput;
import com.jc.recommendation.model.offline.OfflineEvaluationContracts.CollectedRankingV3Result;
import com.jc.recommendation.model.offline.OfflineEvaluationContracts.RankingV3ReplayInputSnapshot;
import com.jc.recommendation.model.popularity.CalculatePopularityInput;
import com.jc.recommendation.model.popularity.PopularityEngagementSnapshot;
import com.jc.recommendation.model.popularity.PopularityTrustStatus;
import com.jc.recommendation.model.score.CandidateScoreNotApplicableReason;
import com.jc.recommendation.model.score.CandidateScoreResult;
import com.jc.recommendation.model.score.CandidateScoreStatus;
import com.jc.recommendation.model.score.ScoreCandidateInput;
import com.jc.recommendation.offline.RankingV3FullResultCollector;
import com.jc.recommendation.policy.DiversityPolicies;
import com.jc.recommendation.policy.ExplorationPolicies;
import com.jc.recommendation.policy.RankingIntegrationPolicies;
import com.jc.recommendation.policy.ScoringPolicies;
import com.jc.recommendation.popularity.PopularityCalculator;
import com.jc.recommendation.scoring.CandidateScorer;
import com.jc.recommendation.vocabulary.FeatureVocabularyV1;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/** Adapts backend candidates to the frozen Java Core 1.0 orchestration contract. */
@Component
public final class RecommendationCorePipeline {

    private static final long MAX_SAFE_INTEGER = 9_007_199_254_740_991L;

    private final ContextEligibilityEvaluator contextEligibility = new ContextEligibilityEvaluator();
    private final ContextMatchCalculator contextMatch = new ContextMatchCalculator();
    private final CandidateScorer candidateScorer = new CandidateScorer();
    private final RankingV3FullResultCollector collector = new RankingV3FullResultCollector();

    public PipelineResult execute(PipelineRequest request) {
        Objects.requireNonNull(request, "request");
        RecommendationContext context = homeDiscoveryContext(request.contextId(), request.referenceTime());
        List<CandidateScoreResult> scores = request.candidates().stream()
                .map(candidate -> score(request, context, candidate))
                .toList();
        Map<String, RecommendationCoreCandidate> candidateById = request.candidates().stream()
                .collect(Collectors.toUnmodifiableMap(
                        candidate -> candidate.entity().id(), Function.identity()));

        List<DiversityCandidateMetadata> diversityMetadata = scores.stream()
                .filter(score -> score.status() == CandidateScoreStatus.SCORED)
                .map(score -> requireCandidate(candidateById, score.entityId()).diversity())
                .toList();
        List<ExplorationCandidateMetadata> explorationMetadata = scores.stream()
                .filter(score -> score.status() == CandidateScoreStatus.NOT_APPLICABLE)
                .filter(score -> score.notApplicableReason()
                        == CandidateScoreNotApplicableReason.NO_ANCHOR_COMPONENT)
                .map(score -> requireCandidate(candidateById, score.entityId()).exploration())
                .toList();

        RankingV3ReplayInputSnapshot rankingInput = new RankingV3ReplayInputSnapshot(
                request.rankingSnapshotId(),
                request.metadataSnapshotId(),
                request.explorationSnapshotId(),
                Long.toString(request.userId()),
                request.contextId(),
                ScoringPolicies.SCORE_COMPOSITION_V1.policyVersion(),
                ScoringPolicies.SCORE_COMPOSITION_V1.expectedComponentPolicyVersions(),
                request.explorationSeed(),
                scores,
                diversityMetadata,
                explorationMetadata,
                RankingIntegrationPolicies.V3,
                DiversityPolicies.V1,
                ExplorationPolicies.V1);
        CollectedRankingV3Result collected = collector.collect(rankingInput);
        if (!collected.invariantViolations().isEmpty()) {
            throw new IllegalStateException(
                    "Recommendation collector invariant violations: "
                            + collected.invariantViolations());
        }
        return new PipelineResult(rankingInput, collected);
    }

    private CandidateScoreResult score(
            PipelineRequest request,
            RecommendationContext context,
            RecommendationCoreCandidate candidate) {
        String userId = Long.toString(request.userId());
        var entity = candidate.entity();
        var eligibility = contextEligibility.evaluate(new EvaluateContextEligibilityInput(
                context,
                entity.id(),
                entity.entityType(),
                candidate.features(),
                request.referenceTime(),
                null,
                null));
        var contextResult = contextMatch.calculate(new CalculateContextMatchInput(
                context,
                eligibility,
                entity.id(),
                entity.entityType(),
                candidate.features(),
                request.referenceTime(),
                null,
                null));
        var interestResult = InterestMatchCalculator.calculate(new CalculateInterestMatchInput(
                userId,
                entity.id(),
                List.of(),
                List.of(),
                candidate.features()));
        var freshnessResult = FreshnessCalculator.calculate(new CalculateFreshnessInput(
                entity.id(),
                entity.entityType(),
                strictMillis(request.referenceTime()),
                strictMillis(candidate.publishedAt()),
                FreshnessTimestampSource.PUBLISHED_AT));
        var popularityResult = PopularityCalculator.calculate(new CalculatePopularityInput(
                entity.id(),
                entity.entityType(),
                strictMillis(request.referenceTime()),
                popularitySnapshot(candidate, request.referenceTime())));
        return candidateScorer.score(new ScoreCandidateInput(
                userId,
                request.contextId(),
                entity.id(),
                entity.entityType(),
                eligibility,
                interestResult,
                contextResult,
                freshnessResult,
                popularityResult,
                null));
    }

    private RecommendationContext homeDiscoveryContext(String contextId, Instant referenceTime) {
        List<String> regionFeatures = FeatureVocabularyV1.getFeaturesByGroup(FeatureGroup.REGION)
                .stream().map(FeatureDefinition::id).toList();
        List<String> themeFeatures = FeatureVocabularyV1.getFeaturesByGroup(FeatureGroup.THEME)
                .stream().map(FeatureDefinition::id).toList();
        List<ContextClause> clauses = new ArrayList<>();
        clauses.add(new ContextClause(
                "system-home-supported-region",
                FeatureGroup.REGION,
                regionFeatures,
                ContextClauseEnforcement.SOFT_PREFERRED,
                ContextMatchMode.ANY,
                1.0,
                ContextClauseSource.SYSTEM,
                FeatureValidationStatus.ACCEPTED));
        clauses.add(new ContextClause(
                "system-home-supported-theme",
                FeatureGroup.THEME,
                themeFeatures,
                ContextClauseEnforcement.SOFT_PREFERRED,
                ContextMatchMode.ANY,
                1.0,
                ContextClauseSource.SYSTEM,
                FeatureValidationStatus.ACCEPTED));
        return new RecommendationContext(
                contextId,
                ContextSurface.HOME_FEED,
                ContextScope.REQUEST,
                referenceTime,
                null,
                clauses,
                ContextSchemaVersion.V1);
    }

    private PopularityEngagementSnapshot popularitySnapshot(
            RecommendationCoreCandidate candidate,
            Instant referenceTime) {
        EngagementRawData engagement = candidate.entity().engagement();
        long views = safeCount(engagement == null ? 0.0 : engagement.viewCount());
        long likes = safeCount(engagement == null ? 0.0 : engagement.likeCount());
        long saves = safeCount(engagement == null ? 0.0 : engagement.saveCount());
        long shares = safeCount(engagement == null ? 0.0 : engagement.shareCount());
        long exposure = Math.max(Math.max(views, likes), Math.max(saves, shares));
        long accepted = cappedAdd(exposure, cappedAdd(likes, cappedAdd(saves, shares)));
        int windowDays = ScoringPolicies.POPULARITY_V1.windowDaysByEntityType()
                .getOrDefault(RecommendationEntityType.POST, 14);
        Instant windowStart = referenceTime.minus(windowDays, ChronoUnit.DAYS);
        return new PopularityEngagementSnapshot(
                "runtime-popularity:" + candidate.entity().id() + ":" + referenceTime.toEpochMilli(),
                candidate.entity().id(),
                candidate.entity().entityType(),
                strictMillis(windowStart),
                strictMillis(referenceTime),
                exposure,
                Math.min(likes, exposure),
                Math.min(saves, exposure),
                Math.min(shares, exposure),
                accepted,
                accepted,
                0,
                PopularityTrustStatus.PARTIAL,
                "backend-counter-projection-v1",
                "unverified-unique-actors-v1");
    }

    private static RecommendationCoreCandidate requireCandidate(
            Map<String, RecommendationCoreCandidate> candidates,
            String entityId) {
        RecommendationCoreCandidate candidate = candidates.get(entityId);
        if (candidate == null) {
            throw new IllegalStateException("Core score references unknown candidate " + entityId);
        }
        return candidate;
    }

    private static long safeCount(double value) {
        if (!Double.isFinite(value) || value < 0.0) {
            throw new IllegalArgumentException("Recommendation engagement count must be finite and nonnegative");
        }
        return Math.min(Math.round(value), MAX_SAFE_INTEGER);
    }

    private static long cappedAdd(long left, long right) {
        if (left >= MAX_SAFE_INTEGER - right) {
            return MAX_SAFE_INTEGER;
        }
        return left + right;
    }

    private static String strictMillis(Instant value) {
        Instant millis = value.truncatedTo(ChronoUnit.MILLIS);
        String text = millis.toString();
        if (!text.contains(".")) {
            return text.substring(0, text.length() - 1) + ".000Z";
        }
        int dot = text.indexOf('.');
        int z = text.length() - 1;
        String fraction = text.substring(dot + 1, z);
        String padded = (fraction + "000").substring(0, 3);
        return text.substring(0, dot) + "." + padded + "Z";
    }

    public record PipelineRequest(
            long userId,
            String contextId,
            Instant referenceTime,
            String rankingSnapshotId,
            String metadataSnapshotId,
            String explorationSnapshotId,
            String explorationSeed,
            List<RecommendationCoreCandidate> candidates) {

        public PipelineRequest {
            if (userId <= 0) {
                throw new IllegalArgumentException("userId must be positive");
            }
            Objects.requireNonNull(contextId, "contextId");
            Objects.requireNonNull(referenceTime, "referenceTime");
            Objects.requireNonNull(rankingSnapshotId, "rankingSnapshotId");
            Objects.requireNonNull(metadataSnapshotId, "metadataSnapshotId");
            Objects.requireNonNull(explorationSnapshotId, "explorationSnapshotId");
            Objects.requireNonNull(explorationSeed, "explorationSeed");
            candidates = List.copyOf(Objects.requireNonNull(candidates, "candidates"));
        }
    }

    public record PipelineResult(
            RankingV3ReplayInputSnapshot rankingInput,
            CollectedRankingV3Result collected) {
        public PipelineResult {
            Objects.requireNonNull(rankingInput, "rankingInput");
            Objects.requireNonNull(collected, "collected");
        }
    }
}
