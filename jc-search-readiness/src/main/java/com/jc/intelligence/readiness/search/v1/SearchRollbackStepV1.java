package com.jc.intelligence.readiness.search.v1;

import java.util.Objects;

public record SearchRollbackStepV1(
        SearchRollbackLevel level,
        String ownerRef,
        boolean requiresDeployment,
        boolean legacyResponseImpact,
        boolean evidencePreserved,
        String verificationCode) {
    public SearchRollbackStepV1 {
        Objects.requireNonNull(level, "level");
        if (ownerRef == null || !ownerRef.matches("[a-z0-9][a-z0-9_./:-]{0,99}")) throw new IllegalArgumentException("invalid ownerRef");
        if (legacyResponseImpact) throw new IllegalArgumentException("rollback cannot affect legacy response");
        if (verificationCode == null || !verificationCode.matches("[a-z][a-z0-9_]{0,63}")) throw new IllegalArgumentException("invalid verificationCode");
    }
}
