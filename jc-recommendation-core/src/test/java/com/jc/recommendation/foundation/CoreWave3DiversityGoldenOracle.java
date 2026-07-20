package com.jc.recommendation.foundation;

import com.jc.recommendation.canonical.CanonicalJson;
import com.jc.recommendation.diversity.DiversityReranker;
import com.jc.recommendation.model.diversity.*;
import com.jc.recommendation.model.entity.RecommendationEntityType;
import com.jc.recommendation.model.ranking.RankCandidatesInput;
import com.jc.recommendation.model.ranking.RankedCandidate;
import com.jc.recommendation.model.score.*;
import com.jc.recommendation.policy.DiversityPolicies;
import com.jc.recommendation.policy.DiversityPolicy;
import com.jc.recommendation.ranking.CandidateRanker;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;

public final class CoreWave3DiversityGoldenOracle {
    private static final ScoreComponentPolicyVersions VERSIONS = new ScoreComponentPolicyVersions("context-match-v1","interest-match-v1","freshness-v1","popularity-v1");
    private final List<Map<String,Object>> records=new ArrayList<>(); private final CandidateRanker ranker=new CandidateRanker(); private final DiversityReranker reranker=new DiversityReranker();
    public static void main(String[] args){new CoreWave3DiversityGoldenOracle().run();}
    private void run(){DiversityPolicy p=DiversityPolicies.V1;emit("POLICY",p.policyVersion(),p.effectiveFrom().toString(),Integer.toString(p.exposureWindowSize()),Integer.toString(p.maxPromotionDistance()),Integer.toString(p.maxDemotionDistance()),Integer.toString(p.exposureCaps().duplicateGroup()),Integer.toString(p.exposureCaps().author()),Integer.toString(p.exposureCaps().region()),Integer.toString(p.exposureCaps().theme()),joinDimensions(p.relaxationOrder()));
        List<CandidateScoreResult> unique=List.of(candidate("u1",.9),candidate("u2",.8),candidate("u3",.7));run("unchanged",unique,unique.stream().map(c->metadata(c.entityId(),"author:"+c.entityId(),null,null,"dup:"+c.entityId())).toList(),null);
        List<CandidateScoreResult> duplicate=List.of(candidate("x1",.9),candidate("x2",.8),candidate("y1",.7));run("strict",duplicate,List.of(metadata("x1","author:x1","region:seoul","theme:cafe","x"),metadata("x2","author:x2","region:seoul","theme:cafe","x"),metadata("y1","author:y1","region:seoul","theme:cafe","y")),null);
        List<CandidateScoreResult> same=List.of(candidate("s1",.9),candidate("s2",.8),candidate("s3",.7));run("relaxed",same,same.stream().map(c->metadata(c.entityId(),"same-author","region:seoul","theme:cafe","same")).toList(),null);
        DiversityPolicy forced=new DiversityPolicy("diversity-forced-test",p.effectiveFrom(),p.expectedRankingPolicyVersion(),p.expectedScorePolicyVersion(),p.maximumCandidateCount(),p.exposureWindowSize(),p.maxPromotionDistance(),0,p.exposureCaps(),p.relaxationOrder(),p.missingMetadataBehavior(),p.scoreMutation(),p.candidateRemoval(),p.candidateInsertion(),p.paginationStage(),p.explorationStage());
        run("forced",duplicate,List.of(metadata("x1","author:x1","region:seoul","theme:cafe","x"),metadata("x2","author:x2","region:seoul","theme:cafe","x"),metadata("y1","author:y1","region:seoul","theme:cafe","y")),forced);
        List<CandidateScoreResult> zero=List.of(candidate("m1",-0.0),candidate("m2",0.0));run("missing-zero",zero,zero.stream().map(c->metadata(c.entityId(),null,null,null,null)).toList(),null);
        Map<String,Object>d=new LinkedHashMap<>();d.put("fixtureVersion","wave3-diversity-v1");d.put("referencePackage","yeojeong-personalization-phase2-9b-offline-evaluation@0.1.0");d.put("records",records);try{System.out.write((CanonicalJson.stringify(d)+"\n").getBytes(StandardCharsets.UTF_8));}catch(IOException e){throw new IllegalStateException(e);}}
    private void run(String label,List<CandidateScoreResult> candidates,List<DiversityCandidateMetadata> metadata,DiversityPolicy policy){List<RankedCandidate> ranked=ranker.rank(new RankCandidatesInput("div-ranking","ranking-user","ranking-context","score-composition-v1",VERSIONS,candidates,30,null,null)).rankedCandidates();emitResult(label,reranker.rerank(new DiversityRerankInput("div-ranking","meta:"+label,"ranking-v1","score-composition-v1",ranked,metadata,policy)));}
    private void emitResult(String label, DiversityRerankResult result) {
        List<String> summary = new ArrayList<>();
        summary.add(label);
        summary.add(result.status().wireValue());
        summary.add(result.rankingPolicyVersion());
        summary.add(result.scorePolicyVersion());
        summary.add(result.diversityPolicyVersion());
        summary.add(Integer.toString(result.inputCount()));
        summary.add(Integer.toString(result.outputCount()));
        summary.add(Integer.toString(result.movedCandidateCount()));
        summary.add(Integer.toString(result.maxPromotionObserved()));
        summary.add(Integer.toString(result.maxDemotionObserved()));
        summary.add(Integer.toString(result.movementBoundForcedCount()));
        addCounts(summary, result.relaxationCountByDimension());
        addCounts(summary, result.violationCountByDimension());
        addCounts(summary, result.missingMetadataCountByDimension());
        emit("DIVERSITY_RESULT", summary.toArray(String[]::new));
        for (DiversifiedCandidate candidate : result.diversifiedCandidates()) {
            emit(
                    "DIVERSIFIED",
                    label,
                    candidate.entityId(),
                    candidate.entityType().wireValue(),
                    bits(candidate.score()),
                    bits(candidate.scoredWeight()),
                    bits(candidate.neutralFilledWeight()),
                    candidate.compositionMode().wireValue(),
                    candidate.scorePolicyVersion(),
                    Integer.toString(candidate.baseAbsoluteRank()),
                    Integer.toString(candidate.diversifiedAbsoluteRank()),
                    Integer.toString(candidate.displacement()),
                    Integer.toString(candidate.promotionDistance()),
                    Integer.toString(candidate.demotionDistance()),
                    bits(candidate.baseSortKey().score()),
                    bits(candidate.baseSortKey().neutralFilledWeight()),
                    Integer.toString(candidate.baseSortKey().entityTypeRank()),
                    candidate.baseSortKey().entityId(),
                    nullable(candidate.diversityMetadata().authorId()),
                    nullable(candidate.diversityMetadata().primaryRegionFeatureId()),
                    nullable(candidate.diversityMetadata().primaryThemeFeatureId()),
                    nullable(candidate.diversityMetadata().duplicateGroupId()),
                    candidate.selectionReason().wireValue(),
                    joinDimensions(candidate.appliedRelaxations()),
                    joinDimensions(candidate.violatedDimensionsAtSelection())
            );
        }
    }

