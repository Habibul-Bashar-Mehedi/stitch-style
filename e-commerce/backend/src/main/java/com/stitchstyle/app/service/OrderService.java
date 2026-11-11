package com.stitchstyle.app.service;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import com.stitchstyle.app.dto.request.OrderRequest;
import com.stitchstyle.app.dto.response.AdminOrderResponse;
import com.stitchstyle.app.dto.response.OrderResponse;
import com.stitchstyle.app.dto.response.PageResponse;
import com.stitchstyle.app.dto.response.TrackingResponse;
import com.stitchstyle.app.exception.*;
import com.stitchstyle.app.model.entity.*;
import com.stitchstyle.app.repository.IdempotencyKeyRepository;
import com.stitchstyle.app.repository.OrderItemRepository;
import com.stitchstyle.app.repository.OrderRepository;
import com.stitchstyle.app.repository.ProductVariantRepository;
import com.stitchstyle.app.repository.UserRepository;
import com.stitchstyle.app.service.CouponService;
import jakarta.persistence.criteria.Predicate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Service
public class OrderService {

    private static final DateTimeFormatter ORDER_DATE = DateTimeFormatter.ofPattern("yyyyMMdd").withZone(ZoneOffset.UTC);
    private static final String ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";

    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final ProductVariantRepository variantRepository;
    private final UserRepository userRepository;
    private final IdempotencyKeyRepository idempotencyRepository;
    private final DeliveryAreaService deliveryAreaService;
    private final InventoryService inventoryService;
    private final PaymentService paymentService;
    private final CartService cartService;
    private final NotificationService notificationService;
    private final CouponService couponService;
    private final AuditService auditService;
    private final ObjectMapper objectMapper;
    private final Duration reservationTtl;
    private final Duration idempotencyTtl;

    public OrderService(OrderRepository orderRepository, OrderItemRepository orderItemRepository,
                        ProductVariantRepository variantRepository, UserRepository userRepository,
                        IdempotencyKeyRepository idempotencyRepository, DeliveryAreaService deliveryAreaService,
                        InventoryService inventoryService, PaymentService paymentService, CartService cartService,
                        NotificationService notificationService, CouponService couponService,
                        AuditService auditService, ObjectMapper objectMapper,
                        @Value("${app.reservation.ttl:PT15M}") Duration reservationTtl,
                        @Value("${app.idempotency.ttl:PT24H}") Duration idempotencyTtl) {
        this.orderRepository = orderRepository;
        this.orderItemRepository = orderItemRepository;
        this.variantRepository = variantRepository;
        this.userRepository = userRepository;
        this.idempotencyRepository = idempotencyRepository;
        this.deliveryAreaService = deliveryAreaService;
        this.inventoryService = inventoryService;
        this.paymentService = paymentService;
        this.cartService = cartService;
        this.notificationService = notificationService;
        this.couponService = couponService;
        this.auditService = auditService;
        this.objectMapper = objectMapper;
        this.reservationTtl = reservationTtl;
        this.idempotencyTtl = idempotencyTtl;
    }

