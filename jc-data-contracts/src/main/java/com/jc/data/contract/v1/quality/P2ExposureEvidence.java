package com.jc.data.contract.v1.quality;

import com.jc.data.contract.v1.projection.ExperimentExposureBinding;
import java.util.Objects;

public record P2ExposureEvidence(
        ExperimentExposureBinding binding,
        boolean authoritative,
        boolean generalExposure,
        boolean fallbackAuthorityMatched) {
    public P2ExposureEvidence {
        Objects.requireNonNull(binding, "binding");
    }
}
