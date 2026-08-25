package com.jc.backend.recommendation.rca2;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

class Rca2StaticBoundaryTest {
    private static final Path ROOT = locateRepositoryRoot();
    private static final Pattern SQL_FILE = Pattern.compile("^(\\d{2})_.*\\.sql$");

    @Test void defaultsRemainOffZeroAndProductionIsExcluded() throws Exception {
        String config = Files.readString(ROOT.resolve("jc-backend/src/main/resources/application-rca2-isolated-nonproduction.yml"));
        assertThat(config).contains("flag: off", "traffic-percent: 0", "max-production-dark-read-percent: 0",
                "production-route-allowed: false", "config-signature-verified: false");
        String spring = Files.readString(ROOT.resolve("jc-backend/src/main/java/com/jc/backend/recommendation/rca2/Rca2SpringConfiguration.java"));
        assertThat(spring).contains("!prod", "!production", "Rca2BoundedExecutor")
                .doesNotContain("ForkJoinPool", "newCachedThreadPool", "CallerRunsPolicy");
    }

    @Test void canonicalSuccessorMigrationsExistAndPersistentRca2ObjectsAreAbsent() throws Exception {
        Path db = ROOT.resolve("database/journey-connect-db-v2.7");
        List<Path> scripts;
        try (Stream<Path> files = Files.list(db)) {
            scripts = files.filter(Files::isRegularFile)
                    .filter(path -> SQL_FILE.matcher(path.getFileName().toString()).matches())
                    .sorted(Comparator.comparingInt(Rca2StaticBoundaryTest::sqlNumber))
                    .toList();
        }
        assertThat(scripts).hasSizeGreaterThanOrEqualTo(54);
        for (int index = 0; index < scripts.size(); index++) {
            assertThat(sqlNumber(scripts.get(index))).isEqualTo(index + 1);
        }
        assertThat(scripts.get(52).getFileName().toString())
                .isEqualTo("53_admin_control_plane_hardening.sql");
        assertThat(scripts.get(53).getFileName().toString())
                .isEqualTo("54_admin_control_plane_hardening_smoke_test.sql");
        assertThat(scripts).noneMatch(path -> path.getFileName().toString().toLowerCase().contains("rca2"));

        try (Stream<Path> files = Files.walk(ROOT.resolve("jc-backend/src/main/java/com/jc/backend/recommendation/rca2"))) {
            for (Path path : files.filter(Files::isRegularFile).toList()) {
                String source = Files.readString(path);
                assertThat(source).doesNotContain("JpaRepository", "JdbcTemplate", "EntityManager", "ApplicationEventPublisher",
                        "KafkaTemplate", "Repository.save", "flush()", "@Transactional", "production.example", "jdbc:postgresql://prod");
            }
        }
    }

    private static int sqlNumber(Path path) {
        Matcher matcher = SQL_FILE.matcher(path.getFileName().toString());
        if (!matcher.matches()) {
            throw new IllegalArgumentException("not a canonical SQL file: " + path);
        }
        return Integer.parseInt(matcher.group(1));
    }

    private static Path locateRepositoryRoot() {
        Path current = Path.of("").toAbsolutePath().normalize();
        for (Path candidate = current; candidate != null; candidate = candidate.getParent()) {
            if (Files.isRegularFile(candidate.resolve("jc-backend/build.gradle.kts"))
                    && Files.isDirectory(candidate.resolve("database"))) {
                return candidate;
            }
        }
        throw new IllegalStateException("RCA-2 repository root not found from " + current);
    }
}
