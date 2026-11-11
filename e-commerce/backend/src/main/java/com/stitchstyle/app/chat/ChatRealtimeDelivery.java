package com.stitchstyle.app.chat;

import com.stitchstyle.app.model.entity.User;
import com.stitchstyle.app.repository.UserRepository;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ChatRealtimeDelivery {

    private final SimpMessagingTemplate messagingTemplate;
    private final UserRepository userRepository;

    public ChatRealtimeDelivery(SimpMessagingTemplate messagingTemplate, UserRepository userRepository) {
        this.messagingTemplate = messagingTemplate;
        this.userRepository = userRepository;
    }

    public void deliver(List<Long> recipientUserIds, ChatEvent event) {
        for (Long userId : recipientUserIds) {
            userRepository.findById(userId).map(User::getEmail).ifPresent(email ->
                    messagingTemplate.convertAndSendToUser(email, "/queue/chat", event));
        }
    }
}
