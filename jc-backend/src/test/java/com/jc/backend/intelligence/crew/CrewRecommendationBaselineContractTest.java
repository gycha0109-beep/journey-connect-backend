package com.jc.backend.intelligence.crew;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class CrewRecommendationBaselineContractTest {

    @Test
    void existingCrewListRemainsLegacyNewestFirstAndUnranked() throws IOException {
        String service = read("jc-backend/src/main/java/com/jc/backend/crew/CrewService.java");
        String repository = read("jc-backend/src/main/java/com/jc/backend/crew/CrewRepository.java");
        String controller = read("jc-backend/src/main/java/com/jc/backend/crew/CrewController.java");

        assertTrue(service.contains("findByRecruitingTrueOrderByCreatedAtDescIdDesc"));
        assertTrue(repository.contains("findByRecruitingTrueOrderByCreatedAtDescIdDesc"));
        assertTrue(controller.contains("crewService.list(pageable)"));
        assertFalse(service.contains("RecommendationCrew"));
        assertFalse(controller.contains("RecommendationCrew"));
    }

    @Test
    void currentCrewDtoStillHasNoTagFieldBeforeCr1() throws IOException {
        String dto = read("jc-backend/src/main/java/com/jc/backend/crew/CrewDtos.java");
        String entity = read("jc-backend/src/main/java/com/jc/backend/crew/Crew.java");

        assertFalse(dto.contains("List<String> tags"));
        assertFalse(dto.contains("tagSlugs"));
        assertFalse(entity.contains("crew_tag"));
        assertFalse(entity.contains("CrewTag"));
    }

    @Test
    void cr0DocumentsKeepDatabaseAndRuntimeMutationOutOfScope() throws IOException {
        String design = read(
                "docs/recommendation/CR-0-CREW-RECOMMENDATION-CONTRACT-DESIGN.md");
        String decision = read(
                "docs/platform/system/CR-0-CREW-RECOMMENDATION-ENTRY-DECISION.md");

        for (String required : new String[] {
                "DB change: NONE",
                "Runtime/API change: NONE",
                "CR-1 migration sequence: UNASSIGNED",
                "DEFAULT_ENABLED=false",
                "crew-service-list-v1"
        }) {
            assertTrue(design.contains(required), "CR-0 design missing: " + required);
        }
        assertTrue(decision.contains("CR-1 DB sequence: UNASSIGNED"));
        assertTrue(decision.contains("crew_recommendation_exposure_v1"));
        assertTrue(decision.contains("PROPOSED_NOT_REGISTERED"));
    }

    private static String read(String relativePath) throws IOException {
        return Files.readString(repositoryRoot().resolve(relativePath))
                .replace("\r\n", "\n")
                .replace('\r', '\n');
    }

    private static Path repositoryRoot() {
        Path current = Path.of("").toAbsolutePath().normalize();
        for (Path candidate = current; candidate != null; candidate = candidate.getParent()) {
            if (Files.isDirectory(candidate.resolve("docs/recommendation"))
                    && Files.isRegularFile(candidate.resolve("jc-backend/build.gradle.kts"))) {
                return candidate;
            }
        }
        throw new IllegalStateException("repository root not found from " + current);
    }
}
