package com.stitchstyle.app.security;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;

class SecurityFiltersTest {

    @Test
    void correlationIdIsReusedWhenProvided() throws Exception {
        CorrelationIdFilter filter = new CorrelationIdFilter();
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/public/home");
        request.addHeader("X-Request-Id", "trace-123");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertThat(response.getHeader("X-Request-Id")).isEqualTo("trace-123");
        assertThat(MDC.get("correlationId")).isNull();
    }

    @Test
    void correlationIdIsGeneratedWhenMissing() throws Exception {
        CorrelationIdFilter filter = new CorrelationIdFilter();
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(new MockHttpServletRequest("GET", "/"), response, new MockFilterChain());

        assertThat(response.getHeader("X-Request-Id")).isNotBlank();
    }

    @Test
    void guestIdCookieIsCreatedForNewVisitors() throws Exception {
        GuestIdFilter filter = new GuestIdFilter();
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertThat(request.getAttribute("guestId")).isNotNull();
        assertThat(response.getHeader("Set-Cookie")).contains("guest_id=");
        assertThat(response.getHeader("Set-Cookie")).contains("HttpOnly");
        assertThat(response.getHeader("Set-Cookie")).contains("SameSite=Lax");
    }

    @Test
    void existingGuestIdCookieIsReused() throws Exception {
        GuestIdFilter filter = new GuestIdFilter();
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/");
        request.setCookies(new Cookie("guest_id", "guest-existing"));
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertThat(request.getAttribute("guestId")).isEqualTo("guest-existing");
        assertThat(response.getHeader("Set-Cookie")).isNull();
    }

    @Test
    void returnUrlIsOnlyCapturedWhenSafe() throws Exception {
        ReturnUrlCaptureFilter filter = new ReturnUrlCaptureFilter();
        MockHttpServletRequest safe = new MockHttpServletRequest("GET", "/oauth2/authorization/google");
        safe.setParameter("returnUrl", "/cart");
        filter.doFilter(safe, new MockHttpServletResponse(), new MockFilterChain());
        assertThat(safe.getSession().getAttribute(ReturnUrlCaptureFilter.SESSION_KEY)).isEqualTo("/cart");

        MockHttpServletRequest unsafe = new MockHttpServletRequest("GET", "/oauth2/authorization/google");
        unsafe.setParameter("returnUrl", "https://evil.example");
        filter.doFilter(unsafe, new MockHttpServletResponse(), new MockFilterChain());
        assertThat(unsafe.getSession().getAttribute(ReturnUrlCaptureFilter.SESSION_KEY)).isNull();
    }

    @Test
    void returnUrlIsIgnoredForOtherPaths() throws Exception {
        ReturnUrlCaptureFilter filter = new ReturnUrlCaptureFilter();
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/cart");
        request.setParameter("returnUrl", "/orders");
        filter.doFilter(request, new MockHttpServletResponse(), new MockFilterChain());

        assertThat(request.getSession().getAttribute(ReturnUrlCaptureFilter.SESSION_KEY)).isNull();
    }
}
