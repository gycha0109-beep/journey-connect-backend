package com.jc.backend.recommendation.rca2;

import java.util.Objects;

/** Validates candidate evidence while preserving the primary response as the only served result. */
public final class Rca2CandidateResponseMapper {
    public static final int MAX_RESPONSE_BYTES = 65_536;
    public static final String SERVING = "FORBIDDEN";

    public Rca2RuntimeContracts.CandidateResult validate(
            Rca2RuntimeContracts.ShadowRequest request,
            Rca2RuntimeContracts.CandidateResult candidate,
            int responseBytes,
            String protocolVersion) {
        Objects.requireNonNull(request, "request");
        Objects.requireNonNull(candidate, "candidate");
        if (responseBytes < 0 || responseBytes > MAX_RESPONSE_BYTES) {
            throw new IllegalArgumentException("candidate response payload exceeds boundary");
        }
        if (protocolVersion == null || protocolVersion.isBlank() || "UNRESOLVED".equals(protocolVersion)) {
            throw new IllegalArgumentException("candidate protocol version is unresolved");
        }
        if (candidate.lane() != request.primary().lane()) {
            throw new IllegalArgumentException("candidate lane mismatch");
        }
        return candidate;
    }
}
