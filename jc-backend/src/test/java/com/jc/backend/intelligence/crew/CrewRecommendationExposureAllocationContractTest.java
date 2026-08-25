package com.jc.backend.intelligence.crew;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class CrewRecommendationExposureAllocationContractTest {

    @Test
    void successorAllocationRegistersCrewExposureWithoutStealingExistingAuthorities() throws IOException {
        String allocation = read("docs/platform/governance/SC-PF4-CREW-EXPOSURE-ALLOCATION.md");

        for (String required : new String[] {
                "APPROVED / IMPLEMENTATION_AUTHORITY_GRANTED",
                "crew_recommendation_exposure_v1",
                "EXPOSURE_SEMANTIC=server_delivery_commit_v1",
                "PERSISTENCE_REQUIRED_BEFORE_RESPONSE=YES",
                "PERSISTENCE_FAILURE_BEHAVIOR=FAIL_PERSONALIZED_RESPONSE",
                "VIEWPORT_IMPRESSION_SEMANTIC=NO",
                "61_crew_recommendation_exposure.sql",
                "62_crew_recommendation_exposure_smoke_test.sql",
                "SQL `63+` remains unallocated",
                "recommendation_p2_experiment_exposure",
                "/api/v1/recommendation/crews"
        }) {
            assertTrue(allocation.contains(required), "Crew exposure allocation missing: " + required);
        }

        assertTrue(allocation.contains("writing Crew delivery evidence into `recommendation_p2_experiment_exposure`"));
        assertTrue(allocation.contains("reusing Search exposure tables or Search CTR denominator semantics"));
        assertTrue(allocation.contains("reinterpreting `recommendation_behavior_event` IMPRESSION as Crew delivery evidence"));
        assertTrue(allocation.contains("public legacy `/api/v1/crews` feed"));
        assertTrue(allocation.contains("may not switch APP and RECOMMENDATION roles"));
    }

    @Test
    void allocationDoesNotPretendViewportOrLegacyCutoverAuthorityExists() throws IOException {
        String allocation = read("docs/platform/governance/SC-PF4-CREW-EXPOSURE-ALLOCATION.md");
        assertTrue(allocation.contains("It does **not** mean that the client rendered the response"));
        assertTrue(allocation.contains("replacing the ordering of public `/api/v1/crews`"));
        assertTrue(allocation.contains("removing the legacy newest-first fallback"));
        assertFalse(allocation.contains("VIEWPORT_IMPRESSION_SEMANTIC=YES"));
    }

    private static String read(String relativePath) throws IOException {
        return Files.readString(repositoryRoot().resolve(relativePath))
                .replace("\r\n", "\n")
                .replace('\r', '\n');
    }

    private static Path repositoryRoot() {
        Path current = Path.of("").toAbsolutePath().normalize();
        for (Path candidate = current; candidate != null; candidate = candidate.getParent()) {
            if (Files.isDirectory(candidate.resolve("docs/platform/governance"))
                    && Files.isRegularFile(candidate.resolve("jc-backend/build.gradle.kts"))) {
                return candidate;
            }
        }
        throw new IllegalStateException("repository root not found from " + current);
    }
}
