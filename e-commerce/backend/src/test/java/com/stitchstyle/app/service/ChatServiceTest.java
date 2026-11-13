package com.stitchstyle.app.service;

import com.stitchstyle.app.chat.ChatUnreadNotifier;
import com.stitchstyle.app.dto.request.AssignConversationRequest;
import com.stitchstyle.app.dto.request.CreateInternalConversationRequest;
import com.stitchstyle.app.dto.request.CreateSupportConversationRequest;
import com.stitchstyle.app.dto.request.MarkReadRequest;
import com.stitchstyle.app.dto.request.SendChatMessageRequest;
import com.stitchstyle.app.dto.response.ChatConversationResponse;
import com.stitchstyle.app.exception.InvalidChatException;
import com.stitchstyle.app.exception.InvalidStatusTransitionException;
import com.stitchstyle.app.exception.RateLimitExceededException;
import com.stitchstyle.app.exception.ResourceNotFoundException;
import com.stitchstyle.app.model.entity.ChatConversation;
import com.stitchstyle.app.model.entity.ChatConversationStatus;
import com.stitchstyle.app.model.entity.ChatConversationType;
import com.stitchstyle.app.model.entity.ChatMessage;
import com.stitchstyle.app.model.entity.ChatParticipant;
import com.stitchstyle.app.model.entity.Role;
import com.stitchstyle.app.model.entity.User;
import com.stitchstyle.app.repository.ChatConversationRepository;
import com.stitchstyle.app.repository.ChatMessageRepository;
import com.stitchstyle.app.repository.ChatParticipantRepository;
import com.stitchstyle.app.repository.OrderRepository;
import com.stitchstyle.app.repository.UserRepository;
import com.stitchstyle.app.security.RateLimitStore;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.access.AccessDeniedException;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ChatServiceTest {

    private ChatConversationRepository conversationRepository;
    private ChatParticipantRepository participantRepository;
    private ChatMessageRepository messageRepository;
    private UserRepository userRepository;
    private RateLimitStore rateLimitStore;
    private ApplicationEventPublisher eventPublisher;
    private ChatUnreadNotifier unreadNotifier;
    private ChatService service;

    private final User customer = user(1L, "Nusrat", "nusrat@x.com", Role.CUSTOMER);
    private final User admin = user(2L, "Admin", "admin@x.com", Role.ADMIN);
    private final User manager = user(3L, "Manager", "manager@x.com", Role.MANAGER);

    @BeforeEach
    void setUp() {
        conversationRepository = mock(ChatConversationRepository.class);
        participantRepository = mock(ChatParticipantRepository.class);
        messageRepository = mock(ChatMessageRepository.class);
        userRepository = mock(UserRepository.class);
        rateLimitStore = mock(RateLimitStore.class);
        eventPublisher = mock(ApplicationEventPublisher.class);
        unreadNotifier = mock(ChatUnreadNotifier.class);
        service = new ChatService(conversationRepository, participantRepository, messageRepository,
                userRepository, mock(OrderRepository.class), mock(AuditService.class), rateLimitStore,
                eventPublisher, unreadNotifier, new SimpleMeterRegistry());
    }

    @Test
    void customerConversationRejectsRateLimitedCreation() {
        when(rateLimitStore.tryConsume("chat:new:1", 5)).thenReturn(false);

        assertThatThrownBy(() -> service.createOrGetSupport(customer,
                new CreateSupportConversationRequest(null, "client-1", "Hello")))
                .isInstanceOf(RateLimitExceededException.class);
    }

    @Test
    void creatingSupportThreadSendsTheFirstMessage() {
        when(rateLimitStore.tryConsume("chat:new:1", 5)).thenReturn(true);
        when(rateLimitStore.tryConsume("chat:send:1", 30)).thenReturn(true);
        ChatConversation conversation = conversation(10L, ChatConversationType.SUPPORT, customer);
        when(conversationRepository.findFirstByTypeAndStatusAndCustomer_Id(
                ChatConversationType.SUPPORT, ChatConversationStatus.OPEN, 1L)).thenReturn(Optional.empty());
        when(conversationRepository.save(any(ChatConversation.class))).thenAnswer(invocation -> {
            ChatConversation saved = invocation.getArgument(0);
            saved.setId(10L);
            return saved;
        });
        when(participantRepository.findByConversationIdAndUserId(any(), any())).thenReturn(Optional.empty());
        when(participantRepository.save(any(ChatParticipant.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(messageRepository.findByConversationIdAndSenderIdAndClientMessageId(any(), any(), any()))
                .thenReturn(Optional.empty());
        when(messageRepository.save(any(ChatMessage.class))).thenAnswer(invocation -> {
            ChatMessage message = invocation.getArgument(0);
            message.setId(20L);
            message.setCreatedAt(Instant.now());
            return message;
        });
        when(participantRepository.findByConversationId(10L)).thenReturn(List.of(
                ChatParticipant.builder().conversation(conversation).user(customer).build(),
                ChatParticipant.builder().conversation(conversation).user(admin).build()));
        when(messageRepository.countByConversationIdAndIdGreaterThanAndSenderIdNot(any(), anyLong(), anyLong()))
                .thenReturn(0L);

        ChatConversationResponse response = service.createOrGetSupport(customer,
                new CreateSupportConversationRequest(null, "client-1", "  Hello  "));

        assertThat(response.id()).isEqualTo(10L);
        assertThat(response.lastMessagePreview()).isEqualTo("Hello");
        verify(messageRepository).save(any(ChatMessage.class));
        verify(eventPublisher).publishEvent(any(ChatService.ChatMessageCreatedEvent.class));
        verify(unreadNotifier).notifyOffline(any(), any(ChatConversation.class), eq(customer), eq("Hello"));
    }

    @Test
    void supportAccessIsRestrictedToTheOwningCustomer() {
        ChatConversation conversation = conversation(10L, ChatConversationType.SUPPORT,
                user(99L, "Other", "other@x.com", Role.CUSTOMER));
        when(conversationRepository.findById(10L)).thenReturn(Optional.of(conversation));

        assertThatThrownBy(() -> service.sendCustomer(customer, 10L,
                new SendChatMessageRequest("client", "Hi")))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void closedConversationsCannotReceiveMessages() {
        ChatConversation conversation = conversation(10L, ChatConversationType.SUPPORT, customer);
        conversation.setStatus(ChatConversationStatus.CLOSED);
        when(conversationRepository.findById(10L)).thenReturn(Optional.of(conversation));

        assertThatThrownBy(() -> service.sendCustomer(customer, 10L,
                new SendChatMessageRequest("client", "Hi")))
                .isInstanceOf(InvalidStatusTransitionException.class)
                .hasMessageContaining("closed");
    }

    @Test
    void blankAndTooLongMessagesAreRejected() {
        ChatConversation conversation = conversation(10L, ChatConversationType.SUPPORT, customer);
        when(conversationRepository.findById(10L)).thenReturn(Optional.of(conversation));

        assertThatThrownBy(() -> service.sendCustomer(customer, 10L,
                new SendChatMessageRequest("client", "   ")))
                .isInstanceOf(InvalidChatException.class);
        assertThatThrownBy(() -> service.sendCustomer(customer, 10L,
                new SendChatMessageRequest("client", "x".repeat(4001))))
                .isInstanceOf(InvalidChatException.class);
    }

    @Test
    void repeatedClientMessageIdReturnsTheStoredMessageWithoutSendingAgain() {
        ChatConversation conversation = conversation(10L, ChatConversationType.SUPPORT, customer);
        ChatMessage existing = ChatMessage.builder().id(21L).conversation(conversation).sender(customer)
                .clientMessageId("client-1").body("Hello").createdAt(Instant.now()).build();
        when(conversationRepository.findById(10L)).thenReturn(Optional.of(conversation));
        when(messageRepository.findByConversationIdAndSenderIdAndClientMessageId(10L, 1L, "client-1"))
                .thenReturn(Optional.of(existing));
        when(participantRepository.findByConversationId(10L)).thenReturn(List.of());

        var response = service.sendCustomer(customer, 10L, new SendChatMessageRequest("client-1", "Hello"));

        assertThat(response.id()).isEqualTo(21L);
        verify(messageRepository, never()).save(any());
        verify(rateLimitStore, never()).tryConsume(any(), eq(30));
    }

    @Test
    void sendIsRateLimited() {
        ChatConversation conversation = conversation(10L, ChatConversationType.SUPPORT, customer);
        when(conversationRepository.findById(10L)).thenReturn(Optional.of(conversation));
        when(messageRepository.findByConversationIdAndSenderIdAndClientMessageId(any(), any(), any()))
                .thenReturn(Optional.empty());
        when(rateLimitStore.tryConsume("chat:send:1", 30)).thenReturn(false);

        assertThatThrownBy(() -> service.sendCustomer(customer, 10L,
                new SendChatMessageRequest("client-2", "Hello")))
                .isInstanceOf(RateLimitExceededException.class);
    }

    @Test
    void markReadAdvancesTheCursorAndPublishesAnEvent() {
        ChatConversation conversation = conversation(10L, ChatConversationType.SUPPORT, customer);
        ChatMessage message = ChatMessage.builder().id(30L).conversation(conversation).sender(admin)
                .clientMessageId("admin-1").body("Hi").createdAt(Instant.now()).build();
        ChatParticipant participant = ChatParticipant.builder().id(1L).conversation(conversation)
                .user(customer).lastReadMessageId(10L).build();
        when(conversationRepository.findById(10L)).thenReturn(Optional.of(conversation));
        when(messageRepository.findById(30L)).thenReturn(Optional.of(message));
        when(participantRepository.findByConversationIdAndUserId(10L, 1L)).thenReturn(Optional.of(participant));
        when(participantRepository.findByConversationId(10L)).thenReturn(List.of(participant,
                ChatParticipant.builder().conversation(conversation).user(admin).build()));

        service.markRead(customer, 10L, new MarkReadRequest(30L));

        assertThat(participant.getLastReadMessageId()).isEqualTo(30L);
        verify(participantRepository).save(participant);
        ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
        verify(eventPublisher).publishEvent(captor.capture());
        assertThat(captor.getValue()).isInstanceOf(ChatService.ChatReadEvent.class);
    }

    @Test
    void markReadRejectsMessagesFromAnotherConversation() {
        ChatConversation conversation = conversation(10L, ChatConversationType.SUPPORT, customer);
        ChatConversation other = conversation(11L, ChatConversationType.SUPPORT, customer);
        ChatMessage message = ChatMessage.builder().id(30L).conversation(other).sender(admin).build();
        when(conversationRepository.findById(10L)).thenReturn(Optional.of(conversation));
        when(messageRepository.findById(30L)).thenReturn(Optional.of(message));

        assertThatThrownBy(() -> service.markRead(customer, 10L, new MarkReadRequest(30L)))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void onlyAdminsCanAssignSupportThreadsToSomeoneElse() {
        ChatConversation conversation = conversation(10L, ChatConversationType.SUPPORT, customer);
        when(conversationRepository.findById(10L)).thenReturn(Optional.of(conversation));
        User otherManager = user(4L, "Other Manager", "other@x.com", Role.MANAGER);
        when(userRepository.findById(4L)).thenReturn(Optional.of(otherManager));
        when(userRepository.findById(3L)).thenReturn(Optional.of(manager));

        assertThatThrownBy(() -> service.assign(manager, 10L, new AssignConversationRequest(4L)))
                .isInstanceOf(AccessDeniedException.class);

        var response = service.assign(admin, 10L, new AssignConversationRequest(3L));
        assertThat(response.assignedStaff().id()).isEqualTo(3L);
        assertThat(conversation.getAssignedStaff()).isSameAs(manager);
    }

    @Test
    void assigningValidatesTheAssigneeAndConversationType() {
        ChatConversation internal = conversation(10L, ChatConversationType.INTERNAL, null);
        when(conversationRepository.findById(10L)).thenReturn(Optional.of(internal));
        assertThatThrownBy(() -> service.assign(admin, 10L, new AssignConversationRequest(3L)))
                .isInstanceOf(InvalidChatException.class);

        ChatConversation support = conversation(11L, ChatConversationType.SUPPORT, customer);
        when(conversationRepository.findById(11L)).thenReturn(Optional.of(support));
        when(userRepository.findById(3L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.assign(admin, 11L, new AssignConversationRequest(3L)))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void closingSupportThreadsWorksButInternalCannotBeClosed() {
        ChatConversation support = conversation(10L, ChatConversationType.SUPPORT, customer);
        when(conversationRepository.findById(10L)).thenReturn(Optional.of(support));

        var response = service.close(admin, 10L);

        assertThat(response.status()).isEqualTo(ChatConversationStatus.CLOSED);
        assertThat(support.getClosedAt()).isNotNull();

        ChatConversation internal = conversation(11L, ChatConversationType.INTERNAL, null);
        when(conversationRepository.findById(11L)).thenReturn(Optional.of(internal));
        assertThatThrownBy(() -> service.close(admin, 11L)).isInstanceOf(InvalidChatException.class);
    }

    @Test
    void contactsReturnTheComplementaryStaffRole() {
        when(userRepository.findByRoleAndActiveTrue(Role.MANAGER)).thenReturn(List.of(manager));

        assertThat(service.contacts(admin)).extracting(contact -> contact.fullName())
                .containsExactly("Manager");

        when(userRepository.findByRoleAndActiveTrue(Role.ADMIN)).thenReturn(List.of(admin));
        assertThat(service.contacts(manager)).extracting(contact -> contact.fullName())
                .containsExactly("Admin");
    }

    @Test
    void internalConversationRejectsSelfMessagesAndInvalidPairs() {
        assertThatThrownBy(() -> service.createOrGetInternal(admin,
                new CreateInternalConversationRequest(2L, "client", "Hi")))
                .isInstanceOf(InvalidChatException.class)
                .hasMessageContaining("another staff member");

        User otherCustomer = user(4L, "C", "c@x.com", Role.CUSTOMER);
        when(userRepository.findById(4L)).thenReturn(Optional.of(otherCustomer));
        assertThatThrownBy(() -> service.createOrGetInternal(admin,
                new CreateInternalConversationRequest(4L, "client", "Hi")))
                .isInstanceOf(InvalidChatException.class)
                .hasMessageContaining("admin and a manager");
    }

    @Test
    void internalConversationIsReusedByDmKey() {
        ChatConversation existing = conversation(12L, ChatConversationType.INTERNAL, null);
        existing.setDmKey("2:3");
        when(userRepository.findById(3L)).thenReturn(Optional.of(manager));
        when(conversationRepository.findByTypeAndDmKey(ChatConversationType.INTERNAL, "2:3"))
                .thenReturn(Optional.of(existing));
        when(participantRepository.findByConversationIdAndUserId(any(), any())).thenReturn(Optional.empty());
        when(participantRepository.save(any(ChatParticipant.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(rateLimitStore.tryConsume("chat:send:2", 30)).thenReturn(true);
        when(messageRepository.findByConversationIdAndSenderIdAndClientMessageId(any(), any(), any()))
                .thenReturn(Optional.empty());
        when(messageRepository.save(any(ChatMessage.class))).thenAnswer(invocation -> {
            ChatMessage message = invocation.getArgument(0);
            message.setId(40L);
            message.setCreatedAt(Instant.now());
            return message;
        });
        when(participantRepository.findByConversationId(12L)).thenReturn(List.of());
        when(messageRepository.countByConversationIdAndIdGreaterThanAndSenderIdNot(any(), anyLong(), anyLong()))
                .thenReturn(0L);

        var response = service.createOrGetInternal(admin,
                new CreateInternalConversationRequest(3L, "client", "Hi"));

        assertThat(response.id()).isEqualTo(12L);
        verify(conversationRepository, never()).findFirstByTypeAndStatusAndCustomer_Id(any(), any(), any());
    }

    @Test
    void messagesListIsPagedAndAscending() {
        ChatConversation conversation = conversation(10L, ChatConversationType.SUPPORT, customer);
        ChatMessage older = ChatMessage.builder().id(1L).conversation(conversation).sender(customer)
                .body("one").createdAt(Instant.now()).build();
        ChatMessage newer = ChatMessage.builder().id(2L).conversation(conversation).sender(customer)
                .body("two").createdAt(Instant.now()).build();
        when(conversationRepository.findById(10L)).thenReturn(Optional.of(conversation));
        when(messageRepository.findTop50ByConversationIdOrderByIdDesc(10L)).thenReturn(List.of(newer, older));
        when(participantRepository.findByConversationId(10L)).thenReturn(List.of());

        var page = service.messages(customer, 10L, null);

        assertThat(page.items()).extracting(item -> item.body()).containsExactly("one", "two");
        assertThat(page.nextBeforeId()).isEqualTo(1L);
        assertThat(page.hasMore()).isFalse();
    }

    @Test
    void typingPublishesToTheOtherParticipants() {
        ChatConversation conversation = conversation(10L, ChatConversationType.SUPPORT, customer);
        when(conversationRepository.findById(10L)).thenReturn(Optional.of(conversation));
        when(participantRepository.findByConversationId(10L)).thenReturn(List.of(
                ChatParticipant.builder().conversation(conversation).user(customer).build(),
                ChatParticipant.builder().conversation(conversation).user(admin).build()));

        service.typing(customer, 10L);

        verify(eventPublisher).publishEvent(any(ChatService.ChatTypingEvent.class));
    }

    @Test
    void staffListSupportsInternalSearch() {
        ChatConversation conversation = conversation(12L, ChatConversationType.INTERNAL, null);
        conversation.setId(12L);
        when(conversationRepository.conversationsForParticipant(2L, ChatConversationType.INTERNAL))
                .thenReturn(List.of(conversation));
        when(participantRepository.findByConversationId(12L)).thenReturn(List.of(
                ChatParticipant.builder().conversation(conversation).user(admin).build(),
                ChatParticipant.builder().conversation(conversation).user(manager).build()));
        when(messageRepository.countByConversationIdAndIdGreaterThanAndSenderIdNot(any(), anyLong(), anyLong()))
                .thenReturn(0L);

        assertThat(service.staffConversations(admin, "internal", null, null, "man")).hasSize(1);
        assertThat(service.staffConversations(admin, "internal", null, null, "zzz")).isEmpty();
    }

    private static ChatConversation conversation(Long id, ChatConversationType type, User customer) {
        return ChatConversation.builder().id(id).type(type).customer(customer)
                .status(ChatConversationStatus.OPEN).lastMessageAt(Instant.now()).build();
    }

    private static User user(Long id, String name, String email, Role role) {
        return User.builder().id(id).fullName(name).email(email).role(role).active(true).build();
    }
}
