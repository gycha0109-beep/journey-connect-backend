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
class SearchCtrManualActivationStoreIntegrationTest {

    @Autowired private SearchCtrManualActivationPort activationPort;
    @Autowired private JdbcTemplate jdbcTemplate;

    @Test
    void executesZeroDenominatorWindowThroughIdentityFreeBoundaryAndAppendsAudit() {
        Instant start = Instant.parse("2001-01-01T00:00:00Z");
        Instant end = Instant.parse("2001-01-01T01:00:00Z");
        Instant observedAt = Instant.parse("2001-01-01T01:35:00Z");

        SearchCtrManualActivationPort.Result stored = activationPort.execute(
                command(
                        "search-ctr-manual-run:33333333333333333333333333333333",
                        start,
                        end,
                        observedAt));
        SearchCtrManualActivationPort.Result duplicate = activationPort.execute(
                command(
                        "search-ctr-manual-run:44444444444444444444444444444444",
                        start,
                        end,
                        observedAt));

        assertThat(stored.writeStatus()).isEqualTo(SearchCtrProjectionPort.WriteStatus.STORED);
        assertThat(stored.eligibleExposureCount()).isZero();
        assertThat(stored.attributedExposureCount()).isZero();
        assertThat(stored.ctrBasisPoints()).isNull();
        assertThat(stored.projectionStatus()).isEqualTo(SearchCtrContract.PROVISIONAL_STATUS);
        assertThat(duplicate.writeStatus()).isEqualTo(SearchCtrProjectionPort.WriteStatus.DUPLICATE);
        assertThat(duplicate.projectionId()).isEqualTo(stored.projectionId());

        Long auditCount = jdbcTemplate.queryForObject(
                "select count(*) from public.search_ctr_manual_run_audit_v1",
                Long.class);
        Long finalityAttempts = jdbcTemplate.queryForObject(
                "select count(*) from public.search_ctr_manual_run_audit_v1 "
                        + "where finality_write_attempted",
                Long.class);
        assertThat(auditCount).isEqualTo(2L);
        assertThat(finalityAttempts).isZero();
    }

    private static SearchCtrManualActivationPort.Command command(
            String operationId,
            Instant start,
            Instant end,
            Instant observedAt) {
        return new SearchCtrManualActivationPort.Command(
                operationId,
                start,
                end,
                "test",
                SearchCtrActivationPolicy.POLICY_VERSION,
                observedAt,
                "search-ctr:manual-integration-v1",
                "sr6ff-integration-v1");
    }
}
