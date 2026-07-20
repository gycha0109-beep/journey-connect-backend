package com.jc.intelligence.runtime.search.v1;

import java.util.Objects;

public record SearchRuntimeStageEvidenceV1(String stage, String status, int itemCount) {
    public SearchRuntimeStageEvidenceV1 {
        Objects.requireNonNull(stage, "stage");
        Objects.requireNonNull(status, "status");
        if (stage.isBlank() || status.isBlank() || itemCount < 0) {
            throw new IllegalArgumentException("invalid stage evidence");
        }
    }
}
