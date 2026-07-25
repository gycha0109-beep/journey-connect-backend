package com.jc.backend.recommendation.rca2;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.Set;
import org.junit.jupiter.api.Test;

class Rca2FeatureFlagAndIdentityTest {
    private static final Instant NOW = Instant.parse("2026-07-25T12:00:00Z");

    @Test void defaultMissingMalformedExpiredStaleUnknownAndTrafficZeroFailClosed() {
        assertThat(new Rca2FeatureFlagPolicy(Rca2FeatureFlagPolicy.Snapshot.defaultOff(NOW))
                .evaluate(Rca2RuntimeContracts.Lane.P1, NOW).enabled()).isFalse();
        assertOff(new Rca2FeatureFlagPolicy.Snapshot("missing", true, true, 1,
                Rca2RuntimeContracts.ENVIRONMENT, "v", NOW, NOW, NOW.plusSeconds(60), true),
                Rca2FeatureFlagPolicy.Reason.MISSING);
        assertOff(new Rca2FeatureFlagPolicy.Snapshot(" on ", true, true, 1,
                Rca2RuntimeContracts.ENVIRONMENT, "v", NOW, NOW, NOW.plusSeconds(60), true),
                Rca2FeatureFlagPolicy.Reason.MALFORMED);
        assertOff(new Rca2FeatureFlagPolicy.Snapshot("on", true, true, 1,
                Rca2RuntimeContracts.ENVIRONMENT, "v", NOW.minusSeconds(61), NOW.minusSeconds(61), NOW, true),
                Rca2FeatureFlagPolicy.Reason.EXPIRED);
        assertOff(new Rca2FeatureFlagPolicy.Snapshot("on", true, true, 1,
                Rca2RuntimeContracts.ENVIRONMENT, "v", NOW.minusSeconds(121), NOW.minusSeconds(121), NOW.plusSeconds(60), true),
                Rca2FeatureFlagPolicy.Reason.STALE);
        assertOff(new Rca2FeatureFlagPolicy.Snapshot("maybe", true, true, 1,
                Rca2RuntimeContracts.ENVIRONMENT, "v", NOW, NOW, NOW.plusSeconds(60), true),
                Rca2FeatureFlagPolicy.Reason.UNKNOWN);
        assertOff(new Rca2FeatureFlagPolicy.Snapshot("on", true, true, 0,
                Rca2RuntimeContracts.ENVIRONMENT, "v", NOW, NOW, NOW.plusSeconds(60), true),
                Rca2FeatureFlagPolicy.Reason.TRAFFIC_ZERO);
        assertOff(new Rca2FeatureFlagPolicy.Snapshot("on", true, true, 1,
                "PRODUCTION", "v", NOW, NOW, NOW.plusSeconds(60), true),
                Rca2FeatureFlagPolicy.Reason.INVALID_ENVIRONMENT);
        assertOff(new Rca2FeatureFlagPolicy.Snapshot("on", true, true, 1,
                Rca2RuntimeContracts.ENVIRONMENT, "v", NOW, NOW, NOW.plusSeconds(60), false),
                Rca2FeatureFlagPolicy.Reason.UNVERIFIED);
    }

    @Test void laneFlagAndKillSwitchAreIndependent() {
        var policy = enabled(100, true, false);
        assertThat(policy.evaluate(Rca2RuntimeContracts.Lane.P1, NOW).enabled()).isTrue();
        assertThat(policy.evaluate(Rca2RuntimeContracts.Lane.P2, NOW).enabled()).isFalse();
        var kill = new Rca2KillSwitch();
        kill.killLane(Rca2RuntimeContracts.Lane.P1);
        assertThat(kill.laneKilled(Rca2RuntimeContracts.Lane.P1)).isTrue();
        assertThat(kill.laneKilled(Rca2RuntimeContracts.Lane.P2)).isFalse();
        kill.killGlobal();
        assertThat(kill.globalKilled()).isTrue();
    }

    @Test void identityAllowsOnlySyntheticOrExplicitTestAccountAndBlocksActualProductionIdentity() {
        String testHash = Rca2IdentityPolicy.sha256("test-account-a");
        var policy = new Rca2IdentityPolicy(Set.of(testHash), Set.of("rca2-contract-test"));
        var synthetic = identity("synthetic:fixture-a");
        assertThat(policy.validate(synthetic, NOW).allowed()).isTrue();
        assertThat(policy.validate(identity("test-account:" + testHash), NOW).allowed()).isTrue();
        assertThat(policy.validate(identity("user:42"), NOW).reason())
                .isEqualTo(Rca2IdentityPolicy.Reason.ACTUAL_PRODUCTION_IDENTITY_BLOCKED);
        assertThat(policy.validate(identity("anonymous"), NOW).allowed()).isFalse();
    }

    private static Rca2IdentityPolicy.Identity identity(String ref) {
        return new Rca2IdentityPolicy.Identity(ref, Rca2IdentityPolicy.PURPOSE, "rca2-contract-test",
                Rca2RuntimeContracts.ENVIRONMENT, NOW.plusSeconds(60), false, false, true, true);
    }

    private static Rca2FeatureFlagPolicy enabled(int traffic, boolean p1, boolean p2) {
        return new Rca2FeatureFlagPolicy(new Rca2FeatureFlagPolicy.Snapshot("on", p1, p2, traffic,
                Rca2RuntimeContracts.ENVIRONMENT, "v", NOW, NOW, NOW.plusSeconds(60), true));
    }

    private static void assertOff(Rca2FeatureFlagPolicy.Snapshot snapshot, Rca2FeatureFlagPolicy.Reason reason) {
        assertThat(new Rca2FeatureFlagPolicy(snapshot).evaluate(Rca2RuntimeContracts.Lane.P1, NOW).reason()).isEqualTo(reason);
    }
}
