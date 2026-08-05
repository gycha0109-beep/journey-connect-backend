package com.jc.backend.intelligence.search;

public interface SearchCtrProjectionPort {

    WriteResult write(SearchCtrCanonicalizer.CanonicalProjection projection);

    static SearchCtrProjectionPort disabledPendingApproval() {
        return projection -> {
            if (projection == null) {
                throw new IllegalArgumentException("search CTR projection is required");
            }
            return new WriteResult(WriteStatus.DISABLED_PENDING_APPROVAL, projection.fingerprint());
        };
    }

    enum WriteStatus {
        DISABLED_PENDING_APPROVAL
    }

    record WriteResult(WriteStatus status, String projectionFingerprint) {}
}
