package com.jc.intelligence.runtime.search.v1;

@FunctionalInterface
public interface SearchRuntime {
    SearchRuntimeResultV1 execute(SearchRuntimeExecutionRequestV1 execution);
}
