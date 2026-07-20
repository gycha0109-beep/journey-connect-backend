package com.jc.intelligence.production.search.v1;

import java.util.concurrent.atomic.AtomicReference;

public final class TestMutableSearchShadowKillSwitch implements SearchShadowKillSwitch {
    private final AtomicReference<SearchShadowKillState> state=new AtomicReference<>(SearchShadowKillState.KILLED);
    public void enable(){state.set(SearchShadowKillState.ENABLED);} public void kill(){state.set(SearchShadowKillState.KILLED);}
    @Override public SearchShadowKillState state(){return state.get();}
}
