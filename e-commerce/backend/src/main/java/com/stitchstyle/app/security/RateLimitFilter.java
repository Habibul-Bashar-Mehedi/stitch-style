package com.stitchstyle.app.security;

import tools.jackson.databind.ObjectMapper;
import com.stitchstyle.app.exception.RateLimitExceededException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Map;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class RateLimitFilter extends OncePerRequestFilter {

    private final RateLimitStore rateLimitStore;
    private final ObjectMapper objectMapper;

    @Value("${app.rate-limit.enabled:true}")
    private boolean enabled;

    public RateLimitFilter(RateLimitStore rateLimitStore, ObjectMapper objectMapper) {
        this.rateLimitStore = rateLimitStore;
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        int limit = enabled ? limitFor(request) : 0;
        if (limit <= 0) {
            chain.doFilter(request, response);
            return;
        }
        String key = clientIp(request) + ":" + request.getMethod() + ":" + request.getRequestURI();
        if (!rateLimitStore.tryConsume(key, limit)) {
            response.setStatus(429);
            response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
            response.setHeader("Retry-After", "60");
            var body = Map.of(
                    "type", "https://stitchandstyle.bd/problems/rate-limited",
                    "title", "Rate limit exceeded",
                    "status", 429,
                    "detail", "Too many requests. Retry after the window resets.",
                    "code", "RATE_LIMITED",
                    "traceId", MDC.get("correlationId") == null ? "" : MDC.get("correlationId")
            );
            response.getWriter().write(objectMapper.writeValueAsString(body));
            return;
        }
        chain.doFilter(request, response);
    }

    private int limitFor(HttpServletRequest request) {
        String method = request.getMethod();
        String path = request.getRequestURI();
        if ("POST".equals(method) && "/api/v1/public/orders".equals(path)) return 5;
        if ("POST".equals(method) && "/api/v1/public/orders/track".equals(path)) return 10;
        if ("GET".equals(method) && path.startsWith("/api/v1/public/orders/track/")) return 30;
        if ("GET".equals(method) && "/oauth2/authorization/google".equals(path)) return 20;
        if ("GET".equals(method) && "/login/oauth2/code/google".equals(path)) return 30;
        if ("POST".equals(method) && "/api/v1/admin/media".equals(path)) return 30;
        return 0;
    }

    private String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("Forwarded");
        if (forwarded != null && !forwarded.isBlank()) {
            int idx = forwarded.indexOf("for=");
            if (idx >= 0) return forwarded.substring(idx + 4).split("[;,\\s]")[0];
        }
        return request.getRemoteAddr();
    }
}
