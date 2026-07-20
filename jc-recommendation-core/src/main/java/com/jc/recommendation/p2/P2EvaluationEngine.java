package com.jc.recommendation.p2;

import com.jc.recommendation.p2.P2EvaluationContracts.*;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.SplittableRandom;

public final class P2EvaluationEngine {
    private static final String ALL = "all";
    private final P2ReleaseStateMachine releaseStateMachine = new P2ReleaseStateMachine();

    public EvaluationResult evaluate(EvaluationInput input) {
        Objects.requireNonNull(input, "input");
        Validation validation = validate(input);
        List<MutableMetric> metrics = new ArrayList<>();
        List<MutableSegment> segments = new ArrayList<>();
        for (String segment : segmentOrder(input.observations())) {
            List<Observation> observations = input.observations().stream()
                    .filter(observation -> ALL.equals(segment) || observation.segment().equals(segment))
                    .toList();
            List<MutableMetric> current = new ArrayList<>();
            for (MetricDefinition definition : input.metricDefinitions()) {
                MutableMetric metric = calculate(input, segment, observations, definition);
                current.add(metric);
                metrics.add(metric);
            }
            segments.add(new MutableSegment(segment,
                    count(observations, Observation::assigned),
                    count(observations, Observation::exposed),
                    count(observations, observation -> observation.assigned() && observation.exposed() && observation.eligible()),
                    current));
        }
        applyHolm(metrics, input.evaluationPolicy().familyWiseAlpha());
        metrics.forEach(metric -> metric.performancePass = performance(metric, input.evaluationPolicy()));
        List<SegmentResult> immutableSegments = segments.stream().map(MutableSegment::immutable).toList();
        List<GateResult> gates = gates(input, validation, metrics);
        Decision decision = decide(input, gates, metrics);
        String version = input.metricDefinitions().getFirst().metricDefinitionVersion();
        String fingerprint = fingerprint(input, immutableSegments, gates, decision);
        return new EvaluationResult(input.evaluationRunId(), input.datasetSnapshotId(), version,
                immutableSegments, gates, decision.finalDecision, decision.targetState, fingerprint);
    }

    private Validation validate(EvaluationInput input) {
        Set<String> metricIds = new HashSet<>();
        Set<String> versions = new HashSet<>();
        boolean primary = false;
        for (MetricDefinition definition : input.metricDefinitions()) {
            if (!metricIds.add(definition.metricId())) throw new IllegalArgumentException("duplicate metricId");
            versions.add(definition.metricDefinitionVersion());
            primary |= definition.role() == MetricRole.PRIMARY;
        }
        if (versions.size() != 1 || !primary) throw new IllegalArgumentException("metric contract invalid");
        Set<String> observationIds = new HashSet<>();
        Set<String> subjects = new HashSet<>();
        int outOfWindow = 0;
        int unassigned = 0;
        for (Observation observation : input.observations()) {
            if (!observationIds.add(observation.observationId())) throw new IllegalArgumentException("duplicate observationId");
            if (!subjects.add(observation.subjectRef())) throw new IllegalArgumentException("subject has multiple assignments");
            if (observation.occurredAt().isBefore(input.observedFrom()) || !observation.occurredAt().isBefore(input.observedTo())) outOfWindow++;
            if (!observation.assigned()) unassigned++;
        }
        return new Validation(outOfWindow, unassigned);
    }

