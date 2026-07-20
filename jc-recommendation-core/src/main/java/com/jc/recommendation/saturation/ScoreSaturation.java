package com.jc.recommendation.saturation;

import com.jc.recommendation.policy.SaturationPolicy;

import java.util.Objects;

public final class ScoreSaturation {
    private ScoreSaturation() {
    }

    public static double saturate(double decayedRawScore, SaturationPolicy policy) {
        Objects.requireNonNull(policy, "policy");
        if (!Double.isFinite(decayedRawScore)) {
            throw new IllegalArgumentException("decayedRawScore must be finite");
        }
        if (!Double.isFinite(policy.scale()) || policy.scale() <= 0) {
            throw new IllegalArgumentException("Saturation scale must be a positive finite number");
        }
        double nonNegativeScore = Math.max(0.0, decayedRawScore);
        return 1.0 - Math.exp(-nonNegativeScore / policy.scale());
    }
}
