package com.jc.intelligence.production.search.v1;
public final class EmptyProductionShadowCohortSelector implements ProductionShadowCohortSelector { @Override public boolean includes(String key){return false;} }
