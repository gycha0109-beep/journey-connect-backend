package com.jc.recommendation.p2;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public final class P2EvaluationContracts {

    private P2EvaluationContracts() {}

    public enum Variant {
        BASELINE,
        TREATMENT;

        public String wireValue() {
            return name().toLowerCase(java.util.Locale.ROOT);
        }
    }

    public enum MetricRole {
        PRIMARY,
        GUARDRAIL;

        public String wireValue() {
            return name().toLowerCase(java.util.Locale.ROOT);
        }
    }

    public enum MetricDirection {
        HIGHER_IS_BETTER,
        LOWER_IS_BETTER;

        public String wireValue() {
            return name().toLowerCase(java.util.Locale.ROOT);
        }

        public double orient(double rawEffect) {
            return this == HIGHER_IS_BETTER ? rawEffect : -rawEffect;
        }
    }

    public enum Gate {
        CONTRACT_INTEGRITY("gate_a_contract_integrity"),
        DATA_QUALITY("gate_b_data_quality"),
        SAMPLE_SUFFICIENCY("gate_c_sample_sufficiency"),
        PERFORMANCE_GUARDRAIL("gate_d_performance_guardrail"),
        OPERATIONAL_APPROVAL("gate_e_operational_approval");

        private final String wireValue;

        Gate(String wireValue) {
            this.wireValue = wireValue;
        }

        public String wireValue() {
            return wireValue;
        }
    }

    public enum GateStatus {
        PASS,
        FAIL,
        HOLD;

        public String wireValue() {
            return name().toLowerCase(java.util.Locale.ROOT);
        }
    }

    public enum ReleaseState {
        DRAFT,
        SHADOW,
        CANARY,
        LIVE,
        HOLD,
        ROLLED_BACK;

        public String wireValue() {
            return name().toLowerCase(java.util.Locale.ROOT);
        }
    }

    public enum FinalDecision {
        CANARY,
        LIVE,
        HOLD,
        ROLLBACK;

        public String wireValue() {
            return name().toLowerCase(java.util.Locale.ROOT);
        }
    }

    public record MetricDefinition(
            String metricId,
            String metricDefinitionVersion,
            MetricRole role,
            MetricDirection direction,
            double minimumEffect,
            double maximumAllowedRegression,
            long attributionWindowSeconds,
            String numeratorDefinition,
            String denominatorDefinition,
            String eligibilityDefinition,
            String deduplicationDefinition) {

        public MetricDefinition {
            requireIdentifier(metricId, "metricId");
            requireIdentifier(metricDefinitionVersion, "metricDefinitionVersion");
            if (role == null || direction == null) {
                throw new IllegalArgumentException("metric role/direction required");
            }
            requireFiniteNonNegative(minimumEffect, "minimumEffect");
            requireFiniteNonNegative(maximumAllowedRegression, "maximumAllowedRegression");
            if (role == MetricRole.PRIMARY
                    && Double.doubleToRawLongBits(maximumAllowedRegression)
                    != Double.doubleToRawLongBits(0.0d)) {
                throw new IllegalArgumentException("primary maximum regression must be zero");
            }
            if (attributionWindowSeconds < 1 || attributionWindowSeconds > 31_536_000L) {
                throw new IllegalArgumentException("invalid attribution window");
            }
            requireDefinition(numeratorDefinition, "numeratorDefinition");
            requireDefinition(denominatorDefinition, "denominatorDefinition");
            requireDefinition(eligibilityDefinition, "eligibilityDefinition");
            requireDefinition(deduplicationDefinition, "deduplicationDefinition");
        }
    }

    public record Observation(
            String observationId,
            String subjectRef,
            String segment,
            Variant variant,
            boolean assigned,
            boolean exposed,
            boolean eligible,
            Instant occurredAt,
            Map<String, Double> metricValues) {

        public Observation {
            requireIdentifier(observationId, "observationId");
            requireIdentifier(subjectRef, "subjectRef");
            requireIdentifier(segment, "segment");
            if (variant == null || occurredAt == null || metricValues == null) {
                throw new IllegalArgumentException("observation fields required");
            }
            if (exposed && !assigned) {
                throw new IllegalArgumentException("exposed observation must be assigned");
            }
            metricValues = Map.copyOf(metricValues);
            metricValues.forEach((metricId, value) -> {
                requireIdentifier(metricId, "metricId");
                requireFinite(value, "metricValue");
            });
        }
    }

    public record EvaluationPolicy(
            String policyVersion,
            int minimumSamplePerVariant,
            int minimumSegmentSamplePerVariant,
            double minimumCommonSupportRate,
            double confidenceLevel,
            int bootstrapIterations,
            long bootstrapSeed,
            double familyWiseAlpha,
            double minimumAbsoluteEffectSize,
            double rollbackRegressionThreshold) {

        public EvaluationPolicy {
            requireIdentifier(policyVersion, "policyVersion");
            if (minimumSamplePerVariant < 1 || minimumSegmentSamplePerVariant < 1) {
                throw new IllegalArgumentException("sample threshold");
            }
            requireUnit(minimumCommonSupportRate, "support");
            if (confidenceLevel <= 0.5 || confidenceLevel >= 1.0d) {
                throw new IllegalArgumentException("confidence");
            }
            if (bootstrapIterations < 100) {
                throw new IllegalArgumentException("bootstrap iterations");
            }
            if (familyWiseAlpha <= 0.0d || familyWiseAlpha >= 1.0d) {
                throw new IllegalArgumentException("alpha");
            }
            requireFiniteNonNegative(minimumAbsoluteEffectSize, "effect size");
            requireFiniteNonNegative(rollbackRegressionThreshold, "rollback threshold");
        }
    }

    public record EvaluationInput(
            String evaluationRunId,
            String experimentId,
            String experimentVersion,
            String datasetSnapshotId,
            String datasetSchemaVersion,
            String baselinePolicyVersion,
            String treatmentPolicyVersion,
            Instant observedFrom,
            Instant observedTo,
            Instant evaluatedAt,
            List<MetricDefinition> metricDefinitions,
            List<Observation> observations,
            EvaluationPolicy evaluationPolicy,
            ReleaseState currentState,
            ReleaseState requestedState,
            boolean operationalApproval,
            String evaluatorBuildId) {

        public EvaluationInput {
            for (String[] pair : List.of(
                    new String[] {evaluationRunId, "evaluationRunId"},
                    new String[] {experimentId, "experimentId"},
                    new String[] {experimentVersion, "experimentVersion"},
                    new String[] {datasetSnapshotId, "datasetSnapshotId"},
                    new String[] {datasetSchemaVersion, "datasetSchemaVersion"},
                    new String[] {baselinePolicyVersion, "baselinePolicyVersion"},
                    new String[] {treatmentPolicyVersion, "treatmentPolicyVersion"},
                    new String[] {evaluatorBuildId, "evaluatorBuildId"})) {
                requireIdentifier(pair[0], pair[1]);
            }
            if (observedFrom == null
                    || observedTo == null
                    || evaluatedAt == null
                    || !observedFrom.isBefore(observedTo)
                    || evaluatedAt.isBefore(observedTo)) {
                throw new IllegalArgumentException("invalid evaluation time");
            }
            if (metricDefinitions == null
                    || metricDefinitions.isEmpty()
                    || observations == null
                    || evaluationPolicy == null
                    || currentState == null
                    || requestedState == null) {
                throw new IllegalArgumentException("evaluation fields required");
            }
            metricDefinitions = List.copyOf(metricDefinitions);
            observations = List.copyOf(observations);
        }
    }

    public record ConfidenceInterval(double lower, double upper, double confidenceLevel) {
        public ConfidenceInterval {
            requireFinite(lower, "lower");
            requireFinite(upper, "upper");
            if (lower > upper) {
                throw new IllegalArgumentException("CI order");
            }
        }
    }

    public record MetricResult(
            String segment,
            String metricId,
            int baselineCount,
            int treatmentCount,
            int eligibleExposedCount,
            int missingMetricCount,
            double commonSupportRate,
            double baselineMean,
            double treatmentMean,
            double rawEffect,
            double orientedEffect,
            double effectSize,
            ConfidenceInterval confidenceInterval,
            double pValue,
            double adjustedPValue,
            boolean sampleSufficient,
            boolean dataQualityPass,
            boolean performancePass) {}

    public record SegmentResult(
            String segment,
            int assignedCount,
            int exposedCount,
            int eligibleExposedCount,
            List<MetricResult> metrics) {
        public SegmentResult {
            metrics = List.copyOf(metrics);
        }
    }

    public record GateResult(Gate gate, GateStatus status, List<String> reasonCodes) {
        public GateResult {
            reasonCodes = List.copyOf(reasonCodes);
        }
    }

    public record EvaluationResult(
            String evaluationRunId,
            String datasetSnapshotId,
            String metricDefinitionVersion,
            List<SegmentResult> segments,
            List<GateResult> gates,
            FinalDecision finalDecision,
            ReleaseState targetState,
            String fingerprint) {
        public EvaluationResult {
            segments = List.copyOf(segments);
            gates = List.copyOf(gates);
        }
    }

    static void requireIdentifier(String value, String field) {
        if (value == null || !value.matches("[A-Za-z0-9][A-Za-z0-9._:-]{0,127}")) {
            throw new IllegalArgumentException(field + " is invalid");
        }
    }

    private static void requireDefinition(String value, String field) {
        if (value == null || value.isBlank() || value.length() > 512) {
            throw new IllegalArgumentException(field + " is invalid");
        }
    }

    static void requireFinite(double value, String field) {
        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException(field + " must be finite");
        }
    }

    static void requireFiniteNonNegative(double value, String field) {
        requireFinite(value, field);
        if (value < 0.0d) {
            throw new IllegalArgumentException(field + " must be nonnegative");
        }
    }

    static void requireUnit(double value, String field) {
        requireFinite(value, field);
        if (value < 0.0d || value > 1.0d) {
            throw new IllegalArgumentException(field + " must be 0..1");
        }
    }
}
