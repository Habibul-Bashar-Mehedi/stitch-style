package com.stitchstyle.app.service;

import com.stitchstyle.app.dto.request.RefundRequest;
import com.stitchstyle.app.dto.request.ReturnQuoteRequest;
import com.stitchstyle.app.dto.request.ReturnRequestCreate;
import com.stitchstyle.app.dto.request.ReturnStatusRequest;
import com.stitchstyle.app.dto.response.MyOrderResponse;
import com.stitchstyle.app.dto.response.ReturnQuoteResponse;
import com.stitchstyle.app.dto.response.ReturnResponse;
import com.stitchstyle.app.dto.response.TrackingResponse;
import com.stitchstyle.app.exception.InvalidReturnException;
import com.stitchstyle.app.exception.InvalidStatusTransitionException;
import com.stitchstyle.app.exception.ResourceNotFoundException;
import com.stitchstyle.app.model.entity.*;
import com.stitchstyle.app.repository.OrderItemRepository;
import com.stitchstyle.app.repository.OrderRepository;
import com.stitchstyle.app.repository.ReturnItemRepository;
import com.stitchstyle.app.repository.ReturnRequestRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class ReturnService {

    private static final BigDecimal MIN_REFUND = new BigDecimal("0.01");
    private static final String CURRENCY = "BDT";

    private final ReturnRequestRepository returnRepository;
    private final ReturnItemRepository returnItemRepository;
    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final InventoryService inventoryService;
    private final PaymentService paymentService;
    private final AuditService auditService;
    private final NotificationService notificationService;
    private final int windowDays;

    public ReturnService(ReturnRequestRepository returnRepository, ReturnItemRepository returnItemRepository,
                         OrderRepository orderRepository, OrderItemRepository orderItemRepository,
                         InventoryService inventoryService, PaymentService paymentService, AuditService auditService,
                         NotificationService notificationService,
                         @Value("${app.returns.window-days:7}") int windowDays) {
        this.returnRepository = returnRepository;
        this.returnItemRepository = returnItemRepository;
        this.orderRepository = orderRepository;
        this.orderItemRepository = orderItemRepository;
        this.inventoryService = inventoryService;
        this.paymentService = paymentService;
        this.auditService = auditService;
        this.notificationService = notificationService;
        this.windowDays = windowDays;
    }

    @Transactional
    public ReturnResponse request(User user, ReturnRequestCreate request) {
        if (user == null) throw new InvalidStatusTransitionException("Sign in to request a return.");
        Order order = deliveredOrder(user, request.orderNumber());
        if (returnRepository.findFirstByOrderIdAndStatusNot(order.getId(), ReturnStatus.REJECTED).isPresent()) {
            throw new InvalidStatusTransitionException("A return request already exists for this order.");
        }
        List<Line> lines = normalizeCreateItems(order, request.items());
        ReturnRequest returnRequest = ReturnRequest.builder()
                .order(order)
                .status(ReturnStatus.REQUESTED)
                .reason(request.reason())
                .build();
        returnRepository.save(returnRequest);
        for (Line line : lines) {
            returnItemRepository.save(ReturnItem.builder()
                    .returnRequest(returnRequest)
                    .orderItem(line.item())
                    .quantity(line.quantity())
                    .condition(line.condition())
                    .build());
        }
        auditService.record(AuditAction.CREATE, "RETURN", returnRequest.getId(), null,
                Map.of("orderNumber", order.getOrderNumber(), "status", ReturnStatus.REQUESTED.name()));
        return toResponse(returnRequest);
    }

    @Transactional(readOnly = true)
    public ReturnQuoteResponse quote(User user, String orderNumber, ReturnQuoteRequest request) {
        Order order = deliveredOrder(user, orderNumber);
        return computeQuote(order, normalizeQuoteItems(order, request.items()));
    }

    @Transactional(readOnly = true)
    public List<ReturnResponse> list() {
        return returnRepository.findAllByOrderByRequestedAtDesc().stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public List<ReturnResponse> listForUser(User user) {
        return returnRepository.findByOrderUserIdOrderByRequestedAtDesc(user.getId()).stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public List<MyOrderResponse> enrichOrders(User user, List<TrackingResponse> orders) {
        Map<String, ReturnRequest> latestByOrder = new LinkedHashMap<>();
        for (ReturnRequest request : returnRepository.findByOrderUserIdOrderByRequestedAtDesc(user.getId())) {
            latestByOrder.putIfAbsent(request.getOrder().getOrderNumber(), request);
        }
        return orders.stream()
                .map(order -> toMyOrder(order, latestByOrder.get(order.orderNumber())))
                .toList();
    }

    @Transactional(readOnly = true)
    public ReturnResponse get(Long id) {
        return toResponse(getRequired(id));
    }

    @Transactional
    public ReturnResponse advance(Long id, ReturnStatusRequest request) {
        ReturnRequest returnRequest = getRequired(id);
        ReturnStatus before = returnRequest.getStatus();
        ReturnStatus target = request.status();
        if (!allowed(before, target)) {
            throw new InvalidStatusTransitionException("Cannot move return from " + returnRequest.getStatus() + " to " + target + ".");
        }
        if (target == ReturnStatus.RECEIVED) {
            for (ReturnItem item : returnItemRepository.findByReturnRequestId(id)) {
                inventoryService.restockReturn(returnRequest.getOrder(), item.getOrderItem().getVariant().getId(), item.getQuantity());
            }
        }
        returnRequest.setStatus(target);
        if (request.note() != null && !request.note().isBlank()) {
            returnRequest.setResolutionNote(request.note().trim());
        }
        if (target == ReturnStatus.REJECTED) returnRequest.setResolvedAt(Instant.now());
        returnRepository.save(returnRequest);
        auditService.record(AuditAction.STATUS_CHANGE, "RETURN", id,
                Map.of("status", before.name()),
                Map.of("status", target.name()));
        if (target == ReturnStatus.APPROVED || target == ReturnStatus.REJECTED || target == ReturnStatus.RECEIVED) {
            notificationService.enqueueReturnStatus(returnRequest, "RETURN_" + target.name());
        }
        return toResponse(returnRequest);
    }

    @Transactional
    public ReturnResponse refund(Long id, RefundRequest request) {
        ReturnRequest returnRequest = getRequired(id);
        if (returnRequest.getStatus() != ReturnStatus.RECEIVED) {
            throw new InvalidStatusTransitionException("Only received returns can be refunded.");
        }
        ReturnQuoteResponse quote = computeQuote(returnRequest.getOrder(), existingLines(returnRequest));
        BigDecimal amount = request.amount() == null ? quote.total() : request.amount();
        if (amount.compareTo(MIN_REFUND) < 0 || amount.compareTo(quote.total()) > 0) {
            throw new InvalidReturnException("RETURN_REFUND_AMOUNT_INVALID",
                    "Refund amount must be between " + MIN_REFUND.toPlainString() + " and " + quote.total().toPlainString() + ".");
        }
        String method = paymentService.refund(returnRequest.getOrder(), amount, "RETURN_" + returnRequest.getId());
        returnRequest.setStatus(ReturnStatus.REFUNDED);
        returnRequest.setRefundMethod(method);
        returnRequest.setRefundAmount(amount);
        returnRequest.setResolvedAt(Instant.now());
        returnRepository.save(returnRequest);
        Order order = returnRequest.getOrder();
        order.setPaymentStatus(amount.compareTo(order.getGrandTotal()) >= 0
                ? PaymentStatus.REFUNDED
                : PaymentStatus.PARTIALLY_REFUNDED);
        orderRepository.save(order);
        auditService.record(AuditAction.UPDATE, "RETURN", id,
                Map.of("status", ReturnStatus.RECEIVED.name()),
                Map.of("status", ReturnStatus.REFUNDED.name(), "amount", amount.toPlainString()));
        notificationService.enqueueReturnStatus(returnRequest, "RETURN_REFUNDED");
        return toResponse(returnRequest);
    }

    private Order deliveredOrder(User user, String orderNumber) {
        Order order = orderRepository.findByOrderNumber(orderNumber)
                .filter(o -> o.getUser() != null && o.getUser().getId().equals(user.getId()))
                .orElseThrow(() -> new ResourceNotFoundException("Order not found."));
        if (order.getStatus() != OrderStatus.DELIVERED || order.getDeliveredAt() == null) {
            throw new InvalidStatusTransitionException("Only delivered orders can be returned.");
        }
        if (order.getDeliveredAt().plus(windowDays, ChronoUnit.DAYS).isBefore(Instant.now())) {
            throw new InvalidStatusTransitionException("The return window has closed.");
        }
        return order;
    }

    private List<Line> normalizeCreateItems(Order order, List<ReturnRequestCreate.Item> items) {
        Map<Long, Integer> quantities = new LinkedHashMap<>();
        Map<Long, ReturnCondition> conditions = new HashMap<>();
        for (ReturnRequestCreate.Item item : items) {
            quantities.merge(item.orderItemId(), item.quantity(), Integer::sum);
            ReturnCondition previous = conditions.putIfAbsent(item.orderItemId(), item.condition());
            if (previous != null && previous != item.condition()) {
                throw new InvalidReturnException("RETURN_DUPLICATE_ITEM",
                        "Duplicate order-item lines must use the same condition.");
            }
        }
        return toLines(order, quantities, conditions);
    }

    private List<Line> normalizeQuoteItems(Order order, List<ReturnQuoteRequest.Item> items) {
        Map<Long, Integer> quantities = new LinkedHashMap<>();
        for (ReturnQuoteRequest.Item item : items) {
            if (item.quantity() > 0) quantities.merge(item.orderItemId(), item.quantity(), Integer::sum);
        }
        return toLines(order, quantities, Map.of());
    }

    private List<Line> toLines(Order order, Map<Long, Integer> quantities, Map<Long, ReturnCondition> conditions) {
        Map<Long, OrderItem> orderItems = orderItemRepository.findByOrderId(order.getId()).stream()
                .collect(Collectors.toMap(OrderItem::getId, Function.identity()));
        List<Line> lines = new ArrayList<>();
        for (Map.Entry<Long, Integer> entry : quantities.entrySet()) {
            OrderItem orderItem = orderItems.get(entry.getKey());
            if (orderItem == null) throw new ResourceNotFoundException("Order item not found: " + entry.getKey());
            if (entry.getValue() > orderItem.getQuantity()) {
                throw new InvalidReturnException("RETURN_QUANTITY_EXCEEDED",
                        "Return quantity exceeds the ordered quantity for " + orderItem.getProductNameSnapshot() + ".");
            }
            lines.add(new Line(orderItem, entry.getValue(), conditions.getOrDefault(entry.getKey(), ReturnCondition.UNOPENED)));
        }
        return lines;
    }

    private List<Line> existingLines(ReturnRequest request) {
        return returnItemRepository.findByReturnRequestId(request.getId()).stream()
                .map(item -> new Line(item.getOrderItem(), item.getQuantity(), item.getCondition()))
                .toList();
    }

    private ReturnQuoteResponse computeQuote(Order order, Collection<Line> lines) {
        BigDecimal itemsAmount = lines.stream()
                .map(line -> line.item().getUnitPriceSnapshot().multiply(BigDecimal.valueOf(line.quantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);
        Map<Long, Integer> returned = lines.stream()
                .collect(Collectors.toMap(line -> line.item().getId(), Line::quantity, Integer::sum));
        List<OrderItem> allItems = orderItemRepository.findByOrderId(order.getId());
        boolean fullOrder = !allItems.isEmpty()
                && allItems.stream().allMatch(item -> returned.getOrDefault(item.getId(), 0) >= item.getQuantity());
        BigDecimal subtotal = order.getSubtotal() == null ? BigDecimal.ZERO : order.getSubtotal();
        BigDecimal coupon = order.getCouponDiscount() == null ? BigDecimal.ZERO : order.getCouponDiscount();
        BigDecimal couponShare = subtotal.signum() > 0
                ? coupon.multiply(itemsAmount).divide(subtotal, 2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO.setScale(2);
        if (couponShare.compareTo(coupon) > 0) couponShare = coupon.setScale(2, RoundingMode.HALF_UP);
        BigDecimal delivery = fullOrder && order.getDeliveryCharge() != null
                ? order.getDeliveryCharge()
                : BigDecimal.ZERO.setScale(2);
        BigDecimal total = itemsAmount.subtract(couponShare).add(delivery)
                .max(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);
        Instant windowEndsAt = order.getDeliveredAt() == null
                ? null
                : order.getDeliveredAt().plus(windowDays, ChronoUnit.DAYS);
        return new ReturnQuoteResponse(itemsAmount, couponShare, delivery, total, fullOrder, CURRENCY, windowEndsAt);
    }

    private MyOrderResponse toMyOrder(TrackingResponse order, ReturnRequest existing) {
        Instant windowEndsAt = order.deliveredAt() == null
                ? null
                : order.deliveredAt().plus(windowDays, ChronoUnit.DAYS);
        MyOrderResponse.Eligibility eligibility;
        String reason = null;
        if (order.status() != OrderStatus.DELIVERED || order.deliveredAt() == null) {
            eligibility = MyOrderResponse.Eligibility.NOT_DELIVERED;
            reason = "The order has not been delivered.";
        } else if (existing != null && existing.getStatus() != ReturnStatus.REJECTED) {
            eligibility = MyOrderResponse.Eligibility.ALREADY_REQUESTED;
            reason = "A return request already exists for this order.";
        } else if (windowEndsAt != null && windowEndsAt.isBefore(Instant.now())) {
            eligibility = MyOrderResponse.Eligibility.WINDOW_CLOSED;
            reason = "The return window has closed.";
        } else {
            eligibility = MyOrderResponse.Eligibility.ELIGIBLE;
        }
        boolean active = existing != null && existing.getStatus() != ReturnStatus.REJECTED;
        return new MyOrderResponse(
                order.orderNumber(), order.status(), order.paymentStatus(), order.grandTotal(),
                order.placedAt(), order.shippedAt(), order.deliveredAt(), order.items(),
                new MyOrderResponse.ReturnSummary(
                        active ? existing.getId() : null,
                        existing == null ? null : existing.getStatus(),
                        existing == null ? null : existing.getRequestedAt(),
                        existing == null ? null : existing.getResolvedAt(),
                        windowEndsAt, eligibility, reason,
                        existing != null && existing.getStatus() == ReturnStatus.REJECTED ? existing.getId() : null,
                        existing != null && existing.getStatus() == ReturnStatus.REJECTED ? existing.getResolutionNote() : null));
    }

    private ReturnRequest getRequired(Long id) {
        return returnRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Return not found: " + id));
    }

    private boolean allowed(ReturnStatus from, ReturnStatus to) {
        return switch (from) {
            case REQUESTED -> to == ReturnStatus.APPROVED || to == ReturnStatus.REJECTED;
            case APPROVED -> to == ReturnStatus.RECEIVED;
            case RECEIVED -> to == ReturnStatus.REFUNDED;
            default -> false;
        };
    }

    private ReturnResponse toResponse(ReturnRequest returnRequest) {
        List<ReturnItem> rows = returnItemRepository.findByReturnRequestId(returnRequest.getId());
        List<Line> lines = rows.stream()
                .map(item -> new Line(item.getOrderItem(), item.getQuantity(), item.getCondition()))
                .toList();
        ReturnQuoteResponse quote = computeQuote(returnRequest.getOrder(), lines);
        List<ReturnResponse.Item> items = rows.stream()
                .map(item -> {
                    BigDecimal unitPrice = item.getOrderItem().getUnitPriceSnapshot();
                    BigDecimal lineTotal = unitPrice.multiply(BigDecimal.valueOf(item.getQuantity()))
                            .setScale(2, RoundingMode.HALF_UP);
                    return new ReturnResponse.Item(item.getId(), item.getOrderItem().getId(),
                            item.getOrderItem().getProductNameSnapshot(), item.getOrderItem().getSizeLabelSnapshot(),
                            item.getQuantity(), item.getCondition(), unitPrice, lineTotal);
                })
                .toList();
        return new ReturnResponse(returnRequest.getId(), returnRequest.getOrder().getOrderNumber(),
                returnRequest.getStatus(), returnRequest.getReason(), returnRequest.getResolutionNote(),
                returnRequest.getRefundMethod(), returnRequest.getRefundAmount(), returnRequest.getRequestedAt(),
                returnRequest.getResolvedAt(), quote, items);
    }

    private record Line(OrderItem item, int quantity, ReturnCondition condition) {
    }
}
