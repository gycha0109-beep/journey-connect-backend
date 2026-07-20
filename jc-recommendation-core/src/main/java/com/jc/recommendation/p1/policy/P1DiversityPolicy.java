package com.jc.recommendation.p1.policy;

import com.jc.recommendation.model.diversity.DiversityDimension;
import com.jc.recommendation.model.diversity.DiversityExposureCaps;
import com.jc.recommendation.policy.VersionedPolicy;
import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;

public record P1DiversityPolicy(
        String policyVersion,
        Instant effectiveFrom,
        int exposureWindowSize,
        int maxPromotionDistance,
        int maxDemotionDistance,
        DiversityExposureCaps exposureCaps,
        List<DiversityDimension> relaxationOrder) implements VersionedPolicy {

    public P1DiversityPolicy {
        Objects.requireNonNull(policyVersion, "policyVersion");
        Objects.requireNonNull(effectiveFrom, "effectiveFrom");
        Objects.requireNonNull(exposureCaps, "exposureCaps");
        relaxationOrder = List.copyOf(Objects.requireNonNull(relaxationOrder, "relaxationOrder"));
        if (policyVersion.isBlank()) {
            throw new IllegalArgumentException("policyVersion must not be blank");
        }
        if (exposureWindowSize < 1 || maxPromotionDistance < 0 || maxDemotionDistance < 0) {
            throw new IllegalArgumentException("diversity limits are invalid");
        }
        if (exposureCaps.duplicateGroup() < 1 || exposureCaps.author() < 1
                || exposureCaps.region() < 1 || exposureCaps.theme() < 1) {
            throw new IllegalArgumentException("diversity caps must be positive");
        }
        if (relaxationOrder.size() != DiversityDimension.values().length
                || EnumSet.copyOf(relaxationOrder).size() != DiversityDimension.values().length) {
            throw new IllegalArgumentException("relaxationOrder must contain every dimension exactly once");
        }
    }
}
