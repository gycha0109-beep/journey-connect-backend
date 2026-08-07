package com.jc.backend.intelligence.crew;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * CR-0 crew recommendation contract.
 *
 * <p>This type defines deterministic eligibility and feature-coverage semantics only. It does not
 * query the database, rank crews, persist exposures, or change the current crew API.
 */
public final class CrewRecommendationContract {

    public static final String CONTRACT_VERSION = "crew-recommendation-contract-v1";
    public static final String POLICY_VERSION = "crew-ranking-policy-v1";
    public static final String SURFACE = "crew_list";
    public static final String ENTITY_TYPE = "crew";
    public static final String LEGACY_FALLBACK = "crew-service-list-v1";
    public static final boolean DEFAULT_ENABLED = false;

    public static final double TAG_INTEREST_WEIGHT = 0.40d;
    public static final double REGION_INTEREST_WEIGHT = 0.30d;
    public static final double TRAVEL_DATE_FIT_WEIGHT = 0.10d;
    public static final double CAPACITY_REMAINING_WEIGHT = 0.10d;
    public static final double FRESHNESS_WEIGHT = 0.10d;

    public static final double LEGACY_TAGLESS_REGION_WEIGHT = 0.75d;
    public static final double LEGACY_TAGLESS_FRESHNESS_WEIGHT = 0.25d;

    private CrewRecommendationContract() {}

    public static EligibilityDecision eligibility(
            CandidateFacts candidate,
            ViewerRelation viewerRelation,
            VisibilityState visibilityState,
            LocalDate referenceDate) {
        Objects.requireNonNull(candidate, "candidate");
        Objects.requireNonNull(viewerRelation, "viewerRelation");
        Objects.requireNonNull(visibilityState, "visibilityState");
        Objects.requireNonNull(referenceDate, "referenceDate");

        if (!candidate.recruiting()) {
            return EligibilityDecision.NOT_RECRUITING;
        }
        if (candidate.travelDate() != null && candidate.travelDate().isBefore(referenceDate)) {
            return EligibilityDecision.TRAVEL_DATE_ELAPSED;
        }
        if (candidate.capacityRemaining() <= 0) {
            return EligibilityDecision.CAPACITY_FULL;
        }
        if (viewerRelation == ViewerRelation.OWNER) {
            return EligibilityDecision.VIEWER_IS_OWNER;
        }
        if (viewerRelation == ViewerRelation.PENDING) {
            return EligibilityDecision.ALREADY_PENDING;
        }
        if (viewerRelation == ViewerRelation.APPROVED) {
            return EligibilityDecision.ALREADY_MEMBER;
        }
        if (visibilityState == VisibilityState.INELIGIBLE) {
            return EligibilityDecision.VISIBILITY_INELIGIBLE;
        }
        return EligibilityDecision.ELIGIBLE;
    }

    public static CoverageMode coverageMode(CandidateFacts candidate) {
        Objects.requireNonNull(candidate, "candidate");
        return candidate.tagFeatureState() == TagFeatureState.PRESENT
                ? CoverageMode.FULL_FEATURED
                : CoverageMode.LEGACY_TAGLESS;
    }

    public static String entityRef(long crewId) {
        if (crewId <= 0) {
            throw new IllegalArgumentException("crewId must be positive");
        }
        return ENTITY_TYPE + ":" + crewId;
    }

    public enum EligibilityDecision {
        ELIGIBLE("eligible"),
        NOT_RECRUITING("not_recruiting"),
        TRAVEL_DATE_ELAPSED("travel_date_elapsed"),
        CAPACITY_FULL("capacity_full"),
        VIEWER_IS_OWNER("viewer_is_owner"),
        ALREADY_PENDING("already_pending"),
        ALREADY_MEMBER("already_member"),
        VISIBILITY_INELIGIBLE("visibility_ineligible");

        private final String wireValue;

        EligibilityDecision(String wireValue) {
            this.wireValue = wireValue;
        }

        public String wireValue() {
            return wireValue;
        }
    }

    public enum ViewerRelation {
        NONE,
        OWNER,
        PENDING,
        APPROVED,
        HISTORY_ONLY
    }

    /**
     * NOT_INTEGRATED preserves the current crew baseline until an approved Operations read contract
     * exists. It must never be interpreted as an Operations approval decision.
     */
    public enum VisibilityState {
        NOT_INTEGRATED,
        ELIGIBLE,
        INELIGIBLE
    }

    public enum TagFeatureState {
        UNAVAILABLE,
        EMPTY,
        PRESENT
    }

    public enum CoverageMode {
        FULL_FEATURED("full_featured"),
        LEGACY_TAGLESS("legacy_tagless");

        private final String wireValue;

        CoverageMode(String wireValue) {
            this.wireValue = wireValue;
        }

        public String wireValue() {
            return wireValue;
        }
    }

    public record CandidateFacts(
            long crewId,
            long ownerId,
            String regionCode,
            LocalDate travelDate,
            int capacity,
            long activeMemberCount,
            boolean recruiting,
            Instant createdAt,
            TagFeatureState tagFeatureState,
            List<String> tagSlugs) {

        public CandidateFacts {
            if (crewId <= 0 || ownerId <= 0) {
                throw new IllegalArgumentException("crew candidate IDs must be positive");
            }
            if (regionCode == null || regionCode.isBlank()) {
                throw new IllegalArgumentException("regionCode is required");
            }
            regionCode = regionCode.trim().toLowerCase(Locale.ROOT);
            if (capacity < 2) {
                throw new IllegalArgumentException("capacity must be at least two");
            }
            if (activeMemberCount < 0) {
                throw new IllegalArgumentException("activeMemberCount must be nonnegative");
            }
            Objects.requireNonNull(createdAt, "createdAt");
            Objects.requireNonNull(tagFeatureState, "tagFeatureState");
            tagSlugs = List.copyOf(Objects.requireNonNull(tagSlugs, "tagSlugs"));
            if (tagFeatureState == TagFeatureState.PRESENT && tagSlugs.isEmpty()) {
                throw new IllegalArgumentException("PRESENT tag feature state requires tags");
            }
            if (tagFeatureState != TagFeatureState.PRESENT && !tagSlugs.isEmpty()) {
                throw new IllegalArgumentException("tagless feature states cannot carry tags");
            }
            if (tagSlugs.stream().anyMatch(tag -> tag == null || tag.isBlank())) {
                throw new IllegalArgumentException("tagSlugs cannot contain blank values");
            }
        }

        public long capacityRemaining() {
            return Math.max(0L, (long) capacity - activeMemberCount);
        }
    }
}
