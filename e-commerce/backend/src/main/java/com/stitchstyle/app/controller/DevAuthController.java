package com.stitchstyle.app.controller;

import com.stitchstyle.app.model.entity.User;
import com.stitchstyle.app.service.DevAuthService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Test-only sign-in helper for the Playwright suite.
 *
 * <p>The real sign-in path is Google OIDC, which a headless browser cannot
 * drive. This endpoint creates the same server-side session that OIDC creates,
 * so E2E tests exercise the real session cookie, CSRF contract and URL
 * authorization instead of bypassing security. It only exists when
 * {@code app.dev-tools.enabled=true} (never in production, where the default is
 * false) and is served under the already permit-all, CSRF-exempt
 * {@code /api/v1/dev} prefix.
 */
@RestController
@RequestMapping("/api/v1/dev")
@ConditionalOnProperty(name = "app.dev-tools.enabled", havingValue = "true")
public class DevAuthController {

    private final DevAuthService devAuthService;

    public DevAuthController(DevAuthService devAuthService) {
        this.devAuthService = devAuthService;
    }

    public record LoginRequest(String email, String role, String fullName) {}

    public record LoginResponse(Long id, String email, String fullName, String role) {}

    @PostMapping("/session")
    public LoginResponse login(@RequestBody(required = false) LoginRequest body, HttpServletRequest request) {
        User user = devAuthService.signIn(
                body != null ? body.email() : null,
                body != null ? body.role() : null,
                body != null ? body.fullName() : null);

        var authentication = UsernamePasswordAuthenticationToken.authenticated(
                user.getEmail(), null, List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole().name())));
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
        request.getSession(true).setAttribute(
                HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, context);

        return new LoginResponse(user.getId(), user.getEmail(), user.getFullName(), user.getRole().name());
    }
}
