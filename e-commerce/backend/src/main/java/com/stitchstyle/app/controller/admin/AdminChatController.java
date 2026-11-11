package com.stitchstyle.app.controller.admin;

import com.stitchstyle.app.dto.request.AssignConversationRequest;
import com.stitchstyle.app.dto.request.CreateInternalConversationRequest;
import com.stitchstyle.app.dto.request.MarkReadRequest;
import com.stitchstyle.app.dto.request.SendChatMessageRequest;
import com.stitchstyle.app.dto.response.ChatContactResponse;
import com.stitchstyle.app.dto.response.ChatConversationResponse;
import com.stitchstyle.app.dto.response.ChatMessagePageResponse;
import com.stitchstyle.app.dto.response.ChatMessageResponse;
import com.stitchstyle.app.service.ChatService;
import com.stitchstyle.app.service.CurrentUserService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/admin/chat")
@PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
public class AdminChatController {

    private final ChatService chatService;
    private final CurrentUserService currentUserService;

    public AdminChatController(ChatService chatService, CurrentUserService currentUserService) {
        this.chatService = chatService;
        this.currentUserService = currentUserService;
    }

    @GetMapping("/conversations")
    public List<ChatConversationResponse> conversations(@RequestParam(required = false) String type,
                                                        @RequestParam(required = false) String status,
                                                        @RequestParam(required = false) String assigned,
                                                        @RequestParam(required = false) String q) {
        return chatService.staffConversations(currentUserService.require(), type, status, assigned, q);
    }

    @GetMapping("/conversations/{id}")
    public ChatConversationResponse conversation(@PathVariable Long id) {
        return chatService.conversation(currentUserService.require(), id);
    }

    @GetMapping("/conversations/{id}/messages")
    public ChatMessagePageResponse messages(@PathVariable Long id,
                                            @RequestParam(required = false) Long beforeId) {
        return chatService.messages(currentUserService.require(), id, beforeId);
    }

    @PostMapping("/conversations/{id}/messages")
    public ChatMessageResponse send(@PathVariable Long id, @Valid @RequestBody SendChatMessageRequest request) {
        return chatService.sendStaff(currentUserService.require(), id, request);
    }

    @PostMapping("/conversations/{id}/read")
    public void read(@PathVariable Long id, @Valid @RequestBody MarkReadRequest request) {
        chatService.markRead(currentUserService.require(), id, request);
    }

    @PatchMapping("/conversations/{id}/assign")
    public ChatConversationResponse assign(@PathVariable Long id,
                                           @Valid @RequestBody AssignConversationRequest request) {
        return chatService.assign(currentUserService.require(), id, request);
    }

    @PostMapping("/conversations/{id}/close")
    public ChatConversationResponse close(@PathVariable Long id) {
        return chatService.close(currentUserService.require(), id);
    }

    @GetMapping("/contacts")
    public List<ChatContactResponse> contacts() {
        return chatService.contacts(currentUserService.require());
    }

    @PostMapping("/internal")
    public ChatConversationResponse internal(@Valid @RequestBody CreateInternalConversationRequest request) {
        return chatService.createOrGetInternal(currentUserService.require(), request);
    }
}
