package com.jc.backend.crew;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class CrewOpenChatImplementationBoundaryTest {

    @Test
    void recommendationProjectionDoesNotReadOpenChatUrl() throws IOException {
        String source = read("jc-backend/src/main/java/com/jc/backend/intelligence/crew/CrewRecommendationCandidateSource.java");

        assertFalse(source.contains("open_chat_url"));
        assertFalse(source.contains("select c.*"));
        for (String required : new String[] {
                "c.id crew_id",
                "c.owner_id",
                "c.travel_date",
                "c.capacity",
                "c.recruiting",
                "c.created_at"
        }) {
            assertTrue(source.contains(required), "Crew recommendation projection missing: " + required);
        }
    }

    @Test
    void canonicalSql63And64CopiesRemainByteIdentical() throws IOException {
        assertByteIdentical("63_crew_open_chat.sql");
        assertByteIdentical("64_crew_open_chat_smoke_test.sql");
    }

    @Test
    void sql63NarrowsRecommendationCrewReadToFactColumnsOnly() throws IOException {
        String sql = read("database/journey-connect-db-v2.7/63_crew_open_chat.sql");

        assertTrue(sql.contains("ADD COLUMN open_chat_url varchar(500)"));
        assertTrue(sql.contains("GRANT UPDATE (open_chat_url) ON public.crews TO jc_app"));
        assertTrue(sql.contains("REVOKE SELECT ON public.crews FROM jc_recommendation"));
        assertTrue(sql.contains("GRANT SELECT ("));
        for (String required : new String[] {
                "id,",
                "owner_id,",
                "region_id,",
                "travel_date,",
                "capacity,",
                "recruiting,",
                "created_at"
        }) {
            assertTrue(sql.contains(required), "PF6 recommendation Crew fact grant missing: " + required);
        }
        assertFalse(sql.contains("GRANT SELECT (open_chat_url"));
    }

    private void assertByteIdentical(String fileName) throws IOException {
        byte[] canonical = Files.readAllBytes(repositoryRoot()
                .resolve("database/journey-connect-db-v2.7")
                .resolve(fileName));
        byte[] mirror = Files.readAllBytes(repositoryRoot()
                .resolve("jc-backend/src/test/resources/db/canonical")
                .resolve(fileName));
        assertTrue(java.util.Arrays.equals(canonical, mirror), fileName + " canonical/test mirror drift");
    }

    private String read(String relativePath) throws IOException {
        return Files.readString(repositoryRoot().resolve(relativePath))
                .replace("\r\n", "\n")
                .replace('\r', '\n');
    }

    private Path repositoryRoot() {
        Path current = Path.of("").toAbsolutePath().normalize();
        for (Path candidate = current; candidate != null; candidate = candidate.getParent()) {
            if (Files.isDirectory(candidate.resolve("database/journey-connect-db-v2.7"))
                    && Files.isRegularFile(candidate.resolve("jc-backend/build.gradle.kts"))) {
                return candidate;
            }
        }
        throw new IllegalStateException("repository root not found from " + current);
    }
}
