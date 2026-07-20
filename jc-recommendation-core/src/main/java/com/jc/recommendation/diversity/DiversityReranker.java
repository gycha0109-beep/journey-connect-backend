package com.jc.recommendation.diversity;

import com.jc.recommendation.model.diversity.*;
import com.jc.recommendation.model.ranking.RankedCandidate;
import com.jc.recommendation.model.ranking.RankingSortKey;
import com.jc.recommendation.policy.DiversityPolicies;
import com.jc.recommendation.policy.DiversityPolicy;
import java.util.*;

public final class DiversityReranker {
    private record Entry(RankedCandidate candidate, DiversityCandidateMetadata metadata) { }
    private record Selection(Entry entry, DiversitySelectionReason reason, List<DiversityDimension> relaxations, List<DiversityDimension> violations) { }

    public DiversityRerankResult rerank(DiversityRerankInput input) {
        nonBlank(input.rankingSnapshotId(), "rankingSnapshotId"); nonBlank(input.metadataSnapshotId(), "metadataSnapshotId");
        nonBlank(input.rankingPolicyVersion(), "rankingPolicyVersion"); nonBlank(input.scorePolicyVersion(), "scorePolicyVersion");
        DiversityPolicy policy = input.policy() == null ? DiversityPolicies.V1 : input.policy();
        DiversityContracts.validatePolicy(policy);
        if (!input.rankingPolicyVersion().equals(policy.expectedRankingPolicyVersion())) throw new IllegalArgumentException("rankingPolicyVersion is incompatible with Diversity policy");
        if (!input.scorePolicyVersion().equals(policy.expectedScorePolicyVersion())) throw new IllegalArgumentException("scorePolicyVersion is incompatible with Diversity policy");
        if (input.baseRankedCandidates().size() > policy.maximumCandidateCount()) throw new IllegalArgumentException("baseRankedCandidates exceeds maximumCandidateCount");

        Set<String> candidateIds = new HashSet<>(); List<RankedCandidate> candidates = input.baseRankedCandidates();
        for (int i=0;i<candidates.size();i++) {
            RankedCandidate c=candidates.get(i); DiversityContracts.validateCandidate(c,i,input.scorePolicyVersion());
            if (c.absoluteRank()!=i+1) throw new IllegalArgumentException("baseRankedCandidates["+i+"].absoluteRank must equal "+(i+1));
            if (!candidateIds.add(identity(c.entityType().wireValue(),c.entityId()))) throw new IllegalArgumentException("duplicate base candidate identity: "+identity(c.entityType().wireValue(),c.entityId()));
        }
        Map<String,DiversityCandidateMetadata> metadata=new HashMap<>();
        for (int i=0;i<input.candidateMetadata().size();i++) {
            DiversityCandidateMetadata m=input.candidateMetadata().get(i); DiversityContracts.validateMetadata(m,i);
            String key=identity(m.entityType().wireValue(),m.entityId()); if(metadata.putIfAbsent(key,m)!=null) throw new IllegalArgumentException("duplicate metadata identity: "+key);
        }
        if(metadata.size()!=candidateIds.size()) throw new IllegalArgumentException("candidate metadata coverage mismatch");
        for(String key:candidateIds) if(!metadata.containsKey(key)) throw new IllegalArgumentException("missing metadata for candidate: "+key);
        for(String key:metadata.keySet()) if(!candidateIds.contains(key)) throw new IllegalArgumentException("extra metadata row: "+key);
        List<Entry> entries=new ArrayList<>(); for(RankedCandidate c:candidates) entries.add(new Entry(c,metadata.get(identity(c.entityType().wireValue(),c.entityId()))));

        EnumMap<DiversityDimension,Integer> missing=zero();
        for(Entry e:entries) for(DiversityDimension d:DiversityContracts.DIMENSIONS) if(value(e.metadata(),d)==null) increment(missing,d);
        List<Entry> remaining=new ArrayList<>(entries), selected=new ArrayList<>(); List<DiversifiedCandidate> diversified=new ArrayList<>();
        EnumMap<DiversityDimension,Integer> relax=zero(), violations=zero(); int forced=0;
        for(int position=1;position<=entries.size();position++) {
            Selection decision=choose(remaining,selected,position,policy);
            if(!remaining.remove(decision.entry())) throw new IllegalStateException("selected candidate was not remaining");
            selected.add(decision.entry()); for(DiversityDimension d:decision.relaxations()) increment(relax,d); for(DiversityDimension d:decision.violations()) increment(violations,d);
            if(decision.reason()==DiversitySelectionReason.MOVEMENT_BOUND_FORCED) forced++;
            RankedCandidate c=decision.entry().candidate(); int displacement=position-c.absoluteRank(); int promotion=Math.max(0,-displacement), demotion=Math.max(0,displacement);
            if(promotion>policy.maxPromotionDistance()||demotion>policy.maxDemotionDistance()) throw new IllegalStateException("Diversity movement bound violated");
            diversified.add(new DiversifiedCandidate(c.entityId(),c.entityType(),c.score(),c.scoredWeight(),c.neutralFilledWeight(),c.compositionMode(),c.scorePolicyVersion(),c.absoluteRank(),position,displacement,promotion,demotion,
                    new RankingSortKey(c.sortKey().score(),c.sortKey().neutralFilledWeight(),c.sortKey().entityTypeRank(),c.sortKey().entityId()),
                    new DiversityCandidateMetadata(decision.entry().metadata().entityId(),decision.entry().metadata().entityType(),decision.entry().metadata().authorId(),decision.entry().metadata().primaryRegionFeatureId(),decision.entry().metadata().primaryThemeFeatureId(),decision.entry().metadata().duplicateGroupId()),
                    decision.reason(),decision.relaxations(),decision.violations()));
        }
        if(!remaining.isEmpty()||diversified.size()!=entries.size()) throw new IllegalStateException("Diversity candidate count invariant failed");
        Set<String> out=new HashSet<>(); for(DiversifiedCandidate c:diversified) out.add(identity(c.entityType().wireValue(),c.entityId()));
        if(out.size()!=candidateIds.size()||!out.containsAll(candidateIds)) throw new IllegalStateException("Diversity identity preservation invariant failed");
        int moved=0,maxPromotion=0,maxDemotion=0; boolean unchanged=true;
        for(int i=0;i<diversified.size();i++){DiversifiedCandidate c=diversified.get(i);if(c.displacement()!=0)moved++;maxPromotion=Math.max(maxPromotion,c.promotionDistance());maxDemotion=Math.max(maxDemotion,c.demotionDistance());Entry e=entries.get(i);if(!c.entityId().equals(e.candidate().entityId())||c.entityType()!=e.candidate().entityType())unchanged=false;}
        return new DiversityRerankResult(input.rankingSnapshotId(),input.metadataSnapshotId(),input.rankingPolicyVersion(),input.scorePolicyVersion(),policy.policyVersion(),
                unchanged?DiversityRerankStatus.UNCHANGED:DiversityRerankStatus.RERANKED,entries.size(),diversified.size(),moved,maxPromotion,maxDemotion,forced,counts(relax),counts(violations),counts(missing),diversified);
    }

