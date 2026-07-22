package com.jc.data.contract.v1.projection;

import java.util.List;
import java.util.Objects;

public final class ProjectionResult<T extends ProjectionRecord> {
    private final ProjectionRun run;
    private final List<T> records;
    private final List<ProjectionLineage> lineage;
    private final ProjectionSnapshot snapshot;
    private final ProjectionFailure failure;

    private ProjectionResult(
            ProjectionRun run,
            List<T> records,
            List<ProjectionLineage> lineage,
            ProjectionSnapshot snapshot,
            ProjectionFailure failure) {
        this.run = run;
        this.records = List.copyOf(records);
        this.lineage = List.copyOf(lineage);
        this.snapshot = snapshot;
        this.failure = failure;
    }

    public static <T extends ProjectionRecord> ProjectionResult<T> success(
            ProjectionRun run,
            List<T> records,
            List<ProjectionLineage> lineage,
            ProjectionSnapshot snapshot) {
        Objects.requireNonNull(run, "run");
        Objects.requireNonNull(snapshot, "snapshot");
        if (records.isEmpty() || lineage.isEmpty()) {
            throw new IllegalArgumentException("successful projection requires records and lineage");
        }
        return new ProjectionResult<>(run, records, lineage, snapshot, null);
    }

    public static <T extends ProjectionRecord> ProjectionResult<T> failure(ProjectionFailure failure) {
        return new ProjectionResult<>(null, List.of(), List.of(), null,
                Objects.requireNonNull(failure, "failure"));
    }

    public boolean isSuccess() {
        return failure == null;
    }

    public ProjectionRun run() {
        return run;
    }

    public List<T> records() {
        return records;
    }

    public List<ProjectionLineage> lineage() {
        return lineage;
    }

    public ProjectionSnapshot snapshot() {
        return snapshot;
    }

    public ProjectionFailure failure() {
        return failure;
    }
}
