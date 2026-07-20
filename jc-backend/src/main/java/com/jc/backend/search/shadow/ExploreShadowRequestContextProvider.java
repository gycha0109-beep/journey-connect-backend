package com.jc.backend.search.shadow;

/** Supplies server-derived request context for an explicitly enabled test/stage assembly. */
@FunctionalInterface
public interface ExploreShadowRequestContextProvider {
    ExploreShadowRequestContext current();
}
