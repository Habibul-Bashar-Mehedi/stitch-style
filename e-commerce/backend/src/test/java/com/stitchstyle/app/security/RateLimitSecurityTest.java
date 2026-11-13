package com.stitchstyle.app.security;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.util.ReflectionTestUtils;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;

class RateLimitSecurityTest {

    @Test
    void inMemoryStoreAllowsUpToTheLimitThenRejects() {
        InMemoryRateLimitStore store = new InMemoryRateLimitStore();

        assertThat(store.tryConsume("k", 2)).isTrue();
        assertThat(store.tryConsume("k", 2)).isTrue();
        assertThat(store.tryConsume("k", 2)).isFalse();
        // different keys are independent
        assertThat(store.tryConsume("other", 1)).isTrue();
    }

    @Test
    void rateLimitFilterRejectsAfterOrderLimitAndWritesProblemJson() throws Exception {
        RateLimitFilter filter = new RateLimitFilter(new InMemoryRateLimitStore(), JsonMapper.builder().build());
        ReflectionTestUtils.setField(filter, "enabled", true);

        for (int i = 0; i < 5; i++) {
            MockHttpServletResponse response = new MockHttpServletResponse();
            filter.doFilter(request("POST", "/api/v1/public/orders"), response, new MockFilterChain());
            assertThat(response.getStatus()).isEqualTo(200);
        }

        MockHttpServletResponse limited = new MockHttpServletResponse();
        filter.doFilter(request("POST", "/api/v1/public/orders"), limited, new MockFilterChain());

        assertThat(limited.getStatus()).isEqualTo(429);
        assertThat(limited.getHeader("Retry-After")).isEqualTo("60");
        assertThat(limited.getContentType()).contains("application/problem+json");
        assertThat(limited.getContentAsString()).contains("\"code\":\"RATE_LIMITED\"");
    }

    @Test
    void unprotectedPathsAreNeverLimited() throws Exception {
        RateLimitFilter filter = new RateLimitFilter(new InMemoryRateLimitStore(), JsonMapper.builder().build());
        ReflectionTestUtils.setField(filter, "enabled", true);

        for (int i = 0; i < 20; i++) {
            MockHttpServletResponse response = new MockHttpServletResponse();
            filter.doFilter(request("GET", "/api/v1/public/products"), response, new MockFilterChain());
            assertThat(response.getStatus()).isEqualTo(200);
        }
    }

    @Test
    void disabledRateLimitPassesEverything() throws Exception {
        RateLimitFilter filter = new RateLimitFilter(new InMemoryRateLimitStore(), JsonMapper.builder().build());
        ReflectionTestUtils.setField(filter, "enabled", false);

        for (int i = 0; i < 20; i++) {
            MockHttpServletResponse response = new MockHttpServletResponse();
            filter.doFilter(request("POST", "/api/v1/public/orders"), response, new MockFilterChain());
            assertThat(response.getStatus()).isEqualTo(200);
        }
    }

    @Test
    void forwardedHeaderSelectsTheClientIp() throws Exception {
        RateLimitFilter filter = new RateLimitFilter(new RateLimitStore() {
            String observed;

            @Override
            public boolean tryConsume(String key, int limit) {
                observed = key;
                return true;
            }
        }, JsonMapper.builder().build());
        ReflectionTestUtils.setField(filter, "enabled", true);

        MockHttpServletRequest request = request("POST", "/api/v1/public/orders");
        request.addHeader("Forwarded", "for=203.0.113.9;proto=https");
        filter.doFilter(request, new MockHttpServletResponse(), new MockFilterChain());

        String observed = (String) ReflectionTestUtils.getField(
                ReflectionTestUtils.getField(filter, "rateLimitStore"), "observed");
        assertThat(observed).startsWith("203.0.113.9:POST:/api/v1/public/orders");
    }

    private static MockHttpServletRequest request(String method, String path) {
        MockHttpServletRequest request = new MockHttpServletRequest(method, path);
        request.setRemoteAddr("127.0.0.1");
        return request;
    }
}
