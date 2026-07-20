package com.jc.intelligence.integration.search.v1;

import com.jc.intelligence.compat.search.explore.v1.LegacyExploreCompatibilityResult;
import com.jc.intelligence.compat.search.explore.v1.LegacyExploreCompatibilityStatus;
import com.jc.intelligence.runtime.search.v1.SearchRuntimeResultV1;
import com.jc.intelligence.runtime.search.v1.SearchRuntimeStatus;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class SearchShadowComparisonHarness implements SearchShadowComparator {
    @Override public SearchShadowComparisonResultV1 compare(
            LegacyExploreCompatibilityResult legacy,
            SearchRuntimeResultV1 runtime,
            int topK,
            Duration runtimeDuration) {
        if (legacy == null || runtime == null) throw new IllegalArgumentException("legacy and runtime results are required");
        if (topK < 1) throw new IllegalArgumentException("topK must be positive");
        List<String> legacyRefs = legacy.items().stream().map(item -> item.entityRef().value()).toList();
        List<String> runtimeRefs = runtime.snapshot() == null ? List.of()
                : runtime.snapshot().items().stream().map(item -> item.candidate().entityRef().value()).toList();

        ArrayList<SearchShadowMismatchV1> mismatches = new ArrayList<>();
        boolean legacySuccess = legacy.status() == LegacyExploreCompatibilityStatus.SUCCESS;
        boolean runtimeSuccess = runtime.status() == SearchRuntimeStatus.SUCCESS
                || runtime.status() == SearchRuntimeStatus.FALLBACK
                || runtime.status() == SearchRuntimeStatus.NO_RESULTS;
        if (legacySuccess && runtime.status() == SearchRuntimeStatus.NO_RESULTS) {
            add(mismatches, SearchShadowMismatchCode.LEGACY_SUCCESS_RUNTIME_NO_RESULTS, SearchShadowSeverity.WARNING);
        } else if (legacySuccess && runtimeSuccess) {
            add(mismatches, SearchShadowMismatchCode.LEGACY_SUCCESS_RUNTIME_SUCCESS, SearchShadowSeverity.INFO);
        } else if (legacySuccess) {
            add(mismatches, SearchShadowMismatchCode.LEGACY_SUCCESS_RUNTIME_FAILED, SearchShadowSeverity.ERROR);
            add(mismatches, SearchShadowMismatchCode.RUNTIME_FAILURE, SearchShadowSeverity.ERROR);
        } else if (runtimeSuccess) {
            add(mismatches, SearchShadowMismatchCode.LEGACY_FAILURE_RUNTIME_SUCCESS, SearchShadowSeverity.WARNING);
        } else {
            add(mismatches, SearchShadowMismatchCode.RUNTIME_FAILURE, SearchShadowSeverity.ERROR);
        }

        Map<String, Integer> legacyPositions = firstPositions(legacyRefs);
        Map<String, Integer> runtimePositions = firstPositions(runtimeRefs);
        int duplicateCount = duplicateCount(legacyRefs) + duplicateCount(runtimeRefs);
        if (duplicateCount > 0) add(mismatches, SearchShadowMismatchCode.DUPLICATE_ENTITY, SearchShadowSeverity.WARNING);

        Set<String> legacySet = new LinkedHashSet<>(legacyRefs);
        Set<String> runtimeSet = new LinkedHashSet<>(runtimeRefs);
        Set<String> intersection = new LinkedHashSet<>(legacySet);
        intersection.retainAll(runtimeSet);
        Set<String> legacyOnly = new LinkedHashSet<>(legacySet);
        legacyOnly.removeAll(runtimeSet);
        Set<String> runtimeOnly = new LinkedHashSet<>(runtimeSet);
        runtimeOnly.removeAll(legacySet);

        if (legacyRefs.size() != runtimeRefs.size()) add(mismatches, SearchShadowMismatchCode.COUNT_MISMATCH, SearchShadowSeverity.WARNING);
        if (!legacySet.equals(runtimeSet)) add(mismatches, SearchShadowMismatchCode.ENTITY_SET_MISMATCH, SearchShadowSeverity.WARNING);
        for (String ref : legacyOnly) {
            mismatches.add(new SearchShadowMismatchV1(SearchShadowMismatchCode.MISSING_ENTITY, SearchShadowSeverity.WARNING,
                    ref, legacyPositions.get(ref), null));
        }
        for (String ref : runtimeOnly) {
            mismatches.add(new SearchShadowMismatchV1(SearchShadowMismatchCode.UNEXPECTED_ENTITY, SearchShadowSeverity.WARNING,
                    ref, null, runtimePositions.get(ref)));
        }
        if (legacySet.equals(runtimeSet) && !legacyRefs.equals(runtimeRefs)) {
            add(mismatches, SearchShadowMismatchCode.ORDERING_MISMATCH, SearchShadowSeverity.WARNING);
        }
        for (String ref : intersection) {
            Integer left = legacyPositions.get(ref);
            Integer right = runtimePositions.get(ref);
            if (!left.equals(right)) {
                mismatches.add(new SearchShadowMismatchV1(SearchShadowMismatchCode.POSITION_MISMATCH,
                        SearchShadowSeverity.WARNING, ref, left, right));
            }
        }

        add(mismatches, SearchShadowMismatchCode.PAGINATION_NOT_COMPARABLE, SearchShadowSeverity.NOT_COMPARABLE);
        add(mismatches, SearchShadowMismatchCode.CURSOR_NOT_COMPARABLE, SearchShadowSeverity.NOT_COMPARABLE);
        add(mismatches, SearchShadowMismatchCode.VISIBILITY_NOT_COMPARABLE, SearchShadowSeverity.NOT_COMPARABLE);
        add(mismatches, SearchShadowMismatchCode.RANKING_NOT_COMPARABLE, SearchShadowSeverity.NOT_COMPARABLE);
        if (legacy.failure() != null && legacy.failure().failureCode().wireValue().contains("sort")) {
            add(mismatches, SearchShadowMismatchCode.UNSUPPORTED_LEGACY_SORT, SearchShadowSeverity.NOT_COMPARABLE);
        }

        int topKOverlap = topKOverlap(legacyRefs, runtimeRefs, topK);
        int denominator = Math.min(topK, Math.max(legacyRefs.size(), runtimeRefs.size()));
        double overlapRatio = denominator == 0 ? 1.0d : (double) topKOverlap / denominator;
        SearchShadowComparisonMetricsV1 metrics = new SearchShadowComparisonMetricsV1(
                legacyRefs.size(), runtimeRefs.size(), intersection.size(), legacyOnly.size(), runtimeOnly.size(),
                topKOverlap, overlapRatio, samePrefix(legacyRefs, runtimeRefs), duplicateCount,
                Duration.ZERO, runtimeDuration);
        SearchShadowComparisonStatus status = runtimeSuccess && legacySuccess
                ? SearchShadowComparisonStatus.COMPARED : SearchShadowComparisonStatus.NOT_COMPARABLE;
        return new SearchShadowComparisonResultV1(status, metrics, mismatches);
    }

    public SearchShadowComparisonResultV1 failure(Duration runtimeDuration) {
        SearchShadowMismatchV1 mismatch = new SearchShadowMismatchV1(SearchShadowMismatchCode.COMPARISON_FAILURE,
                SearchShadowSeverity.ERROR, null, null, null);
        return new SearchShadowComparisonResultV1(SearchShadowComparisonStatus.FAILED,
                new SearchShadowComparisonMetricsV1(0, 0, 0, 0, 0, 0, 1.0d, 0, 0,
                        Duration.ZERO, runtimeDuration), List.of(mismatch));
    }

    private static Map<String, Integer> firstPositions(List<String> refs) {
        HashMap<String, Integer> positions = new HashMap<>();
        for (int index = 0; index < refs.size(); index++) positions.putIfAbsent(refs.get(index), index + 1);
        return positions;
    }
    private static int duplicateCount(List<String> refs) { return refs.size() - new LinkedHashSet<>(refs).size(); }
    private static int samePrefix(List<String> left, List<String> right) {
        int limit = Math.min(left.size(), right.size());
        int count = 0;
        while (count < limit && left.get(count).equals(right.get(count))) count++;
        return count;
    }
    private static int topKOverlap(List<String> left, List<String> right, int topK) {
        Set<String> a = new LinkedHashSet<>(left.subList(0, Math.min(topK, left.size())));
        Set<String> b = new LinkedHashSet<>(right.subList(0, Math.min(topK, right.size())));
        a.retainAll(b);
        return a.size();
    }
    private static void add(List<SearchShadowMismatchV1> target, SearchShadowMismatchCode code, SearchShadowSeverity severity) {
        target.add(new SearchShadowMismatchV1(code, severity, null, null, null));
    }
}
