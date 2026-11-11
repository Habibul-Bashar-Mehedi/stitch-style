package com.stitchstyle.app.security;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
public class ReturnUrlAuthenticationSuccessHandler implements AuthenticationSuccessHandler {

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
                                        Authentication authentication) throws IOException, ServletException {
        Object stored = request.getSession(false) == null ? null
                : request.getSession(false).getAttribute(ReturnUrlCaptureFilter.SESSION_KEY);
        String target = RelativeReturnUrlValidator.safeOrDefault(stored == null ? null : stored.toString(), "/");
        if (request.getSession(false) != null) {
            request.getSession(false).removeAttribute(ReturnUrlCaptureFilter.SESSION_KEY);
        }
        // Staff who sign in from the storefront should land in the admin panel,
        // not on the customer home page. An explicit deep link is still honoured.
        if ("/".equals(target) && isStaff(authentication)) {
            target = "/admin";
        }
        response.sendRedirect(target);
    }

    private boolean isStaff(Authentication authentication) {
        return authentication.getAuthorities().stream()
                .map(authority -> authority.getAuthority())
                .anyMatch(authority -> "ROLE_ADMIN".equals(authority) || "ROLE_MANAGER".equals(authority));
    }
}
