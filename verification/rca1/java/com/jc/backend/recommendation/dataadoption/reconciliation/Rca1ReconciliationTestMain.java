package com.jc.backend.recommendation.dataadoption.reconciliation;

import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TimeZone;

public final class Rca1ReconciliationTestMain {
    private Rca1ReconciliationTestMain() {}

    public static void main(String[] args) throws Exception {
        if(args.length!=4) throw new IllegalArgumentException("p1 fixture, p2 fixture, output directory and tested SHA required");
        Path p1Path=Path.of(args[0]), p2Path=Path.of(args[1]), output=Path.of(args[2]); String testedSha=args[3];
        Rca1FixtureReader reader=new Rca1FixtureReader();
        List<Rca1Contracts.ReconciliationCase> p1Cases=reader.read(p1Path,Rca1Contracts.Lane.P1);
        List<Rca1Contracts.ReconciliationCase> p2Cases=reader.read(p2Path,Rca1Contracts.Lane.P2);
        require(p1Cases.size()==23,"P1 scenario count"); require(p2Cases.size()==39,"P2 scenario count");

        Run first=execute(p1Cases,p2Cases,testedSha);
        require(first.p1Verdict()==Rca1Contracts.Verdict.RECONCILED_WITH_EXPECTED_GAPS,"P1 aggregate verdict");
        require(first.p2Verdict()==Rca1Contracts.Verdict.RECONCILED_WITH_MIGRATION_GAPS,"P2 aggregate verdict");
        require(first.p2Markers().equals(Rca1Contracts.P2_MARKERS),"P2 markers");
        verifyTargetExpectations(p1Cases,first.p1()); verifyTargetExpectations(p2Cases,first.p2());
        verifyBaseline(first.p1()); verifyBaseline(first.p2()); verifyIdentityCases(p1Cases,p2Cases);

        Locale locale=Locale.getDefault(); TimeZone zone=TimeZone.getDefault();
        try {
            Locale.setDefault(Locale.forLanguageTag("tr-TR")); TimeZone.setDefault(TimeZone.getTimeZone("Pacific/Honolulu"));
            List<Rca1Contracts.ReconciliationCase> reversedP1=new ArrayList<>(p1Cases); java.util.Collections.reverse(reversedP1);
            List<Rca1Contracts.ReconciliationCase> reversedP2=new ArrayList<>(p2Cases); java.util.Collections.reverse(reversedP2);
            Run second=execute(reversedP1,reversedP2,testedSha);
            require(first.evidenceTsv().equals(second.evidenceTsv()),"locale/timezone/order deterministic TSV");
            require(first.evidenceJson().equals(second.evidenceJson()),"locale/timezone/order deterministic JSON");
        } finally { Locale.setDefault(locale); TimeZone.setDefault(zone); }

        Rca1EvidenceWriter writer=new Rca1EvidenceWriter();
        writer.write(output,first.records(),first.counters(),first.p1Verdict(),first.p2Verdict(),
                first.p1Mismatches(),first.p2Mismatches());
        System.out.println("RCA1_DEPENDENCY_FREE_FIXTURE_RUNNER_PASS");
        System.out.println("P1_RESULT="+first.p1Verdict());
        System.out.println("P2_RESULT="+first.p2Verdict());
        Rca1Contracts.P2_MARKERS.stream().sorted().forEach(System.out::println);
        System.out.println("reconciliation_case_count="+first.counters().reconciliationCaseCount());
        System.out.println("identity_blocked_count="+first.counters().identityBlockedCount());
    }

    private static Run execute(List<Rca1Contracts.ReconciliationCase> p1Cases,List<Rca1Contracts.ReconciliationCase> p2Cases,String testedSha) {
        P1ShadowComparator p1Comparator=new P1ShadowComparator(); P2ShadowComparator p2Comparator=new P2ShadowComparator();
        List<Rca1Contracts.P1ComparisonResult> p1=new ArrayList<>(); List<Rca1Contracts.P2ComparisonResult> p2=new ArrayList<>();
        for(var c:p1Cases) p1.add(p1Comparator.compare(c,p1Reference(c),p1Candidate(c),identity(c),c.instant("referenceTime")));
        for(var c:p2Cases) p2.add(p2Comparator.compare(c,p2Reference(c),p2Candidate(c),identity(c),c.instant("referenceTime")));
        Rca1Contracts.Verdict p1Verdict=aggregateP1(p1), p2Verdict=aggregateP2(p2);
        Rca1EvidenceWriter writer=new Rca1EvidenceWriter(); Instant timestamp=Instant.parse("2026-07-24T00:00:00Z");
        var records=writer.records(p1,p2,timestamp,testedSha); var counters=Rca1EvidenceWriter.counters(p1,p2);
        List<Rca1Contracts.MismatchItem> p1m=p1.stream().flatMap(r->r.mismatchInventory().stream()).toList();
        List<Rca1Contracts.MismatchItem> p2m=p2.stream().flatMap(r->r.mismatchInventory().stream()).toList();
        Set<String> markers=new HashSet<>(); p2.forEach(r->markers.addAll(r.authorityMarkers()));
        return new Run(p1,p2,p1Verdict,p2Verdict,records,counters,p1m,p2m,Set.copyOf(markers),writer.tsv(records),writer.json(records));
    }

