package com.stitchstyle.app.chat;

import com.stitchstyle.app.repository.UserRepository;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionConnectedEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

import java.security.Principal;

@Component
public class ChatPresenceListener {

    private final ChatPresenceRegistry presence;
    private final UserRepository userRepository;

    public ChatPresenceListener(ChatPresenceRegistry presence, UserRepository userRepository) {
        this.presence = presence;
        this.userRepository = userRepository;
    }

    @EventListener
    public void connected(SessionConnectedEvent event) {
        Principal principal = StompHeaderAccessor.wrap(event.getMessage()).getUser();
        resolve(principal).ifPresent(presence::online);
    }

    @EventListener
    public void disconnected(SessionDisconnectEvent event) {
        Principal principal = event.getUser();
        resolve(principal).ifPresent(presence::offline);
    }

    private java.util.Optional<Long> resolve(Principal principal) {
        if (principal == null || principal.getName() == null) return java.util.Optional.empty();
        return userRepository.findByEmailIgnoreCase(principal.getName()).map(user -> user.getId());
    }
}
