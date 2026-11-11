package com.stitchstyle.app.config;

import com.stitchstyle.app.model.entity.AuditAction;
import com.stitchstyle.app.service.AuditService;
import jakarta.servlet.http.HttpServletRequest;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.Map;
import java.util.Set;

@Aspect
@Component
@Order(Ordered.LOWEST_PRECEDENCE)
public class AuditAspect {

    private static final Set<String> MUTATIONS = Set.of("POST", "PUT", "PATCH", "DELETE");

    private final AuditService auditService;

    public AuditAspect(AuditService auditService) {
        this.auditService = auditService;
    }

    @Around("execution(* com.stitchstyle.app.controller.admin..*(..))")
    public Object audit(ProceedingJoinPoint joinPoint) throws Throwable {
        var attributes = RequestContextHolder.getRequestAttributes();
        if (!(attributes instanceof ServletRequestAttributes servletAttributes)) {
            return joinPoint.proceed();
        }
        HttpServletRequest request = servletAttributes.getRequest();
        if (!MUTATIONS.contains(request.getMethod())) {
            return joinPoint.proceed();
        }
        Object result = joinPoint.proceed();
        String controller = joinPoint.getSignature().getDeclaringType().getSimpleName()
                .replace("Admin", "").replace("Controller", "").toUpperCase();
        auditService.record(action(request.getMethod()), controller, null, null,
                Map.of("method", joinPoint.getSignature().getName(),
                        "path", request.getRequestURI(),
                        "args", summarize(joinPoint.getArgs())));
        return result;
    }

    private AuditAction action(String method) {
        return switch (method) {
            case "POST" -> AuditAction.CREATE;
            case "DELETE" -> AuditAction.DELETE;
            default -> AuditAction.UPDATE;
        };
    }

    private String summarize(Object[] args) {
        StringBuilder summary = new StringBuilder();
        for (Object arg : args) {
            if (arg == null) continue;
            String value = arg.toString();
            if (value.length() > 200) value = value.substring(0, 200) + "…";
            if (summary.length() > 0) summary.append("; ");
            summary.append(value);
        }
        return summary.toString();
    }
}
