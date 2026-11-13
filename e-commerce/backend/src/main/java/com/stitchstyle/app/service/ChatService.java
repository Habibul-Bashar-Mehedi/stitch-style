package com.stitchstyle.app.service;

import com.stitchstyle.app.dto.request.AssignConversationRequest;
import com.stitchstyle.app.dto.request.CreateInternalConversationRequest;
import com.stitchstyle.app.dto.request.CreateSupportConversationRequest;
import com.stitchstyle.app.dto.request.MarkReadRequest;
import com.stitchstyle.app.dto.request.SendChatMessageRequest;
import com.stitchstyle.app.dto.response.ChatContactResponse;
import com.stitchstyle.app.dto.response.ChatConversationResponse;
import com.stitchstyle.app.dto.response.ChatMessagePageResponse;
import com.stitchstyle.app.dto.response.ChatMessageResponse;
import com.stitchstyle.app.dto.response.ChatUserResponse;
import com.stitchstyle.app.exception.InvalidChatException;
import com.stitchstyle.app.exception.InvalidStatusTransitionException;
import com.stitchstyle.app.exception.RateLimitExceededException;
import com.stitchstyle.app.exception.ResourceNotFoundException;
import com.stitchstyle.app.model.entity.*;
import com.stitchstyle.app.chat.ChatUnreadNotifier;
import com.stitchstyle.app.repository.*;
import com.stitchstyle.app.security.RateLimitStore;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;

@Service
public class ChatService {

    private static final int MAX_BODY = 4000;
    private static final int PREVIEW_LENGTH = 160;
    private static final int SEND_PER_MINUTE = 30;
    private static final int NEW_CONVERSATION_PER_MINUTE = 5;

    private final ChatConversationRepository conversationRepository;
    private final ChatParticipantRepository participantRepository;
    private final ChatMessageRepository messageRepository;
    private final UserRepository userRepository;
    private final OrderRepository orderRepository;
    private final AuditService auditService;
    private final RateLimitStore rateLimitStore;
    private final ApplicationEventPublisher eventPublisher;
    private final ChatUnreadNotifier unreadNotifier;
    private final Counter messagesSent;

    public ChatService(ChatConversationRepository conversationRepository,
                       ChatParticipantRepository participantRepository,
                       ChatMessageRepository messageRepository,
                       UserRepository userRepository,
                       OrderRepository orderRepository,
                       AuditService auditService,
                       RateLimitStore rateLimitStore,
                       ApplicationEventPublisher eventPublisher,
                       ChatUnreadNotifier unreadNotifier,
                       MeterRegistry meterRegistry) {
        this.conversationRepository = conversationRepository;
        this.participantRepository = participantRepository;
        this.messageRepository = messageRepository;
        this.userRepository = userRepository;
        this.orderRepository = orderRepository;
        this.auditService = auditService;
        this.rateLimitStore = rateLimitStore;
        this.eventPublisher = eventPublisher;
        this.unreadNotifier = unreadNotifier;
        this.messagesSent = Counter.builder("chat.messages.sent").register(meterRegistry);
    }

    @Transactional
    public ChatConversationResponse createOrGetSupport(User customer, CreateSupportConversationRequest request) {
        requireCustomer(customer);
        if (!rateLimitStore.tryConsume("chat:new:" + customer.getId(), NEW_CONVERSATION_PER_MINUTE)) {
            throw new RateLimitExceededException("Too many new conversations. Try again shortly.");
        }
        ChatConversation conversation = conversationRepository
                .findFirstByTypeAndStatusAndCustomer_Id(ChatConversationType.SUPPORT,
                        ChatConversationStatus.OPEN, customer.getId())
                .orElseGet(() -> {
                    ChatConversation created = ChatConversation.builder()
                            .type(ChatConversationType.SUPPORT)
                            .status(ChatConversationStatus.OPEN)
                            .customer(customer)
                            .build();
                    if (request.orderNumber() != null && !request.orderNumber().isBlank()) {
                        Order order = orderRepository.findByOrderNumber(request.orderNumber())
                                .filter(o -> o.getUser() != null && o.getUser().getId().equals(customer.getId()))
                                .orElseThrow(() -> new ResourceNotFoundException("Order not found."));
                        created.setOrder(order);
                    }
                    conversationRepository.save(created);
                    ensureParticipant(created, customer);
                    return created;
                });
        doSend(conversation, customer, new SendChatMessageRequest(request.clientMessageId(), request.body()));
        return summary(conversation, customer);
    }

