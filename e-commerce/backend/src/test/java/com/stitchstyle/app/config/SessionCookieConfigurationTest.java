package com.stitchstyle.app.config;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;

class SessionCookieConfigurationTest {

    private static final String COOKIE_NAME = "STITCHSTYLE_SESSION";

    private SessionCookieConfiguration.SafeCookieSerializer serializer;

    @BeforeEach
    void setUp() {
        serializer = new SessionCookieConfiguration.SafeCookieSerializer();
        serializer.setCookieName(COOKIE_NAME);
    }

    @Test
    void rejectsBase64ThatDecodesToNulBytes() {
        // "AAAA" is valid Base64 and decodes to 00 00 00, the exact value that
        // made PostgreSQL fail with invalid byte sequence 0x00.
        assertThat(serializer.readCookieValues(request(COOKIE_NAME, "AAAA"))).isEmpty();
    }

    @Test
    void acceptsTheUuidSessionIdSpringSessionWrites() {
        String id = "2e770ff8-1df7-49fc-b82a-2cd2c1e41f84";
        assertThat(serializer.readCookieValues(request(COOKIE_NAME, base64(id))))
                .containsExactly(id);
    }

    @Test
    void ignoresOtherApplicationsCookiesWithTheSameName() {
        assertThat(serializer.readCookieValues(request("SESSION", base64(
                "2e770ff8-1df7-49fc-b82a-2cd2c1e41f84")))).isEmpty();
    }

    @Test
    void rejectsBase64ValuesThatAreNotUuids() {
        assertThat(serializer.readCookieValues(request(COOKIE_NAME, base64("not-a-session-id")))).isEmpty();
        assertThat(serializer.readCookieValues(request(COOKIE_NAME, base64("../../etc/passwd")))).isEmpty();
    }

    private static String base64(String value) {
        return Base64.getEncoder().encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    private static MockHttpServletRequest request(String name, String value) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setCookies(new Cookie(name, value));
        return request;
    }
}
