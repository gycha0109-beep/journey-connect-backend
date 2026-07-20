package com.jc.intelligence.runtime.search.v1;

import com.jc.intelligence.contract.v1.run.IntelligenceRunV1;
import com.jc.intelligence.contract.v1.search.run.SearchRunV1;
import com.jc.intelligence.runtime.search.v1.pagination.SearchRuntimePageV1;
import com.jc.intelligence.runtime.search.v1.snapshot.SearchResultSnapshotV1;
import java.util.Objects;

public record SearchRuntimeResultV1(
        SearchRuntimeStatus status,
        SearchResultSnapshotV1 snapshot,
        SearchRuntimePageV1 page,
        SearchRunV1 searchRun,
        IntelligenceRunV1 intelligenceRun,
        SearchRuntimeFailureV1 failure,
        SearchRuntimeFallbackV1 fallback,
        SearchRuntimeEvidenceV1 evidence,
        SearchRuntimeAuthorityV1 authority) {
    public SearchRuntimeResultV1 {
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(evidence, "evidence");
        Objects.requireNonNull(authority, "authority");
        switch (status) {
            case SUCCESS, NO_RESULTS -> {
                if (snapshot == null || page == null || searchRun == null || intelligenceRun == null
                        || failure != null || fallback != null) throw new IllegalArgumentException("invalid successful result shape");
                if ((status == SearchRuntimeStatus.SUCCESS) == snapshot.items().isEmpty()) {
                    throw new IllegalArgumentException("success requires results and no_results requires an empty snapshot");
                }
                if (!snapshot.snapshotId().equals(evidence.snapshotId()) || evidence.failureCode() != null
                        || evidence.fallbackCode() != null || evidence.resultCount() != snapshot.items().size()
                        || !searchRun.outputSnapshotRef().equals(snapshot.snapshotId())
                        || !intelligenceRun.outputSnapshotRef().equals(snapshot.snapshotId())) {
                    throw new IllegalArgumentException("successful evidence mismatch");
                }
            }
            case FALLBACK -> {
                if (snapshot == null || page == null || searchRun == null || intelligenceRun == null
                        || failure != null || fallback == null) throw new IllegalArgumentException("invalid fallback result shape");
                if (!snapshot.snapshotId().equals(evidence.snapshotId()) || evidence.failureCode() != null
                        || evidence.fallbackCode() != fallback.fallbackCode()
                        || evidence.resultCount() != snapshot.items().size()
                        || !searchRun.outputSnapshotRef().equals(snapshot.snapshotId())
                        || !intelligenceRun.outputSnapshotRef().equals(snapshot.snapshotId())) {
                    throw new IllegalArgumentException("fallback evidence mismatch");
                }
            }
            case FAILED, INVALID_REQUEST, DEPENDENCY_UNAVAILABLE -> {
                if (snapshot != null || page != null || searchRun != null || intelligenceRun != null
                        || failure == null || fallback != null) {
                    throw new IllegalArgumentException("invalid failed result shape");
                }
                if (evidence.snapshotId() != null || evidence.failureCode() != failure.failureCode()
                        || evidence.fallbackCode() != null) throw new IllegalArgumentException("failure evidence mismatch");
            }
        }
    }
}
