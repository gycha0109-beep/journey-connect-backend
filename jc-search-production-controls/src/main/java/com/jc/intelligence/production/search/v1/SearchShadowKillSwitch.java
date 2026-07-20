package com.jc.intelligence.production.search.v1;

public interface SearchShadowKillSwitch {
    SearchShadowKillState state();
    default boolean killed(){return state()!=SearchShadowKillState.ENABLED;}
}
