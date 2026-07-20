package com.jc.backend.common;

import org.springframework.http.HttpStatus;

/** 예상 가능한 도메인 규칙 위반을 HTTP 상태와 오류 코드로 전달합니다. */
public class DomainException extends RuntimeException {

    private final HttpStatus status;
    private final String code;

    public DomainException(HttpStatus status, String message) {
        this(status, status.name(), message);
    }

    public DomainException(HttpStatus status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getCode() {
        return code;
    }
}
