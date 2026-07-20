package com.jc.backend.verification;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

final class RepositoryLayout {
    private static final Path ROOT = locateRoot();

    private RepositoryLayout() {
    }

    static Path root() {
        return ROOT;
    }

    static Path resolve(String relative) {
        return ROOT.resolve(relative).normalize();
    }

    static String read(String relative) throws IOException {
        return Files.readString(resolve(relative), StandardCharsets.UTF_8);
    }

    static String relative(Path path) {
        return ROOT.relativize(path.toAbsolutePath().normalize()).toString().replace('\\', '/');
    }

    private static Path locateRoot() {
        Path current = Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize();
        while (current != null) {
            if (Files.isDirectory(current.resolve("jc-backend"))
                    && Files.isDirectory(current.resolve("database/journey-connect-db-v2.1"))) {
                return current;
            }
            current = current.getParent();
        }
        throw new IllegalStateException("Journey Connect repository root could not be located from user.dir");
    }
}
