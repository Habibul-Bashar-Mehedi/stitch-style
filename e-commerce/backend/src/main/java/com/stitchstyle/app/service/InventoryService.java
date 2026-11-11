package com.stitchstyle.app.service;

import com.stitchstyle.app.dto.response.VariantResponse;
import com.stitchstyle.app.exception.InsufficientStockException;
import com.stitchstyle.app.exception.ResourceNotFoundException;
import com.stitchstyle.app.model.entity.*;
import com.stitchstyle.app.repository.InventoryMovementRepository;
import com.stitchstyle.app.repository.ProductVariantRepository;
import com.stitchstyle.app.repository.StockReservationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Service
public class InventoryService {

    private final ProductVariantRepository variantRepository;
    private final StockReservationRepository reservationRepository;
    private final InventoryMovementRepository movementRepository;

    public InventoryService(ProductVariantRepository variantRepository,
                            StockReservationRepository reservationRepository,
                            InventoryMovementRepository movementRepository) {
        this.variantRepository = variantRepository;
        this.reservationRepository = reservationRepository;
        this.movementRepository = movementRepository;
    }

    @Transactional
    public List<LockedLine> lockAndValidate(List<Line> lines) {
        List<Line> ordered = lines.stream().sorted(Comparator.comparing(Line::variantId)).toList();
        List<LockedLine> locked = new ArrayList<>();
        List<InsufficientStockException.ItemError> errors = new ArrayList<>();
        for (Line line : ordered) {
            ProductVariant variant = variantRepository.findByIdForUpdate(line.variantId())
                    .orElseThrow(() -> new ResourceNotFoundException("Variant not found: " + line.variantId()));
            if (!variant.isActive() || !variant.getProduct().isActive()) {
                errors.add(new InsufficientStockException.ItemError("items[" + line.index() + "].variantId",
                        "This size is no longer available"));
                continue;
            }
            if (variant.available() < line.quantity()) {
                errors.add(new InsufficientStockException.ItemError("items[" + line.index() + "].quantity",
                        "Only " + Math.max(0, variant.available()) + " left in size " + variant.getSizeLabel()));
                continue;
            }
            locked.add(new LockedLine(variant, line.quantity(), line.index()));
        }
        if (!errors.isEmpty()) {
            throw new InsufficientStockException("One or more items no longer have enough stock.", errors);
        }
        return locked;
    }

    @Transactional
    public void commitCod(Order order, List<LockedLine> lines) {
        for (LockedLine line : lines) {
            ProductVariant variant = line.variant();
            variant.setStockQuantity(variant.getStockQuantity() - line.quantity());
            variantRepository.save(variant);
            movementRepository.save(InventoryMovement.builder()
                    .variant(variant)
                    .delta(-line.quantity())
                    .reason(InventoryReason.ORDER)
                    .referenceType("ORDER")
                    .referenceId(order.getId())
                    .balanceAfter(variant.getStockQuantity())
                    .build());
        }
    }

    @Transactional
    public void reservePrepaid(Order order, List<LockedLine> lines, Instant expiresAt) {
        for (LockedLine line : lines) {
            ProductVariant variant = line.variant();
            variant.setReservedQuantity(variant.getReservedQuantity() + line.quantity());
            variantRepository.save(variant);
            reservationRepository.save(StockReservation.builder()
                    .order(order)
                    .variant(variant)
                    .quantity(line.quantity())
                    .status(ReservationStatus.ACTIVE)
                    .expiresAt(expiresAt)
                    .build());
        }
    }

