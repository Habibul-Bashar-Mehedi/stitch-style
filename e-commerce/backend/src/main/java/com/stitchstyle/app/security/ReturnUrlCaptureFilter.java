package com.stitchstyle.app.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.annotation.Order;
import org.springframework.session.web.http.SessionRepositoryFilter;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
// Must run AFTER Spring Session wraps the request, otherwise getSession(true)
// returns Tomcat's container session instead of the Spring Session stored in
// PostgreSQL. The return URL was silently lost and the OAuth2 callback could
// not find it. Security's filter chain (-100) still runs after this.
@Order(SessionRepositoryFilter.DEFAULT_ORDER + 1)
public class ReturnUrlCaptureFilter extends OncePerRequestFilter {

    public static final String SESSION_KEY = "oauth.returnUrl";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if ("/oauth2/authorization/google".equals(request.getRequestURI())) {
            String returnUrl = request.getParameter("returnUrl");
            if (RelativeReturnUrlValidator.isSafe(returnUrl)) {
                request.getSession(true).setAttribute(SESSION_KEY, returnUrl);
            }
        }
        chain.doFilter(request, response);
    }
}
