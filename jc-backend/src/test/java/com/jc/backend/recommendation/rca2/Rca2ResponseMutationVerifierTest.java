package com.jc.backend.recommendation.rca2;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.junit.jupiter.api.Test;

class Rca2ResponseMutationVerifierTest {
    @Test void identicalPrimaryResponsePassesForEveryShadowOutcomeClass() {
        var verifier = new Rca2ResponseMutationVerifier();
        var snapshot = snapshot("body");
        for (String ignored : new String[] {"success", "mismatch", "timeout", "exception", "queue_rejection",
                "breaker_open", "global_kill", "lane_kill", "identity_blocked", "checkpoint_mismatch",
                "lineage_mismatch", "stale_candidate"}) {
            verifier.verifyUnchanged(snapshot, snapshot("body"));
        }
    }

    @Test void anyMutationIsBlocked() {
        var verifier = new Rca2ResponseMutationVerifier();
        assertThatThrownBy(() -> verifier.verifyUnchanged(snapshot("body"), snapshot("changed")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("SHADOW_RESPONSE_MUTATION_DETECTED");
    }

    private static Rca2ResponseMutationVerifier.ResponseSnapshot snapshot(String body) {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        return new Rca2ResponseMutationVerifier.ResponseSnapshot(bytes, 200, Map.of("content-type", "application/json"),
                Rca2IdentityPolicy.sha256(body), Rca2IdentityPolicy.sha256("1,2,3"), 3,
                Rca2IdentityPolicy.sha256("cursor"), "RecommendationP1ProfileSource", "RecommendationP2ObservationSource");
    }
}
