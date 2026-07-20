package com.jc.backend.common;

import java.util.List;
import org.springframework.data.domain.Page;

/** 페이지 구현 세부사항을 노출하지 않고 프론트가 필요한 정보만 반환합니다. */
public record PageResponse<T>(
        List<T> items,
        int page,
        int size,
        long totalElements,
        int totalPages,
        boolean last) {

    public static <T> PageResponse<T> from(Page<T> page) {
        return new PageResponse<>(
                page.getContent(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages(),
                page.isLast());
    }
}
