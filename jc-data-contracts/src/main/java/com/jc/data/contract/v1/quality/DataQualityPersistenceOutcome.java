package com.jc.data.contract.v1.quality;

public record DataQualityPersistenceOutcome(
        DataQualityPersistenceDisposition disposition,
        String validationRunRef,
        String snapshotRef,
        String stableFailureCode) {
    public static final String CONFLICT_CODE = "QUALITY_VERDICT_CONFLICT";

    public DataQualityPersistenceOutcome {
        if (disposition == null) throw new NullPointerException("disposition");
        validationRunRef = QualityContractSupport.reference(validationRunRef, "validationRunRef");
        snapshotRef = QualityContractSupport.reference(snapshotRef, "snapshotRef");
        if (disposition == DataQualityPersistenceDisposition.CONFLICT) {
            if (!CONFLICT_CODE.equals(stableFailureCode)) throw new IllegalArgumentException("conflict code required");
        } else if (stableFailureCode != null) {
            throw new IllegalArgumentException("non-conflict cannot carry failure code");
        }
    }

    public static DataQualityPersistenceOutcome decide(
            String runRef,
            String snapshotRef,
            String existingInputFingerprint,
            String existingVerdictFingerprint,
            String candidateInputFingerprint,
            String candidateVerdictFingerprint) {
        if (existingInputFingerprint == null && existingVerdictFingerprint == null) {
            return new DataQualityPersistenceOutcome(DataQualityPersistenceDisposition.NEW, runRef, snapshotRef, null);
        }
        QualityContractSupport.fingerprint(existingInputFingerprint, "existingInputFingerprint");
        QualityContractSupport.fingerprint(existingVerdictFingerprint, "existingVerdictFingerprint");
        QualityContractSupport.fingerprint(candidateInputFingerprint, "candidateInputFingerprint");
        QualityContractSupport.fingerprint(candidateVerdictFingerprint, "candidateVerdictFingerprint");
        if (existingInputFingerprint.equals(candidateInputFingerprint)
                && existingVerdictFingerprint.equals(candidateVerdictFingerprint)) {
            return new DataQualityPersistenceOutcome(DataQualityPersistenceDisposition.DUPLICATE, runRef, snapshotRef, null);
        }
        return new DataQualityPersistenceOutcome(DataQualityPersistenceDisposition.CONFLICT, runRef, snapshotRef, CONFLICT_CODE);
    }
}
