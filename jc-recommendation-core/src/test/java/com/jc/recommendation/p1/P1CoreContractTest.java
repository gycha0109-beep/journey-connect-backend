package com.jc.recommendation.p1;

import com.jc.recommendation.model.context.ContextSurface;
import com.jc.recommendation.model.diversity.DiversityCandidateMetadata;
import com.jc.recommendation.model.entity.RecommendationEntityType;
import com.jc.recommendation.model.event.EventType;
import com.jc.recommendation.model.feature.PreferenceKind;
import com.jc.recommendation.p1.evaluation.P1RankingComparison;
import com.jc.recommendation.p1.evaluation.P1RankingComparisonEngine;
import com.jc.recommendation.p1.policy.P1ExperimentAssignment;
import com.jc.recommendation.p1.policy.P1PolicySelection;
import com.jc.recommendation.p1.policy.P1PolicySelector;
import com.jc.recommendation.p1.policy.P1PolicySelectorInput;
import com.jc.recommendation.p1.policy.P1SessionContext;
import com.jc.recommendation.p1.profile.BehaviorProfileBuilder;
import com.jc.recommendation.p1.profile.BehaviorProfileEvent;
import com.jc.recommendation.p1.profile.BehaviorProfilePolicies;
import com.jc.recommendation.p1.profile.BehaviorProfileSnapshot;
import com.jc.recommendation.p1.profile.BuildBehaviorProfileInput;
import com.jc.recommendation.p1.profile.ExplicitPreference;
import com.jc.recommendation.p1.profile.UserProfileSegment;
import com.jc.recommendation.p1.ranking.P1CandidateInput;
import com.jc.recommendation.p1.ranking.P1RankingEngine;
import com.jc.recommendation.p1.ranking.P1RankingInput;
import com.jc.recommendation.p1.ranking.P1RankingResult;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public final class P1CoreContractTest {
    private static final Instant REFERENCE_TIME = Instant.parse("2026-07-19T00:00:00Z");

    private P1CoreContractTest() {
    }

    public static void main(String[] args) {
        profileDeterminismAndExactPartition();
        profileDeduplicatesAndRejectsConflicts();
        duplicateExplicitPreferencesAreCanonicalized();
        profilePolicyEffectiveTimeIsEnforced();
        profileCombinesExplicitAndBehaviorSignals();
        profileSegmentsAreStable();
        p1VocabularyExtensionIsAcceptedWithoutChangingV1();
        selectorProducesVersionedSurfaceAndSegmentPolicies();
        selectorRejectsBaselineAssignment();
        rankingIsDeterministicAndInputOrderIndependent();
        rankingFingerprintIgnoresStorageSnapshotIdentity();
        rankingAppliesPopularityCompressionAndLowExposureBoost();
        rankingAppliesInterestAndAvoidSignals();
        rankingEnforcesCandidateLimitAndDuplicateIdentity();
        diversityReducesAuthorConcentration();
        diversityMovementBoundsAndMetadataFingerprintAreEnforced();
        comparisonMetricsAndFingerprintAreDeterministic();
        System.out.println("P1 core contract: PASS (17 scenarios)");
    }

    private static void profileDeterminismAndExactPartition() {
        BehaviorProfileBuilder builder = new BehaviorProfileBuilder();
        List<BehaviorProfileEvent> events = List.of(
                event("e1", EventType.SAVE, 60, "theme:food"),
                event("e2", EventType.SEARCH, 30, "theme:cafe"),
                event("e3", EventType.LIKE, 100L * 86_400L, "theme:nature"));
        BuildBehaviorProfileInput input = new BuildBehaviorProfileInput(
                "user:1", REFERENCE_TIME, List.of(), events, BehaviorProfilePolicies.V1);
        BehaviorProfileSnapshot first = builder.build(input);
        BehaviorProfileSnapshot second = builder.build(input);
        require(first.equals(second), "profile must be deterministic");
        require(first.acceptedEventCount() == 1, "one event must be accepted");
        require(first.ignoredEventCount() == 2, "two events must be ignored");
        require(first.duplicateEventCount() == 0, "no duplicate expected");
    }

    private static void profileDeduplicatesAndRejectsConflicts() {
        BehaviorProfileBuilder builder = new BehaviorProfileBuilder();
        BehaviorProfileEvent event = event("same", EventType.LIKE, 60, "theme:food");
        BehaviorProfileSnapshot snapshot = builder.build(new BuildBehaviorProfileInput(
                "user:1", REFERENCE_TIME, List.of(), List.of(event, event), BehaviorProfilePolicies.V1));
        require(snapshot.duplicateEventCount() == 1, "duplicate count must be one");
        expectFailure(() -> builder.build(new BuildBehaviorProfileInput(
                "user:1",
                REFERENCE_TIME,
                List.of(),
                List.of(event, event("same", EventType.HIDE, 60, "theme:food")),
                BehaviorProfilePolicies.V1)));
    }

    private static void duplicateExplicitPreferencesAreCanonicalized() {
        ExplicitPreference preference = new ExplicitPreference(
                "theme:food", PreferenceKind.PREFER, 1.0d);
        BehaviorProfileSnapshot single = profile(List.of(preference), List.of());
        BehaviorProfileSnapshot duplicated = profile(List.of(preference, preference), List.of());
        require(duplicated.explicitPreferenceCount() == 1,
                "identical explicit preferences must be canonicalized to one feature");
        require(single.fingerprint().equals(duplicated.fingerprint()),
                "canonical duplicate explicit preferences must not change fingerprint");
    }

    private static void profilePolicyEffectiveTimeIsEnforced() {
        expectFailure(() -> new BehaviorProfileBuilder().build(new BuildBehaviorProfileInput(
                "user:1",
                Instant.parse("2026-07-18T14:59:59Z"),
                List.of(),
                List.of(),
                BehaviorProfilePolicies.V1)));
    }

    private static void profileCombinesExplicitAndBehaviorSignals() {
        BehaviorProfileSnapshot snapshot = profile(
                List.of(new ExplicitPreference("theme:food", PreferenceKind.PREFER, 1.0d)),
                List.of(event("e1", EventType.SAVE, 60, "theme:food")));
        require(snapshot.signals().size() == 1, "combined signal must remain one feature");
        require(snapshot.signals().getFirst().source().wireValue().equals("combined"),
                "signal source must preserve combined provenance");
        require(snapshot.signals().getFirst().direction() == PreferenceKind.PREFER,
                "combined signal must be positive");
    }

    private static void profileSegmentsAreStable() {
        require(profile(List.of(), List.of()).segment() == UserProfileSegment.EMPTY,
                "empty profile segment");
        require(profile(
                List.of(new ExplicitPreference("theme:food", PreferenceKind.PREFER, 1.0d)),
                List.of()).segment() == UserProfileSegment.EXPLICIT_ONLY,
                "explicit-only segment");
        require(profile(List.of(), List.of(event("e1", EventType.LIKE, 60, "theme:food")))
                .segment() == UserProfileSegment.EMERGING,
                "emerging segment");
        List<BehaviorProfileEvent> established = new ArrayList<>();
        for (int index = 0; index < 10; index++) {
            established.add(event("est-" + index, EventType.SAVE, 60L * (index + 1), "theme:food"));
        }
        require(profile(List.of(), established).segment() == UserProfileSegment.ESTABLISHED,
                "established segment");
    }

    private static void p1VocabularyExtensionIsAcceptedWithoutChangingV1() {
        BehaviorProfileSnapshot snapshot = profile(
                List.of(new ExplicitPreference("activity:running", PreferenceKind.PREFER, 0.8d)),
                List.of(event("e1", EventType.SAVE, 60, "theme:adventure")));
        require(snapshot.signals().stream().anyMatch(signal -> signal.featureId().equals("activity:running")),
                "P1 explicit feature must be accepted");
        require(snapshot.signals().stream().anyMatch(signal -> signal.featureId().equals("theme:adventure")),
                "P1 behavior feature must be accepted");
    }

    private static void selectorProducesVersionedSurfaceAndSegmentPolicies() {
        P1PolicySelector selector = new P1PolicySelector();
        P1PolicySelection emptyHome = selector.select(selectorInput(
                UserProfileSegment.EMPTY, ContextSurface.HOME_FEED));
        P1PolicySelection establishedSearch = selector.select(selectorInput(
                UserProfileSegment.ESTABLISHED, ContextSurface.SEARCH_RESULT));
        require(emptyHome.policyBundle().scorePolicy().policyVersion().equals("ranking-policy-v2-empty"),
                "empty score policy version");
        require(establishedSearch.policyBundle().scorePolicy().policyVersion()
                        .equals("ranking-policy-v2-established"),
                "established score policy version");
        require(!emptyHome.policyBundle().diversityPolicy().policyVersion()
                        .equals(establishedSearch.policyBundle().diversityPolicy().policyVersion()),
                "surface-specific diversity policy must differ");
        require(emptyHome.reasons().size() == 4, "policy selection reasons must be persisted");
        for (UserProfileSegment segment : UserProfileSegment.values()) {
            for (ContextSurface surface : ContextSurface.values()) {
                P1PolicySelection selected = selector.select(selectorInput(segment, surface));
                require(selected.policyBundle().segment() == segment,
                        "selected policy segment must match input");
                require(selected.policyBundle().surface() == surface,
                        "selected policy surface must match input");
                require(selected.policyBundle().scorePolicy().policyVersion().startsWith("ranking-policy-v2-"),
                        "every segment must use a parallel v2 score policy");
                require(selected.policyBundle().diversityPolicy().policyVersion().endsWith("-v2"),
                        "every surface must use a parallel v2 diversity policy");
            }
        }
    }

    private static void selectorRejectsBaselineAssignment() {
        expectFailure(() -> new P1PolicySelector().select(new P1PolicySelectorInput(
                UserProfileSegment.EMPTY,
                ContextSurface.HOME_FEED,
                new P1SessionContext(false, 0),
                P1ExperimentAssignment.BASELINE)));
    }

    private static void rankingIsDeterministicAndInputOrderIndependent() {
        BehaviorProfileSnapshot profile = profile(
                List.of(new ExplicitPreference("theme:food", PreferenceKind.PREFER, 1.0d)),
                List.of());
        P1PolicySelection selection = selection(profile.segment());
        P1CandidateInput first = candidate("1", "author:1", "region:seoul", "theme:food", 100, 10, 5, 0, 1);
        P1CandidateInput second = candidate("2", "author:2", "region:busan", "theme:nature", 100, 10, 5, 0, 2);
        P1RankingEngine engine = new P1RankingEngine();
        P1RankingResult ordered = engine.rank(rankingInput(profile, selection, List.of(first, second)));
        P1RankingResult reversed = engine.rank(rankingInput(profile, selection, List.of(second, first)));
        require(ordered.candidates().stream().map(candidate -> candidate.entityId()).toList()
                        .equals(reversed.candidates().stream().map(candidate -> candidate.entityId()).toList()),
                "ranking must not depend on input order");
        require(ordered.fingerprint().equals(reversed.fingerprint()),
                "ranking fingerprint must be input-order independent");
    }

    private static void rankingFingerprintIgnoresStorageSnapshotIdentity() {
        BehaviorProfileSnapshot profile = profile(List.of(), List.of());
        P1PolicySelection selection = selection(profile.segment());
        List<P1CandidateInput> candidates = List.of(
                candidate("1", "author:1", "region:seoul", "theme:food", 10, 1, 0, 0, 1));
        P1RankingResult first = new P1RankingEngine().rank(new P1RankingInput(
                "snapshot:storage-a", "user:1", "context:p1-test", REFERENCE_TIME,
                profile, selection, candidates));
        P1RankingResult second = new P1RankingEngine().rank(new P1RankingInput(
                "snapshot:storage-b", "user:1", "context:p1-test", REFERENCE_TIME,
                profile, selection, candidates));
        require(first.fingerprint().equals(second.fingerprint()),
                "storage snapshot identity must not alter algorithm fingerprint");
    }

    private static void rankingAppliesPopularityCompressionAndLowExposureBoost() {
        BehaviorProfileSnapshot profile = profile(List.of(), List.of());
        P1RankingResult result = new P1RankingEngine().rank(rankingInput(
                profile,
                selection(profile.segment()),
                List.of(
                        candidate("1", "author:1", "region:seoul", "theme:food", 10_000, 1_000, 300, 10, 1),
                        candidate("2", "author:2", "region:busan", "theme:nature", 20, 2, 0, 0, 2))));
        require(result.candidates().stream().allMatch(candidate -> candidate.score() <= 1.0d),
                "score must be capped at one");
        require(result.candidates().stream().anyMatch(candidate -> candidate.lowExposureBoost() > 0.0d),
                "low-exposure candidate must receive a bounded boost");
        require(result.candidates().stream().allMatch(candidate ->
                        candidate.adjustedPopularityScore() >= candidate.rawPopularityScore()),
                "compression exponent below one must flatten normalized popularity");
    }

    private static void rankingAppliesInterestAndAvoidSignals() {
        BehaviorProfileSnapshot profile = profile(
                List.of(
                        new ExplicitPreference("theme:food", PreferenceKind.PREFER, 1.0d),
                        new ExplicitPreference("theme:nature", PreferenceKind.AVOID, 1.0d)),
                List.of());
        P1RankingResult result = new P1RankingEngine().rank(rankingInput(
                profile,
                selection(profile.segment()),
                List.of(
                        candidate("1", "author:1", "region:seoul", "theme:food", 10, 1, 0, 1, 1),
                        candidate("2", "author:2", "region:seoul", "theme:nature", 10, 1, 0, 1, 2))));
        require(result.candidates().getFirst().entityId().equals("1"),
                "preferred feature must outrank avoided feature under equal evidence");
        require(result.candidates().getFirst().interestScore()
                        > result.candidates().get(1).interestScore(),
                "interest score must encode preference direction");
    }

    private static void rankingEnforcesCandidateLimitAndDuplicateIdentity() {
        BehaviorProfileSnapshot profile = profile(List.of(), List.of());
        P1PolicySelection selection = selection(profile.segment());
        List<P1CandidateInput> candidates = new ArrayList<>();
        for (int index = 0; index < 101; index++) {
            candidates.add(candidate(
                    Integer.toString(index + 1),
                    "author:" + index,
                    "region:seoul",
                    "theme:food",
                    1,
                    0,
                    0,
                    0,
                    index + 1));
        }
        expectFailure(() -> new P1RankingEngine().rank(rankingInput(profile, selection, candidates)));
        P1CandidateInput duplicate = candidate("1", "author:1", "region:seoul", "theme:food", 1, 0, 0, 0, 1);
        expectFailure(() -> new P1RankingEngine().rank(rankingInput(
                profile, selection, List.of(duplicate, duplicate))));
    }

    private static void diversityReducesAuthorConcentration() {
        BehaviorProfileSnapshot profile = profile(List.of(), List.of());
        List<P1CandidateInput> candidates = List.of(
                candidate("1", "author:same", "region:seoul", "theme:food", 1_000, 50, 10, 4, 1),
                candidate("2", "author:same", "region:seoul", "theme:food", 900, 40, 9, 4, 2),
                candidate("3", "author:same", "region:seoul", "theme:food", 800, 30, 8, 4, 3),
                candidate("4", "author:other", "region:busan", "theme:nature", 100, 3, 1, 0, 4));
        P1RankingResult result = new P1RankingEngine().rank(rankingInput(
                profile, selection(profile.segment()), candidates));
        require(result.candidates().subList(0, 3).stream()
                        .anyMatch(candidate -> candidate.diversityMetadata().authorId().equals("author:other")),
                "diversity must introduce another author into the leading window");
    }

    private static void diversityMovementBoundsAndMetadataFingerprintAreEnforced() {
        BehaviorProfileSnapshot profile = profile(List.of(), List.of());
        P1PolicySelection selection = selection(profile.segment());
        List<P1CandidateInput> candidates = List.of(
                candidate("1", "author:same", "region:seoul", "theme:food", 100, 10, 1, 4, 1),
                candidate("2", "author:same", "region:seoul", "theme:food", 90, 9, 1, 4, 2),
                candidate("3", "author:same", "region:seoul", "theme:food", 80, 8, 1, 4, 3),
                candidate("4", "author:other", "region:busan", "theme:nature", 10, 1, 0, 0, 4));
        P1RankingResult result = new P1RankingEngine().rank(rankingInput(profile, selection, candidates));
        int maxPromotion = selection.policyBundle().diversityPolicy().maxPromotionDistance();
        int maxDemotion = selection.policyBundle().diversityPolicy().maxDemotionDistance();
        result.candidates().forEach(candidate -> {
            if (candidate.absoluteRank() < candidate.baseRank()) {
                require(candidate.baseRank() - candidate.absoluteRank() <= maxPromotion,
                        "diversity promotion bound exceeded");
            } else {
                require(candidate.absoluteRank() - candidate.baseRank() <= maxDemotion,
                        "diversity demotion bound exceeded");
            }
        });

        P1CandidateInput original = candidate(
                "9", "author:a", "region:seoul", "theme:food", 10, 1, 0, 0, 1);
        P1CandidateInput metadataChanged = new P1CandidateInput(
                original.entityId(),
                original.entityType(),
                original.publishedAt(),
                original.featureIds(),
                original.viewCount(),
                original.likeCount(),
                original.bookmarkCount(),
                original.recentExposureCount(),
                original.contextScore(),
                new DiversityCandidateMetadata(
                        original.entityId(), original.entityType(), "author:b",
                        "region:seoul", "theme:food", "post:9"));
        String originalFingerprint = new P1RankingEngine().rank(rankingInput(
                profile, selection, List.of(original))).fingerprint();
        String changedFingerprint = new P1RankingEngine().rank(rankingInput(
                profile, selection, List.of(metadataChanged))).fingerprint();
        require(!originalFingerprint.equals(changedFingerprint),
                "diversity metadata must be covered by result fingerprint");
    }

    private static void comparisonMetricsAndFingerprintAreDeterministic() {
        BehaviorProfileSnapshot profile = profile(List.of(), List.of());
        P1RankingResult result = new P1RankingEngine().rank(rankingInput(
                profile,
                selection(profile.segment()),
                List.of(
                        candidate("1", "author:1", "region:seoul", "theme:food", 10, 1, 0, 0, 1),
                        candidate("2", "author:2", "region:busan", "theme:nature", 9, 1, 0, 0, 2),
                        candidate("3", "author:3", "region:jeju", "theme:cafe", 8, 1, 0, 0, 3))));
        P1RankingComparisonEngine engine = new P1RankingComparisonEngine();
        P1RankingComparison first = engine.compare(
                "ranking-policy-v1", List.of("3", "2", "1"), result.policyBundleVersion(), result.candidates(), 3);
        P1RankingComparison second = engine.compare(
                "ranking-policy-v1", List.of("3", "2", "1"), result.policyBundleVersion(), result.candidates(), 3);
        require(first.equals(second), "comparison must be deterministic");
        require(first.overlapCount() == 3, "all candidates must overlap");
        require(first.treatmentUniqueAuthorCount() == 3, "three authors expected");
    }

    private static BehaviorProfileSnapshot profile(
            List<ExplicitPreference> explicit,
            List<BehaviorProfileEvent> events) {
        return new BehaviorProfileBuilder().build(new BuildBehaviorProfileInput(
                "user:1", REFERENCE_TIME, explicit, events, BehaviorProfilePolicies.V1));
    }

    private static BehaviorProfileEvent event(
            String eventId,
            EventType eventType,
            long ageSeconds,
            String featureId) {
        return new BehaviorProfileEvent(
                eventId, eventType, REFERENCE_TIME.minusSeconds(ageSeconds), List.of(featureId));
    }

    private static P1PolicySelectorInput selectorInput(
            UserProfileSegment segment,
            ContextSurface surface) {
        return new P1PolicySelectorInput(
                segment,
                surface,
                new P1SessionContext(false, 0),
                P1ExperimentAssignment.TREATMENT);
    }

    private static P1PolicySelection selection(UserProfileSegment segment) {
        return new P1PolicySelector().select(selectorInput(segment, ContextSurface.HOME_FEED));
    }

    private static P1RankingInput rankingInput(
            BehaviorProfileSnapshot profile,
            P1PolicySelection selection,
            List<P1CandidateInput> candidates) {
        return new P1RankingInput(
                "snapshot:p1-test",
                "user:1",
                "context:p1-test",
                REFERENCE_TIME,
                profile,
                selection,
                candidates);
    }

    private static P1CandidateInput candidate(
            String id,
            String author,
            String region,
            String theme,
            long views,
            long likes,
            long bookmarks,
            int exposures,
            int ageDays) {
        return new P1CandidateInput(
                id,
                RecommendationEntityType.POST,
                REFERENCE_TIME.minusSeconds(ageDays * 86_400L),
                List.of(region, theme),
                views,
                likes,
                bookmarks,
                exposures,
                1.0d,
                new DiversityCandidateMetadata(
                        id,
                        RecommendationEntityType.POST,
                        author,
                        region,
                        theme,
                        "post:" + id));
    }

    private static void expectFailure(Runnable runnable) {
        try {
            runnable.run();
            throw new AssertionError("expected failure");
        } catch (IllegalArgumentException expected) {
            // Expected contract rejection.
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
