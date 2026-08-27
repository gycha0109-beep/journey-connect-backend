package com.jc.backend.report;

import com.jc.backend.common.DomainException;
import com.jc.backend.database.DatabaseRole;
import com.jc.backend.database.DatabaseTransactional;
import java.sql.SQLException;
import java.util.Locale;
import java.util.Set;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/** PF9 authenticated user entry point for the canonical report command. */
@Service
@DatabaseTransactional(role = DatabaseRole.APP, readOnly = true)
public class UserReportService {

    private static final Set<String> REASON_CATEGORIES = Set.of(
            "spam",
            "harassment",
            "hate",
            "sexual_content",
            "violence",
            "misinformation",
            "privacy",
            "copyright",
            "other");

    private final JdbcTemplate jdbc;

    public UserReportService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @DatabaseTransactional(role = DatabaseRole.APP)
    public UserReportDtos.CreateResult reportPost(
            long postId,
            UserReportDtos.CreateRequest request) {
        if (postId <= 0) {
            throw targetNotFound();
        }
        String reasonCategory = normalizeReason(request.reasonCategory());
        String reasonDetail = normalizeDetail(request.reasonDetail());

        try {
            Long reportId = jdbc.queryForObject(
                    "select public.submit_report('post', ?, ?, ?)",
                    Long.class,
                    postId,
                    reasonCategory,
                    reasonDetail);
            if (reportId == null) {
                throw new IllegalStateException("submit_report returned null report ID");
            }
            return new UserReportDtos.CreateResult(reportId, "pending");
        } catch (DataAccessException exception) {
            String state = sqlState(exception);
            if ("P0002".equals(state)) {
                throw targetNotFound();
            }
            if ("23505".equals(state)) {
                throw new DomainException(
                        HttpStatus.CONFLICT,
                        "REPORT_ALREADY_EXISTS",
                        "이미 처리 중인 신고가 있습니다.");
            }
            if ("42501".equals(state)) {
                throw new DomainException(
                        HttpStatus.FORBIDDEN,
                        "USER_INACTIVE",
                        "비활성 계정은 신고할 수 없습니다.");
            }
            if ("23514".equals(state)) {
                throw invalidReason();
            }
            throw exception;
        }
    }

    private String normalizeReason(String value) {
        String normalized = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
        if (!REASON_CATEGORIES.contains(normalized)) {
            throw invalidReason();
        }
        return normalized;
    }

    private String normalizeDetail(String value) {
        return value == null || value.isBlank() ? "" : value.trim();
    }

    private DomainException invalidReason() {
        return new DomainException(
                HttpStatus.BAD_REQUEST,
                "INVALID_REPORT_REASON",
                "지원하지 않는 신고 사유입니다.");
    }

    private DomainException targetNotFound() {
        return new DomainException(
                HttpStatus.NOT_FOUND,
                "REPORT_TARGET_NOT_FOUND",
                "신고할 수 있는 게시물을 찾을 수 없습니다.");
    }

    private static String sqlState(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            if (current instanceof SQLException sqlException) {
                return sqlException.getSQLState();
            }
            current = current.getCause();
        }
        return null;
    }
}
