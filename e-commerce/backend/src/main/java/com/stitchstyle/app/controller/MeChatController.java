package com.stitchstyle.app.controller;

import com.stitchstyle.app.dto.request.CreateSupportConversationRequest;
import com.stitchstyle.app.dto.request.MarkReadRequest;
import com.stitchstyle.app.dto.request.SendChatMessageRequest;
import com.stitchstyle.app.dto.response.ChatConversationResponse;
import com.stitchstyle.app.dto.response.ChatMessagePageResponse;
import com.stitchstyle.app.dto.response.ChatMessageResponse;
import com.stitchstyle.app.service.ChatService;
import com.stitchstyle.app.service.CurrentUserService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/me/chat")
public class MeChatController {

    private final ChatService chatService;
    private final CurrentUserService currentUserService;

    public MeChatController(ChatService chatService, CurrentUserService currentUserService) {
        this.chatService = chatService;
        this.currentUserService = currentUserService;
    }

    @GetMapping("/conversations")
    public List<ChatConversationResponse> conversations() {
        return chatService.customerConversations(currentUserService.require());
    }

    @PostMapping("/conversations")
    public ChatConversationResponse create(@Valid @RequestBody CreateSupportConversationRequest request) {
        return chatService.createOrGetSupport(currentUserService.require(), request);
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
        return chatService.sendCustomer(currentUserService.require(), id, request);
    }

    @PostMapping("/conversations/{id}/read")
    public void read(@PathVariable Long id, @Valid @RequestBody MarkReadRequest request) {
        chatService.markRead(currentUserService.require(), id, request);
    }
}
