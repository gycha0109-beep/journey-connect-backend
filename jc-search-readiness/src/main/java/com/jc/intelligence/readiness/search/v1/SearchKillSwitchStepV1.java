package com.jc.intelligence.readiness.search.v1;

import java.util.Objects;

public record SearchKillSwitchStepV1(
        int priority,
        SearchKillSwitchKey key,
        SearchPrerequisiteStatus status,
        String ownerRef,
        boolean requiresDeployment,
        String effectCode) {
    public SearchKillSwitchStepV1 {
        if (priority < 1 || priority > 5) throw new IllegalArgumentException("priority must be 1..5");
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(status, "status");
        if (ownerRef == null || !ownerRef.matches("[a-z0-9][a-z0-9_./:-]{0,99}")) throw new IllegalArgumentException("invalid ownerRef");
        if (effectCode == null || !effectCode.matches("[a-z][a-z0-9_]{0,63}")) throw new IllegalArgumentException("invalid effectCode");
    }
}