    private MutableMetric calculate(EvaluationInput input, String segment, List<Observation> observations, MetricDefinition definition) {
        List<Observation> included = observations.stream()
                .filter(Observation::assigned).filter(Observation::exposed).filter(Observation::eligible)
                .filter(observation -> !observation.occurredAt().isBefore(input.observedFrom()) && observation.occurredAt().isBefore(input.observedTo()))
                .sorted(Comparator.comparing(Observation::observationId)).toList();
        List<Double> baseline = values(included, definition.metricId(), Variant.BASELINE);
        List<Double> treatment = values(included, definition.metricId(), Variant.TREATMENT);
        int missing = (int) included.stream().filter(observation -> !observation.metricValues().containsKey(definition.metricId())).count();
        double support = support(baseline.size(), treatment.size());
        double baselineMean = mean(baseline);
        double treatmentMean = mean(treatment);
        double raw = treatmentMean - baselineMean;
        double oriented = definition.direction().orient(raw);
        double effectSize = definition.direction().orient(cohensD(baseline, treatment));
        ConfidenceInterval interval = bootstrap(baseline, treatment, definition.direction(), input.evaluationPolicy(), segment, definition.metricId());
        double p = pValue(baseline, treatment);
        boolean sample = baseline.size() >= (ALL.equals(segment) ? input.evaluationPolicy().minimumSamplePerVariant() : input.evaluationPolicy().minimumSegmentSamplePerVariant())
                && treatment.size() >= (ALL.equals(segment) ? input.evaluationPolicy().minimumSamplePerVariant() : input.evaluationPolicy().minimumSegmentSamplePerVariant());
        double missingRate = included.isEmpty() ? 1.0d : (double) missing / included.size();
        boolean quality = support >= input.evaluationPolicy().minimumCommonSupportRate() && missingRate <= 0.05d;
        return new MutableMetric(segment, definition, baseline.size(), treatment.size(), included.size(), missing,
                support, baselineMean, treatmentMean, raw, oriented, effectSize, interval, p, p, sample, quality, false);
    }

    private static boolean performance(MutableMetric metric, EvaluationPolicy policy) {
        if (!metric.sampleSufficient || !metric.dataQualityPass) return false;
        if (metric.definition.role() == MetricRole.PRIMARY) {
            return metric.orientedEffect >= metric.definition.minimumEffect()
                    && Math.abs(metric.effectSize) >= policy.minimumAbsoluteEffectSize()
                    && metric.interval.lower() > 0.0d
                    && metric.adjustedPValue <= policy.familyWiseAlpha();
        }
        return metric.orientedEffect >= -metric.definition.maximumAllowedRegression()
                && metric.interval.lower() >= -metric.definition.maximumAllowedRegression();
    }

    private List<GateResult> gates(EvaluationInput input, Validation validation, List<MutableMetric> metrics) {
        List<GateResult> result = new ArrayList<>();
        result.add(new GateResult(Gate.CONTRACT_INTEGRITY, GateStatus.PASS, List.of("VERSIONED_CONTRACTS_BOUND")));
        boolean quality = validation.outOfWindow == 0 && validation.unassigned == 0 && metrics.stream().allMatch(metric -> metric.dataQualityPass);
        result.add(new GateResult(Gate.DATA_QUALITY, quality ? GateStatus.PASS : GateStatus.FAIL,
                quality ? List.of("DATASET_QUALITY_ACCEPTED") : List.of("DATASET_QUALITY_REJECTED")));
        boolean sample = metrics.stream().allMatch(metric -> metric.sampleSufficient);
        result.add(new GateResult(Gate.SAMPLE_SUFFICIENCY, sample ? GateStatus.PASS : GateStatus.HOLD,
                sample ? List.of("SAMPLE_THRESHOLDS_MET") : List.of("SAMPLE_THRESHOLDS_NOT_MET")));
        boolean severe = severeRegression(metrics, input.evaluationPolicy());
        boolean performance = metrics.stream().allMatch(metric -> metric.performancePass);
        result.add(new GateResult(Gate.PERFORMANCE_GUARDRAIL,
                severe ? GateStatus.FAIL : performance ? GateStatus.PASS : GateStatus.HOLD,
                severe ? List.of("GUARDRAIL_ROLLBACK_THRESHOLD_EXCEEDED")
                        : performance ? List.of("PRIMARY_AND_GUARDRAILS_PASS") : List.of("PERFORMANCE_EVIDENCE_INSUFFICIENT")));
        result.add(new GateResult(Gate.OPERATIONAL_APPROVAL, input.operationalApproval() ? GateStatus.PASS : GateStatus.HOLD,
                input.operationalApproval() ? List.of("OPERATIONAL_APPROVAL_RECORDED") : List.of("OPERATIONAL_APPROVAL_REQUIRED")));
        return List.copyOf(result);
    }

