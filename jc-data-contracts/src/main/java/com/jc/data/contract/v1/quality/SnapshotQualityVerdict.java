package com.jc.data.contract.v1.quality;

import java.math.BigDecimal;
import java.util.Objects;

public record SnapshotQualityVerdict(
        String snapshotRef,
        String validationRunRef,
        String qualityPolicyVersion,
        SnapshotQualityStatus overallStatus,
        long blockerCount,
        long errorCount,
        long warningCount,
        long passedCheckCount,
        long failedCheckCount,
        long skippedRequiredCheckCount,
        BigDecimal qualityScore,
        String verdictFingerprint) {
    public SnapshotQualityVerdict {
        snapshotRef = QualityContractSupport.reference(snapshotRef, "snapshotRef");
        validationRunRef = QualityContractSupport.reference(validationRunRef, "validationRunRef");
        qualityPolicyVersion = QualityContractSupport.version(qualityPolicyVersion, "qualityPolicyVersion");
        Objects.requireNonNull(overallStatus, "overallStatus");
        if (blockerCount < 0 || errorCount < 0 || warningCount < 0 || passedCheckCount < 0
                || failedCheckCount < 0 || skippedRequiredCheckCount < 0) {
            throw new IllegalArgumentException("verdict counts cannot be negative");
        }
        qualityScore = QualityContractSupport.decimal(qualityScore, "qualityScore");
        if (qualityScore.signum() < 0 || qualityScore.compareTo(new BigDecimal("100")) > 0) {
            throw new IllegalArgumentException("qualityScore out of range");
        }
        verdictFingerprint = QualityContractSupport.fingerprint(verdictFingerprint, "verdictFingerprint");
    }
}
