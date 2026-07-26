package com.jc.backend.recommendation.rca2;

import java.util.Locale;

/** Machine-readable candidate source decision. OP-1 remains unresolved until an actual source is approved. */
public record Rca2CandidateSourceDecision(
        String source,
        Protocol protocol,
        String apiVersion,
        String owner,
        boolean readOnly,
        boolean idempotent,
        boolean servingAllowed,
        boolean productionLike) {

    public enum Protocol { HTTP, GRPC, IN_PROCESS, OTHER, UNRESOLVED }

    public Rca2CandidateSourceDecision {
        source = normalized(source);
        apiVersion = normalized(apiVersion);
        owner = normalized(owner);
        protocol = protocol == null ? Protocol.UNRESOLVED : protocol;
        if (!"INTELLIGENCE".equals(owner)) throw new IllegalArgumentException("candidate owner must be INTELLIGENCE");
        if (servingAllowed) throw new IllegalArgumentException("candidate result serving is forbidden");
        if (productionLike) throw new IllegalArgumentException("production-like candidate source is forbidden");
    }

    public boolean ready() {
        return !"UNRESOLVED".equals(source)
                && protocol != Protocol.UNRESOLVED
                && !"UNRESOLVED".equals(apiVersion)
                && readOnly && idempotent && !servingAllowed && !productionLike;
    }

    public static Rca2CandidateSourceDecision unresolved() {
        return new Rca2CandidateSourceDecision("UNRESOLVED", Protocol.UNRESOLVED, "UNRESOLVED",
                "INTELLIGENCE", true, true, false, false);
    }

    private static String normalized(String value) {
        return value == null || value.isBlank() ? "UNRESOLVED" : value.trim().toUpperCase(Locale.ROOT);
    }
}
