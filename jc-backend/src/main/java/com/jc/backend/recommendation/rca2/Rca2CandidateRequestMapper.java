package com.jc.backend.recommendation.rca2;

import java.util.Objects;

/** Maps only privacy-safe, bounded evidence references into a read-only candidate request. */
public final class Rca2CandidateRequestMapper {
    public static final int MAX_PAYLOAD_BYTES = 16_384;
    public static final String OPERATION = "READ_ONLY_CANDIDATE_COMPARISON";

    public record CandidateRequest(
            String hashedRequestRef,
            String hashedSubjectRef,
            String lane,
            String checkpointRefHash,
            String lineageFingerprint,
            String operation,
            int estimatedPayloadBytes) {
        public CandidateRequest {
            requireHash(hashedRequestRef, "hashedRequestRef");
            requireHash(hashedSubjectRef, "hashedSubjectRef");
            requireHash(checkpointRefHash, "checkpointRefHash");
            requireHash(lineageFingerprint, "lineageFingerprint");
            lane = Objects.requireNonNull(lane, "lane");
            operation = Objects.requireNonNull(operation, "operation");
            if (!OPERATION.equals(operation)) throw new IllegalArgumentException("operation must be read-only");
            if (estimatedPayloadBytes < 0 || estimatedPayloadBytes > MAX_PAYLOAD_BYTES) {
                throw new IllegalArgumentException("candidate request payload exceeds boundary");
            }
        }
    }

    public CandidateRequest map(Rca2RuntimeContracts.ShadowRequest request, String hashedSubjectRef) {
        Objects.requireNonNull(request, "request");
        String checkpoint = Rca2IdentityPolicy.sha256("checkpoint|" + request.primary().checkpoint().opaqueRef());
        return new CandidateRequest(request.hashedRequestRef(), hashedSubjectRef,
                request.primary().lane().name(), checkpoint, request.primary().lineage().fingerprint(),
                OPERATION, 512);
    }

    private static void requireHash(String value, String field) {
        if (value == null || !value.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException(field + " must be sha256");
        }
    }
}
