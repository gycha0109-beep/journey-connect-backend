package com.jc.backend.verification;

import static org.junit.jupiter.api.Assertions.assertFalse;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("p0-verification")
class JavaOnlyVerificationBoundaryTest {
    @Test
    void backendAndRecommendationVerificationUseOnlyJavaAndGradle() throws IOException {
        List<String> offenders = new ArrayList<>();
        for (String directory : List.of("scripts", "reference/recommendation-ts-2.9b")) {
            Path root = RepositoryLayout.resolve(directory);
            if (!Files.exists(root)) {
                continue;
            }
            try (var files = Files.walk(root)) {
                files.filter(Files::isRegularFile)
                        .filter(path -> {
                            String name = path.getFileName().toString();
                            return name.endsWith(".py") || name.endsWith(".mjs")
                                    || name.endsWith(".js") || name.endsWith(".sh")
                                    || name.endsWith(".ts");
                        })
                        .map(RepositoryLayout::relative)
                        .forEach(offenders::add);
            }
        }
        assertFalse(offenders.size() > 0, () -> "non-Java verification artifacts remain: " + offenders);

        Path workflows = RepositoryLayout.resolve(".github/workflows");
        if (Files.isDirectory(workflows)) {
            try (var files = Files.walk(workflows)) {
                for (Path path : files.filter(Files::isRegularFile).toList()) {
                    String text = Files.readString(path);
                    if (text.contains("setup-node") || text.contains("npm ")
                            || text.contains("python scripts/")
                            || text.contains("bash scripts/recommendation")) {
                        offenders.add(RepositoryLayout.relative(path));
                    }
                }
            }
        }
        assertFalse(offenders.size() > 0, () -> "CI still invokes non-Java verification: " + offenders);
    }
}
