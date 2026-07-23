package com.jc.data.contract.v1.quality;

final class QualityChecks {
    private QualityChecks() { }

    static DataQualityCheckResult pass(String code, DataQualityValidationScope scope,
            String expected, String observed, boolean required) {
        return result(code, scope, expected, observed, "0", DataQualitySeverity.INFO,
                DataQualityCheckStatus.PASS, null, null, required);
    }

    static DataQualityCheckResult fail(String code, DataQualityValidationScope scope,
            String expected, String observed, String difference, DataQualitySeverity severity,
            DataQualityFailure failure, boolean required) {
        return result(code, scope, expected, observed, difference, severity,
                DataQualityCheckStatus.FAIL, failure, null, required);
    }

    static DataQualityCheckResult notApplicable(String code, DataQualityValidationScope scope,
            String reason, boolean required) {
        return result(code, scope, "not_applicable", "not_applicable", "0", DataQualitySeverity.INFO,
                DataQualityCheckStatus.NOT_APPLICABLE, null, reason, required);
    }

    static DataQualityCheckResult skipped(String code, DataQualityValidationScope scope,
            String reason, boolean required) {
        return result(code, scope, "required", "not_executed", "unknown", DataQualitySeverity.ERROR,
                DataQualityCheckStatus.SKIPPED, null, reason, required);
    }

    private static DataQualityCheckResult result(String code, DataQualityValidationScope scope,
            String expected, String observed, String difference, DataQualitySeverity severity,
            DataQualityCheckStatus status, DataQualityFailure failure, String reason, boolean required) {
        String fp = DataQualityFingerprints.check(code, scope, expected, observed, difference,
                severity, status, failure, reason, required);
        return new DataQualityCheckResult(code, scope, expected, observed, difference,
                severity, status, failure, reason, required, fp);
    }
}
