package com.jc.backend.admin;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
final class AdminRequestValidationInterceptor implements HandlerInterceptor {

    private static final Map<String, Set<String>> LIST_QUERY_PARAMETERS = Map.of(
            "/api/admin/reports", Set.of("status", "targetType", "search", "page", "size"),
            "/api/admin/posts", Set.of("moderationStatus", "visibility", "search", "page", "size"),
            "/api/admin/users", Set.of("role", "accountStatus", "search", "page", "size"));

    @Override
    public boolean preHandle(
            HttpServletRequest request,
            HttpServletResponse response,
            Object handler) {
        String path = request.getRequestURI();
        Set<String> allowed = HttpMethod.GET.matches(request.getMethod())
                ? LIST_QUERY_PARAMETERS.getOrDefault(path, Collections.emptySet())
                : Collections.emptySet();

        for (String name : request.getParameterMap().keySet()) {
            if (!allowed.contains(name)) {
                throw AdminQueryPolicy.invalid("지원하지 않는 관리자 query parameter입니다.");
            }
            String[] values = request.getParameterValues(name);
            if (values != null && values.length != 1) {
                throw AdminQueryPolicy.invalid("같은 관리자 query parameter를 중복 전달할 수 없습니다.");
            }
        }
        return true;
    }
}
