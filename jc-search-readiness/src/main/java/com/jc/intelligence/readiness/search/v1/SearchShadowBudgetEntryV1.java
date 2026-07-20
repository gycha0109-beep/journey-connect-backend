package com.jc.intelligence.readiness.search.v1;

import java.util.Objects;

public record SearchShadowBudgetEntryV1(
        SearchBudgetKey key,
        SearchPrerequisiteStatus status,
        String approvedValue,
        String unit,
        String ownerRef) {
    public SearchShadowBudgetEntryV1 {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(status, "status");
        unit = safe(unit, "unit");
        ownerRef = safe(ownerRef, "ownerRef");
        if (status == SearchPrerequisiteStatus.RESOLVED) {
            if (approvedValue == null || approvedValue.isBlank() || "unresolved".equals(approvedValue)) {
                throw new IllegalArgumentException("resolved budget requires approvedValue");
            }
            if ("unassigned".equals(ownerRef)) throw new IllegalArgumentException("resolved budget requires owner");
        } else if (approvedValue != null && !"unresolved".equals(approvedValue)) {
            throw new IllegalArgumentException("unresolved budget cannot contain approved production value");
        }
        approvedValue = approvedValue == null ? "unresolved" : approvedValue;
        if (!approvedValue.matches("[a-z0-9][a-z0-9_.:/-]{0,99}")) {
            throw new IllegalArgumentException("approvedValue must be a safe token");
        }
    }
    private static String safe(String value, String field) {
        if (value == null || !value.matches("[a-z0-9][a-z0-9_./:-]{0,99}")) throw new IllegalArgumentException(field + " must be safe");
        return value;
    }
}
