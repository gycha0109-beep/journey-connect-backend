package com.jc.backend.search.shadow.production;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class ProductionSearchShadowSamplingGateTest {
    @Test
    void tenBasisPointsMeansTenOfTenThousandNotTenPercent() {
        var gate = new ProductionSearchShadowSamplingGate(10);
        var decision = gate.decide(
                "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef");
        assertThat(decision.basisPoints()).isEqualTo(10);
        assertThat(decision.bucket()).isBetween(0, 9_999);
        assertThat(decision.included()).isEqualTo(decision.bucket() < 10);
    }

    @Test
    void zeroAlwaysExcludesAndElevenIsRejected() {
        var zero = new ProductionSearchShadowSamplingGate(0).decide(
                "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef");
        assertThat(zero.included()).isFalse();
        assertThatThrownBy(() -> new ProductionSearchShadowSamplingGate(11))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