    @Transactional
    public void commitReservations(Order order) {
        for (StockReservation reservation : reservationRepository.findByOrderIdAndStatus(order.getId(), ReservationStatus.ACTIVE)) {
            ProductVariant variant = variantRepository.findByIdForUpdate(reservation.getVariant().getId())
                    .orElseThrow(() -> new ResourceNotFoundException("Variant not found: " + reservation.getVariant().getId()));
            reservation.setStatus(ReservationStatus.COMMITTED);
            reservationRepository.save(reservation);
            variant.setStockQuantity(variant.getStockQuantity() - reservation.getQuantity());
            variant.setReservedQuantity(Math.max(0, variant.getReservedQuantity() - reservation.getQuantity()));
            variantRepository.save(variant);
            movementRepository.save(InventoryMovement.builder()
                    .variant(variant)
                    .delta(-reservation.getQuantity())
                    .reason(InventoryReason.ORDER)
                    .referenceType("ORDER")
                    .referenceId(order.getId())
                    .balanceAfter(variant.getStockQuantity())
                    .build());
        }
    }

    @Transactional
    public void releaseReservations(Order order) {
        for (StockReservation reservation : reservationRepository.findByOrderIdAndStatus(order.getId(), ReservationStatus.ACTIVE)) {
            ProductVariant variant = variantRepository.findByIdForUpdate(reservation.getVariant().getId())
                    .orElseThrow(() -> new ResourceNotFoundException("Variant not found: " + reservation.getVariant().getId()));
            reservation.setStatus(ReservationStatus.RELEASED);
            reservationRepository.save(reservation);
            variant.setReservedQuantity(Math.max(0, variant.getReservedQuantity() - reservation.getQuantity()));
            variantRepository.save(variant);
            movementRepository.save(InventoryMovement.builder()
                    .variant(variant)
                    .delta(0)
                    .reason(InventoryReason.RELEASE)
                    .referenceType("ORDER")
                    .referenceId(order.getId())
                    .balanceAfter(variant.getStockQuantity())
                    .build());
        }
    }

    @Transactional
    public void restockReturn(Order order, Long variantId, int quantity) {
        ProductVariant variant = variantRepository.findByIdForUpdate(variantId)
                .orElseThrow(() -> new ResourceNotFoundException("Variant not found: " + variantId));
        variant.setStockQuantity(variant.getStockQuantity() + quantity);
        variantRepository.save(variant);
        movementRepository.save(InventoryMovement.builder()
                .variant(variant)
                .delta(quantity)
                .reason(InventoryReason.RETURN)
                .referenceType("ORDER")
                .referenceId(order.getId())
                .balanceAfter(variant.getStockQuantity())
                .build());
    }

    @Transactional
    public void restoreOnCancel(Order order) {
        releaseReservations(order);
        for (InventoryMovement movement : movementRepository.findByReferenceTypeAndReferenceId("ORDER", order.getId())) {
            if (movement.getReason() == InventoryReason.ORDER && movement.getDelta() < 0) {
                ProductVariant variant = variantRepository.findByIdForUpdate(movement.getVariant().getId())
                        .orElseThrow(() -> new ResourceNotFoundException("Variant not found: " + movement.getVariant().getId()));
                int restore = -movement.getDelta();
                variant.setStockQuantity(variant.getStockQuantity() + restore);
                variantRepository.save(variant);
                movementRepository.save(InventoryMovement.builder()
                        .variant(variant)
                        .delta(restore)
                        .reason(InventoryReason.CANCEL)
                        .referenceType("ORDER")
                        .referenceId(order.getId())
                        .balanceAfter(variant.getStockQuantity())
                        .build());
            }
        }
    }

    public VariantResponse available(Long variantId) {
        ProductVariant variant = variantRepository.findById(variantId)
                .orElseThrow(() -> new ResourceNotFoundException("Variant not found: " + variantId));
        return new VariantResponse(variant.getId(), variant.getSizeLabel(), variant.getSku(),
                variant.getStockQuantity(), variant.getLowStockThreshold(), variant.getReservedQuantity(),
                variant.available(), variant.getPriceOverride(), variant.isActive());
    }

    public record Line(Long variantId, int quantity, int index) {}
    public record LockedLine(ProductVariant variant, int quantity, int index) {}
}
