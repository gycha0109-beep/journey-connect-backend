package com.jc.intelligence.production.search.v1;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

public final class SearchDocumentEligibilityPolicyV1 {
    private final Duration maximumAuthorityAge;
    public SearchDocumentEligibilityPolicyV1(Duration maximumAuthorityAge) {
        this.maximumAuthorityAge=Objects.requireNonNull(maximumAuthorityAge,"maximumAuthorityAge");
        if (maximumAuthorityAge.isNegative() || maximumAuthorityAge.isZero() || maximumAuthorityAge.compareTo(Duration.ofDays(30))>0) {
            throw new IllegalArgumentException("maximumAuthorityAge must be >0 and <=30 days");
        }
    }
    public SearchDocumentEligibilityDecisionV1 evaluate(SearchDocumentSourceV1 source, Instant referenceTime) {
        if (source == null) return SearchDocumentEligibilityDecisionV1.deny("source_missing");
        Objects.requireNonNull(referenceTime,"referenceTime");
        if (source.visibilityStatus()!=ProjectionVisibilityStatus.PUBLIC) return SearchDocumentEligibilityDecisionV1.deny("visibility_not_public");
        if (source.publicationStatus()!=ProjectionPublicationStatus.PUBLISHED) return SearchDocumentEligibilityDecisionV1.deny("publication_not_published");
        if (source.moderationStatus()!=ProjectionModerationStatus.ELIGIBLE) return SearchDocumentEligibilityDecisionV1.deny("moderation_not_eligible");
        if (source.deletionStatus()!=ProjectionDeletionStatus.ACTIVE) return SearchDocumentEligibilityDecisionV1.deny("source_deleted");
        if (source.operationalExcluded()) return SearchDocumentEligibilityDecisionV1.deny("operationally_excluded");
        if (source.regionId()==null || source.regionReference()==null) return SearchDocumentEligibilityDecisionV1.deny("region_missing");
        if (source.title().isBlank() || source.body().isBlank()) return SearchDocumentEligibilityDecisionV1.deny("content_invalid");
        if (source.sourceUpdatedAt().isAfter(referenceTime.plusSeconds(5))) return SearchDocumentEligibilityDecisionV1.deny("authority_time_invalid");
        if (source.sourceUpdatedAt().isBefore(referenceTime.minus(maximumAuthorityAge))) return SearchDocumentEligibilityDecisionV1.deny("authority_stale");
        return SearchDocumentEligibilityDecisionV1.allow();
    }
}
