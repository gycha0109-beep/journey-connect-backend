package com.jc.backend.recommendation.rca2;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

class Rca2Op1BoundaryUnitTest {
    private static final Instant NOW = Instant.parse("2026-07-27T00:00:00Z");
    private static final String HOST = "candidate.nonprod.journey-connect.internal";
    private static final String HASH = "a".repeat(64);

    @Test void endpointPolicyRequiresHttpsAllowlistReadOnlyPathAndRejectsProductionLikeRoutes() {
        var policy = new Rca2ShadowEndpointPolicy(Set.of(HOST), Set.of(".nonprod.journey-connect.internal"), false);
        assertThat(policy.validate("https://" + HOST + "/v1/candidates/read", "jc-rca2-stage1", "").allowed()).isTrue();
        assertThat(policy.validate("http://" + HOST + "/v1/candidates/read", "jc-rca2-stage1", "").rejection())
                .isEqualTo(Rca2ShadowEndpointPolicy.Rejection.HTTPS_REQUIRED);
        assertThat(policy.validate("https://candidate.prod.journey-connect.internal/v1/candidates/read", "jc-rca2-stage1", "").rejection())
                .isEqualTo(Rca2ShadowEndpointPolicy.Rejection.PRODUCTION_HOST_FORBIDDEN);
        assertThat(policy.validate("https://127.0.0.1/v1/candidates/read", "jc-rca2-stage1", "").rejection())
                .isEqualTo(Rca2ShadowEndpointPolicy.Rejection.IP_LITERAL_FORBIDDEN);
        assertThat(policy.validate("https://" + HOST + "/write", "jc-rca2-stage1", "").rejection())
                .isEqualTo(Rca2ShadowEndpointPolicy.Rejection.PATH_NOT_ALLOWLISTED);
        assertThat(policy.validate("https://user:secret@" + HOST + "/v1/candidates/read", "jc-rca2-stage1", "").rejection())
                .isEqualTo(Rca2ShadowEndpointPolicy.Rejection.USERINFO_FORBIDDEN);
        assertThat(policy.validate("https://" + HOST + "/v1/candidates/read?redirect=x", "jc-rca2-stage1", "").rejection())
                .isEqualTo(Rca2ShadowEndpointPolicy.Rejection.QUERY_FORBIDDEN);
        assertThat(policy.validate("https://" + HOST + "/v1/candidates/read", "production", "").rejection())
                .isEqualTo(Rca2ShadowEndpointPolicy.Rejection.PRODUCTION_NAMESPACE_FORBIDDEN);
        assertThat(policy.validate("https://" + HOST + "/v1/candidates/read", "jc-rca2-stage1", "jdbc:production").rejection())
                .isEqualTo(Rca2ShadowEndpointPolicy.Rejection.PRODUCTION_DATABASE_ROUTE_FORBIDDEN);
    }

    @Test void credentialBoundaryRejectsMissingExpiredRevokedLongLivedProductionAndWriteScopes() {
        var ready = lease(NOW.minusSeconds(10), NOW.plusSeconds(600), Set.of("candidate:read"), false);
        assertThat(Rca2WorkloadCredentialProvider.validate(ready, NOW))
                .isEqualTo(Rca2WorkloadCredentialProvider.Status.READY);
        assertThat(Rca2WorkloadCredentialProvider.validate(lease(NOW.minusSeconds(100), NOW, Set.of("candidate:read"), false), NOW))
                .isEqualTo(Rca2WorkloadCredentialProvider.Status.EXPIRED);
        assertThat(Rca2WorkloadCredentialProvider.validate(lease(NOW, NOW.plusSeconds(3601), Set.of("candidate:read"), false), NOW))
                .isEqualTo(Rca2WorkloadCredentialProvider.Status.TTL_EXCEEDED);
        assertThat(Rca2WorkloadCredentialProvider.validate(lease(NOW, NOW.plusSeconds(600), Set.of("production:read"), false), NOW))
                .isEqualTo(Rca2WorkloadCredentialProvider.Status.PRODUCTION_SCOPE);
        assertThat(Rca2WorkloadCredentialProvider.validate(lease(NOW, NOW.plusSeconds(600), Set.of("candidate:write"), false), NOW))
                .isEqualTo(Rca2WorkloadCredentialProvider.Status.WRITE_SCOPE);
        assertThat(Rca2WorkloadCredentialProvider.validate(lease(NOW, NOW.plusSeconds(600), Set.of("candidate:read"), true), NOW))
                .isEqualTo(Rca2WorkloadCredentialProvider.Status.REVOKED);
        assertThat(ready.toString()).contains("REDACTED").doesNotContain("fixture-token");
    }

