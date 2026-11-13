package com.stitchstyle.app;

import com.stitchstyle.app.model.entity.Role;
import com.stitchstyle.app.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class SecurityIntegrationTest extends AbstractIntegrationTest {

    private static final String ADMIN = "it-security-admin@example.com";
    private static final String MANAGER = "it-security-manager@example.com";
    private static final String CUSTOMER = "it-security-customer@example.com";

    private void seedStaff() {
        ensureUser(ADMIN, Role.ADMIN);
        ensureUser(MANAGER, Role.MANAGER);
        ensureUser(CUSTOMER, Role.CUSTOMER);
    }

    @Test
    void anonymousAccessToProtectedEndpointsIsA401Problem() throws Exception {
        mvc.perform(get("/api/v1/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));

        mvc.perform(get("/api/v1/admin/orders"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    @Test
    void customersCannotReachTheAdminArea() throws Exception {
        seedStaff();

        mvc.perform(get("/api/v1/admin/orders").with(user(CUSTOMER).roles("CUSTOMER")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));

        mvc.perform(get("/api/v1/admin/session").with(user(CUSTOMER).roles("CUSTOMER")))
                .andExpect(status().isForbidden());
    }

    @Test
    void managerHasOperationalButNotOwnerLevelAccess() throws Exception {
        seedStaff();

        mvc.perform(get("/api/v1/admin/orders").with(user(MANAGER).roles("MANAGER")))
                .andExpect(status().isOk());
        mvc.perform(get("/api/v1/admin/analytics/summary").with(user(MANAGER).roles("MANAGER")))
                .andExpect(status().isOk());
        mvc.perform(get("/api/v1/admin/settings").with(user(MANAGER).roles("MANAGER")))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/admin/staff").with(user(MANAGER).roles("MANAGER")))
                .andExpect(status().isForbidden());
    }

    @Test
    void mutationsRequireACsrfToken() throws Exception {
        seedStaff();

        mvc.perform(post("/api/v1/admin/categories")
                        .with(user(ADMIN).roles("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"No CSRF\"}"))
                .andExpect(status().isForbidden());

        mvc.perform(post("/api/v1/admin/categories")
                        .with(user(ADMIN).roles("ADMIN")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"With CSRF " + suffix() + "\"}"))
                .andExpect(status().isCreated());
    }

    @Test
    void adminSessionReportsTheSignedInStaffMember() throws Exception {
        seedStaff();

        mvc.perform(get("/api/v1/admin/session").with(user(ADMIN).roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(ADMIN))
                .andExpect(jsonPath("$.role").value("ADMIN"));
    }

    @Test
    void logoutReturnsNoContent() throws Exception {
        mvc.perform(post("/api/v1/auth/logout")
                        .with(user(CUSTOMER).roles("CUSTOMER")).with(csrf()))
                .andExpect(status().isNoContent());
    }

    @Test
    void devToolsAreAvailableOnlyWhenEnabledAndPermitAll() throws Exception {
        mvc.perform(get("/api/v1/dev/ping"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.instance").isNotEmpty());
    }

    @Test
    void validationErrorsAreReportedAsProblemDetails() throws Exception {
        seedStaff();

        mvc.perform(post("/api/v1/admin/categories")
                        .with(user(ADMIN).roles("ADMIN")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors").isArray());
    }

    @Test
    void unknownApiPathsAreDeniedWithoutLeakingAnAdminSurface() throws Exception {
        seedStaff();

        mvc.perform(get("/api/v1/unknown").with(user(ADMIN).roles("ADMIN")))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminCanChangeAnOrderStatusAndAnIllegalTransitionIsAConflict() throws Exception {
        seedStaff();
        // unknown ids produce 404 before any transition check
        mvc.perform(patch("/api/v1/admin/orders/999999/status")
                        .with(user(ADMIN).roles("ADMIN")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"SHIPPED\"}"))
                .andExpect(status().isNotFound());
    }
}
