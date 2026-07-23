package com.jc.data.contract.v1.integration;

public record CrossTrackRetentionRule(
        int sourceRetentionDays,
        int targetRetentionDays,
        int integrationEvidenceRetentionDays,
        boolean deletionSemanticsAligned,
        boolean automaticPurgeEnabled,
        boolean physicalDeleteEnabled) {
    public CrossTrackRetentionRule {
        if (sourceRetentionDays < 1 || targetRetentionDays < 1 || integrationEvidenceRetentionDays != 90) {
            throw new IllegalArgumentException("retention days are invalid");
        }
    }
}
