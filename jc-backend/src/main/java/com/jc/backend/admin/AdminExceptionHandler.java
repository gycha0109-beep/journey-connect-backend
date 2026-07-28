package com.jc.backend.admin;

import com.jc.backend.common.ApiErrorResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import java.sql.SQLException;
import java.util.Set;
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

    private static final Set<String> STATE_CONFLICT_SQL_STATES =
            Set.of("P0001", "23514", "42501", "23505", "40001", "40P01");

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
        String sqlState = sqlState(exception);
        if ("P0002".equals(sqlState)) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiErrorResponse.of(
                    "ADMIN_TARGET_NOT_FOUND",
                    "관리 대상 정보를 찾을 수 없습니다."));
        }
        if (STATE_CONFLICT_SQL_STATES.contains(sqlState)) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(ApiErrorResponse.of(
                    "ADMIN_STATE_CONFLICT",
                    "현재 상태에서는 관리자 명령을 적용할 수 없습니다."));
        }
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ApiErrorResponse.of(
                "ADMIN_OPERATION_FAILED",
                "관리자 요청을 안전하게 완료하지 못했습니다."));
    }

    private static String sqlState(DataAccessException exception) {
        Throwable cause = exception.getMostSpecificCause();
        return cause instanceof SQLException sqlException ? sqlException.getSQLState() : null;
    }
}
