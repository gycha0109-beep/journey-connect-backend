package com.jc.intelligence.production.search.v1;
public final class DisabledSearchShadowKillSwitch implements SearchShadowKillSwitch {
    @Override public SearchShadowKillState state(){return SearchShadowKillState.KILLED;}
}