    private static Selection choose(List<Entry> remaining,List<Entry> selected,int position,DiversityPolicy policy){
        int threshold=position-policy.maxDemotionDistance(); Entry overdue=earliest(remaining.stream().filter(e->e.candidate().absoluteRank()<=threshold).toList());
        if(overdue!=null)return new Selection(overdue,DiversitySelectionReason.MOVEMENT_BOUND_FORCED,List.of(),prospective(selected,overdue,policy));
        List<Entry> pool=remaining.stream().filter(e->e.candidate().absoluteRank()<=position+policy.maxPromotionDistance()).toList(); if(pool.isEmpty())throw new IllegalStateException("Diversity promotion pool unexpectedly empty");
        Map<String,List<DiversityDimension>> map=new HashMap<>(); for(Entry e:pool)map.put(key(e),prospective(selected,e,policy));
        Entry strict=earliest(pool.stream().filter(e->map.get(key(e)).isEmpty()).toList());
        if(strict!=null)return new Selection(strict,earliest(remaining)==strict?DiversitySelectionReason.BASE_ORDER_PRESERVED:DiversitySelectionReason.STRICT_DIVERSITY,List.of(),List.of());
        EnumSet<DiversityDimension> allowed=EnumSet.noneOf(DiversityDimension.class); List<DiversityDimension> prefix=new ArrayList<>();
        for(DiversityDimension d:policy.relaxationOrder()){allowed.add(d);prefix.add(d);Entry found=earliest(pool.stream().filter(e->allowed.containsAll(map.get(key(e)))).toList());if(found!=null)return new Selection(found,DiversitySelectionReason.RELAXED_DIVERSITY,List.copyOf(prefix),map.get(key(found)));}
        throw new IllegalStateException("Diversity relaxation failed to select a candidate");
    }
    private static List<DiversityDimension> prospective(List<Entry> selected,Entry entry,DiversityPolicy policy){
        int count=policy.exposureWindowSize()-1; int from=count==0?0:Math.max(0,selected.size()-count); List<Entry> recent=selected.subList(from,selected.size()); List<DiversityDimension> result=new ArrayList<>();
        for(DiversityDimension d:DiversityContracts.DIMENSIONS){String key=value(entry.metadata(),d);if(key==null)continue;int seen=1;for(Entry prior:recent)if(Objects.equals(value(prior.metadata(),d),key))seen++;if(seen>policy.exposureCaps().get(d))result.add(d);}
        return List.copyOf(result);
    }
    private static Entry earliest(List<Entry> entries){Entry result=null;for(Entry e:entries)if(result==null||e.candidate().absoluteRank()<result.candidate().absoluteRank())result=e;return result;}
    private static String value(DiversityCandidateMetadata m,DiversityDimension d){return switch(d){case DUPLICATE_GROUP->m.duplicateGroupId();case AUTHOR->m.authorId();case REGION->m.primaryRegionFeatureId();case THEME->m.primaryThemeFeatureId();};}
    private static String identity(String type,String id){return type+'\u0000'+id;} private static String key(Entry e){return identity(e.candidate().entityType().wireValue(),e.candidate().entityId());}
    private static EnumMap<DiversityDimension,Integer> zero(){EnumMap<DiversityDimension,Integer> m=new EnumMap<>(DiversityDimension.class);for(DiversityDimension d:DiversityContracts.DIMENSIONS)m.put(d,0);return m;}
    private static void increment(EnumMap<DiversityDimension,Integer> map,DiversityDimension d){map.put(d,map.get(d)+1);}
    private static DiversityDimensionCounts counts(EnumMap<DiversityDimension,Integer> m){return new DiversityDimensionCounts(m.get(DiversityDimension.DUPLICATE_GROUP),m.get(DiversityDimension.AUTHOR),m.get(DiversityDimension.REGION),m.get(DiversityDimension.THEME));}
    private static void nonBlank(String value,String label){if(value==null||value.trim().isEmpty())throw new IllegalArgumentException(label+" must be nonblank");}
}
