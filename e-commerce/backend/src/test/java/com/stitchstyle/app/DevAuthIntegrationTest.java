package com.stitchstyle.app;

import com.stitchstyle.app.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import jakarta.servlet.http.Cookie;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;


import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The E2E suite signs in through /api/v1/dev/session because Google OIDC cannot
 * be automated. It must create a real session that the normal authorization
 * rules accept, otherwise the Playwright tests would be testing nothing.
 */
class DevAuthIntegrationTest extends AbstractIntegrationTest {

    @Test
    void adminSessionCanCallAdminApis() throws Exception {
        MvcResult login = mvc.perform(post("/api/v1/dev/session")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"e2e-admin@stitchandstyle.test","role":"ADMIN","fullName":"E2E Admin"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("ADMIN"))
                .andReturn();

        Cookie session = sessionCookie(login);

        mvc.perform(get("/api/v1/admin/session").cookie(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("e2e-admin@stitchandstyle.test"))
                .andExpect(jsonPath("$.role").value("ADMIN"));

        mvc.perform(get("/api/v1/me").cookie(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("ADMIN"));
    }

    @Test
    void customerSessionIsDeniedBackOfficeAccess() throws Exception {
        MvcResult login = mvc.perform(post("/api/v1/dev/session")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"e2e-customer@stitchandstyle.test","role":"CUSTOMER"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("CUSTOMER"))
                .andReturn();

        Cookie session = sessionCookie(login);

        mvc.perform(get("/api/v1/me").cookie(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("CUSTOMER"));

        mvc.perform(get("/api/v1/admin/session").cookie(session))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    private static Cookie sessionCookie(MvcResult login) {
        Cookie cookie = login.getResponse().getCookie("STITCHSTYLE_SESSION");
        assertThat(cookie).isNotNull();
        return cookie;
    }
}
