package com.stitchstyle.app.exception;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;

import java.net.URI;
import java.util.List;

public final class ProblemDetailFactory {

    private ProblemDetailFactory() {}

    public static ProblemDetail create(HttpStatus status, String title, String detail,
                                       String code, HttpServletRequest request) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(title);
        problem.setType(URI.create("https://stitchandstyle.bd/problems/" + code.toLowerCase().replace('_', '-')));
        problem.setInstance(URI.create(request.getRequestURI()));
        problem.setProperty("code", code);
        String traceId = MDC.get("correlationId");
        if (traceId != null) problem.setProperty("traceId", traceId);
        return problem;
    }

    public static ProblemDetail withErrors(HttpStatus status, String title, String detail,
                                           String code, List<InsufficientStockException.ItemError> errors,
                                           HttpServletRequest request) {
        ProblemDetail problem = create(status, title, detail, code, request);
        problem.setProperty("errors", errors);
        return problem;
    }
}
