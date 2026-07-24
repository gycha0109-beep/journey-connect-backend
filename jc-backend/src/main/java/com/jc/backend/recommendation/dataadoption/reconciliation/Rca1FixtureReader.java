package com.jc.backend.recommendation.dataadoption.reconciliation;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class Rca1FixtureReader {
    private static final String HEADER="scenario\trole\texpectedVerdict\ttargetDimension\texpectedPrimaryClassification\tfields";
    private static final String DELETE="__DELETE__";

    public List<Rca1Contracts.ReconciliationCase> read(Path path,Rca1Contracts.Lane lane) throws IOException {
        List<Rca1Contracts.ReconciliationCase> out=new ArrayList<>(); Set<String> names=new HashSet<>();
        try(BufferedReader reader=Files.newBufferedReader(path,StandardCharsets.UTF_8)) {
            if(!HEADER.equals(reader.readLine())) throw new IllegalArgumentException("unexpected fixture header");
            String line; int number=1;
            while((line=reader.readLine())!=null) {
                number++; if(line.isBlank()||line.startsWith("#")) continue;
                String[] c=line.split("\\t",-1); if(c.length!=6) throw new IllegalArgumentException("fixture columns at "+number);
                if(!names.add(c[0])) throw new IllegalArgumentException("duplicate scenario "+c[0]);
                Map<String,String> fields=new LinkedHashMap<>(lane==Rca1Contracts.Lane.P1?p1Defaults():p2Defaults());
                overrides(fields,c[5],number);
                out.add(new Rca1Contracts.ReconciliationCase(lane,c[0],Rca1Contracts.Role.valueOf(c[1]),
                        Rca1Contracts.Verdict.valueOf(c[2]),Rca1Contracts.Dimension.valueOf(c[3]),
                        Rca1Contracts.Classification.valueOf(c[4]),fields));
            }
        }
        return List.copyOf(out);
    }

    private static void overrides(Map<String,String> fields,String source,int line) {
        if(source.isBlank()) return; Set<String> keys=new HashSet<>();
        for(String entry:source.split(";",-1)) {
            int i=entry.indexOf('='); if(i<1) throw new IllegalArgumentException("override at "+line);
            String key=entry.substring(0,i), value=entry.substring(i+1); if(!keys.add(key)) throw new IllegalArgumentException("duplicate override "+key);
            if(DELETE.equals(value)) fields.remove(key); else fields.put(key,value);
        }
    }

    private static Map<String,String> common() {
        Map<String,String> f=new LinkedHashMap<>();
        f.put("referenceTime","2026-07-24T00:00:00Z");
        f.put("evidenceTimestamp","2026-07-24T00:00:00Z");
        f.put("identityStatus","VALID"); f.put("identitySubjectRef","synthetic-subject:alpha");
        f.put("identityUserRef","synthetic-user:42"); f.put("identityPurpose",Rca1Contracts.PURPOSE);
        f.put("identityCaller",Rca1Contracts.CALLER); f.put("identityValidUntil","2026-08-24T00:00:00Z");
        f.put("identityDeleted","false"); return f;
    }
    private static Map<String,String> p1Defaults() {
        Map<String,String> f=common();
        f.put("referenceProfileSchemaVersion","recommendation-profile-input-v1");
        f.put("candidateProfileSchemaVersion","recommendation-profile-input-v1");
        f.put("referenceProjectionPolicyVersion","recommendation-profile-projection-policy-v1");
        f.put("candidateProjectionPolicyVersion","recommendation-profile-projection-policy-v1");
        f.put("referenceInteractionCounts","post_like:2,recommendation_click:3");
        f.put("candidateInteractionCounts","recommendation_click:3,post_like:2");
        f.put("referenceActivityWindowDays","7"); f.put("candidateActivityWindowDays","7");
        f.put("referenceWindow7","5"); f.put("candidateWindow7","5");
        f.put("referenceWindow30","9"); f.put("candidateWindow30","9");
        f.put("referenceWindow90","15"); f.put("candidateWindow90","15");
        f.put("referenceCheckpointRef","checkpoint:p1"); f.put("candidateCheckpointRef","checkpoint:p1");
        f.put("referenceCheckpointSequence","100"); f.put("candidateCheckpointSequence","100");
        f.put("referenceCheckpointAt","2026-07-23T23:30:00Z"); f.put("candidateCheckpointAt","2026-07-23T23:30:00Z");
        f.put("checkpointMaxStalenessSeconds","7200");
        f.put("referenceLineage","1".repeat(64)); f.put("candidateLineage","1".repeat(64));
        f.put("referenceFingerprint","2".repeat(64)); f.put("candidateFingerprint","3".repeat(64));
        f.put("orderingComparable","false"); f.put("eventGrainAvailable","false");
        f.put("explicitPreferencesAvailable","false"); f.put("transformPolicyAvailable","false");
        return f;
    }
    private static Map<String,String> p2Defaults() {
        Map<String,String> f=common();
        f.put("referenceExperimentRef","experiment:ranking"); f.put("candidateExperimentRef","experiment:ranking");
        f.put("referenceExperimentVersion","experiment-ranking-v1"); f.put("candidateExperimentVersion","experiment-ranking-v1");
        f.put("referenceVariantRef","baseline"); f.put("candidateVariantRef","baseline");
        f.put("candidateExposureAuthority",Rca1Contracts.P2_EXPOSURE_AUTHORITY); f.put("candidateExposureKind","p2_experiment_exposure");
        f.put("referenceExposureRef","exposure:p2"); f.put("candidateExposureRef","exposure:p2");
        f.put("referenceSubjectRef","synthetic-subject:alpha"); f.put("candidateSubjectRef","synthetic-subject:alpha");
        f.put("referenceSessionRef","synthetic-session:alpha"); f.put("candidateSessionRef","synthetic-session:alpha");
        f.put("referenceRunRef","synthetic-run:alpha"); f.put("candidateRunRef","synthetic-run:alpha");
        f.put("referenceOutcomeWindowSeconds","604800"); f.put("candidateOutcomeWindowSeconds","604800");
        f.put("referenceEngagementEvents",""); f.put("candidateEngagementEvents","");
        f.put("referenceFallbackObserved","false"); f.put("candidateFallbackObserved","false");
        f.put("candidateFallbackBoundRunRef","synthetic-run:alpha");
        f.put("candidateStaleUnexposedAssignmentGap","false"); f.put("candidatePersistedDedupeGap","false");
        f.put("referenceCheckpointRef","checkpoint:p2"); f.put("candidateCheckpointRef","checkpoint:p2");
        f.put("referenceCheckpointSequence","200"); f.put("candidateCheckpointSequence","200");
        f.put("referenceCheckpointAt","2026-07-23T23:30:00Z"); f.put("candidateCheckpointAt","2026-07-23T23:30:00Z");
        f.put("checkpointMaxStalenessSeconds","7200");
        f.put("referenceLineage","4".repeat(64)); f.put("candidateLineage","4".repeat(64));
        return f;
    }
}
