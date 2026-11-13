package com.stitchstyle.app.security;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;

class RestSecurityHandlersTest {

    @Test
    void unauthenticatedEntryPointWritesProblemDetail() throws Exception {
        RestAuthenticationEntryPoint point = new RestAuthenticationEntryPoint(JsonMapper.builder().build());
        MockHttpServletResponse response = new MockHttpServletResponse();

        point.commence(new MockHttpServletRequest("GET", "/api/v1/me"), response,
                new BadCredentialsException("no auth"));

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentType()).contains("application/problem+json");
        assertThat(response.getContentAsString()).contains("\"code\":\"UNAUTHENTICATED\"");
        assertThat(response.getContentAsString()).contains("\"instance\":\"/api/v1/me\"");
    }

    @Test
    void accessDeniedHandlerWritesProblemDetail() throws Exception {
        RestAccessDeniedHandler handler = new RestAccessDeniedHandler(JsonMapper.builder().build());
        MockHttpServletResponse response = new MockHttpServletResponse();

        handler.handle(new MockHttpServletRequest("GET", "/api/v1/admin/orders"), response,
                new AccessDeniedException("denied"));

        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(response.getContentType()).contains("application/problem+json");
        assertThat(response.getContentAsString()).contains("\"code\":\"FORBIDDEN\"");
    }
}
