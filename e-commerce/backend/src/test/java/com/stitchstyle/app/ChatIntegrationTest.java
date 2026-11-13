package com.stitchstyle.app;

import com.stitchstyle.app.model.entity.Role;
import com.stitchstyle.app.model.entity.User;
import com.stitchstyle.app.repository.ChatConversationRepository;
import com.stitchstyle.app.repository.ChatMessageRepository;
import com.stitchstyle.app.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ChatIntegrationTest extends AbstractIntegrationTest {

    @Autowired ChatConversationRepository conversationRepository;
    @Autowired ChatMessageRepository messageRepository;

    @Test
    void supportConversationRunsFromCustomerMessageToStaffClose() throws Exception {
        String tag = suffix();
        String customerEmail = "it-chat-customer-" + tag + "@example.com";
        String managerEmail = "it-chat-manager-" + tag + "@example.com";
        User customer = ensureUser(customerEmail, Role.CUSTOMER);
        User manager = ensureUser(managerEmail, Role.MANAGER);
        ensureUser("it-chat-admin-" + tag + "@example.com", Role.ADMIN);

        MvcResult created = mvc.perform(post("/api/v1/me/chat/conversations")
                        .with(user(customerEmail).roles("CUSTOMER")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"clientMessageId\":\"client-" + tag + "\",\"body\":\"I need help\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.type").value("SUPPORT"))
                .andExpect(jsonPath("$.status").value("OPEN"))
                .andExpect(jsonPath("$.lastMessagePreview").value("I need help"))
                .andReturn();
        long conversationId = id(created);

        mvc.perform(get("/api/v1/me/chat/conversations")
                        .with(user(customerEmail).roles("CUSTOMER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value((int) conversationId));

        MvcResult sent = mvc.perform(post("/api/v1/me/chat/conversations/" + conversationId + "/messages")
                        .with(user(customerEmail).roles("CUSTOMER")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"clientMessageId\":\"follow-up-" + tag + "\",\"body\":\"Any update?\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.body").value("Any update?"))
                .andReturn();
        long messageId = id(sent);

        mvc.perform(post("/api/v1/me/chat/conversations/" + conversationId + "/read")
                        .with(user(customerEmail).roles("CUSTOMER")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"lastReadMessageId\":" + messageId + "}"))
                .andExpect(status().isOk());

        mvc.perform(get("/api/v1/admin/chat/conversations")
                        .with(user("it-chat-admin-" + tag + "@example.com").roles("ADMIN"))
                        .param("type", "SUPPORT"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id==" + conversationId + ")]").exists());

        mvc.perform(get("/api/v1/admin/chat/conversations/" + conversationId + "/messages")
                        .with(user("it-chat-admin-" + tag + "@example.com").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(2));

        mvc.perform(post("/api/v1/admin/chat/conversations/" + conversationId + "/messages")
                        .with(user("it-chat-admin-" + tag + "@example.com").roles("ADMIN")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"clientMessageId\":\"staff-" + tag + "\",\"body\":\"We are on it\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.body").value("We are on it"));

        mvc.perform(patch("/api/v1/admin/chat/conversations/" + conversationId + "/assign")
                        .with(user("it-chat-admin-" + tag + "@example.com").roles("ADMIN")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"staffUserId\":" + manager.getId() + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.assignedStaff.id").value(manager.getId().intValue()));

        mvc.perform(post("/api/v1/admin/chat/conversations/" + conversationId + "/close")
                        .with(user("it-chat-admin-" + tag + "@example.com").roles("ADMIN")).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CLOSED"));

        mvc.perform(post("/api/v1/me/chat/conversations/" + conversationId + "/messages")
                        .with(user(customerEmail).roles("CUSTOMER")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"clientMessageId\":\"after-close-" + tag + "\",\"body\":\"Hello?\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_STATUS_TRANSITION"));

        assertThat(conversationRepository.findById(conversationId).orElseThrow().getAssignedStaff().getId())
                .isEqualTo(manager.getId());
        assertThat(messageRepository.findTop50ByConversationIdOrderByIdDesc(conversationId)).hasSize(3);
    }

    @Test
    void oneCustomerCannotReadAnotherCustomersConversation() throws Exception {
        String tag = suffix();
        String ownerEmail = "it-chat-owner-" + tag + "@example.com";
        String intruderEmail = "it-chat-intruder-" + tag + "@example.com";
        ensureUser(ownerEmail, Role.CUSTOMER);
        ensureUser(intruderEmail, Role.CUSTOMER);

        MvcResult created = mvc.perform(post("/api/v1/me/chat/conversations")
                        .with(user(ownerEmail).roles("CUSTOMER")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"clientMessageId\":\"owner-" + tag + "\",\"body\":\"Private\"}"))
                .andExpect(status().isOk()).andReturn();
        long conversationId = id(created);

        mvc.perform(get("/api/v1/me/chat/conversations/" + conversationId)
                        .with(user(intruderEmail).roles("CUSTOMER")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));

        mvc.perform(get("/api/v1/me/chat/conversations/" + conversationId + "/messages")
                        .with(user(intruderEmail).roles("CUSTOMER")))
                .andExpect(status().isForbidden());
    }

    @Test
    void internalConversationsConnectAdminsAndManagersOnly() throws Exception {
        String tag = suffix();
        String adminEmail = "it-chat-internal-admin-" + tag + "@example.com";
        String managerEmail = "it-chat-internal-manager-" + tag + "@example.com";
        ensureUser(adminEmail, Role.ADMIN);
        User manager = ensureUser(managerEmail, Role.MANAGER);

        MvcResult created = mvc.perform(post("/api/v1/admin/chat/internal")
                        .with(user(adminEmail).roles("ADMIN")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"recipientUserId\":" + manager.getId()
                                + ",\"clientMessageId\":\"dm-" + tag + "\",\"body\":\"Hello manager\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.type").value("INTERNAL"))
                .andReturn();
        long conversationId = id(created);

        mvc.perform(post("/api/v1/admin/chat/internal")
                        .with(user(adminEmail).roles("ADMIN")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"recipientUserId\":" + manager.getId()
                                + ",\"clientMessageId\":\"dm-2-" + tag + "\",\"body\":\"Second\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value((int) conversationId));

        mvc.perform(get("/api/v1/admin/chat/conversations")
                        .with(user(managerEmail).roles("MANAGER")).param("type", "INTERNAL"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id==" + conversationId + ")]").exists());

        mvc.perform(get("/api/v1/admin/chat/contacts")
                        .with(user(adminEmail).roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].fullName").value(org.hamcrest.Matchers.hasItem(manager.getFullName())));

        // Customers cannot use internal chat.
        String customerEmail = "it-chat-internal-customer-" + tag + "@example.com";
        ensureUser(customerEmail, Role.CUSTOMER);
        mvc.perform(post("/api/v1/admin/chat/internal")
                        .with(user(customerEmail).roles("CUSTOMER")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"recipientUserId\":" + manager.getId()
                                + ",\"clientMessageId\":\"nope-" + tag + "\",\"body\":\"Nope\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void supportConversationsAreReusedWhileOpenAndAllowOrderReferences() throws Exception {
        String tag = suffix();
        String customerEmail = "it-chat-reuse-" + tag + "@example.com";
        ensureUser(customerEmail, Role.CUSTOMER);

        MvcResult first = mvc.perform(post("/api/v1/me/chat/conversations")
                        .with(user(customerEmail).roles("CUSTOMER")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"clientMessageId\":\"one-" + tag + "\",\"body\":\"First\"}"))
                .andExpect(status().isOk()).andReturn();
        long conversationId = id(first);

        mvc.perform(post("/api/v1/me/chat/conversations")
                        .with(user(customerEmail).roles("CUSTOMER")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"clientMessageId\":\"two-" + tag + "\",\"body\":\"Second\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value((int) conversationId));

        // Duplicate client message ids are idempotent.
        mvc.perform(post("/api/v1/me/chat/conversations/" + conversationId + "/messages")
                        .with(user(customerEmail).roles("CUSTOMER")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"clientMessageId\":\"two-" + tag + "\",\"body\":\"Second\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.body").value("Second"));
    }

    private long id(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asLong();
    }
}
