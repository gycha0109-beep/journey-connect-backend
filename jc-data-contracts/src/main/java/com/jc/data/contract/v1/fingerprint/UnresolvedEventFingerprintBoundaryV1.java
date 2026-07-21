package com.jc.data.contract.v1.fingerprint;

import com.jc.data.contract.v1.validation.DataValidationErrorCode;
import com.jc.data.contract.v1.validation.PlatformEventEnvelopeValidatorV1;
import com.jc.data.contract.v1.validation.ValidationError;
import java.util.List;

public final class UnresolvedEventFingerprintBoundaryV1 implements EventFingerprintBoundaryV1 {
    @Override
    public FingerprintResultV1 fingerprint(FingerprintRequestV1 request) {
        if (request == null || request.canonicalizationVersion() == null
                || request.canonicalBytes() == null || request.canonicalBytes().length == 0) {
            return new FingerprintResultV1(
                    FingerprintStatus.INVALID_REQUEST,
                    null,
                    List.of(new ValidationError(
                            DataValidationErrorCode.FINGERPRINT_FAILURE, "fingerprint", "canonical bytes required")));
        }
        if (!PlatformEventEnvelopeValidatorV1.CANONICALIZATION_VERSION
                .equals(request.canonicalizationVersion().value())) {
            return new FingerprintResultV1(
                    FingerprintStatus.INVALID_REQUEST,
                    null,
                    List.of(new ValidationError(
                            DataValidationErrorCode.INVALID_CANONICALIZATION_VERSION,
                            "canonicalizationVersion", "unsupported")));
        }
        return new FingerprintResultV1(
                FingerprintStatus.UNRESOLVED_CONTRACT,
                null,
                List.of(new ValidationError(
                        DataValidationErrorCode.FINGERPRINT_CONTRACT_UNRESOLVED,
                        "fingerprint", "SC-DP1-009 algorithm, encoding, wire ID and field set unresolved")));
    }
}
