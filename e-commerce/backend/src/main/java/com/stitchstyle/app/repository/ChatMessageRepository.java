package com.stitchstyle.app.repository;

import com.stitchstyle.app.model.entity.ChatMessage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {
    List<ChatMessage> findTop50ByConversationIdOrderByIdDesc(Long conversationId);
    List<ChatMessage> findTop50ByConversationIdAndIdLessThanOrderByIdDesc(Long conversationId, Long beforeId);
    Optional<ChatMessage> findByConversationIdAndSenderIdAndClientMessageId(Long conversationId, Long senderId, String clientMessageId);
    long countByConversationIdAndIdGreaterThanAndSenderIdNot(Long conversationId, Long id, Long senderId);

    @Modifying
    @Query("delete from ChatMessage m where m.createdAt < :cutoff")
    int deleteOlderThan(@Param("cutoff") Instant cutoff);
}
