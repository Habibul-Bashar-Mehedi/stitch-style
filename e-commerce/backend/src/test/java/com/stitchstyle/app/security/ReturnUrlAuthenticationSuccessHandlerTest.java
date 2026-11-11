package com.stitchstyle.app.security;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ReturnUrlAuthenticationSuccessHandlerTest {

    private final ReturnUrlAuthenticationSuccessHandler handler = new ReturnUrlAuthenticationSuccessHandler();

    @Test
    void adminLandingOnTheStorefrontIsSentToTheAdminPanel() throws Exception {
        assertThat(redirect("/", "ROLE_ADMIN")).isEqualTo("/admin");
        assertThat(redirect(null, "ROLE_ADMIN")).isEqualTo("/admin");
    }

    @Test
    void managerLandingOnTheStorefrontIsSentToTheAdminPanel() throws Exception {
        assertThat(redirect("/", "ROLE_MANAGER")).isEqualTo("/admin");
    }

    @Test
    void explicitDeepLinksArePreservedForStaff() throws Exception {
        assertThat(redirect("/cart", "ROLE_ADMIN")).isEqualTo("/cart");
        assertThat(redirect("/products/panjabi", "ROLE_MANAGER")).isEqualTo("/products/panjabi");
    }

    @Test
    void customersStayOnTheStorefront() throws Exception {
        assertThat(redirect("/", "ROLE_CUSTOMER")).isEqualTo("/");
        assertThat(redirect(null, "ROLE_CUSTOMER")).isEqualTo("/");
    }

    private String redirect(String returnUrl, String role) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        if (returnUrl != null) {
            request.getSession(true).setAttribute(ReturnUrlCaptureFilter.SESSION_KEY, returnUrl);
        }
        MockHttpServletResponse response = new MockHttpServletResponse();
        handler.onAuthenticationSuccess(request, response, authentication(role));
        return response.getRedirectedUrl();
    }

    private static Authentication authentication(String role) {
        return UsernamePasswordAuthenticationToken.authenticated("user", null,
                List.of(new SimpleGrantedAuthority(role)));
    }
}
