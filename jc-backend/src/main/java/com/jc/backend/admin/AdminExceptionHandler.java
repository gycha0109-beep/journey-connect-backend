package com.jc.backend.admin;

import com.jc.backend.common.ApiErrorResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice(assignableTypes = {
    AdminDashboardController.class,
    AdminReportController.class,
    AdminPostController.class,
    AdminUserController.class
})
@Order(Ordered.HIGHEST_PRECEDENCE)
class AdminExceptionHandler {

    @ExceptionHandler({
        HttpMessageNotReadableException.class,
        MissingServletRequestParameterException.class,
        MethodArgumentTypeMismatchException.class
    })
    ResponseEntity<ApiErrorResponse> invalidCommand(Exception exception) {
        return ResponseEntity.badRequest().body(ApiErrorResponse.of(
                "INVALID_ADMIN_COMMAND",
                "관리자 요청 형식 또는 파라미터를 확인해주세요."));
    }

    @ExceptionHandler(DataAccessException.class)
    ResponseEntity<ApiErrorResponse> databaseFailure(DataAccessException exception) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ApiErrorResponse.of(
                "ADMIN_OPERATION_FAILED",
                "관리자 요청을 안전하게 완료하지 못했습니다."));
    }
}
