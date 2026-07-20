package com.jc.intelligence.readiness.search.v1;

import com.jc.intelligence.contract.v1.version.ContractId;
import java.util.ArrayList;
import java.util.List;

public record SearchGradleRegressionManifestV1(
        ContractId contractVersion,
        String gradleVersion,
        int javaVersion,
        List<String> unifiedTaskDependencies,
        List<String> externalBackendCommands,
        boolean ignoreFailures,
        boolean dockerRequiredForBackend,
        String postgresImage) {
    public SearchGradleRegressionManifestV1 {
        if (!SearchReadinessContractIds.REGRESSION.equals(contractVersion)) throw new IllegalArgumentException("unexpected contractVersion");
        if (!"8.14.5".equals(gradleVersion)) throw new IllegalArgumentException("Gradle 8.14.5 is required");
        if (javaVersion != 21) throw new IllegalArgumentException("Java 21 is required");
        unifiedTaskDependencies = safeTasks(unifiedTaskDependencies, "unifiedTaskDependencies");
        externalBackendCommands = safeTasks(externalBackendCommands, "externalBackendCommands");
        if (ignoreFailures) throw new IllegalArgumentException("ignoreFailures is prohibited");
        if (!dockerRequiredForBackend) throw new IllegalArgumentException("backend Testcontainers requirement must be explicit");
        if (!"postgres:15-alpine".equals(postgresImage)) throw new IllegalArgumentException("canonical Testcontainers image mismatch");
    }
    private static List<String> safeTasks(List<String> values, String field) {
        if (values == null || values.isEmpty()) throw new IllegalArgumentException(field + " are required");
        ArrayList<String> copy = new ArrayList<>(values);
        if (copy.stream().anyMatch(value -> value == null || !value.matches("[:.a-zA-Z0-9_-]{2,160}"))) {
            throw new IllegalArgumentException(field + " contain invalid task paths");
        }
        if (copy.stream().distinct().count() != copy.size()) throw new IllegalArgumentException(field + " contain duplicates");
        return List.copyOf(copy);
    }
}
