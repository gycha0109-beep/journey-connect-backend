package com.jc.recommendation.policy;

import java.time.Instant;

public interface VersionedPolicy {
    String policyVersion();

    Instant effectiveFrom();
}
