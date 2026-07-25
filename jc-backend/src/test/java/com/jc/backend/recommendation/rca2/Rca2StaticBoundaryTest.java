package com.jc.backend.recommendation.rca2;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

class Rca2StaticBoundaryTest {
    private static final Path ROOT = Path.of("..").toAbsolutePath().normalize();

    @Test void defaultsRemainOffZeroAndProductionIsExcluded() throws Exception {
        String config = Files.readString(ROOT.resolve("jc-backend/src/main/resources/application-rca2-isolated-nonproduction.yml"));
        assertThat(config).contains("flag: off", "traffic-percent: 0", "max-production-dark-read-percent: 0",
                "production-route-allowed: false", "config-signature-verified: false");
        String spring = Files.readString(ROOT.resolve("jc-backend/src/main/java/com/jc/backend/recommendation/rca2/Rca2SpringConfiguration.java"));
        assertThat(spring).contains("!prod", "!production", "Rca2BoundedExecutor")
                .doesNotContain("ForkJoinPool", "newCachedThreadPool", "CallerRunsPolicy");
    }

    @Test void sql53PlusAndPersistentRca2ObjectsAreAbsent() throws Exception {
        Path db = ROOT.resolve("database");
        try (Stream<Path> files = Files.walk(db)) {
            assertThat(files.filter(Files::isRegularFile)
                    .map(path -> path.getFileName().toString())
                    .anyMatch(name -> name.matches("(?:V)?(?:0*5[3-9]|[6-9][0-9]|[1-9][0-9]{2,}).*\\.sql"))).isFalse();
        }
        try (Stream<Path> files = Files.walk(ROOT.resolve("jc-backend/src/main/java/com/jc/backend/recommendation/rca2"))) {
            for (Path path : files.filter(Files::isRegularFile).toList()) {
                String source = Files.readString(path);
                assertThat(source).doesNotContain("JpaRepository", "JdbcTemplate", "EntityManager", "ApplicationEventPublisher",
                        "KafkaTemplate", "Repository.save", "flush()", "@Transactional", "production.example", "jdbc:postgresql://prod");
            }
        }
    }
}