    @Transactional(readOnly = true)
    public List<ChatConversationResponse> customerConversations(User customer) {
        requireCustomer(customer);
        return conversationRepository.findByCustomer_IdOrderByLastMessageAtDesc(customer.getId()).stream()
                .map(conversation -> summary(conversation, customer))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<ChatConversationResponse> staffConversations(User staff, String type, String status,
                                                             String assigned, String query) {
        requireStaff(staff);
        ChatConversationType conversationType = "INTERNAL".equalsIgnoreCase(type)
                ? ChatConversationType.INTERNAL : ChatConversationType.SUPPORT;
        ChatConversationStatus conversationStatus = status == null || status.isBlank()
                ? null : ChatConversationStatus.valueOf(status.toUpperCase());
        List<ChatConversation> conversations;
        if (conversationType == ChatConversationType.INTERNAL) {
            conversations = conversationRepository.conversationsForParticipant(staff.getId(), ChatConversationType.INTERNAL);
            if (query != null && !query.isBlank()) {
                String needle = query.toLowerCase();
                conversations = conversations.stream()
                        .filter(c -> counterpart(c, staff) != null
                                && counterpart(c, staff).fullName().toLowerCase().contains(needle))
                        .toList();
            }
        } else {
            boolean unassigned = "unassigned".equalsIgnoreCase(assigned);
            Long assignedId = "me".equalsIgnoreCase(assigned) ? staff.getId() : null;
            conversations = conversationRepository.staffQueue(ChatConversationType.SUPPORT, conversationStatus,
                    assignedId, unassigned, query == null || query.isBlank() ? "" : query.trim());
        }
        return conversations.stream().map(conversation -> summary(conversation, staff)).toList();
    }

    @Transactional(readOnly = true)
    public ChatConversationResponse conversation(User user, Long conversationId) {
        ChatConversation conversation = requireConversation(conversationId);
        requireAccess(conversation, user);
        return summary(conversation, user);
    }

    @Transactional(readOnly = true)
    public ChatMessagePageResponse messages(User user, Long conversationId, Long beforeId) {
        ChatConversation conversation = requireConversation(conversationId);
        requireAccess(conversation, user);
        List<ChatMessage> rows = beforeId == null
                ? messageRepository.findTop50ByConversationIdOrderByIdDesc(conversationId)
                : messageRepository.findTop50ByConversationIdAndIdLessThanOrderByIdDesc(conversationId, beforeId);
        boolean hasMore = rows.size() == 50;
        List<ChatMessage> ascending = new ArrayList<>(rows);
        Collections.reverse(ascending);
        List<ChatParticipant> participants = participantRepository.findByConversationId(conversationId);
        List<ChatMessageResponse> items = ascending.stream()
                .map(message -> toMessage(message, user, participants))
                .toList();
        Long nextBeforeId = ascending.isEmpty() ? null : ascending.get(0).getId();
        return new ChatMessagePageResponse(items, nextBeforeId, hasMore);
    }

    @Transactional
    public ChatMessageResponse sendCustomer(User customer, Long conversationId, SendChatMessageRequest request) {
        ChatConversation conversation = requireConversation(conversationId);
        if (conversation.getType() != ChatConversationType.SUPPORT
                || conversation.getCustomer() == null
                || !conversation.getCustomer().getId().equals(customer.getId())) {
            throw new AccessDeniedException("This conversation does not belong to you.");
        }
        return doSend(conversation, customer, request);
    }

    @Transactional
    public ChatMessageResponse sendStaff(User staff, Long conversationId, SendChatMessageRequest request) {
        ChatConversation conversation = requireConversation(conversationId);
        requireStaffAccess(conversation, staff);
        return doSend(conversation, staff, request);
    }

    @Transactional
    public void markRead(User user, Long conversationId, MarkReadRequest request) {
        ChatConversation conversation = requireConversation(conversationId);
        requireAccess(conversation, user);
        ChatMessage message = messageRepository.findById(request.lastReadMessageId())
                .filter(m -> m.getConversation().getId().equals(conversationId))
                .orElseThrow(() -> new ResourceNotFoundException("Message not found."));
        ChatParticipant participant = ensureParticipant(conversation, user);
        if (participant.getLastReadMessageId() == null || participant.getLastReadMessageId() < message.getId()) {
            participant.setLastReadMessageId(message.getId());
            participantRepository.save(participant);
            List<Long> recipients = participantRepository.findByConversationId(conversation.getId()).stream()
                    .map(other -> other.getUser().getId())
                    .filter(id -> !id.equals(user.getId()))
                    .toList();
            if (!recipients.isEmpty()) {
                eventPublisher.publishEvent(new ChatReadEvent(conversationId, user.getId(), message.getId(), recipients));
            }
        }
    }

    @Transactional
    public ChatConversationResponse assign(User actor, Long conversationId, AssignConversationRequest request) {
        requireStaff(actor);
        ChatConversation conversation = requireConversation(conversationId);
        if (conversation.getType() != ChatConversationType.SUPPORT) {
            throw new InvalidChatException("CHAT_NOT_SUPPORT", "Only support threads can be assigned.");
        }
        User assignee = userRepository.findById(request.staffUserId())
                .filter(User::isActive)
                .filter(u -> u.getRole() == Role.ADMIN || u.getRole() == Role.MANAGER)
                .orElseThrow(() -> new ResourceNotFoundException("Staff member not found."));
        if (actor.getRole() != Role.ADMIN && !actor.getId().equals(assignee.getId())) {
            throw new AccessDeniedException("Only an admin can assign this thread to another person.");
        }
        conversation.setAssignedStaff(assignee);
        conversationRepository.save(conversation);
        ensureParticipant(conversation, assignee);
        auditService.record(AuditAction.UPDATE, "CHAT", conversationId,
                Map.of("assignedStaffId", "none"),
                Map.of("assignedStaffId", assignee.getId()));
        publishToParticipants(conversation, new ChatConversationChangedEvent(conversationId, List.of(), "ASSIGNED"));
        return summary(conversation, actor);
    }

    @Transactional
    public ChatConversationResponse close(User actor, Long conversationId) {
        requireStaff(actor);
        ChatConversation conversation = requireConversation(conversationId);
        if (conversation.getType() != ChatConversationType.SUPPORT) {
            throw new InvalidChatException("CHAT_NOT_SUPPORT", "Internal messages cannot be closed.");
        }
        conversation.setStatus(ChatConversationStatus.CLOSED);
        conversation.setClosedAt(Instant.now());
        conversationRepository.save(conversation);
        auditService.record(AuditAction.STATUS_CHANGE, "CHAT", conversationId,
                Map.of("status", "OPEN"), Map.of("status", "CLOSED"));
        publishToParticipants(conversation, new ChatConversationChangedEvent(conversationId, List.of(), "CLOSED"));
        return summary(conversation, actor);
    }

    @Transactional(readOnly = true)
    public List<ChatContactResponse> contacts(User staff) {
        requireStaff(staff);
        Role target = staff.getRole() == Role.ADMIN ? Role.MANAGER : Role.ADMIN;
        return userRepository.findByRoleAndActiveTrue(target).stream()
                .filter(user -> !user.getId().equals(staff.getId()))
                .map(user -> new ChatContactResponse(user.getId(), user.getFullName(), user.getRole(), user.getPictureUrl()))
                .toList();
    }

    @Transactional
    public ChatConversationResponse createOrGetInternal(User actor, CreateInternalConversationRequest request) {
        requireStaff(actor);
        if (actor.getId().equals(request.recipientUserId())) {
            throw new InvalidChatException("CHAT_SELF_MESSAGE", "Choose another staff member.");
        }
        User recipient = userRepository.findById(request.recipientUserId())
                .filter(User::isActive)
                .orElseThrow(() -> new ResourceNotFoundException("Staff member not found."));
        boolean validPair = (actor.getRole() == Role.ADMIN && recipient.getRole() == Role.MANAGER)
                || (actor.getRole() == Role.MANAGER && recipient.getRole() == Role.ADMIN);
        if (!validPair) {
            throw new InvalidChatException("CHAT_INVALID_RECIPIENT", "Only an admin and a manager can message each other.");
        }
        String dmKey = Math.min(actor.getId(), recipient.getId()) + ":" + Math.max(actor.getId(), recipient.getId());
        ChatConversation conversation = conversationRepository.findByTypeAndDmKey(ChatConversationType.INTERNAL, dmKey)
                .orElseGet(() -> {
                    ChatConversation created = ChatConversation.builder()
                            .type(ChatConversationType.INTERNAL)
                            .status(ChatConversationStatus.OPEN)
                            .dmKey(dmKey)
                            .build();
                    conversationRepository.save(created);
                    ensureParticipant(created, actor);
                    ensureParticipant(created, recipient);
                    return created;
                });
        ensureParticipant(conversation, actor);
        ensureParticipant(conversation, recipient);
        doSend(conversation, actor, new SendChatMessageRequest(request.clientMessageId(), request.body()));
        return summary(conversation, actor);
    }

    private ChatMessageResponse doSend(ChatConversation conversation, User sender, SendChatMessageRequest request) {
        if (conversation.getStatus() == ChatConversationStatus.CLOSED) {
            throw new InvalidStatusTransitionException("This conversation is closed.");
        }
        String body = request.body() == null ? "" : request.body().trim();
        if (body.isEmpty() || body.length() > MAX_BODY) {
            throw new InvalidChatException("CHAT_MESSAGE_INVALID", "A message must be 1 to 4000 characters.");
        }
        Optional<ChatMessage> existing = messageRepository.findByConversationIdAndSenderIdAndClientMessageId(
                conversation.getId(), sender.getId(), request.clientMessageId());
        if (existing.isPresent()) {
            return toMessage(existing.get(), sender, participantRepository.findByConversationId(conversation.getId()));
        }
        if (!rateLimitStore.tryConsume("chat:send:" + sender.getId(), SEND_PER_MINUTE)) {
            throw new RateLimitExceededException("Too many messages. Try again shortly.");
        }
        ChatMessage message = ChatMessage.builder()
                .conversation(conversation)
                .sender(sender)
                .clientMessageId(request.clientMessageId())
                .body(body)
                .build();
        messageRepository.save(message);
        conversation.setLastMessagePreview(body.length() > PREVIEW_LENGTH ? body.substring(0, PREVIEW_LENGTH) : body);
        conversation.setLastMessageAt(message.getCreatedAt() == null ? Instant.now() : message.getCreatedAt());
        conversationRepository.save(conversation);
        ensureParticipant(conversation, sender);
        List<Long> recipients = participantRepository.findByConversationId(conversation.getId()).stream()
                .map(participant -> participant.getUser().getId())
                .filter(id -> !id.equals(sender.getId()))
                .toList();
        List<ChatParticipant> participants = participantRepository.findByConversationId(conversation.getId());
        ChatMessageResponse response = toMessage(message, sender, participants);
        messagesSent.increment();
        unreadNotifier.notifyOffline(participants.stream()
                        .map(ChatParticipant::getUser)
                        .filter(user -> !user.getId().equals(sender.getId()))
                        .toList(),
                conversation, sender, body);
        if (!recipients.isEmpty()) {
            eventPublisher.publishEvent(new ChatMessageCreatedEvent(conversation.getId(), response, recipients));
        }
        return response;
    }

    private ChatConversationResponse summary(ChatConversation conversation, User viewer) {
        List<ChatParticipant> participants = participantRepository.findByConversationId(conversation.getId());
        ChatParticipant viewerParticipant = participants.stream()
                .filter(participant -> participant.getUser().getId().equals(viewer.getId()))
                .findFirst().orElse(null);
        long lastRead = viewerParticipant == null || viewerParticipant.getLastReadMessageId() == null
                ? 0 : viewerParticipant.getLastReadMessageId();
        long unread = messageRepository.countByConversationIdAndIdGreaterThanAndSenderIdNot(
                conversation.getId(), lastRead, viewer.getId());
        ChatUserResponse counterpart = counterpart(conversation, viewer);
        ChatUserResponse assigned = conversation.getAssignedStaff() == null
                ? null : toUser(conversation.getAssignedStaff());
        boolean assignedToMe = conversation.getAssignedStaff() != null
                && conversation.getAssignedStaff().getId().equals(viewer.getId());
        return new ChatConversationResponse(conversation.getId(), conversation.getType(), conversation.getStatus(),
                counterpart, assigned,
                conversation.getOrder() == null ? null : conversation.getOrder().getOrderNumber(),
                conversation.getLastMessagePreview(), conversation.getLastMessageAt(), unread, assignedToMe);
    }

    private ChatUserResponse counterpart(ChatConversation conversation, User viewer) {
        if (conversation.getType() == ChatConversationType.SUPPORT) {
            if (conversation.getCustomer() != null && conversation.getCustomer().getId().equals(viewer.getId())) {
                return conversation.getAssignedStaff() == null ? null : toUser(conversation.getAssignedStaff());
            }
            return conversation.getCustomer() == null ? null : toUser(conversation.getCustomer());
        }
        return participantRepository.findByConversationId(conversation.getId()).stream()
                .map(ChatParticipant::getUser)
                .filter(user -> !user.getId().equals(viewer.getId()))
                .findFirst()
                .map(this::toUser)
                .orElse(null);
    }

    private ChatMessageResponse toMessage(ChatMessage message, User viewer, List<ChatParticipant> participants) {
        boolean readByOthers = participants.stream()
                .filter(participant -> !participant.getUser().getId().equals(viewer.getId()))
                .anyMatch(participant -> participant.getLastReadMessageId() != null
                        && participant.getLastReadMessageId() >= message.getId());
        return new ChatMessageResponse(message.getId(), message.getConversation().getId(),
                toUser(message.getSender()), message.getBody(), message.getClientMessageId(),
                message.getCreatedAt(), readByOthers);
    }

    private ChatUserResponse toUser(User user) {
        return new ChatUserResponse(user.getId(), user.getFullName(), user.getRole(), user.getPictureUrl());
    }

    private ChatParticipant ensureParticipant(ChatConversation conversation, User user) {
        return participantRepository.findByConversationIdAndUserId(conversation.getId(), user.getId())
                .orElseGet(() -> participantRepository.save(ChatParticipant.builder()
                        .conversation(conversation)
                        .user(user)
                        .roleAtJoin(user.getRole())
                        .build()));
    }

    private void requireAccess(ChatConversation conversation, User user) {
        if (user.getRole() == Role.CUSTOMER) {
            if (conversation.getType() != ChatConversationType.SUPPORT
                    || conversation.getCustomer() == null
                    || !conversation.getCustomer().getId().equals(user.getId())) {
                throw new AccessDeniedException("This conversation does not belong to you.");
            }
            return;
        }
        requireStaff(user);
        requireStaffAccess(conversation, user);
    }

    private void requireStaffAccess(ChatConversation conversation, User staff) {
        requireStaff(staff);
        if (conversation.getType() == ChatConversationType.INTERNAL) {
            participantRepository.findByConversationIdAndUserId(conversation.getId(), staff.getId())
                    .orElseThrow(() -> new AccessDeniedException("You are not part of this conversation."));
        }
    }

    private void requireCustomer(User user) {
        if (user.getRole() != Role.CUSTOMER) {
            throw new AccessDeniedException("Customer access is required.");
        }
    }

    private void requireStaff(User user) {
        if (user.getRole() != Role.ADMIN && user.getRole() != Role.MANAGER) {
            throw new AccessDeniedException("Staff access is required.");
        }
    }

    private ChatConversation requireConversation(Long id) {
        return conversationRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Conversation not found."));
    }

    private void publishToParticipants(ChatConversation conversation, ChatConversationChangedEvent event) {
        List<Long> recipients = participantRepository.findByConversationId(conversation.getId()).stream()
                .map(participant -> participant.getUser().getId())
                .toList();
        if (!recipients.isEmpty()) {
            eventPublisher.publishEvent(new ChatConversationChangedEvent(event.conversationId(), recipients, event.change()));
        }
    }

    @Transactional(readOnly = true)
    public void typing(User user, Long conversationId) {
        ChatConversation conversation = requireConversation(conversationId);
        requireAccess(conversation, user);
        List<Long> recipients = participantRepository.findByConversationId(conversationId).stream()
                .map(participant -> participant.getUser().getId())
                .filter(id -> !id.equals(user.getId()))
                .toList();
        if (!recipients.isEmpty()) {
            eventPublisher.publishEvent(new ChatTypingEvent(conversationId, user.getFullName(), recipients));
        }
    }

    /**
     * Resolves the user from the authenticated principal's email. Used by the
     * WebSocket endpoint, where the SecurityContext is not populated for the
     * STOMP message but the {@link java.security.Principal} is.
     */
    @Transactional(readOnly = true)
    public void typing(String email, Long conversationId) {
        userRepository.findByEmailIgnoreCase(email)
                .ifPresent(user -> typing(user, conversationId));
    }

    public record ChatMessageCreatedEvent(Long conversationId, ChatMessageResponse message, List<Long> recipientIds) {
    }

    public record ChatReadEvent(Long conversationId, Long readerId, Long lastReadMessageId, List<Long> recipientIds) {
    }

    public record ChatConversationChangedEvent(Long conversationId, List<Long> recipientIds, String change) {
    }

    public record ChatTypingEvent(Long conversationId, String name, List<Long> recipientIds) {
    }
}