    private static Rca1Contracts.RecordedP1Reference p1Reference(Rca1Contracts.ReconciliationCase c) {
        return new Rca1Contracts.RecordedP1Reference(
                Map.of("profileSchemaVersion",c.field("referenceProfileSchemaVersion"),"projectionPolicyVersion",c.field("referenceProjectionPolicyVersion"),"activityWindowDays",c.field("referenceActivityWindowDays")),
                counts(c.field("referenceInteractionCounts")),windows(c,"reference"),checkpoint(c,"reference"),new Rca1Contracts.LineageMetadata(c.field("referenceLineage")),c.field("referenceFingerprint"));
    }
    private static Rca1Contracts.DataCandidateP1Input p1Candidate(Rca1Contracts.ReconciliationCase c) {
        return new Rca1Contracts.DataCandidateP1Input(
                Map.of("profileSchemaVersion",c.field("candidateProfileSchemaVersion"),"projectionPolicyVersion",c.field("candidateProjectionPolicyVersion"),"activityWindowDays",c.field("candidateActivityWindowDays")),
                counts(c.field("candidateInteractionCounts")),windows(c,"candidate"),checkpoint(c,"candidate"),new Rca1Contracts.LineageMetadata(c.field("candidateLineage")),
                c.flag("orderingComparable"),c.flag("eventGrainAvailable"),c.flag("explicitPreferencesAvailable"),c.flag("transformPolicyAvailable"),c.field("candidateFingerprint"));
    }
    private static Rca1Contracts.RecordedP2Reference p2Reference(Rca1Contracts.ReconciliationCase c) {
        return new Rca1Contracts.RecordedP2Reference(c.field("referenceExperimentRef"),c.field("referenceExperimentVersion"),c.field("referenceVariantRef"),
                c.field("referenceExposureRef"),c.field("referenceSubjectRef"),c.field("referenceSessionRef"),c.field("referenceRunRef"),
                c.number("referenceOutcomeWindowSeconds"),events(c.field("referenceEngagementEvents")),c.flag("referenceFallbackObserved"),checkpoint(c,"reference"),new Rca1Contracts.LineageMetadata(c.field("referenceLineage")));
    }
    private static Rca1Contracts.DataCandidateP2Input p2Candidate(Rca1Contracts.ReconciliationCase c) {
        return new Rca1Contracts.DataCandidateP2Input(c.field("candidateExperimentRef"),c.field("candidateExperimentVersion"),c.field("candidateVariantRef"),
                c.field("candidateExposureAuthority"),c.field("candidateExposureKind"),c.field("candidateExposureRef"),c.field("candidateSubjectRef"),
                c.field("candidateSessionRef"),c.field("candidateRunRef"),c.number("candidateOutcomeWindowSeconds"),events(c.field("candidateEngagementEvents")),
                c.flag("candidateFallbackObserved"),c.field("candidateFallbackBoundRunRef"),c.flag("candidateStaleUnexposedAssignmentGap"),
                c.flag("candidatePersistedDedupeGap"),checkpoint(c,"candidate"),new Rca1Contracts.LineageMetadata(c.field("candidateLineage")));
    }
    private static Rca1Contracts.SyntheticIdentityBinding identity(Rca1Contracts.ReconciliationCase c) {
        Rca1Contracts.IdentityStatus status=Rca1Contracts.IdentityStatus.valueOf(c.field("identityStatus"));
        return new Rca1Contracts.SyntheticIdentityBinding(c.field("identitySubjectRef"),c.field("identityUserRef"),status,
                c.field("identityPurpose"),c.field("identityCaller"),c.instant("identityValidUntil"),c.flag("identityDeleted")||status==Rca1Contracts.IdentityStatus.DELETED);
    }
    private static Rca1Contracts.CheckpointMetadata checkpoint(Rca1Contracts.ReconciliationCase c,String prefix) {
        return new Rca1Contracts.CheckpointMetadata(c.field(prefix+"CheckpointRef"),c.number(prefix+"CheckpointSequence"),c.instant(prefix+"CheckpointAt"),c.number("checkpointMaxStalenessSeconds"));
    }
    private static Map<Integer,Long> windows(Rca1Contracts.ReconciliationCase c,String prefix) { return Map.of(7,c.number(prefix+"Window7"),30,c.number(prefix+"Window30"),90,c.number(prefix+"Window90")); }
    private static Map<String,Long> counts(String source) { Map<String,Long> out=new HashMap<>(); if(!source.isBlank()) for(String e:source.split(",")){String[] p=e.split(":",2); out.put(p[0],Long.parseLong(p[1]));} return out; }
    private static Set<String> events(String source) { if(source.isBlank()) return Set.of(); return Set.of(source.split(",")); }

