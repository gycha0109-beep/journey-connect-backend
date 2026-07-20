package com.jc.recommendation.foundation;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public final class JavaCoreIsolationContractTest {
    private static final List<String> FORBIDDEN = List.of(
            "org.springframework",
            "jakarta.persistence",
            "org.hibernate");

    private JavaCoreIsolationContractTest() {
    }

    public static void main(String[] args) throws IOException {
        Path root = locateSourceRoot();
        List<String> offenders = new ArrayList<>();
        try (var files = Files.walk(root)) {
            for (Path path : files.filter(candidate -> candidate.toString().endsWith(".java")).toList()) {
                String text = Files.readString(path);
                for (String forbidden : FORBIDDEN) {
                    if (text.contains(forbidden)) {
                        offenders.add(root.relativize(path) + " -> " + forbidden);
                    }
                }
            }
        }
        if (!offenders.isEmpty()) {
            throw new AssertionError("Framework dependency found in pure Java recommendation core: " + offenders);
        }
        System.out.println("Java recommendation core framework isolation: PASS");
    }

    private static Path locateSourceRoot() {
        Path current = Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize();
        while (current != null) {
            Path direct = current.resolve("src/main/java");
            if (Files.isDirectory(direct.resolve("com/jc/recommendation"))) {
                return direct;
            }
            Path nested = current.resolve("../jc-recommendation-core/src/main/java").normalize();
            if (Files.isDirectory(nested.resolve("com/jc/recommendation"))) {
                return nested;
            }
            current = current.getParent();
        }
        throw new IllegalStateException("jc-recommendation-core source root could not be located");
    }
}
