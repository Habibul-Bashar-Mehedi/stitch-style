package com.stitchstyle.app.security;

import tools.jackson.databind.ObjectMapper;
import com.stitchstyle.app.exception.ProblemDetailFactory;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
public class RestAccessDeniedHandler implements AccessDeniedHandler {

    private static final Logger log = LoggerFactory.getLogger(RestAccessDeniedHandler.class);

    private final ObjectMapper objectMapper;

    public RestAccessDeniedHandler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response, AccessDeniedException accessDeniedException)
            throws IOException {
        log.warn("access denied {} {} reason={} cookie={} xsrf={}", request.getMethod(), request.getRequestURI(),
                accessDeniedException.getMessage(), request.getHeader("Cookie"), request.getHeader("X-XSRF-TOKEN"));
        response.setStatus(HttpStatus.FORBIDDEN.value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        var problem = ProblemDetailFactory.create(HttpStatus.FORBIDDEN, "Forbidden",
                "You do not have access to this resource.", "FORBIDDEN", request);
        objectMapper.writeValue(response.getWriter(), problem);
    }
}
