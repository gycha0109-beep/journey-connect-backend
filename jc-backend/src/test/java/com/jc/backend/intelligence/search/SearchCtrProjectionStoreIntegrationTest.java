package com.jc.backend.intelligence.search;

import static org.assertj.core.api.Assertions.assertThat;

import com.jc.backend.CanonicalPostgresTest;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;

@CanonicalPostgresTest
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class SearchCtrProjectionStoreIntegrationTest {

    @Autowired private SearchCtrProjectionPort projectionPort;
    @Autowired private JdbcTemplate jdbcTemplate;

    @Test
    void storesZeroDenominatorProjectionAndReturnsSemanticDuplicate() {
        Instant start = Instant.parse("2000-01-01T00:00:00Z");
        Instant end = Instant.parse("2000-01-01T01:00:00Z");
        SearchCtrProjectionPort.WriteCommand command = new SearchCtrProjectionPort.WriteCommand(
                start,
                end,
                null,
                "search-ctr-integration-zero-v1",
                "search-ctr-integration-v1");

        SearchCtrProjectionPort.WriteResult stored = projectionPort.write(command);
        SearchCtrProjectionPort.WriteResult duplicate = projectionPort.write(command);
        SearchCtrProjectionPort.WriteResult semanticDuplicate = projectionPort.write(
                new SearchCtrProjectionPort.WriteCommand(
                        start,
                        end,
                        null,
                        "search-ctr-integration-zero-v2",
                        "search-ctr-integration-v1"));

        assertThat(stored.status()).isEqualTo(SearchCtrProjectionPort.WriteStatus.STORED);
        assertThat(stored.eligibleExposureCount()).isZero();
        assertThat(stored.attributedExposureCount()).isZero();
        assertThat(stored.ctrBasisPoints()).isNull();
        assertThat(stored.projectionStatus()).isEqualTo(SearchCtrContract.PROVISIONAL_STATUS);
        assertThat(stored.predecessorProjectionId()).isNull();
        assertThat(duplicate.status()).isEqualTo(SearchCtrProjectionPort.WriteStatus.DUPLICATE);
        assertThat(duplicate.projectionId()).isEqualTo(stored.projectionId());
        assertThat(semanticDuplicate.status()).isEqualTo(SearchCtrProjectionPort.WriteStatus.DUPLICATE);
        assertThat(semanticDuplicate.projectionId()).isEqualTo(stored.projectionId());

        Long rowCount = jdbcTemplate.queryForObject(
                "select count(*) from public.search_ctr_projection_snapshot_v1",
                Long.class);
        String payload = jdbcTemplate.queryForObject(
                "select convert_from(canonical_payload, 'UTF8') "
                        + "from public.search_ctr_projection_snapshot_v1",
                String.class);
        assertThat(rowCount).isEqualTo(1L);
        assertThat(payload)
                .contains("\"eligibleExposureCount\":0")
                .contains("\"ctrBasisPoints\":null")
                .doesNotContain("userId", "subjectRef", "sessionId", "computedAt");
    }
}