    private static void verifyTargetExpectations(List<Rca1Contracts.ReconciliationCase> cases,List<?> results) {
        for(int i=0;i<cases.size();i++) {
            var c=cases.get(i); Rca1Contracts.Verdict verdict; List<Rca1Contracts.DimensionResult> dimensions;
            if(results.get(i) instanceof Rca1Contracts.P1ComparisonResult r){verdict=r.verdict(); dimensions=r.dimensions();}
            else {var r=(Rca1Contracts.P2ComparisonResult)results.get(i); verdict=r.verdict(); dimensions=r.dimensions();}
            require(verdict==c.expectedVerdict(),c.scenario()+" verdict expected "+c.expectedVerdict()+" actual "+verdict);
            require(dimensions.stream().anyMatch(d->d.dimension()==c.targetDimension()&&d.classification()==c.expectedPrimaryClassification()),c.scenario()+" target classification");
        }
    }
    private static void verifyBaseline(List<?> results) {
        for(Object value:results) {
            Rca1Contracts.ReconciliationCase c=value instanceof Rca1Contracts.P1ComparisonResult r?r.reconciliationCase():((Rca1Contracts.P2ComparisonResult)value).reconciliationCase();
            if(c.role()!=Rca1Contracts.Role.BASELINE) continue;
            Rca1Contracts.Verdict v=value instanceof Rca1Contracts.P1ComparisonResult r?r.verdict():((Rca1Contracts.P2ComparisonResult)value).verdict();
            require(v!=Rca1Contracts.Verdict.BLOCKED_BY_IDENTITY&&v!=Rca1Contracts.Verdict.BLOCKED_BY_UNEXPECTED_MISMATCH,"baseline blocker "+c.scenario());
        }
    }
    private static void verifyIdentityCases(List<Rca1Contracts.ReconciliationCase> p1,List<Rca1Contracts.ReconciliationCase> p2) {
        Set<String> required=Set.of("absent","invalid","expired","deleted","mismatched","unauthorized_purpose","unauthorized_caller");
        for(var lane:List.of(p1,p2)) { Set<String> found=new HashSet<>(); for(var c:lane) for(String key:required) if(c.scenario().endsWith(key)) found.add(key); require(found.equals(required),"identity fixture inventory"); }
    }
    private static Rca1Contracts.Verdict aggregateP1(List<Rca1Contracts.P1ComparisonResult> results) { return results.stream().filter(r->r.reconciliationCase().role()==Rca1Contracts.Role.BASELINE).anyMatch(r->r.verdict()==Rca1Contracts.Verdict.BLOCKED_BY_UNEXPECTED_MISMATCH||r.verdict()==Rca1Contracts.Verdict.BLOCKED_BY_IDENTITY)?Rca1Contracts.Verdict.BLOCKED_BY_UNEXPECTED_MISMATCH:Rca1Contracts.Verdict.RECONCILED_WITH_EXPECTED_GAPS; }
    private static Rca1Contracts.Verdict aggregateP2(List<Rca1Contracts.P2ComparisonResult> results) { List<Rca1Contracts.P2ComparisonResult> base=results.stream().filter(r->r.reconciliationCase().role()==Rca1Contracts.Role.BASELINE).toList(); if(base.stream().anyMatch(r->r.verdict()==Rca1Contracts.Verdict.BLOCKED_BY_UNEXPECTED_MISMATCH||r.verdict()==Rca1Contracts.Verdict.BLOCKED_BY_IDENTITY)) return Rca1Contracts.Verdict.BLOCKED_BY_UNEXPECTED_MISMATCH; return base.stream().anyMatch(r->r.verdict()==Rca1Contracts.Verdict.RECONCILED_WITH_MIGRATION_GAPS)?Rca1Contracts.Verdict.RECONCILED_WITH_MIGRATION_GAPS:Rca1Contracts.Verdict.RECONCILED_WITH_EXPECTED_GAPS; }
    private static void require(boolean ok,String message){if(!ok) throw new AssertionError(message);}

    private record Run(List<Rca1Contracts.P1ComparisonResult> p1,List<Rca1Contracts.P2ComparisonResult> p2,
            Rca1Contracts.Verdict p1Verdict,Rca1Contracts.Verdict p2Verdict,List<Rca1Contracts.RedactedEvidenceRecord> records,
            Rca1Contracts.ReconciliationCounters counters,List<Rca1Contracts.MismatchItem> p1Mismatches,List<Rca1Contracts.MismatchItem> p2Mismatches,
            Set<String> p2Markers,String evidenceTsv,String evidenceJson) {}
}
