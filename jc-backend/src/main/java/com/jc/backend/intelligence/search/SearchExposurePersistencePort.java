package com.jc.backend.intelligence.search;

import java.util.Objects;

public interface SearchExposurePersistencePort {

    StoreBatchResult store(SearchExposureCanonicalizer.CanonicalBatch batch);

    static SearchExposurePersistencePort disabledPendingApproval() {
        return batch -> {
            Objects.requireNonNull(batch, "canonical batch is required");
            return new StoreBatchResult(
                    Status.DISABLED_PENDING_APPROVAL,
                    0,
                    0);
        };
    }

    enum Status {
        STORED,
        DUPLICATE,
        DISABLED_PENDING_APPROVAL
    }

    record StoreBatchResult(
            Status status,
            int storedCount,
            int duplicateCount) {
        public StoreBatchResult {
            Objects.requireNonNull(status, "status is required");
            if (storedCount < 0 || duplicateCount < 0) {
                throw new IllegalArgumentException("store counts must not be negative");
            }
            if (status == Status.DISABLED_PENDING_APPROVAL
                    && (storedCount != 0 || duplicateCount != 0)) {
                throw new IllegalArgumentException(
                        "disabled persistence cannot report stored or duplicate rows");
            }
        }
    }
}
