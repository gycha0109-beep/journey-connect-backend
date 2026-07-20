package com.jc.backend.search.shadow.stage;

import com.jc.intelligence.wiring.search.v1.SearchShadowComparisonLogPort;
import com.jc.intelligence.wiring.search.v1.SearchShadowComparisonLogResultV1;
import com.jc.intelligence.wiring.search.v1.SearchShadowLogStatus;
import com.jc.intelligence.wiring.search.v1.SearchShadowStructuredRecordV1;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.atomic.AtomicLong;

/** Bounded memory-only stage recorder. It is neither persistence nor exposure evidence. */
public final class InMemoryStageSearchShadowComparisonLogPort implements SearchShadowComparisonLogPort {
    private final int capacity;
    private final ConcurrentLinkedDeque<SearchShadowStructuredRecordV1> records = new ConcurrentLinkedDeque<>();
    private final AtomicLong attempts = new AtomicLong();

    public InMemoryStageSearchShadowComparisonLogPort(int capacity) {
        if (capacity < 1 || capacity > 10_000) throw new IllegalArgumentException("capacity must be 1..10000");
        this.capacity = capacity;
    }

    @Override public boolean available() { return true; }

    @Override public SearchShadowComparisonLogResultV1 log(SearchShadowStructuredRecordV1 record) {
        if (record == null) throw new IllegalArgumentException("record is required");
        attempts.incrementAndGet();
        records.addLast(record);
        while (records.size() > capacity) records.pollFirst();
        return new SearchShadowComparisonLogResultV1(SearchShadowLogStatus.ACCEPTED, "in_memory_only");
    }

    public List<SearchShadowStructuredRecordV1> records() {
        return List.copyOf(new ArrayList<>(records));
    }

    public long attemptCount() { return attempts.get(); }
    public void clear() { records.clear(); }
}
