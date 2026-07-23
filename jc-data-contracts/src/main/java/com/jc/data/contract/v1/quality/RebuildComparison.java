package com.jc.data.contract.v1.quality;

import java.util.List;
import java.util.Objects;

public record RebuildComparison(
        boolean matched,
        long expectedRecordCount,
        long observedRecordCount,
        long expectedSubjectCount,
        long observedSubjectCount,
        long expectedSourceCount,
        long observedSourceCount,
        List<String> expectedRecordFingerprints,
        List<String> observedRecordFingerprints,
        String expectedSnapshotFingerprint,
        String observedSnapshotFingerprint,
        String expectedLineageFingerprint,
        String observedLineageFingerprint,
        String comparisonFingerprint) {
    public RebuildComparison {
        if (expectedRecordCount < 0 || observedRecordCount < 0 || expectedSubjectCount < 0
                || observedSubjectCount < 0 || expectedSourceCount < 0 || observedSourceCount < 0) {
            throw new IllegalArgumentException("rebuild counts cannot be negative");
        }
        expectedRecordFingerprints = List.copyOf(Objects.requireNonNull(expectedRecordFingerprints, "expectedRecordFingerprints"));
        observedRecordFingerprints = List.copyOf(Objects.requireNonNull(observedRecordFingerprints, "observedRecordFingerprints"));
        expectedSnapshotFingerprint = QualityContractSupport.fingerprint(expectedSnapshotFingerprint, "expectedSnapshotFingerprint");
        observedSnapshotFingerprint = QualityContractSupport.fingerprint(observedSnapshotFingerprint, "observedSnapshotFingerprint");
        expectedLineageFingerprint = QualityContractSupport.fingerprint(expectedLineageFingerprint, "expectedLineageFingerprint");
        observedLineageFingerprint = QualityContractSupport.fingerprint(observedLineageFingerprint, "observedLineageFingerprint");
        comparisonFingerprint = QualityContractSupport.fingerprint(comparisonFingerprint, "comparisonFingerprint");
    }
}