    private Decision decide(EvaluationInput input, List<GateResult> gates, List<MutableMetric> metrics) {
        if (severeRegression(metrics, input.evaluationPolicy())
                && (input.currentState() == ReleaseState.CANARY || input.currentState() == ReleaseState.LIVE)) {
            return new Decision(FinalDecision.ROLLBACK, ReleaseState.ROLLED_BACK);
        }
        boolean pass = gates.stream().allMatch(gate -> gate.status() == GateStatus.PASS);
        if (pass && releaseStateMachine.canTransition(input.currentState(), input.requestedState())) {
            FinalDecision decision = input.requestedState() == ReleaseState.LIVE ? FinalDecision.LIVE : FinalDecision.CANARY;
            return new Decision(decision, input.requestedState());
        }
        ReleaseState target = releaseStateMachine.canTransition(input.currentState(), ReleaseState.HOLD)
                ? ReleaseState.HOLD : input.currentState();
        return new Decision(FinalDecision.HOLD, target);
    }

    private static boolean severeRegression(List<MutableMetric> metrics, EvaluationPolicy policy) {
        return metrics.stream().filter(metric -> ALL.equals(metric.segment))
                .filter(metric -> metric.definition.role() == MetricRole.GUARDRAIL)
                .filter(metric -> metric.sampleSufficient && metric.dataQualityPass)
                .anyMatch(metric -> metric.orientedEffect <= -policy.rollbackRegressionThreshold()
                        && metric.interval.upper() < 0.0d);
    }

    private static List<String> segmentOrder(List<Observation> observations) {
        Set<String> result = new LinkedHashSet<>(); result.add(ALL);
        observations.stream().map(Observation::segment).sorted().forEach(result::add); return List.copyOf(result);
    }
    private static int count(List<Observation> observations, java.util.function.Predicate<Observation> predicate){return (int)observations.stream().filter(predicate).count();}
    private static List<Double> values(List<Observation> observations,String metricId,Variant variant){return observations.stream().filter(o->o.variant()==variant).map(o->o.metricValues().get(metricId)).filter(Objects::nonNull).toList();}
    private static double support(int a,int b){return a+b==0?0.0d:(2.0d*Math.min(a,b))/(a+b);}
    private static double mean(List<Double> values){if(values.isEmpty())return 0.0d;double sum=0;for(double value:values)sum+=value;return sum/values.size();}
    private static double variance(List<Double> values,double mean){if(values.size()<2)return 0;double sum=0;for(double value:values){double d=value-mean;sum+=d*d;}return sum/(values.size()-1);}
    private static double cohensD(List<Double> baseline,List<Double> treatment){if(baseline.isEmpty()||treatment.isEmpty())return 0;double a=mean(baseline),b=mean(treatment);double denom=baseline.size()+treatment.size()-2.0d;if(denom<=0)return 0;double pooled=Math.sqrt(((baseline.size()-1)*variance(baseline,a)+(treatment.size()-1)*variance(treatment,b))/denom);return pooled==0?0:(b-a)/pooled;}
    private static double pValue(List<Double> baseline,List<Double> treatment){if(baseline.size()<2||treatment.size()<2)return 1;double a=mean(baseline),b=mean(treatment);double se=Math.sqrt(variance(baseline,a)/baseline.size()+variance(treatment,b)/treatment.size());if(se==0)return Double.compare(a,b)==0?1:0;double z=Math.abs((b-a)/se);return Math.max(0,Math.min(1,2*(1-normalCdf(z))));}
    private static double normalCdf(double x){double t=1/(1+0.2316419*x);double d=0.3989422804014327*Math.exp(-x*x/2);double p=d*t*(0.319381530+t*(-0.356563782+t*(1.781477937+t*(-1.821255978+t*1.330274429))));return 1-p;}
    private static ConfidenceInterval bootstrap(List<Double> baseline,List<Double> treatment,MetricDirection direction,EvaluationPolicy policy,String segment,String metricId){if(baseline.isEmpty()||treatment.isEmpty())return new ConfidenceInterval(0,0,policy.confidenceLevel());double[] effects=new double[policy.bootstrapIterations()];long seed=policy.bootstrapSeed()^P2Canonical.sha256(List.of(segment,metricId)).substring(0,16).hashCode();SplittableRandom random=new SplittableRandom(seed);for(int i=0;i<effects.length;i++){effects[i]=direction.orient(resampleMean(treatment,random)-resampleMean(baseline,random));}java.util.Arrays.sort(effects);double tail=(1-policy.confidenceLevel())/2;int lower=(int)Math.floor(tail*(effects.length-1));int upper=(int)Math.ceil((1-tail)*(effects.length-1));return new ConfidenceInterval(effects[lower],effects[upper],policy.confidenceLevel());}
    private static double resampleMean(List<Double> values,SplittableRandom random){double sum=0;for(int i=0;i<values.size();i++)sum+=values.get(random.nextInt(values.size()));return sum/values.size();}
    private static void applyHolm(List<MutableMetric> metrics,double alpha){List<MutableMetric> ordered=metrics.stream().sorted(Comparator.comparingDouble(metric->metric.pValue)).toList();int m=ordered.size();double running=0;for(int i=0;i<m;i++){MutableMetric metric=ordered.get(i);double adjusted=Math.min(1,(m-i)*metric.pValue);running=Math.max(running,adjusted);metric.adjustedPValue=running;}}
    private static String fingerprint(EvaluationInput input,List<SegmentResult> segments,List<GateResult> gates,Decision decision){List<String> parts=new ArrayList<>();parts.add("p2-evaluation-result-v1");parts.add(input.evaluationRunId());parts.add(input.datasetSnapshotId());parts.add(input.evaluationPolicy().policyVersion());parts.add(input.baselinePolicyVersion());parts.add(input.treatmentPolicyVersion());for(SegmentResult segment:segments){parts.add(segment.segment());for(MetricResult metric:segment.metrics()){parts.add(metric.metricId());parts.add(P2Canonical.bits(metric.rawEffect()));parts.add(P2Canonical.bits(metric.adjustedPValue()));parts.add(Boolean.toString(metric.performancePass()));}}for(GateResult gate:gates){parts.add(gate.gate().wireValue());parts.add(gate.status().wireValue());}parts.add(decision.finalDecision.wireValue());parts.add(decision.targetState.wireValue());return P2Canonical.sha256(parts);}

