package com.stitchstyle.app.config;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.boot.web.server.Cookie;
import org.springframework.boot.web.server.autoconfigure.ServerProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.session.web.http.CookieSerializer;
import org.springframework.session.web.http.DefaultCookieSerializer;

import java.util.List;
import java.util.regex.Pattern;

/**
 * Session cookie configuration with an input guard.
 *
 * <p>Spring Session decodes the session cookie and passes the result straight to
 * {@code JdbcIndexedSessionRepository.findById(...)}. PostgreSQL rejects NUL bytes
 * in text parameters, so a stale or crafted cookie such as
 * {@code STITCHSTYLE_SESSION=AAAA} (valid Base64, decodes to {@code 00 00 00})
 * would otherwise make every request fail with
 * {@code invalid byte sequence for encoding "UTF8": 0x00}.
 *
 * <p>Only values that look like the UUIDs produced by Spring Session's default
 * {@code UuidSessionIdGenerator} are allowed through. Anything else is treated as
 * "no session" and Spring Session creates a fresh one.
 */
@Configuration
public class SessionCookieConfiguration {

    private static final Pattern SESSION_ID = Pattern.compile(
            "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$");

    @Bean
    CookieSerializer cookieSerializer(ServerProperties serverProperties) {
        Cookie cookie = serverProperties.getServlet().getSession().getCookie();
        SafeCookieSerializer serializer = new SafeCookieSerializer();
        if (cookie.getName() != null) {
            serializer.setCookieName(cookie.getName());
        }
        if (cookie.getDomain() != null) {
            serializer.setDomainName(cookie.getDomain());
        }
        if (cookie.getPath() != null) {
            serializer.setCookiePath(cookie.getPath());
        }
        if (cookie.getMaxAge() != null) {
            serializer.setCookieMaxAge(Math.toIntExact(cookie.getMaxAge().toSeconds()));
        }
        serializer.setUseHttpOnlyCookie(!Boolean.FALSE.equals(cookie.getHttpOnly()));
        serializer.setUseSecureCookie(Boolean.TRUE.equals(cookie.getSecure()));
        if (cookie.getSameSite() != null) {
            serializer.setSameSite(cookie.getSameSite().attributeValue());
        }
        return serializer;
    }

    static final class SafeCookieSerializer extends DefaultCookieSerializer {

        @Override
        public List<String> readCookieValues(HttpServletRequest request) {
            return super.readCookieValues(request).stream()
                    .filter(SafeCookieSerializer::isSessionId)
                    .toList();
        }

        private static boolean isSessionId(String value) {
            return value != null && SESSION_ID.matcher(value).matches();
        }
    }
}
