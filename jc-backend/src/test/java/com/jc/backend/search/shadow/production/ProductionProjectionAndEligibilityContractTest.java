package com.jc.backend.search.shadow.production;

import static org.assertj.core.api.Assertions.assertThat;

import com.jc.intelligence.production.search.v1.InMemorySearchProjectionStore;
import com.jc.intelligence.production.search.v1.ProjectionDeletionStatus;
import com.jc.intelligence.production.search.v1.ProjectionModerationStatus;
import com.jc.intelligence.production.search.v1.ProjectionPublicationStatus;
import com.jc.intelligence.production.search.v1.ProjectionVisibilityStatus;
import com.jc.intelligence.production.search.v1.SearchDocumentEligibilityPolicyV1;
import com.jc.intelligence.production.search.v1.SearchDocumentProjectorV1;
import com.jc.intelligence.production.search.v1.SearchDocumentSourceV1;
import com.jc.intelligence.production.search.v1.SearchProjectionProjectorService;
import com.jc.intelligence.production.search.v1.SearchProjectionWriteStatus;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class ProductionProjectionAndEligibilityContractTest {
    private static final Instant NOW = Instant.parse("2026-07-20T00:00:00Z");

    @Test
    void projectionIsDeterministicIdempotentAndFailClosed() {
        var store = new InMemorySearchProjectionStore();
        var service = new SearchProjectionProjectorService(
                new SearchDocumentEligibilityPolicyV1(Duration.ofDays(7)),
                new SearchDocumentProjectorV1(), store, Clock.fixed(NOW, ZoneOffset.UTC));
        var eligible = source(1, 1, ProjectionVisibilityStatus.PUBLIC,
                ProjectionPublicationStatus.PUBLISHED, ProjectionModerationStatus.ELIGIBLE,
                ProjectionDeletionStatus.ACTIVE, false);

        assertThat(service.apply(eligible).status()).isEqualTo(SearchProjectionWriteStatus.INSERTED);
        assertThat(service.apply(eligible).status()).isEqualTo(SearchProjectionWriteStatus.UNCHANGED);
        assertThat(store.size()).isEqualTo(1);

        var privateSource = source(1, 2, ProjectionVisibilityStatus.NON_PUBLIC,
                ProjectionPublicationStatus.PUBLISHED, ProjectionModerationStatus.ELIGIBLE,
                ProjectionDeletionStatus.ACTIVE, false);
        assertThat(service.apply(privateSource).status()).isEqualTo(SearchProjectionWriteStatus.REMOVED);
        assertThat(store.size()).isZero();

        var blocked = source(2, 1, ProjectionVisibilityStatus.PUBLIC,
                ProjectionPublicationStatus.PUBLISHED, ProjectionModerationStatus.BLOCKED,
                ProjectionDeletionStatus.ACTIVE, false);
        assertThat(service.apply(blocked).status()).isEqualTo(SearchProjectionWriteStatus.SOURCE_MISSING);
        assertThat(store.size()).isZero();
    }

    private static SearchDocumentSourceV1 source(long id, long version,
            ProjectionVisibilityStatus visibility, ProjectionPublicationStatus publication,
            ProjectionModerationStatus moderation, ProjectionDeletionStatus deletion,
            boolean operationalExcluded) {
        return new SearchDocumentSourceV1(id, version, 11L, "kr-seoul", "place:101",
                "Seoul cafe", "public journey content", visibility, publication, moderation,
                deletion, operationalExcluded, NOW.minusSeconds(60));
    }
}
