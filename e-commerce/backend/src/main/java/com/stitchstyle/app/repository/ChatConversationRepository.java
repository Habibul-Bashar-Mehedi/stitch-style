package com.stitchstyle.app.repository;

import com.stitchstyle.app.model.entity.ChatConversation;
import com.stitchstyle.app.model.entity.ChatConversationStatus;
import com.stitchstyle.app.model.entity.ChatConversationType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ChatConversationRepository extends JpaRepository<ChatConversation, Long> {
    Optional<ChatConversation> findFirstByTypeAndStatusAndCustomer_Id(
            ChatConversationType type, ChatConversationStatus status, Long customerId);

    Optional<ChatConversation> findByTypeAndDmKey(ChatConversationType type, String dmKey);

    List<ChatConversation> findByCustomer_IdOrderByLastMessageAtDesc(Long customerId);

    @Query("""
            select c from ChatConversation c
            where c.type = :type
              and (:status is null or c.status = :status)
              and (:assignedId is null or c.assignedStaff.id = :assignedId)
              and (:unassigned = false or c.assignedStaff is null)
              and (:q is null or lower(coalesce(c.customer.fullName, '')) like lower(concat('%', :q, '%'))
                   or lower(coalesce(c.lastMessagePreview, '')) like lower(concat('%', :q, '%')))
            order by c.lastMessageAt desc
            """)
    List<ChatConversation> staffQueue(@Param("type") ChatConversationType type,
                                      @Param("status") ChatConversationStatus status,
                                      @Param("assignedId") Long assignedId,
                                      @Param("unassigned") boolean unassigned,
                                      @Param("q") String q);

    @Query("""
            select p.conversation from ChatParticipant p
            where p.user.id = :userId and p.conversation.type = :type
            order by p.conversation.lastMessageAt desc
            """)
    List<ChatConversation> conversationsForParticipant(@Param("userId") Long userId,
                                                       @Param("type") ChatConversationType type);
}