    @Transactional
    public OrderResponse place(OrderRequest request, User user, String guestId, String idempotencyKey, String lang) {
        validateIdempotencyKey(idempotencyKey);
        String scope = user != null ? "user:" + user.getId() : "guest:" + guestId;
        if (user == null && (guestId == null || guestId.isBlank())) {
            throw new MissingIdempotencyKeyException("A guest_id cookie is required for guest checkout.");
        }
        String hash = hash(request);
        IdempotencyKey existing = idempotencyRepository.findByScopeKeyAndIdempotencyKey(scope, idempotencyKey).orElse(null);
        if (existing != null) {
            if (!existing.getRequestHash().equals(hash)) {
                throw new IdempotencyKeyReusedException("The Idempotency-Key was already used for a different request body.");
            }
            if (existing.getResponseStatus() == null) {
                throw new IdempotencyInProgressException("A request with this Idempotency-Key is still being processed.");
            }
            OrderResponse replayed = objectMapper.convertValue(existing.getResponseBody(), OrderResponse.class);
            return new OrderResponse(replayed.orderNumber(), replayed.publicToken(), replayed.status(),
                    replayed.paymentStatus(), replayed.currency(), replayed.subtotal(), replayed.deliveryCharge(),
                    replayed.discountTotal(), replayed.grandTotal(), replayed.trackingUrl(), replayed.redirectUrl(), true);
        }

        IdempotencyKey record = IdempotencyKey.builder()
                .scopeKey(scope)
                .idempotencyKey(idempotencyKey)
                .requestHash(hash)
                .expiresAt(Instant.now().plus(idempotencyTtl))
                .build();
        try {
            idempotencyRepository.saveAndFlush(record);
        } catch (DataIntegrityViolationException e) {
            throw new IdempotencyInProgressException("A request with this Idempotency-Key is still being processed.");
        }

        DeliveryArea area = deliveryAreaService.getRequired(request.deliveryAreaId());
        if (!area.isActive()) {
            throw new ResourceNotFoundException("Delivery area is not active: " + request.deliveryAreaId());
        }

        List<InventoryService.Line> lines = new ArrayList<>();
        for (int i = 0; i < request.items().size(); i++) {
            var item = request.items().get(i);
            lines.add(new InventoryService.Line(item.variantId(), item.quantity(), i));
        }
        List<InventoryService.LockedLine> locked = inventoryService.lockAndValidate(lines);

        BigDecimal subtotal = BigDecimal.ZERO;
        BigDecimal discount = BigDecimal.ZERO;
        List<OrderItem> orderItems = new ArrayList<>();
        List<Long> variantIds = new ArrayList<>();
        for (InventoryService.LockedLine line : locked) {
            ProductVariant variant = line.variant();
            Product product = variant.getProduct();
            BigDecimal unit = unitPrice(product, variant);
            BigDecimal lineTotal = unit.multiply(BigDecimal.valueOf(line.quantity())).setScale(2, RoundingMode.HALF_UP);
            subtotal = subtotal.add(lineTotal);
            BigDecimal base = product.getBasePrice().multiply(BigDecimal.valueOf(line.quantity()));
            if (base.compareTo(lineTotal) > 0) discount = discount.add(base.subtract(lineTotal));
            orderItems.add(OrderItem.builder()
                    .product(product)
                    .variant(variant)
                    .productNameSnapshot(resolve(product.getNameI18n(), lang))
                    .sizeLabelSnapshot(variant.getSizeLabel())
                    .skuSnapshot(variant.getSku())
                    .unitPriceSnapshot(unit)
                    .quantity(line.quantity())
                    .lineTotal(lineTotal)
                    .build());
            variantIds.add(variant.getId());
        }
        subtotal = subtotal.setScale(2, RoundingMode.HALF_UP);
        discount = discount.setScale(2, RoundingMode.HALF_UP);
        BigDecimal deliveryCharge = deliveryAreaService.deliveryCharge(area, subtotal);
        CouponService.AppliedCoupon appliedCoupon = null;
        BigDecimal couponDiscount = BigDecimal.ZERO.setScale(2);
        if (request.couponCode() != null && !request.couponCode().isBlank()) {
            appliedCoupon = couponService.applyForOrder(request.couponCode(), subtotal, user);
            couponDiscount = appliedCoupon.discount();
        }
        BigDecimal grandTotal = subtotal.add(deliveryCharge).subtract(couponDiscount).max(BigDecimal.ZERO)
                .setScale(2, RoundingMode.HALF_UP);

        Order order = Order.builder()
                .orderNumber(orderNumber())
                .publicToken(UUID.randomUUID().toString().replace("-", ""))
                .user(user)
                .guestId(user == null ? guestId : null)
                .customerName(request.customer().name())
                .customerPhone(normalizePhone(request.customer().phone()))
                .shippingAddress(request.shippingAddress())
                .deliveryArea(area)
                .subtotal(subtotal)
                .deliveryCharge(deliveryCharge)
                .discountTotal(discount)
                .couponCode(appliedCoupon == null ? null : appliedCoupon.coupon().getCode())
                .couponDiscount(couponDiscount)
                .grandTotal(grandTotal)
                .paymentMethod(request.paymentMethod())
                .paymentStatus(request.paymentMethod() == PaymentMethod.COD ? PaymentStatus.UNPAID : PaymentStatus.INITIATED)
                .status(OrderStatus.PENDING)
                .note(request.note())
                .placedAt(Instant.now())
                .build();
        orderRepository.saveAndFlush(order);
        for (OrderItem item : orderItems) item.setOrder(order);
        orderItemRepository.saveAll(orderItems);
        if (appliedCoupon != null) couponService.redeem(order, user, appliedCoupon);

        if (request.paymentMethod() == PaymentMethod.COD) {
            inventoryService.commitCod(order, locked);
        } else {
            inventoryService.reservePrepaid(order, locked, Instant.now().plus(reservationTtl));
        }
        notificationService.enqueueOrderConfirmation(order);
        String redirectUrl = request.paymentMethod() == PaymentMethod.COD ? null : paymentService.initiate(order);
        if (user != null) cartService.removeSelected(user.getId(), variantIds);

        OrderResponse response = toResponse(order, redirectUrl, false);
        record.setResponseStatus(201);
        record.setResponseBody(objectMapper.convertValue(response, new TypeReference<Map<String, Object>>() {}));
        record.setOrder(order);
        idempotencyRepository.save(record);
        return response;
    }