    private static void addCounts(List<String> target, DiversityDimensionCounts counts) {
        target.add(Integer.toString(counts.duplicateGroup()));
        target.add(Integer.toString(counts.author()));
        target.add(Integer.toString(counts.region()));
        target.add(Integer.toString(counts.theme()));
    }

    private static CandidateScoreResult candidate(String id,double score){return new CandidateScoreResult("ranking-user","ranking-context",id,RecommendationEntityType.POST,CandidateScoreStatus.SCORED,score,ScoreCompositionMode.PERSONALIZED_CONTEXTUAL,1.0,0.0,List.of(ScoreComponentName.CONTEXT_MATCH,ScoreComponentName.INTEREST_MATCH),List.of(),null,null,"score-composition-v1",VERSIONS,List.of());}
    private static DiversityCandidateMetadata metadata(String id,String author,String region,String theme,String dup){return new DiversityCandidateMetadata(id,RecommendationEntityType.POST,author,region,theme,dup);}
    private void emit(String kind,String...fields){Map<String,Object>m=new LinkedHashMap<>();m.put("kind",kind);m.put("fields",List.of(fields));records.add(m);}private static String bits(double v){return String.format(Locale.ROOT,"%016x",Double.doubleToRawLongBits(v));}private static String nullable(Object v){return v==null?"null":String.valueOf(v);}private static String joinDimensions(List<DiversityDimension> ds){return String.join(",",ds.stream().map(DiversityDimension::wireValue).toList());}
}
