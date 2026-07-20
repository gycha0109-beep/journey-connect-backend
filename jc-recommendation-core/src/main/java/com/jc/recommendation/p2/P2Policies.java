package com.jc.recommendation.p2;

import com.jc.recommendation.p2.P2EvaluationContracts.EvaluationPolicy;
import com.jc.recommendation.p2.P2EvaluationContracts.MetricDefinition;
import com.jc.recommendation.p2.P2EvaluationContracts.MetricDirection;
import com.jc.recommendation.p2.P2EvaluationContracts.MetricRole;
import java.util.List;

public final class P2Policies {

    public static final String DATASET_SCHEMA_VERSION = "recommendation-evaluation-dataset-v1";

    public static final List<MetricDefinition> METRICS_V1 = List.of(
            new MetricDefinition(
                    "engagement_rate",
                    "recommendation-metrics-v1",
                    MetricRole.PRIMARY,
                    MetricDirection.HIGHER_IS_BETTER,
                    0.01d,
                    0.0d,
                    604_800L,
                    "count distinct exposed eligible subjects with click, like, save, or share within 604800 seconds after first exposure",
                    "count distinct subjects with a valid assignment and at least one bound recommendation exposure",
                    "assigned=true AND exposed=true AND eligible=true",
                    "one observation per experimentId+experimentVersion+subjectRef; engagement is binary per subject"),
            new MetricDefinition(
                    "fallback_rate",
                    "recommendation-metrics-v1",
                    MetricRole.GUARDRAIL,
                    MetricDirection.LOWER_IS_BETTER,
                    0.0d,
                    0.02d,
                    86_400L,
                    "count distinct bound exposed recommendation runs with run_status=fallback",
                    "count distinct bound exposed recommendation runs",
                    "assigned=true AND exposed=true AND eligible=true",
                    "one observation per experimentId+experimentVersion+subjectRef; run IDs are distinct"));

    public static final EvaluationPolicy EVALUATION_POLICY_V1 = new EvaluationPolicy(
            "recommendation-evaluation-policy-v1",
            100,
            30,
            0.80d,
            0.95d,
            10_000,
            91_337L,
            0.05d,
            0.10d,
            0.05d);

    private P2Policies() {}
}
