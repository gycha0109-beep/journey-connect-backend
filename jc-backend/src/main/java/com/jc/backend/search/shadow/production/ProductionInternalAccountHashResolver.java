package com.jc.backend.search.shadow.production;

import java.util.Optional;

@FunctionalInterface
public interface ProductionInternalAccountHashResolver {
    Optional<String> currentAccountHash();
}
