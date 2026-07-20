package com.jc.intelligence.production.search.v1;

public enum SearchShadowMetricName {
    ELIGIBLE("shadow.eligible"),COHORT_ACCEPTED("shadow.cohort.accepted"),SAMPLED("shadow.sampled"),DISPATCHED("shadow.dispatched"),COMPLETED("shadow.completed"),SKIPPED("shadow.skipped"),KILLED("shadow.killed"),INPUT_UNAVAILABLE("shadow.input.unavailable"),TIMEOUT("shadow.timeout"),REJECTED("shadow.rejected"),CIRCUIT_OPEN("shadow.circuit.open"),RUNTIME_FAILURE("shadow.runtime.failure"),COMPARISON_FAILURE("shadow.comparison.failure"),EVIDENCE_FAILURE("shadow.evidence.failure"),QUEUE_DEPTH("shadow.queue.depth"),EXECUTOR_ACTIVE("shadow.executor.active"),RUNTIME_LATENCY("shadow.runtime.latency"),TOTAL_LATENCY("shadow.total.latency"),OVERLAP_BUCKET("shadow.overlap.bucket"),DIVERGENCE_BUCKET("shadow.divergence.bucket"),PROJECTION_STALE("shadow.projection.stale"),PROJECTION_INELIGIBLE("shadow.projection.ineligible");
    private final String wire; SearchShadowMetricName(String wire){this.wire=wire;} public String wireValue(){return wire;}
}