    @Test void allowlistIsHashedDefaultDenyPurposeEnvironmentExpiryAndRevocationBound() {
        var allowlist = new Rca2TestAccountAllowlist();
        var entry = entry(HASH, false, false, NOW.plusSeconds(600));
        var provider = new Rca2TestAccountAllowlist.InMemoryNonProductionFixture(List.of(entry));
        assertThat(allowlist.evaluate(provider, HASH, Rca2TestAccountAllowlist.PURPOSE,
                Rca2TestAccountAllowlist.ENVIRONMENT, NOW).allowed()).isTrue();
        assertThat(allowlist.evaluate(provider, "b".repeat(64), Rca2TestAccountAllowlist.PURPOSE,
                Rca2TestAccountAllowlist.ENVIRONMENT, NOW).status())
                .isEqualTo(Rca2TestAccountAllowlist.Status.NOT_FOUND);
        assertThat(allowlist.evaluate(provider, HASH, "other", Rca2TestAccountAllowlist.ENVIRONMENT, NOW).status())
                .isEqualTo(Rca2TestAccountAllowlist.Status.WRONG_PURPOSE);
        assertThat(allowlist.evaluate(provider, HASH, Rca2TestAccountAllowlist.PURPOSE, "PRODUCTION", NOW).status())
                .isEqualTo(Rca2TestAccountAllowlist.Status.WRONG_ENVIRONMENT);
        var expired = new Rca2TestAccountAllowlist.InMemoryNonProductionFixture(
                List.of(entry(HASH, false, false, NOW.minusSeconds(1))));
        assertThat(allowlist.evaluate(expired, HASH, Rca2TestAccountAllowlist.PURPOSE,
                Rca2TestAccountAllowlist.ENVIRONMENT, NOW).status())
                .isEqualTo(Rca2TestAccountAllowlist.Status.EXPIRED);
        var revoked = new Rca2TestAccountAllowlist.InMemoryNonProductionFixture(
                List.of(entry(HASH, true, false, NOW.plusSeconds(600))));
        assertThat(allowlist.evaluate(revoked, HASH, Rca2TestAccountAllowlist.PURPOSE,
                Rca2TestAccountAllowlist.ENVIRONMENT, NOW).status())
                .isEqualTo(Rca2TestAccountAllowlist.Status.REVOKED);
        assertThatThrownBy(() -> new Rca2TestAccountAllowlist.InMemoryNonProductionFixture(List.of(entry, entry)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test void stableHashIsDeterministicProcessIndependentAndBoundedToOnePercent() {
        var selectorA = new Rca2StableHashCohortSelector("salt-v1", "c".repeat(64));
        var selectorB = new Rca2StableHashCohortSelector("salt-v1", "c".repeat(64));
        String subject = Rca2StableHashCohortSelector.hashSubjectRef("synthetic:fixture-a");
        assertThat(selectorA.select(subject, 0, true, Rca2RuntimeContracts.ENVIRONMENT).selected()).isFalse();
        assertThat(selectorA.select(subject, 1, true, Rca2RuntimeContracts.ENVIRONMENT).bucket())
                .isEqualTo(selectorB.select(subject, 1, true, Rca2RuntimeContracts.ENVIRONMENT).bucket());
        assertThat(selectorA.select(subject, 2, true, Rca2RuntimeContracts.ENVIRONMENT).status())
                .isEqualTo(Rca2StableHashCohortSelector.Status.INVALID_PERCENT);
        assertThat(selectorA.select(subject, -1, true, Rca2RuntimeContracts.ENVIRONMENT).status())
                .isEqualTo(Rca2StableHashCohortSelector.Status.INVALID_PERCENT);
        assertThat(selectorA.select(subject, 1, false, Rca2RuntimeContracts.ENVIRONMENT).status())
                .isEqualTo(Rca2StableHashCohortSelector.Status.NOT_ALLOWLISTED);
        assertThat(selectorA.select(subject, 1, true, "PRODUCTION").status())
                .isEqualTo(Rca2StableHashCohortSelector.Status.PRODUCTION_ENVIRONMENT);
        assertThatThrownBy(() -> Rca2StableHashCohortSelector.hashSubjectRef("user:42"))
                .isInstanceOf(IllegalArgumentException.class);

        long selected = java.util.stream.IntStream.range(0, 10_000)
                .mapToObj(index -> Rca2StableHashCohortSelector.hashSubjectRef("synthetic:distribution-" + index))
                .filter(hash -> selectorA.select(hash, 1, true, Rca2RuntimeContracts.ENVIRONMENT).selected())
                .count();
        assertThat(selected).isBetween(50L, 150L);
        assertThat(new Rca2StableHashCohortSelector("salt-v2", "c".repeat(64))
                .select(subject, 1, true, Rca2RuntimeContracts.ENVIRONMENT).saltVersion()).isEqualTo("salt-v2");
    }

    @Test void candidateSourceAndProtocolBoundaryRemainReadOnlyNonServingAndUnresolved() {
        var unresolved = Rca2CandidateSourceDecision.unresolved();
        assertThat(unresolved.ready()).isFalse();
        assertThat(Rca2CandidateAdapter.isolatedContractOnly().sourceDecision().ready()).isFalse();
        assertThat(Rca2CandidateAdapter.isolatedContractOnly().readOnly()).isTrue();
        assertThat(Rca2CandidateAdapter.isolatedContractOnly().servingAllowed()).isFalse();
        assertThatThrownBy(() -> new Rca2CandidateSourceDecision("source", Rca2CandidateSourceDecision.Protocol.HTTP,
                "v1", "INTELLIGENCE", true, true, true, false)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test void op1ConfigurationForcesFlagOffTrafficZeroCeilingOneAndNoAutomaticRamp() {
        var environment = baseEnvironment();
        var configuration = Rca2Op1Configuration.from(environment);
        assertThat(configuration.shadowEnabled()).isFalse();
        assertThat(configuration.configuredTrafficPercent()).isZero();
        assertThat(configuration.effectiveTrafficPercent()).isZero();
        assertThat(configuration.maxConfigurablePercent()).isEqualTo(1);
        assertThat(configuration.automaticRamp()).isFalse();
        assertThatThrownBy(() -> Rca2Op1Configuration.from(baseEnvironment()
                .withProperty("app.recommendation.rca2.traffic-percent", "1")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Rca2Op1Configuration.from(baseEnvironment()
                .withProperty("app.recommendation.rca2.shadow.enabled", "true")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static MockEnvironment baseEnvironment() {
        return new MockEnvironment()
                .withProperty("app.recommendation.rca2.environment", Rca2RuntimeContracts.ENVIRONMENT)
                .withProperty("app.recommendation.rca2.shadow.enabled", "false")
                .withProperty("app.recommendation.rca2.traffic-percent", "0")
                .withProperty("app.recommendation.rca2.op1.effective-traffic-percent", "0")
                .withProperty("app.recommendation.rca2.op1.max-configurable-percent", "1")
                .withProperty("app.recommendation.rca2.op1.automatic-ramp", "false")
                .withProperty("app.recommendation.rca2.op1.manual-enablement-implemented", "false");
    }

    private static Rca2WorkloadCredentialProvider.Lease lease(
            Instant issuedAt, Instant expiresAt, Set<String> scopes, boolean revoked) {
        return new Rca2WorkloadCredentialProvider.Lease("fixture-token".toCharArray(), HASH,
                Rca2WorkloadCredentialProvider.REQUIRED_AUDIENCE, scopes, issuedAt, expiresAt, revoked);
    }

    private static Rca2TestAccountAllowlist.Entry entry(
            String hash, boolean revoked, boolean disabled, Instant validUntil) {
        return new Rca2TestAccountAllowlist.Entry(hash, Rca2TestAccountAllowlist.PURPOSE,
                Rca2TestAccountAllowlist.ENVIRONMENT, NOW.minusSeconds(60), validUntil,
                revoked, disabled, "OP1_TEST_FIXTURE");
    }
}
