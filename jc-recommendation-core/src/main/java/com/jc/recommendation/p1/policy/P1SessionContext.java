package com.jc.recommendation.p1.policy;

public record P1SessionContext(boolean returningSession, int priorSessionCount) {
    public P1SessionContext {
        if (priorSessionCount < 0) {
            throw new IllegalArgumentException("priorSessionCount must be nonnegative");
        }
    }
}
