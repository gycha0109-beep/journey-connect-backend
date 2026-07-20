package com.jc.intelligence.production.search.v1;

import java.util.Locale;
import java.util.function.Supplier;

public final class PropertyBackedSearchShadowKillSwitch implements SearchShadowKillSwitch {
    private final Supplier<String> rawValue; private final Supplier<Boolean> productionApproval;
    public PropertyBackedSearchShadowKillSwitch(Supplier<String> rawValue,Supplier<Boolean> productionApproval){if(rawValue==null||productionApproval==null)throw new IllegalArgumentException("suppliers required");this.rawValue=rawValue;this.productionApproval=productionApproval;}
    @Override public SearchShadowKillState state(){
        try {
            if(!Boolean.TRUE.equals(productionApproval.get())) return SearchShadowKillState.KILLED;
            String raw=rawValue.get(); if(raw==null||raw.isBlank()||!raw.equals(raw.trim()))return SearchShadowKillState.KILLED;
            String v=raw.toLowerCase(Locale.ROOT); return (v.equals("enabled")||v.equals("true"))?SearchShadowKillState.ENABLED:SearchShadowKillState.KILLED;
        } catch (RuntimeException ignored) {
            return SearchShadowKillState.KILLED;
        }
    }
}
