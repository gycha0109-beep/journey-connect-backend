package com.jc.intelligence.production.search.v1;

import java.util.HashSet;
import java.util.Set;

public final class AllowlistedInternalCohortSelector implements ProductionShadowCohortSelector {
    private final Set<String> hashes;
    public AllowlistedInternalCohortSelector(Set<String> hashes,boolean approved){
        if(!approved||hashes==null){this.hashes=Set.of();return;} Set<String> copy=new HashSet<>();
        for(String h:hashes) if(h!=null&&h.matches("[0-9a-f]{64}")) copy.add(h); this.hashes=Set.copyOf(copy);
    }
    @Override public boolean includes(String key){return key!=null&&key.matches("[0-9a-f]{64}")&&hashes.contains(key);}
}
