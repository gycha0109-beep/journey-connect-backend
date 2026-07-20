package com.jc.recommendation.foundation;

import com.jc.recommendation.canonical.CanonicalJson;
import com.jc.recommendation.context.ContextEligibilityEvaluator;
import com.jc.recommendation.context.ContextMatchCalculator;
import com.jc.recommendation.freshness.FreshnessCalculator;
import com.jc.recommendation.interest.InterestMatchCalculator;
import com.jc.recommendation.model.context.CalculateContextMatchInput;
import com.jc.recommendation.model.context.ContextClause;
import com.jc.recommendation.model.context.ContextClauseEnforcement;
import com.jc.recommendation.model.context.ContextClauseSource;
import com.jc.recommendation.model.context.ContextEligibilityResult;
import com.jc.recommendation.model.context.ContextMatchMode;
import com.jc.recommendation.model.context.ContextMatchResult;
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
import com.jc.recommendation.model.freshness.FreshnessResult;
import com.jc.recommendation.model.freshness.FreshnessTimestampSource;
import com.jc.recommendation.model.interest.CalculateInterestMatchInput;
import com.jc.recommendation.model.interest.InterestMatchResult;
import com.jc.recommendation.model.popularity.CalculatePopularityInput;
import com.jc.recommendation.model.popularity.PopularityEngagementSnapshot;
import com.jc.recommendation.model.popularity.PopularityResult;
import com.jc.recommendation.model.popularity.PopularityTrustStatus;
import com.jc.recommendation.model.score.CandidateScoreResult;
import com.jc.recommendation.model.score.ScoreCandidateInput;
import com.jc.recommendation.popularity.PopularityCalculator;
import com.jc.recommendation.scoring.CandidateScorer;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class CoreWave2ScoringGoldenOracle {
    private static final String REFERENCE_TIME_TEXT = "2026-07-01T00:00:00Z";
    private static final Instant REFERENCE_TIME = Instant.parse(REFERENCE_TIME_TEXT);

    private final List<Map<String, Object>> records = new ArrayList<>();
    private final ContextEligibilityEvaluator eligibilityEvaluator = new ContextEligibilityEvaluator();
    private final ContextMatchCalculator contextCalculator = new ContextMatchCalculator();
    private final CandidateScorer candidateScorer = new CandidateScorer();

    public static void main(String[] args) {
        new CoreWave2ScoringGoldenOracle().run();
    }

    private void run() {
        InterestMatchResult scoredInterest = interestScenarios();
        FreshnessResult freshnessPost7 = freshnessScenarios();
        PopularityResult popularityScored = popularityScenarios();
        ContextScenario contextScenario = contextScenarios();
        scoreScenarios(scoredInterest, freshnessPost7, popularityScored, contextScenario);

        Map<String, Object> document = new LinkedHashMap<>();
        document.put("fixtureVersion", "wave2-scoring-v1");
        document.put("referencePackage", "yeojeong-personalization-phase2-9b-offline-evaluation@0.1.0");
        document.put("records", records);
        try {
            System.out.write((CanonicalJson.stringify(document) + "\n").getBytes(StandardCharsets.UTF_8));
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to write Wave 2 golden oracle", exception);
        }
    }

    private InterestMatchResult interestScenarios() {
        InterestMatchResult scored = InterestMatchCalculator.calculate(new CalculateInterestMatchInput(
                "user:1",
                "post:1",
                List.of(
                        preference("user:1", "theme:cafe", PreferenceKind.PREFER, 0.8),
                        preference("user:1", "mood:lively", PreferenceKind.AVOID, 0.4)
                ),
                List.of(),
                List.of(
                        feature("post:1", "theme:cafe", 0.7, FeatureSource.BEHAVIOR),
                        feature("post:1", "mood:lively", 0.5, FeatureSource.ADMIN),
                        feature("post:1", "region:seoul", 0.8, FeatureSource.SYSTEM)
                )
        ));
        emitInterest("scored", scored);

        InterestMatchResult hard = InterestMatchCalculator.calculate(new CalculateInterestMatchInput(
                "user:1",
                "post:hard",
                List.of(preference("user:1", "activity:hiking", PreferenceKind.AVOID, 1.0)),
                List.of(),
                List.of(feature("post:hard", "activity:hiking", 0.8, FeatureSource.SYSTEM))
        ));
        emitInterest("hard", hard);

        InterestMatchResult empty = InterestMatchCalculator.calculate(new CalculateInterestMatchInput(
                "user:1",
                "post:empty",
                List.of(),
                List.of(),
                List.of(feature("post:empty", "theme:cafe", 0.7, FeatureSource.SYSTEM))
        ));
        emitInterest("empty", empty);
        return scored;
    }

    private FreshnessResult freshnessScenarios() {
        FreshnessResult post7 = FreshnessCalculator.calculate(new CalculateFreshnessInput(
                "post:1", RecommendationEntityType.POST, REFERENCE_TIME_TEXT,
                "2026-06-24T00:00:00Z", FreshnessTimestampSource.PUBLISHED_AT
        ));
        emitFreshness("post7", post7);
        emitFreshness("post14", FreshnessCalculator.calculate(new CalculateFreshnessInput(
                "post:14", RecommendationEntityType.POST, REFERENCE_TIME_TEXT,
                "2026-06-17T00:00:00Z", FreshnessTimestampSource.CREATED_AT
        )));
        emitFreshness("journey30", FreshnessCalculator.calculate(new CalculateFreshnessInput(
                "journey:30", RecommendationEntityType.JOURNEY, REFERENCE_TIME_TEXT,
                "2026-06-01T00:00:00Z", FreshnessTimestampSource.MEANINGFUL_UPDATED_AT
        )));
        emitFreshness("place", FreshnessCalculator.calculate(new CalculateFreshnessInput(
                "place:1", RecommendationEntityType.PLACE, REFERENCE_TIME_TEXT, null, null
        )));
        emitFreshness("missing", FreshnessCalculator.calculate(new CalculateFreshnessInput(
                "post:missing", RecommendationEntityType.POST, REFERENCE_TIME_TEXT, null, null
        )));
        return post7;
    }

    private PopularityResult popularityScenarios() {
        PopularityResult scored = PopularityCalculator.calculate(new CalculatePopularityInput(
                "post:1", RecommendationEntityType.POST, REFERENCE_TIME_TEXT,
                snapshot("post:1", 180, 22, 35, 4, PopularityTrustStatus.TRUSTED)
        ));
        emitPopularity("scored", scored);
        emitPopularity("insufficient", PopularityCalculator.calculate(new CalculatePopularityInput(
                "post:19", RecommendationEntityType.POST, REFERENCE_TIME_TEXT,
                snapshot("post:19", 19, 10, 5, 1, PopularityTrustStatus.TRUSTED)
        )));
        emitPopularity("untrusted", PopularityCalculator.calculate(new CalculatePopularityInput(
                "post:rejected", RecommendationEntityType.POST, REFERENCE_TIME_TEXT,
                snapshot("post:rejected", 180, 22, 35, 4, PopularityTrustStatus.REJECTED)
        )));
        emitPopularity("missing", PopularityCalculator.calculate(new CalculatePopularityInput(
                "post:missing", RecommendationEntityType.POST, REFERENCE_TIME_TEXT, null
        )));
        emitPopularity("place", PopularityCalculator.calculate(new CalculatePopularityInput(
                "place:1", RecommendationEntityType.PLACE, REFERENCE_TIME_TEXT, null
        )));
        return scored;
    }

    private ContextScenario contextScenarios() {
        List<ContextClause> clauses = clauses();
        RecommendationContext context = context("ctx:1", clauses);
        List<EntityFeature> features = List.of(
                feature("post:1", "region:seoul", 1.0, FeatureSource.SYSTEM),
                feature("post:1", "environment:indoor", 0.9, FeatureSource.SYSTEM),
                feature("post:1", "theme:cafe", 0.7, FeatureSource.BEHAVIOR),
                feature("post:1", "mood:lively", 0.5, FeatureSource.BEHAVIOR)
        );
        ContextEligibilityResult eligibility = eligibility(context, "post:1", features);
        ContextMatchResult match = contextMatch(context, eligibility, "post:1", features);
        emitEligibility("eligible", eligibility);
        emitContextMatch("scored", match);

        List<EntityFeature> hardFeatures = List.of(
                feature("post:hardctx", "region:seoul", 1.0, FeatureSource.SYSTEM),
                feature("post:hardctx", "environment:outdoor", 0.9, FeatureSource.SYSTEM)
        );
        ContextEligibilityResult hardEligibility = eligibility(context, "post:hardctx", hardFeatures);
        ContextMatchResult hardMatch = contextMatch(context, hardEligibility, "post:hardctx", hardFeatures);
        emitEligibility("hard", hardEligibility);
        emitContextMatch("hard", hardMatch);

        List<ContextClause> softClauses = clauses.stream().filter(clause -> clause.enforcement().isSoft()).toList();
        RecommendationContext softContext = context("ctx:soft", softClauses);
        ContextEligibilityResult softEligibility = eligibility(softContext, "post:1", features);
        ContextMatchResult softMatch = contextMatch(softContext, softEligibility, "post:1", features);
        emitEligibility("soft-only", softEligibility);
        emitContextMatch("soft-only", softMatch);

        return new ContextScenario(context, eligibility, match, hardFeatures, hardEligibility, hardMatch);
    }

    private void scoreScenarios(
            InterestMatchResult scoredInterest,
            FreshnessResult freshnessPost7,
            PopularityResult popularityScored,
            ContextScenario scenario
    ) {
        CandidateScoreResult composed = candidateScorer.score(new ScoreCandidateInput(
                "user:1", "ctx:1", "post:1", RecommendationEntityType.POST,
                scenario.eligibility(), scoredInterest, scenario.match(), freshnessPost7, popularityScored, null
        ));
        emitScore("composed", composed);

        PopularityResult insufficient = PopularityCalculator.calculate(new CalculatePopularityInput(
                "post:1", RecommendationEntityType.POST, REFERENCE_TIME_TEXT,
                snapshot("post:1", 19, 10, 5, 1, PopularityTrustStatus.TRUSTED)
        ));
        emitScore("neutral-popularity", candidateScorer.score(new ScoreCandidateInput(
                "user:1", "ctx:1", "post:1", RecommendationEntityType.POST,
                scenario.eligibility(), scoredInterest, scenario.match(), freshnessPost7, insufficient, null
        )));

        InterestMatchResult hardEntityInterest = InterestMatchCalculator.calculate(new CalculateInterestMatchInput(
                "user:1",
                "post:hardctx",
                List.of(preference("user:1", "region:seoul", PreferenceKind.PREFER, 1.0)),
                List.of(),
                scenario.hardFeatures()
        ));
        FreshnessResult hardFreshness = FreshnessCalculator.calculate(new CalculateFreshnessInput(
                "post:hardctx", RecommendationEntityType.POST, REFERENCE_TIME_TEXT,
                "2026-06-24T00:00:00Z", FreshnessTimestampSource.PUBLISHED_AT
        ));
        PopularityResult hardPopularity = PopularityCalculator.calculate(new CalculatePopularityInput(
                "post:hardctx", RecommendationEntityType.POST, REFERENCE_TIME_TEXT,
                snapshot("post:hardctx", 180, 22, 35, 4, PopularityTrustStatus.TRUSTED)
        ));
        emitScore("context-hard", candidateScorer.score(new ScoreCandidateInput(
                "user:1", "ctx:1", "post:hardctx", RecommendationEntityType.POST,
                scenario.hardEligibility(), hardEntityInterest, scenario.hardMatch(),
                hardFreshness, hardPopularity, null
        )));
    }

    private ContextEligibilityResult eligibility(
            RecommendationContext context,
            String entityId,
            List<EntityFeature> features
    ) {
        return eligibilityEvaluator.evaluate(new EvaluateContextEligibilityInput(
                context, entityId, RecommendationEntityType.POST, features, REFERENCE_TIME, null, null
        ));
    }

    private ContextMatchResult contextMatch(
            RecommendationContext context,
            ContextEligibilityResult eligibility,
            String entityId,
            List<EntityFeature> features
    ) {
        return contextCalculator.calculate(new CalculateContextMatchInput(
                context, eligibility, entityId, RecommendationEntityType.POST,
                features, REFERENCE_TIME, null, null
        ));
    }

    private static RecommendationContext context(String contextId, List<ContextClause> clauses) {
        return new RecommendationContext(
                contextId, ContextSurface.HOME_FEED, ContextScope.REQUEST,
                REFERENCE_TIME, null, clauses, ContextSchemaVersion.V1
        );
    }

    private static List<ContextClause> clauses() {
        return List.of(
                clause("hard-region", FeatureGroup.REGION, "region:seoul",
                        ContextClauseEnforcement.HARD_REQUIRED, 1.0, ContextClauseSource.EXPLICIT),
                clause("hard-outdoor", FeatureGroup.ENVIRONMENT, "environment:outdoor",
                        ContextClauseEnforcement.HARD_EXCLUDED, 1.0, ContextClauseSource.VALIDATED_QUERY),
                clause("soft-cafe", FeatureGroup.THEME, "theme:cafe",
                        ContextClauseEnforcement.SOFT_PREFERRED, 0.8, ContextClauseSource.EXPLICIT),
                clause("soft-lively", FeatureGroup.MOOD, "mood:lively",
                        ContextClauseEnforcement.SOFT_AVOIDED, 0.4, ContextClauseSource.AI)
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

    private static ExplicitPreference preference(
            String userId,
            String featureId,
            PreferenceKind kind,
            double strength
    ) {
        return new ExplicitPreference(
                userId, featureId, kind, strength, Instant.parse("2026-06-30T00:00:00Z")
        );
    }

    private static PopularityEngagementSnapshot snapshot(
            String entityId,
            long exposure,
            long like,
            long save,
            long share,
            PopularityTrustStatus trustStatus
    ) {
        long accepted = exposure + like + save + share;
        return new PopularityEngagementSnapshot(
                "snapshot:" + entityId,
                entityId,
                RecommendationEntityType.POST,
                "2026-06-17T00:00:00Z",
                REFERENCE_TIME_TEXT,
                exposure,
                like,
                save,
                share,
                accepted,
                accepted,
                0,
                trustStatus,
                "aggregation-v1",
                "anti-abuse-v1"
        );
    }

    private void emitInterest(String label, InterestMatchResult result) {
        emit(
                "INTEREST", label, result.status().wireValue(), bits(result.score()),
                bits(result.positiveCoverage()), bits(result.negativeCoverage()), bits(result.totalEntityFeatureWeight()),
                join(result.consideredFeatureIds()), join(result.matchedPreferFeatureIds()),
                join(result.matchedAvoidFeatureIds()), join(result.unmatchedEntityFeatureIds()),
                join(result.ignoredEntityFeatureIds()), join(result.hardExclusionFeatureIds()),
                nullable(result.notApplicableReason() == null ? null : result.notApplicableReason().wireValue()),
                result.policyVersion()
        );
        result.breakdown().forEach(item -> emit(
                "INTEREST_BREAKDOWN", label, item.featureId(), bits(item.entityWeight()), item.entitySource().wireValue(),
                nullable(item.signalDirection() == null ? null : item.signalDirection().wireValue()),
                bits(item.signalStrength()), nullable(item.signalSource() == null ? null : item.signalSource().wireValue()),
                bits(item.positiveContribution()), bits(item.negativeContribution()), Boolean.toString(item.matched())
        ));
    }

    private void emitFreshness(String label, FreshnessResult result) {
        emit(
                "FRESHNESS", label, result.status().wireValue(), bits(result.score()),
                result.ageMilliseconds() == null ? "null" : Long.toString(result.ageMilliseconds()),
                bits(result.ageDays()), bits(result.halfLifeDays()), nullable(result.freshnessTimestamp()),
                nullable(result.timestampSource() == null ? null : result.timestampSource().wireValue()),
                nullable(result.notApplicableReason() == null ? null : result.notApplicableReason().wireValue()),
                result.policyVersion()
        );
    }

    private void emitPopularity(String label, PopularityResult result) {
        emit(
                "POPULARITY", label, result.status().wireValue(), bits(result.score()), bits(result.qualityScore()),
                bits(result.volumeEvidence()), bits(result.evidenceMultiplier()), bits(result.likeLowerBound()),
                bits(result.saveLowerBound()), bits(result.shareLowerBound()),
                result.uniqueExposureCount() == null ? "null" : Long.toString(result.uniqueExposureCount()),
                nullable(result.notApplicableReason() == null ? null : result.notApplicableReason().wireValue()),
                nullable(result.snapshotId()), result.policyVersion()
        );
    }

    private void emitEligibility(String label, ContextEligibilityResult result) {
        emit(
                "ELIGIBILITY", label, result.status().wireValue(),
                nullable(result.hardExclusionReason() == null ? null : result.hardExclusionReason().wireValue()),
                nullable(result.notApplicableReason() == null ? null : result.notApplicableReason().wireValue()),
                join(result.acceptedHardClauseIds()), join(result.ignoredClauseIds()),
                join(result.matchedExcludedClauseIds()), join(result.missingRequiredClauseIds()),
                join(result.hardUsableFeatureIds()), join(result.ignoredEntityFeatureIds()), result.policyVersion()
        );
        result.breakdown().forEach(item -> emit(
                "HARD_BREAKDOWN", label, item.clauseId(), item.group().wireValue(),
                item.enforcement().wireValue(), item.matchMode().wireValue(), join(item.featureIds()),
                item.source().wireValue(), item.validationStatus().wireValue(), item.evaluationStatus().wireValue(),
                Boolean.toString(item.observedGroup()), join(item.matchedFeatureIds()),
                join(item.observedHardFeatureIds()), nullable(item.requiredSatisfied()),
                Boolean.toString(item.exclusionTriggered())
        ));
    }

    private void emitContextMatch(String label, ContextMatchResult result) {
        emit(
                "CONTEXT_MATCH", label, result.status().wireValue(), bits(result.score()), bits(result.baseScore()),
                bits(result.preferredCoverage()), bits(result.avoidanceCoverage()),
                bits(result.observedPreferredStrength()), bits(result.observedAvoidedStrength()),
                join(result.acceptedSoftClauseIds()), join(result.ignoredClauseIds()),
                join(result.observedClauseIds()), join(result.unknownClauseIds()),
                join(result.matchedPreferredClauseIds()), join(result.matchedAvoidedClauseIds()),
                join(result.softUsableFeatureIds()), join(result.ignoredEntityFeatureIds()),
                nullable(result.notApplicableReason() == null ? null : result.notApplicableReason().wireValue()),
                result.policyVersion()
        );
        result.breakdown().forEach(item -> emit(
                "SOFT_BREAKDOWN", label, item.clauseId(), item.group().wireValue(),
                item.enforcement().wireValue(), item.matchMode().wireValue(), join(item.featureIds()),
                bits(item.strength()), item.source().wireValue(), item.validationStatus().wireValue(),
                item.evaluationStatus().wireValue(), Boolean.toString(item.observedGroup()),
                join(item.observedSoftFeatureIds()), join(item.matchedFeatureIds()),
                bits(item.matchQuality()), bits(item.contribution()), Boolean.toString(item.denominatorIncluded())
        ));
    }

    private void emitScore(String label, CandidateScoreResult result) {
        emit(
                "SCORE", label, result.status().wireValue(), bits(result.score()),
                nullable(result.compositionMode() == null ? null : result.compositionMode().wireValue()),
                bits(result.scoredWeight()), bits(result.neutralFilledWeight()),
                join(result.anchorComponents().stream().map(item -> item.wireValue()).toList()),
                join(result.hardGateComponents().stream().map(item -> item.wireValue()).toList()),
                nullable(result.notApplicableReason() == null ? null : result.notApplicableReason().wireValue()),
                nullable(result.hardExclusionReason() == null ? null : result.hardExclusionReason().wireValue()),
                result.policyVersion(), result.componentPolicyVersions().contextMatch(),
                result.componentPolicyVersions().interestMatch(), result.componentPolicyVersions().freshness(),
                result.componentPolicyVersions().popularity()
        );
        result.breakdown().forEach(item -> emit(
                "SCORE_BREAKDOWN", label, item.component().wireValue(), item.resultStatus(),
                nullable(item.resultNotApplicableReason()), item.resultPolicyVersion(),
                bits(item.globalBaseWeight()), bits(item.entityEffectiveWeight()), item.availability().wireValue(),
                bits(item.rawScore()), bits(item.valueUsed()), bits(item.weightedContribution())
        ));
    }

    private void emit(String kind, String... fields) {
        Map<String, Object> record = new LinkedHashMap<>();
        record.put("kind", kind);
        record.put("fields", List.of(fields));
        records.add(record);
    }

    private static String bits(Double value) {
        return value == null ? "null" : bits(value.doubleValue());
    }

    private static String bits(double value) {
        return String.format(Locale.ROOT, "%016x", Double.doubleToRawLongBits(value));
    }

    private static String join(List<String> values) {
        return String.join(",", values);
    }

    private static String nullable(Object value) {
        return value == null ? "null" : String.valueOf(value);
    }

    private record ContextScenario(
            RecommendationContext context,
            ContextEligibilityResult eligibility,
            ContextMatchResult match,
            List<EntityFeature> hardFeatures,
            ContextEligibilityResult hardEligibility,
            ContextMatchResult hardMatch
    ) {
        private ContextScenario {
            hardFeatures = List.copyOf(hardFeatures);
        }
    }
}
