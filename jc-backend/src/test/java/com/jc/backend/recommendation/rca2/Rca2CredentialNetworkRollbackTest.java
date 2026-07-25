package com.jc.backend.recommendation.rca2;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.Set;
import org.junit.jupiter.api.Test;

class Rca2CredentialNetworkRollbackTest {
    private final Rca2CredentialNetworkContract contract = new Rca2CredentialNetworkContract();
    private final Instant now = Instant.parse("2026-07-25T00:00:00Z");

    @Test void acceptsOnlyShortLivedReadOnlyNonproductionCredential() {
        var metadata = new Rca2CredentialNetworkContract.CredentialMetadata(
                "a".repeat(64), Rca2RuntimeContracts.ENVIRONMENT, now, now.plusSeconds(3600),
                Set.of("candidate:read"), false, false, "OPERATIONS", "PLATFORM_SECRET_MANAGER");
        var provider = new Rca2CredentialNetworkContract.NonProductionFakeProvider(metadata);
        assertThat(contract.validateCredential(provider, now).allowed()).isTrue();
        provider.revoke();
        assertThat(contract.validateCredential(provider, now).rejection())
                .isEqualTo(Rca2CredentialNetworkContract.Rejection.MISSING);
    }

    @Test void rejectsProductionWriteOwnerAndLongLivedCredential() {
        assertThat(contract.validateCredential(provider(true, false, 3600, Rca2RuntimeContracts.ENVIRONMENT), now).allowed()).isFalse();
        assertThat(contract.validateCredential(provider(false, true, 3600, Rca2RuntimeContracts.ENVIRONMENT), now).allowed()).isFalse();
        assertThat(contract.validateCredential(provider(false, false, 3601, Rca2RuntimeContracts.ENVIRONMENT), now).allowed()).isFalse();
        assertThat(contract.validateCredential(provider(false, false, 3600, "PRODUCTION"), now).allowed()).isFalse();
    }

    @Test void networkIsDenyByDefaultTlsOnlyAndNeverProduction() {
        var allowed = new Rca2CredentialNetworkContract.EndpointContract(
                "b".repeat(64), Rca2RuntimeContracts.ENVIRONMENT, true, true, false);
        assertThat(contract.validateEndpoint(allowed).allowed()).isTrue();
        assertThat(contract.validateEndpoint(new Rca2CredentialNetworkContract.EndpointContract(
                "b".repeat(64), Rca2RuntimeContracts.ENVIRONMENT, true, false, false)).allowed()).isFalse();
        assertThat(contract.validateEndpoint(new Rca2CredentialNetworkContract.EndpointContract(
                "b".repeat(64), Rca2RuntimeContracts.ENVIRONMENT, true, true, true)).allowed()).isFalse();
    }

    @Test void rollbackHasSevenLevelsAndExternalRevocationsRemainNotExecuted() {
        var hierarchy = new Rca2RollbackPlan().hierarchy();
        assertThat(hierarchy).hasSize(7);
        assertThat(hierarchy.get(Rca2RollbackPlan.Level.LEVEL_1_FLAG_OFF).status())
                .isEqualTo(Rca2RollbackPlan.Status.PASS);
        assertThat(hierarchy.get(Rca2RollbackPlan.Level.LEVEL_6_CREDENTIAL_REVOKE).status())
                .isEqualTo(Rca2RollbackPlan.Status.NOT_EXECUTED);
        assertThat(hierarchy.get(Rca2RollbackPlan.Level.LEVEL_7_NETWORK_ROUTE_REVOKE).status())
                .isEqualTo(Rca2RollbackPlan.Status.NOT_EXECUTED);
    }

    private Rca2CredentialNetworkContract.Provider provider(boolean owner, boolean write, long ttl, String environment) {
        var metadata = new Rca2CredentialNetworkContract.CredentialMetadata(
                "c".repeat(64), environment, now, now.plusSeconds(ttl),
                write ? Set.of("candidate:write") : Set.of("candidate:read"), owner, write,
                "OPERATIONS", "PLATFORM_SECRET_MANAGER");
        return new Rca2CredentialNetworkContract.NonProductionFakeProvider(metadata);
    }
}
