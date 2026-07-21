package com.jc.backend.search.shadow.production;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Test;

class ProductionSearchShadowPropertiesTest {
    private static final String HASH = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";

    @Test
    void defaultsAreKilledZeroSampleAndEmpty() {
        var config = ProductionSearchShadowPropertiesValidator.validate(new ProductionSearchShadowProperties());
        assertThat(config.enabled()).isFalse();
        assertThat(config.killSwitchActive()).isTrue();
        assertThat(config.configuredSamplingBps()).isZero();
        assertThat(config.effectiveSamplingBps()).isZero();
        assertThat(config.allowlistHashes()).isEmpty();
        assertThat(config.dispatchConfigured()).isFalse();
    }

    @Test
    void approvedBoundsProduceConfiguredButStillExplicitState() {
        var properties = validEnabled();
        var config = ProductionSearchShadowPropertiesValidator.validate(properties);
        assertThat(config.effectiveSamplingBps()).isEqualTo(10);
        assertThat(config.allowlistHashes()).containsExactly(HASH);
        assertThat(config.dispatchConfigured()).isTrue();
    }

    @Test
    void samplingBoundaryRejectsNegativeAndAboveTen() {
        for (int allowed : new int[]{0, 1, 9, 10}) {
            var properties = validEnabled();
            properties.setSamplingBps(allowed);
            assertThat(ProductionSearchShadowPropertiesValidator.validate(properties).configuredSamplingBps())
                    .isEqualTo(allowed);
        }
        for (int rejected : new int[]{-1, 11, 50, 100, Integer.MAX_VALUE}) {
            var properties = validEnabled();
            properties.setSamplingBps(rejected);
            assertThatThrownBy(() -> ProductionSearchShadowPropertiesValidator.validate(properties))
                    .isInstanceOf(IllegalStateException.class);
        }
    }

    @Test
    void resourceBoundsAndImmutableApprovedCeilingAreRejectedWhenChanged() {
        var approvedCeilingProperties = validEnabled();
        approvedCeilingProperties.setMaxApprovedSamplingBps(11);
        assertThatThrownBy(() -> ProductionSearchShadowPropertiesValidator.validate(approvedCeilingProperties))
                .isInstanceOf(IllegalStateException.class);

        var concurrencyProperties = validEnabled();
        concurrencyProperties.setMaxConcurrency(3);
        assertThatThrownBy(() -> ProductionSearchShadowPropertiesValidator.validate(concurrencyProperties))
                .isInstanceOf(IllegalStateException.class);

        var queueProperties = validEnabled();
        queueProperties.setQueueCapacity(9);
        assertThatThrownBy(() -> ProductionSearchShadowPropertiesValidator.validate(queueProperties))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void allowlistIsTrimmedLowercasedDeduplicatedOnlyByRejection() {
        var normalizedProperties = validEnabled();
        normalizedProperties.setAllowlistHashes(List.of("  " + HASH.toUpperCase(java.util.Locale.ROOT) + "  "));
        assertThat(ProductionSearchShadowPropertiesValidator.validate(normalizedProperties).allowlistHashes())
                .containsExactly(HASH);

        var duplicateProperties = validEnabled();
        duplicateProperties.setAllowlistHashes(List.of(HASH, HASH));
        assertThatThrownBy(() -> ProductionSearchShadowPropertiesValidator.validate(duplicateProperties))
                .isInstanceOf(IllegalStateException.class);

        var malformedProperties = validEnabled();
        malformedProperties.setAllowlistHashes(List.of("42"));
        assertThatThrownBy(() -> ProductionSearchShadowPropertiesValidator.validate(malformedProperties))
                .isInstanceOf(IllegalStateException.class);
    }

    private static ProductionSearchShadowProperties validEnabled() {
        var properties = new ProductionSearchShadowProperties();
        properties.setEnabled(true);
        properties.setKillSwitch(false);
        properties.setSamplingBps(10);
        properties.setAllowlistHashes(List.of(HASH));
        return properties;
    }
}
