package com.jc.backend.recommendation.rca2;

public final class Rca2SideEffectGuard {
    private final Rca2Metrics metrics;
    public Rca2SideEffectGuard(Rca2Metrics metrics) { this.metrics = metrics; }

    public void databaseWrite(Rca2RuntimeContracts.Lane lane) { block("shadow_write_attempt_blocked_count", lane); }
    public void cacheWrite(Rca2RuntimeContracts.Lane lane) { block("shadow_write_attempt_blocked_count", lane); }
    public void eventEmission(Rca2RuntimeContracts.Lane lane) { block("shadow_event_attempt_blocked_count", lane); }
    public void notification(Rca2RuntimeContracts.Lane lane) { block("shadow_event_attempt_blocked_count", lane); }
    public void rankingFeedback(Rca2RuntimeContracts.Lane lane) { block("shadow_event_attempt_blocked_count", lane); }
    public void responseMutation(Rca2RuntimeContracts.Lane lane) { block("shadow_response_mutation_blocked_count", lane); }

    private void block(String metric, Rca2RuntimeContracts.Lane lane) {
        metrics.increment(metric, lane, "blocked", Rca2RuntimeContracts.BreakerState.CLOSED);
        throw new UnsupportedOperationException("RCA-2 shadow side effect forbidden");
    }
}