    @Transactional(readOnly = true)
    public TrackingResponse trackByToken(String token) {
        Order order = orderRepository.findByPublicToken(token)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found."));
        return tracking(order);
    }

    @Transactional(readOnly = true)
    public TrackingResponse trackByNumberAndPhone(String orderNumber, String phone) {
        Order order = orderRepository.findByOrderNumberAndCustomerPhone(orderNumber, normalizePhone(phone))
                .orElseThrow(() -> new ResourceNotFoundException("Order not found."));
        return tracking(order);
    }

    @Transactional(readOnly = true)
    public List<TrackingResponse> myOrders(Long userId) {
        return orderRepository.findByUserIdOrderByPlacedAtDesc(userId).stream().map(this::tracking).toList();
    }

    @Transactional(readOnly = true)
    public TrackingResponse myOrder(Long userId, String orderNumber) {
        return orderRepository.findByOrderNumber(orderNumber)
                .filter(o -> o.getUser() != null && o.getUser().getId().equals(userId))
                .map(this::tracking)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found."));
    }

    @Transactional(readOnly = true)
    public PageResponse<AdminOrderResponse> adminList(OrderStatus status, LocalDate from, LocalDate to,
                                                      String phone, String orderNumber, int page, int size) {
        Specification<Order> specification = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (status != null) predicates.add(cb.equal(root.get("status"), status));
            if (from != null) predicates.add(cb.greaterThanOrEqualTo(root.get("placedAt"),
                    from.atStartOfDay().toInstant(ZoneOffset.UTC)));
            if (to != null) predicates.add(cb.lessThan(root.get("placedAt"),
                    to.plusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC)));
            if (phone != null && !phone.isBlank()) predicates.add(cb.equal(root.get("customerPhone"), normalizePhone(phone)));
            if (orderNumber != null && !orderNumber.isBlank()) predicates.add(cb.equal(root.get("orderNumber"), orderNumber));
            return cb.and(predicates.toArray(Predicate[]::new));
        };
        Pageable pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100),
                Sort.by(Sort.Direction.DESC, "placedAt"));
        Page<Order> result = orderRepository.findAll(specification, pageable);
        return new PageResponse<>(result.getContent().stream().map(o -> new AdminOrderResponse(o.getId(),
                o.getOrderNumber(), o.getCustomerName(), o.getCustomerPhone(), o.getStatus(),
                o.getPaymentStatus(), o.getGrandTotal(), o.getPlacedAt())).toList(),
                result.getNumber(), result.getSize(), result.getTotalElements(), result.getTotalPages());
    }

    @Transactional(readOnly = true)
    public TrackingResponse adminGet(Long id) {
        return tracking(getRequired(id));
    }

    @Transactional
    public TrackingResponse updateStatus(Long id, OrderStatus target, String reason) {
        Order order = getRequired(id);
        if (!allowed(order.getStatus(), target)) {
            throw new InvalidStatusTransitionException("Cannot move order from " + order.getStatus() + " to " + target + ".");
        }
        OrderStatus before = order.getStatus();
        Instant now = Instant.now();
        if (target == OrderStatus.CANCELLED) {
            inventoryService.restoreOnCancel(order);
            order.setCancelledAt(now);
            order.setCancelReason(reason == null ? "CANCELLED" : reason);
        } else if (target == OrderStatus.SHIPPED) {
            order.setShippedAt(now);
        } else if (target == OrderStatus.DELIVERED) {
            order.setDeliveredAt(now);
        }
        order.setStatus(target);
        orderRepository.save(order);
        auditService.record(AuditAction.STATUS_CHANGE, "ORDER", order.getId(),
                Map.<String, Object>of("status", before.name()),
                Map.<String, Object>of("status", target.name(), "reason", reason == null ? "" : reason));
        return tracking(order);
    }

    @Transactional
    public void recordCourier(Long id, String courierName, String trackingNumber) {
        Order order = getRequired(id);
        order.setCourierName(courierName);
        order.setTrackingNumber(trackingNumber);
        orderRepository.save(order);
        auditService.record(AuditAction.UPDATE, "ORDER", order.getId(), Map.of(), Map.of(
                "courierName", courierName == null ? "" : courierName,
                "trackingNumber", trackingNumber == null ? "" : trackingNumber));
    }

    public Order getRequired(Long id) {
        return orderRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Order not found: " + id));
    }

    private TrackingResponse tracking(Order order) {
        List<TrackingResponse.TrackingItem> items = orderItemRepository.findByOrderId(order.getId()).stream()
                .map(i -> new TrackingResponse.TrackingItem(i.getId(), i.getProductNameSnapshot(), i.getSizeLabelSnapshot(),
                        i.getQuantity(), i.getUnitPriceSnapshot()))
                .toList();
        return new TrackingResponse(order.getOrderNumber(), order.getStatus(), order.getPaymentStatus(),
                order.getGrandTotal(), order.getPlacedAt(), order.getShippedAt(), order.getDeliveredAt(), items);
    }

    private OrderResponse toResponse(Order order, String redirectUrl, boolean replayed) {
        return new OrderResponse(order.getOrderNumber(), order.getPublicToken(), order.getStatus(),
                order.getPaymentStatus(), "BDT", order.getSubtotal(), order.getDeliveryCharge(),
                order.getDiscountTotal(), order.getGrandTotal(), "/track?token=" + order.getPublicToken(),
                redirectUrl, replayed);
    }

    private boolean allowed(OrderStatus from, OrderStatus to) {
        return switch (from) {
            case PENDING -> to == OrderStatus.SHIPPED || to == OrderStatus.CANCELLED;
            case SHIPPED -> to == OrderStatus.DELIVERED || to == OrderStatus.CANCELLED;
            default -> false;
        };
    }

    private BigDecimal unitPrice(Product product, ProductVariant variant) {
        if (variant.getPriceOverride() != null) return variant.getPriceOverride();
        if (product.getDiscountPrice() != null) return product.getDiscountPrice();
        return product.getBasePrice();
    }

    private String resolve(Map<String, String> values, String lang) {
        if (values == null) return "";
        String key = lang == null ? "en" : lang;
        return values.getOrDefault(key, values.getOrDefault("en", values.values().stream().findFirst().orElse("")));
    }

    private String orderNumber() {
        StringBuilder suffix = new StringBuilder();
        Random random = new Random();
        for (int i = 0; i < 6; i++) suffix.append(ALPHABET.charAt(random.nextInt(ALPHABET.length())));
        return "ORD-" + ORDER_DATE.format(Instant.now()) + "-" + suffix;
    }

    private void validateIdempotencyKey(String key) {
        if (key == null || key.isBlank()) {
            throw new MissingIdempotencyKeyException("The Idempotency-Key header is required.");
        }
        if (!key.matches("^[A-Za-z0-9_-]{16,64}$")) {
            throw new MissingIdempotencyKeyException("The Idempotency-Key must be 16–64 characters from [A-Za-z0-9_-].");
        }
    }

    private String hash(OrderRequest request) {
        try {
            byte[] json = objectMapper.writeValueAsBytes(request);
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(json));
        } catch (Exception e) {
            throw new IllegalStateException("Cannot fingerprint the request", e);
        }
    }

    public static String normalizePhone(String phone) {
        if (phone == null) throw new InvalidPhoneException("A mobile number is required.");
        String value = phone.replaceAll("[\\s-]", "");
        if (value.matches("^01[3-9]\\d{8}$")) return "+88" + value;
        if (value.matches("^\\+8801[3-9]\\d{8}$")) return value;
        if (value.matches("^8801[3-9]\\d{8}$")) return "+" + value;
        throw new InvalidPhoneException("Enter a valid Bangladeshi mobile number (01XXXXXXXXX or +8801XXXXXXXXX).");
    }
}
