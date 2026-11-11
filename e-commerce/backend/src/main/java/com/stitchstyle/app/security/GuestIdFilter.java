package com.stitchstyle.app.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;
import java.util.UUID;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 20)
public class GuestIdFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String guestId = null;
        Cookie[] cookies = request.getCookies();
        if (cookies != null) {
            for (Cookie cookie : cookies) {
                if ("guest_id".equals(cookie.getName())) {
                    guestId = cookie.getValue();
                    break;
                }
            }
        }
        if (guestId == null || guestId.isBlank()) {
            guestId = UUID.randomUUID().toString();
            ResponseCookie cookie = ResponseCookie.from("guest_id", guestId)
                    .httpOnly(true)
                    .sameSite("Lax")
                    .path("/")
                    .maxAge(Duration.ofDays(30))
                    .secure(request.isSecure())
                    .build();
            response.addHeader("Set-Cookie", cookie.toString());
        }
        request.setAttribute("guestId", guestId);
        chain.doFilter(request, response);
    }
}
