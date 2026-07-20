package com.jc.backend.common;

/**
 * 모든 정상 응답의 공통 형태입니다.
 *
 * @param success 정상 처리 여부
 * @param data 응답 본문
 * @param message 선택적 안내 메시지
 */
public record ApiResponse<T>(boolean success, T data, String message) {

    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(true, data, null);
    }

    public static <T> ApiResponse<T> created(T data) {
        return new ApiResponse<>(true, data, "created");
    }
}
