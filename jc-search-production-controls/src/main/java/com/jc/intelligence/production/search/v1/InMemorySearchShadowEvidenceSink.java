package com.jc.intelligence.production.search.v1;

import java.util.ArrayList;import java.util.List;
public final class InMemorySearchShadowEvidenceSink implements SearchShadowEvidenceSink { private final int capacity;private final List<PrivacySafeSearchShadowEvidenceV1> records=new ArrayList<>(); public InMemorySearchShadowEvidenceSink(int capacity){if(capacity<1||capacity>10000)throw new IllegalArgumentException("capacity invalid");this.capacity=capacity;} public synchronized void record(PrivacySafeSearchShadowEvidenceV1 e){if(e==null)throw new IllegalArgumentException("evidence required");if(records.size()==capacity)records.remove(0);records.add(e);} public synchronized List<PrivacySafeSearchShadowEvidenceV1> records(){return List.copyOf(records);} }
