package com.jc.intelligence.readiness.search.v1;

import com.jc.intelligence.contract.support.WireValue;

public enum SearchBudgetKey implements WireValue {
    MAX_CONCURRENCY("max_concurrency"), QUEUE_CAPACITY("queue_capacity"), TASK_TIMEOUT("task_timeout"),
    END_TO_END_SHADOW_BUDGET("end_to_end_shadow_budget"), EXECUTOR_REJECTION_POLICY("executor_rejection_policy"),
    QUEUE_FULL_POLICY("queue_full_policy"), LATE_RESULT_POLICY("late_result_policy"),
    CANCELLATION_POLICY("cancellation_policy"), CIRCUIT_OPEN_POLICY("circuit_open_policy"),
    HOOK_DISPATCH_OVERHEAD("hook_dispatch_overhead"), EXECUTOR_SUBMISSION_OVERHEAD("executor_submission_overhead"),
    QUEUE_WAIT("queue_wait"), RUNTIME_DURATION("runtime_duration"), COMPARISON_DURATION("comparison_duration"),
    LOGGING_DURATION("logging_duration"), TOTAL_SHADOW_DURATION("total_shadow_duration");
    private final String wireValue;
    SearchBudgetKey(String wireValue) { this.wireValue = wireValue; }
    @Override public String wireValue() { return wireValue; }
}
