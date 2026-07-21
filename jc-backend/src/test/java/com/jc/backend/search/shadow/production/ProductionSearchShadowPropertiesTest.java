package com.jc.backend.search.shadow.production;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;

class ProductionSearchShadowPropertiesTest {
    private static final String HASH = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";

    @Test
    void defaultsAreKilledZeroSampleEmptyAndOperationallyUnapproved() {
        var config = ProductionSearchShadowPropertiesValidator.validate(
                new ProductionSearchShadowProperties());

        assertThat(config.enabled()).isFalse();
        assertThat(config.killSwitchActive()).isTrue();
        assertThat(config.configuredSamplingBps()).isZero();
        assertThat(config.effectiveSamplingBps()).isZero();
        assertThat(config.allowlistHashes()).isEmpty();
        assertThat(config.operationalInputsPresent()).isFalse();
        assertThat(config.dispatchConfigured()).isFalse();
    }

    @Test
    void approvedBoundsAndCompleteOperationalInputsProduceConfiguredState() {
        var config = ProductionSearchShadowPropertiesValidator.validate(validEnabled());

        assertThat(config.effectiveSamplingBps()).isEqualTo(10);
        assertThat(config.allowlistHashes()).containsExactly(HASH);
        assertThat(config.operationalInputsPresent()).isTrue();
        assertThat(config.activationWindowStart()).isEqualTo(Instant.parse("2026-07-21T10:00:00Z"));
        assertThat(config.dispatchConfigured()).isTrue();
    }

    @Test
    void activationRejectsMissingOperationalInputsOrEmptyCohort() {
        assertInvalid(properties -> properties.setActivationApprovalRef(""));
        assertInvalid(properties -> properties.setActivationApproverRef(""));
        assertInvalid(properties -> properties.setActivationExecutorRef(""));
        assertInvalid(properties -> properties.setRollbackOwnerRef(""));
        assertInvalid(properties -> properties.setMetricVerificationRef(""));
        assertInvalid(properties -> properties.setActivationWindowStart(""));
        assertInvalid(properties -> properties.setAllowlistHashes(List.of()));
    }

    @Test
    void activationRejectsMalformedReferencesAndWindow() {
        assertInvalid(properties -> properties.setActivationApprovalRef("raw user 42"));
        assertInvalid(properties -> properties.setActivationWindowStart("tomorrow"));
        assertInvalid(properties -> properties.setActivationWindowEnd("2026-07-21T09:00:00Z"));
    }

    @Test
    void samplingBoundaryRejectsNegativeAndAboveTen() {
        for (int allowed : new int[] { 0, 1, 9, 10 }) {
            var properties = validEnabled();
            properties.setSamplingBps(allowed);
            assertThat(ProductionSearchShadowPropertiesValidator.validate(properties).configuredSamplingBps())
                    .isEqualTo(allowed);
        }
        for (int rejected : new int[] { -1, 11, 50, 100, Integer.MAX_VALUE }) {
            assertInvalid(properties -> properties.setSamplingBps(rejected));
        }
    }

    @Test
    void resourceBoundsAndImmutableApprovedCeilingAreRejectedWhenChanged() {
        assertInvalid(properties -> properties.setMaxApprovedSamplingBps(11));
        assertInvalid(properties -> properties.setMaxConcurrency(3));
        assertInvalid(properties -> properties.setQueueCapacity(9));
    }

    @Test
    void allowlistIsTrimmedLowercasedAndDuplicatesOrRawIdsAreRejected() {
        var normalizedProperties = validEnabled();
        normalizedProperties.setAllowlistHashes(
                List.of("  " + HASH.toUpperCase(Locale.ROOT) + "  "));
        assertThat(ProductionSearchShadowPropertiesValidator.validate(normalizedProperties).allowlistHashes())
                .containsExactly(HASH);

        assertInvalid(properties -> properties.setAllowlistHashes(List.of(HASH, HASH)));
        assertInvalid(properties -> properties.setAllowlistHashes(List.of("42")));
    }

    private static void assertInvalid(Consumer<ProductionSearchShadowProperties> mutation) {
        var properties = validEnabled();
        mutation.accept(properties);
        assertThatThrownBy(() -> ProductionSearchShadowPropertiesValidator.validate(properties))
                .isInstanceOf(IllegalStateException.class);
    }

    static ProductionSearchShadowProperties validEnabled() {
        var properties = new ProductionSearchShadowProperties();
        properties.setEnabled(true);
        properties.setKillSwitch(false);
        properties.setSamplingBps(10);
        properties.setAllowlistHashes(List.of(HASH));
        properties.setActivationApprovalRef("approval:ip12-5-pilot-001");
        properties.setActivationApproverRef("role:project-owner");
        properties.setActivationExecutorRef("role:release-operator");
        properties.setRollbackOwnerRef("role:backend-owner");
        properties.setMetricVerificationRef("metric:journey.search.shadow");
        properties.setActivationWindowStart("2026-07-21T10:00:00Z");
        properties.setActivationWindowEnd("2026-07-21T11:00:00Z");
        return properties;
    }
}
