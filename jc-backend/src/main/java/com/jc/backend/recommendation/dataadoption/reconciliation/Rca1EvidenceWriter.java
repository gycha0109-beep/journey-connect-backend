package com.jc.backend.recommendation.dataadoption.reconciliation;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class Rca1EvidenceWriter {
    public static final String TSV_HEADER = "hashedCaseId\tlane\tcontractId\tcontractVersion\tcomparisonDimension\tclassification\tnormalizedExpected\tnormalizedActual\tcheckpoint\tlineageFingerprint\tevidenceTimestamp\tverifierVersion\ttestedSha";

    public List<Rca1Contracts.RedactedEvidenceRecord> records(
            List<Rca1Contracts.P1ComparisonResult> p1,
            List<Rca1Contracts.P2ComparisonResult> p2,
            Instant timestamp,
            String testedSha) {
        List<Rca1Contracts.RedactedEvidenceRecord> out=new ArrayList<>();
        for(var result:p1) add(out,result.reconciliationCase(),result.dimensions(),timestamp,testedSha);
        for(var result:p2) add(out,result.reconciliationCase(),result.dimensions(),timestamp,testedSha);
        out.sort(Comparator.comparing((Rca1Contracts.RedactedEvidenceRecord r)->r.lane().name())
                .thenComparing(Rca1Contracts.RedactedEvidenceRecord::hashedCaseId)
                .thenComparing(r->r.comparisonDimension().name()));
        Set<String> unique=new HashSet<>();
        for(var record:out) if(!unique.add(record.hashedCaseId()+"|"+record.comparisonDimension()))
            throw new IllegalArgumentException("duplicate case/dimension evidence");
        return List.copyOf(out);
    }

    public void write(Path directory,List<Rca1Contracts.RedactedEvidenceRecord> records,
            Rca1Contracts.ReconciliationCounters counters,Rca1Contracts.Verdict p1Verdict,
            Rca1Contracts.Verdict p2Verdict,List<Rca1Contracts.MismatchItem> p1Mismatches,
            List<Rca1Contracts.MismatchItem> p2Mismatches) throws IOException {
        Files.createDirectories(directory);
        Files.writeString(directory.resolve("RCA1_RECONCILIATION_EVIDENCE.tsv"),tsv(records),StandardCharsets.UTF_8);
        Files.writeString(directory.resolve("RCA1_RECONCILIATION_EVIDENCE.json"),json(records),StandardCharsets.UTF_8);
        Files.writeString(directory.resolve("RCA1_RECONCILIATION_COUNTERS.tsv"),counterTsv(counters),StandardCharsets.UTF_8);
        Files.writeString(directory.resolve("RCA1_RECONCILIATION_COUNTERS.json"),counterJson(counters),StandardCharsets.UTF_8);
        Files.writeString(directory.resolve("RCA1_LANE_VERDICTS.tsv"),"lane\tverdict\nP1\t"+p1Verdict+"\nP2\t"+p2Verdict+"\n",StandardCharsets.UTF_8);
        Files.writeString(directory.resolve("RCA1_P1_MISMATCH_INVENTORY.tsv"),mismatchTsv(p1Mismatches),StandardCharsets.UTF_8);
        Files.writeString(directory.resolve("RCA1_P2_MISMATCH_INVENTORY.tsv"),mismatchTsv(p2Mismatches),StandardCharsets.UTF_8);
    }

    public String tsv(List<Rca1Contracts.RedactedEvidenceRecord> records) {
        StringBuilder out=new StringBuilder(TSV_HEADER).append('\n');
        for(var r:records) out.append(r.hashedCaseId()).append('\t').append(r.lane()).append('\t')
                .append(r.contractId()).append('\t').append(r.contractVersion()).append('\t')
                .append(r.comparisonDimension()).append('\t').append(r.classification()).append('\t')
                .append(cell(r.normalizedExpected())).append('\t').append(cell(r.normalizedActual())).append('\t')
                .append(cell(r.checkpoint())).append('\t').append(r.lineageFingerprint()).append('\t')
                .append(r.evidenceTimestamp()).append('\t').append(r.verifierVersion()).append('\t').append(r.testedSha()).append('\n');
        return out.toString();
    }

    public String json(List<Rca1Contracts.RedactedEvidenceRecord> records) {
        StringBuilder out=new StringBuilder("[\n");
        for(int i=0;i<records.size();i++) {
            var r=records.get(i); if(i>0) out.append(",\n");
            out.append("  {\"hashedCaseId\":\"").append(j(r.hashedCaseId())).append("\",\"lane\":\"").append(r.lane())
                    .append("\",\"contractId\":\"").append(j(r.contractId())).append("\",\"contractVersion\":\"").append(j(r.contractVersion()))
                    .append("\",\"comparisonDimension\":\"").append(r.comparisonDimension()).append("\",\"classification\":\"").append(r.classification())
                    .append("\",\"normalizedExpected\":\"").append(j(r.normalizedExpected())).append("\",\"normalizedActual\":\"").append(j(r.normalizedActual()))
                    .append("\",\"checkpoint\":\"").append(j(r.checkpoint())).append("\",\"lineageFingerprint\":\"").append(j(r.lineageFingerprint()))
                    .append("\",\"evidenceTimestamp\":\"").append(r.evidenceTimestamp()).append("\",\"verifierVersion\":\"").append(j(r.verifierVersion()))
                    .append("\",\"testedSha\":\"").append(j(r.testedSha())).append("\"}");
        }
        return out.append("\n]\n").toString();
    }

    public static Rca1Contracts.ReconciliationCounters counters(List<Rca1Contracts.P1ComparisonResult> p1,List<Rca1Contracts.P2ComparisonResult> p2) {
        List<Rca1Contracts.DimensionResult> p1d=p1.stream().flatMap(r->r.dimensions().stream()).toList();
        List<Rca1Contracts.DimensionResult> p2d=p2.stream().flatMap(r->r.dimensions().stream()).toList();
        return new Rca1Contracts.ReconciliationCounters(
                p1.size()+p2.size(), count(p1d,Rca1Contracts.Classification.MATCH_EXACT)+count(p1d,Rca1Contracts.Classification.MATCH_DERIVED),
                count(p1d,Rca1Contracts.Classification.EXPECTED_SEMANTIC_GAP)+count(p1d,Rca1Contracts.Classification.PROTECTED_AUTHORITY_DIFFERENCE),
                p1.stream().filter(r->r.reconciliationCase().role()==Rca1Contracts.Role.EXPECTED_NEGATIVE&&r.verdict()==Rca1Contracts.Verdict.BLOCKED_BY_UNEXPECTED_MISMATCH).count(),
                count(p2d,Rca1Contracts.Classification.MATCH_EXACT)+count(p2d,Rca1Contracts.Classification.MATCH_DERIVED),
                count(p2d,Rca1Contracts.Classification.MIGRATION_REQUIRED),count(p2d,Rca1Contracts.Classification.EXPOSURE_AUTHORITY_MISMATCH),
                count(p1d,Rca1Contracts.Classification.IDENTITY_MAPPING_REQUIRED)+count(p1d,Rca1Contracts.Classification.IDENTITY_SCHEME_MISMATCH)
                        +count(p2d,Rca1Contracts.Classification.IDENTITY_MAPPING_REQUIRED)+count(p2d,Rca1Contracts.Classification.IDENTITY_SCHEME_MISMATCH),
                count(p1d,Rca1Contracts.Classification.SOURCE_CHECKPOINT_MISMATCH)+count(p1d,Rca1Contracts.Classification.SOURCE_STALE)
                        +count(p2d,Rca1Contracts.Classification.SOURCE_CHECKPOINT_MISMATCH)+count(p2d,Rca1Contracts.Classification.SOURCE_STALE),
                count(p1d,Rca1Contracts.Classification.LINEAGE_MISMATCH)+count(p2d,Rca1Contracts.Classification.LINEAGE_MISMATCH),
                count(p1d,Rca1Contracts.Classification.RECONCILIATION_INCONCLUSIVE)+count(p2d,Rca1Contracts.Classification.RECONCILIATION_INCONCLUSIVE));
    }

    private static void add(List<Rca1Contracts.RedactedEvidenceRecord> out,Rca1Contracts.ReconciliationCase c,
            List<Rca1Contracts.DimensionResult> dimensions,Instant timestamp,String testedSha) {
        String id=Rca1Normalizer.hash(Rca1Contracts.FIXTURE_ID+"|"+c.lane()+"|"+c.scenario());
        for(var d:dimensions) out.add(new Rca1Contracts.RedactedEvidenceRecord(id,c.lane(),Rca1Contracts.EVIDENCE_ID,
                Rca1Contracts.VERSION,d.dimension(),d.classification(),Rca1Normalizer.safe(d.expected().value()),
                Rca1Normalizer.safe(d.actual().value()),"redacted-checkpoint",Rca1Normalizer.hash("lineage|"+c.lane()),timestamp,
                Rca1Contracts.VERIFIER_VERSION,testedSha));
    }
    private static long count(List<Rca1Contracts.DimensionResult> source,Rca1Contracts.Classification c) { return source.stream().filter(r->r.classification()==c).count(); }
    private static String mismatchTsv(List<Rca1Contracts.MismatchItem> items) {
        StringBuilder out=new StringBuilder("lane\tscenarioHash\tdimension\tclassification\tblocking\tdetail\n");
        items.stream().sorted(Comparator.comparing((Rca1Contracts.MismatchItem m)->m.lane().name()).thenComparing(Rca1Contracts.MismatchItem::scenario).thenComparing(m->m.dimension().name()))
                .forEach(m->out.append(m.lane()).append('\t').append(Rca1Normalizer.hash(m.scenario())).append('\t').append(m.dimension()).append('\t').append(m.classification()).append('\t').append(m.blocking()).append('\t').append(cell(m.detail())).append('\n'));
        return out.toString();
    }
    private static String counterTsv(Rca1Contracts.ReconciliationCounters c) { return "counter\tvalue\nreconciliation_case_count\t"+c.reconciliationCaseCount()+"\np1_exact_match_count\t"+c.p1ExactMatchCount()+"\np1_expected_gap_count\t"+c.p1ExpectedGapCount()+"\np1_unexpected_mismatch_count\t"+c.p1UnexpectedMismatchCount()+"\np2_exact_match_count\t"+c.p2ExactMatchCount()+"\np2_migration_required_count\t"+c.p2MigrationRequiredCount()+"\np2_authority_mismatch_count\t"+c.p2AuthorityMismatchCount()+"\nidentity_blocked_count\t"+c.identityBlockedCount()+"\ncheckpoint_mismatch_count\t"+c.checkpointMismatchCount()+"\nlineage_mismatch_count\t"+c.lineageMismatchCount()+"\ninconclusive_count\t"+c.inconclusiveCount()+"\n"; }
    private static String counterJson(Rca1Contracts.ReconciliationCounters c) { return "{\n  \"reconciliation_case_count\": "+c.reconciliationCaseCount()+",\n  \"p1_exact_match_count\": "+c.p1ExactMatchCount()+",\n  \"p1_expected_gap_count\": "+c.p1ExpectedGapCount()+",\n  \"p1_unexpected_mismatch_count\": "+c.p1UnexpectedMismatchCount()+",\n  \"p2_exact_match_count\": "+c.p2ExactMatchCount()+",\n  \"p2_migration_required_count\": "+c.p2MigrationRequiredCount()+",\n  \"p2_authority_mismatch_count\": "+c.p2AuthorityMismatchCount()+",\n  \"identity_blocked_count\": "+c.identityBlockedCount()+",\n  \"checkpoint_mismatch_count\": "+c.checkpointMismatchCount()+",\n  \"lineage_mismatch_count\": "+c.lineageMismatchCount()+",\n  \"inconclusive_count\": "+c.inconclusiveCount()+"\n}\n"; }
    private static String j(String value) { return Rca1Normalizer.json(value); }
    private static String cell(String value) { return value.replace('\t',' ').replace("\r\n","\n").replace('\r','\n').replace('\n',' '); }
}
