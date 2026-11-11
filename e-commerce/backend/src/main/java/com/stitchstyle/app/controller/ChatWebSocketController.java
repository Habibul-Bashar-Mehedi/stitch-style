package com.stitchstyle.app.controller;

import com.stitchstyle.app.repository.UserRepository;
import com.stitchstyle.app.service.ChatService;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.stereotype.Controller;

import java.security.Principal;

@Controller
public class ChatWebSocketController {

    private final ChatService chatService;
    private final UserRepository userRepository;

    public ChatWebSocketController(ChatService chatService, UserRepository userRepository) {
        this.chatService = chatService;
        this.userRepository = userRepository;
    }

    @MessageMapping("/chat/{conversationId}/typing")
    public void typing(@DestinationVariable Long conversationId, Principal principal) {
        if (principal == null) return;
        userRepository.findByEmailIgnoreCase(principal.getName())
                .ifPresent(user -> chatService.typing(user, conversationId));
    }
}
