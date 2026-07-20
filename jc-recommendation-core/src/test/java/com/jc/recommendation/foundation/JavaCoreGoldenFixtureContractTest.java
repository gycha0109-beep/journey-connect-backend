package com.jc.recommendation.foundation;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public final class JavaCoreGoldenFixtureContractTest {
    private JavaCoreGoldenFixtureContractTest() {
    }

    public static void main(String[] args) throws Exception {
        Path golden = locateCoreRoot().resolve("src/test/resources/golden");
        List<Fixture> fixtures = List.of(
                new Fixture("foundation-wave1-v1.json", CoreWave1GoldenOracle::main),
                new Fixture("wave2-scoring-v1.json", CoreWave2ScoringGoldenOracle::main),
                new Fixture("wave3-base-ranking-v1.json", CoreWave3BaseRankingGoldenOracle::main),
                new Fixture("wave3-diversity-v1.json", CoreWave3DiversityGoldenOracle::main),
                new Fixture("wave3-exploration-v1.json", CoreWave3ExplorationGoldenOracle::main),
                new Fixture("wave4-ranking-integration-v1.json", CoreWave4RankingIntegrationGoldenOracle::main),
                new Fixture("wave5-exposure-v1.json", CoreWave5ExposureGoldenOracle::main),
                new Fixture("wave6-attribution-v1.json", CoreWave6AttributionGoldenOracle::main),
                new Fixture("wave7-offline-evaluation-v1.json", CoreWave7OfflineEvaluationGoldenOracle::main));

        for (Fixture fixture : fixtures) {
            String expected = normalize(Files.readString(golden.resolve(fixture.fileName()), StandardCharsets.UTF_8));
            String actual = normalize(capture(fixture.oracle()));
            if (!expected.equals(actual)) {
                throw new AssertionError("Java-owned golden fixture drift: " + fixture.fileName());
            }
        }
        System.out.println("Java recommendation core committed golden fixtures: PASS");
    }

    private static String capture(Oracle oracle) throws Exception {
        PrintStream original = System.out;
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (PrintStream captured = new PrintStream(output, true, StandardCharsets.UTF_8)) {
            System.setOut(captured);
            oracle.run(new String[0]);
        } finally {
            System.setOut(original);
        }
        return output.toString(StandardCharsets.UTF_8);
    }

    private static String normalize(String value) {
        return value.replace("\r\n", "\n");
    }

    private static Path locateCoreRoot() throws IOException {
        Path current = Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize();
        while (current != null) {
            if (Files.isDirectory(current.resolve("src/main/java/com/jc/recommendation"))
                    && Files.isDirectory(current.resolve("src/test/resources/golden"))) {
                return current;
            }
            Path nested = current.resolve("../jc-recommendation-core").normalize();
            if (Files.isDirectory(nested.resolve("src/main/java/com/jc/recommendation"))) {
                return nested;
            }
            current = current.getParent();
        }
        throw new IllegalStateException("jc-recommendation-core root could not be located");
    }

    @FunctionalInterface
    private interface Oracle {
        void run(String[] args) throws Exception;
    }

    private record Fixture(String fileName, Oracle oracle) {
    }
}
