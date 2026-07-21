package com.jc.data.contract.v1.fingerprint;

import com.jc.data.contract.support.Sha256DigestV1;
import com.jc.data.contract.v1.validation.DataValidationErrorCode;
import com.jc.data.contract.v1.validation.PlatformEventEnvelopeValidatorV1;
import com.jc.data.contract.v1.validation.ValidationError;
import java.util.List;

public final class Sha256EventFingerprintBoundaryV1 implements EventFingerprintBoundaryV1 {
    public static final String FINGERPRINT_VERSION = PlatformEventFingerprintCanonicalizerV1.FINGERPRINT_VERSION;

    @Override
    public FingerprintResultV1 fingerprint(FingerprintRequestV1 request) {
        if (request == null || request.canonicalizationVersion() == null
                || request.canonicalBytes() == null || request.canonicalBytes().length == 0) {
            return invalid(DataValidationErrorCode.FINGERPRINT_FAILURE, "fingerprint", "canonical bytes required");
        }
        if (!PlatformEventEnvelopeValidatorV1.CANONICALIZATION_VERSION
                .equals(request.canonicalizationVersion().value())) {
            return invalid(
                    DataValidationErrorCode.INVALID_CANONICALIZATION_VERSION,
                    "canonicalizationVersion",
                    "unsupported");
        }
        if (request.canonicalBytes().length > 1_048_576) {
            return invalid(DataValidationErrorCode.FINGERPRINT_FAILURE, "fingerprint", "canonical bytes too large");
        }
        return new FingerprintResultV1(
                FingerprintStatus.SUCCESS,
                Sha256DigestV1.lowercaseHex(request.canonicalBytes()),
                List.of());
    }

    private static FingerprintResultV1 invalid(
            DataValidationErrorCode code,
            String field,
            String message) {
        return new FingerprintResultV1(
                FingerprintStatus.INVALID_REQUEST,
                null,
                List.of(new ValidationError(code, field, message)));
    }
}
