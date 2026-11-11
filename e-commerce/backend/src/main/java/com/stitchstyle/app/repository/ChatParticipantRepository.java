package com.stitchstyle.app.repository;

import com.stitchstyle.app.model.entity.ChatParticipant;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ChatParticipantRepository extends JpaRepository<ChatParticipant, Long> {
    Optional<ChatParticipant> findByConversationIdAndUserId(Long conversationId, Long userId);
    List<ChatParticipant> findByConversationId(Long conversationId);
    List<ChatParticipant> findByUserId(Long userId);
}
