package com.jc.backend.common;

import java.util.Map;

/**
 * 컨트롤러, 검증기, Spring Security에서 동일하게 사용하는 오류 응답입니다.
 * 오류 코드는 프론트가 메시지 문자열에 의존하지 않고 분기할 수 있도록 제공합니다.
 */
public record ApiErrorResponse(
        boolean success,
        String code,
        String message,
        Map<String, String> errors) {

    public static ApiErrorResponse of(String code, String message) {
        return new ApiErrorResponse(false, code, message, Map.of());
    }

    public static ApiErrorResponse validation(Map<String, String> errors) {
        return new ApiErrorResponse(false, "VALIDATION_ERROR", "입력값을 확인해주세요.", errors);
    }

    public static ApiErrorResponse authenticationRequired() {
        return of("AUTHENTICATION_REQUIRED", "로그인이 필요합니다.");
    }

    public static ApiErrorResponse accessDenied() {
        return of("ACCESS_DENIED", "접근 권한이 없습니다.");
    }
}