    private record Validation(int outOfWindow,int unassigned){}
    private record Decision(FinalDecision finalDecision,ReleaseState targetState){}
    private static final class MutableMetric {final String segment;final MetricDefinition definition;final int baselineCount,treatmentCount,eligibleExposedCount,missingMetricCount;final double commonSupportRate,baselineMean,treatmentMean,rawEffect,orientedEffect,effectSize;final ConfidenceInterval interval;final double pValue;double adjustedPValue;final boolean sampleSufficient,dataQualityPass;boolean performancePass;MutableMetric(String segment,MetricDefinition definition,int baselineCount,int treatmentCount,int eligibleExposedCount,int missingMetricCount,double commonSupportRate,double baselineMean,double treatmentMean,double rawEffect,double orientedEffect,double effectSize,ConfidenceInterval interval,double pValue,double adjustedPValue,boolean sampleSufficient,boolean dataQualityPass,boolean performancePass){this.segment=segment;this.definition=definition;this.baselineCount=baselineCount;this.treatmentCount=treatmentCount;this.eligibleExposedCount=eligibleExposedCount;this.missingMetricCount=missingMetricCount;this.commonSupportRate=commonSupportRate;this.baselineMean=baselineMean;this.treatmentMean=treatmentMean;this.rawEffect=rawEffect;this.orientedEffect=orientedEffect;this.effectSize=effectSize;this.interval=interval;this.pValue=pValue;this.adjustedPValue=adjustedPValue;this.sampleSufficient=sampleSufficient;this.dataQualityPass=dataQualityPass;this.performancePass=performancePass;}MetricResult immutable(){return new MetricResult(segment,definition.metricId(),baselineCount,treatmentCount,eligibleExposedCount,missingMetricCount,commonSupportRate,baselineMean,treatmentMean,rawEffect,orientedEffect,effectSize,interval,pValue,adjustedPValue,sampleSufficient,dataQualityPass,performancePass);}}
    private record MutableSegment(String segment,int assignedCount,int exposedCount,int eligibleExposedCount,List<MutableMetric> metrics){SegmentResult immutable(){return new SegmentResult(segment,assignedCount,exposedCount,eligibleExposedCount,metrics.stream().map(MutableMetric::immutable).toList());}}
}
