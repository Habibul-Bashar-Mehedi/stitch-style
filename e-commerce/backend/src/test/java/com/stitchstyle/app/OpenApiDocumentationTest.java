package com.stitchstyle.app;

import com.stitchstyle.app.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import tools.jackson.databind.JsonNode;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Guards the hand-written parts of the OpenAPI document (business-area tags,
 * session/CSRF schemes, CSRF header parameter) and the rule that the docs are
 * only reachable with an ADMIN session.
 */
class OpenApiDocumentationTest extends AbstractIntegrationTest {

    private static RequestPostProcessor admin() {
        return user("admin").roles("ADMIN");
    }

    @Test
    void apiDocumentationIsAdminOnly() throws Exception {
        mvc.perform(get("/v3/api-docs"))
                .andExpect(status().isUnauthorized());

        mvc.perform(get("/v3/api-docs").with(user("manager").roles("MANAGER")))
                .andExpect(status().isForbidden());

        mvc.perform(get("/swagger-ui.html").with(admin()))
                .andExpect(status().is3xxRedirection());
    }

    @Test
    void publishesAreasAuthenticationAndCsrfContract() throws Exception {
        String body = mvc.perform(get("/v3/api-docs").with(admin()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        JsonNode doc = objectMapper.readTree(body);

        assertThat(doc.at("/info/title").asString()).isEqualTo("Stitch & Style API");
        assertThat(doc.at("/servers").isArray()).isTrue();
        assertThat(doc.at("/components/securitySchemes/sessionCookie/name").asString())
                .isEqualTo("STITCHSTYLE_SESSION");
        assertThat(doc.at("/components/securitySchemes/xsrfToken/name").asString())
                .isEqualTo("X-XSRF-TOKEN");

        JsonNode adminList = doc.at("/paths").path("/api/v1/admin/orders").path("get");
        assertThat(adminList.path("tags").get(0).asString()).isEqualTo("Admin · Orders");
        assertThat(adminList.path("security").get(0).has("sessionCookie")).isTrue();

        JsonNode publicList = doc.at("/paths").path("/api/v1/public/products").path("get");
        assertThat(publicList.path("tags").get(0).asString()).isEqualTo("Storefront · Products");
        assertThat(publicList.path("security").size()).isZero();

        JsonNode checkout = doc.at("/paths").path("/api/v1/public/orders").path("post");
        assertThat(parameterNames(checkout)).contains("X-XSRF-TOKEN");
        assertThat(checkout.path("tags").get(0).asString()).isEqualTo("Storefront · Orders");

        JsonNode callback = doc.at("/paths")
                .path("/api/v1/public/payments/{provider}/callback").path("post");
        assertThat(parameterNames(callback)).doesNotContain("X-XSRF-TOKEN");

        List<String> tagNames = new ArrayList<>();
        doc.path("tags").forEach(tag -> tagNames.add(tag.path("name").asString()));
        assertThat(tagNames)
                .contains("Storefront · Cart", "Customer · Chat", "Admin · Staff")
                .doesNotContain("me-controller", "admin-order-controller");
    }

    private static List<String> parameterNames(JsonNode operation) {
        List<String> names = new ArrayList<>();
        operation.path("parameters").forEach(parameter -> names.add(parameter.path("name").asString()));
        return names;
    }
}
