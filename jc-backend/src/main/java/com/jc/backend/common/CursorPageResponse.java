package com.jc.backend.common;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

/**
 * 무한 스크롤 화면에서 전체 개수 쿼리 없이 다음 위치만 전달하는 응답입니다.
 * nextCursor가 null이면 마지막 묶음입니다.
 */
public record CursorPageResponse<T>(
        List<T> items,
        String nextCursor,
        boolean hasNext,
        int size,
        @JsonInclude(JsonInclude.Include.NON_NULL) String recommendationRunId) {

    public static <T> CursorPageResponse<T> of(
            List<T> items,
            String nextCursor,
            boolean hasNext) {
        return new CursorPageResponse<>(items, nextCursor, hasNext, items.size(), null);
    }

    public static <T> CursorPageResponse<T> recommendation(
            List<T> items,
            String nextCursor,
            boolean hasNext,
            String recommendationRunId) {
        return new CursorPageResponse<>(
                items,
                nextCursor,
                hasNext,
                items.size(),
                recommendationRunId);
    }
}
