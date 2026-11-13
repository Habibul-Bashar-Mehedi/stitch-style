package com.stitchstyle.app.service;

import com.stitchstyle.app.dto.response.VariantResponse;
import com.stitchstyle.app.exception.InsufficientStockException;
import com.stitchstyle.app.exception.ResourceNotFoundException;
import com.stitchstyle.app.model.entity.InventoryMovement;
import com.stitchstyle.app.model.entity.InventoryReason;
import com.stitchstyle.app.model.entity.Order;
import com.stitchstyle.app.model.entity.ProductVariant;
import com.stitchstyle.app.model.entity.ReservationStatus;
import com.stitchstyle.app.model.entity.StockReservation;
import com.stitchstyle.app.repository.InventoryMovementRepository;
import com.stitchstyle.app.repository.ProductVariantRepository;
import com.stitchstyle.app.repository.StockReservationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class InventoryServiceTest {

    private ProductVariantRepository variantRepository;
    private StockReservationRepository reservationRepository;
    private InventoryMovementRepository movementRepository;
    private InventoryService service;

    @BeforeEach
    void setUp() {
        variantRepository = mock(ProductVariantRepository.class);
        reservationRepository = mock(StockReservationRepository.class);
        movementRepository = mock(InventoryMovementRepository.class);
        service = new InventoryService(variantRepository, reservationRepository, movementRepository);
    }

    @Test
    void locksLinesInVariantIdOrder() {
        ProductVariant first = variant(1L, 10, 0, true);
        ProductVariant second = variant(2L, 10, 0, true);
        when(variantRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(first));
        when(variantRepository.findByIdForUpdate(2L)).thenReturn(Optional.of(second));

        List<InventoryService.LockedLine> locked = service.lockAndValidate(List.of(
                new InventoryService.Line(2L, 1, 1),
                new InventoryService.Line(1L, 2, 0)));

        assertThat(locked).extracting(line -> line.variant().getId()).containsExactly(1L, 2L);
        assertThat(locked).extracting(InventoryService.LockedLine::quantity).containsExactly(2, 1);
    }

    @Test
    void reportsEveryInsufficientOrInactiveLine() {
        ProductVariant inactive = variant(1L, 10, 0, false);
        ProductVariant shortStock = variant(2L, 1, 0, true);
        when(variantRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(inactive));
        when(variantRepository.findByIdForUpdate(2L)).thenReturn(Optional.of(shortStock));

        assertThatThrownBy(() -> service.lockAndValidate(List.of(
                new InventoryService.Line(1L, 1, 0),
                new InventoryService.Line(2L, 5, 1))))
                .isInstanceOfSatisfying(InsufficientStockException.class, ex -> {
                    assertThat(ex.getErrors()).hasSize(2);
                    assertThat(ex.getErrors().get(0).field()).isEqualTo("items[0].variantId");
                    assertThat(ex.getErrors().get(1).message()).contains("Only 1 left");
                });
    }

    @Test
    void missingVariantIsANotFound() {
        when(variantRepository.findByIdForUpdate(9L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.lockAndValidate(List.of(new InventoryService.Line(9L, 1, 0))))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void codCommitDecrementsStockAndWritesMovement() {
        ProductVariant variant = variant(1L, 10, 2, true);
        Order order = Order.builder().id(77L).build();

        service.commitCod(order, List.of(new InventoryService.LockedLine(variant, 3, 0)));

        assertThat(variant.getStockQuantity()).isEqualTo(7);
        verify(variantRepository).save(variant);
        ArgumentCaptor<InventoryMovement> captor = ArgumentCaptor.forClass(InventoryMovement.class);
        verify(movementRepository).save(captor.capture());
        assertThat(captor.getValue().getDelta()).isEqualTo(-3);
        assertThat(captor.getValue().getReason()).isEqualTo(InventoryReason.ORDER);
        assertThat(captor.getValue().getBalanceAfter()).isEqualTo(7);
    }

    @Test
    void prepaidReservationIncrementsReservedQuantity() {
        ProductVariant variant = variant(1L, 10, 0, true);
        Order order = Order.builder().id(78L).build();
        Instant expiry = Instant.now().plusSeconds(900);

        service.reservePrepaid(order, List.of(new InventoryService.LockedLine(variant, 4, 0)), expiry);

        assertThat(variant.getReservedQuantity()).isEqualTo(4);
        assertThat(variant.available()).isEqualTo(6);
        ArgumentCaptor<StockReservation> captor = ArgumentCaptor.forClass(StockReservation.class);
        verify(reservationRepository).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(ReservationStatus.ACTIVE);
        assertThat(captor.getValue().getExpiresAt()).isEqualTo(expiry);
    }

    @Test
    void committingReservationsMovesStockAndClearsReserved() {
        ProductVariant variant = variant(1L, 10, 3, true);
        StockReservation reservation = StockReservation.builder().id(5L).order(Order.builder().id(80L).build())
                .variant(variant).quantity(2).status(ReservationStatus.ACTIVE).expiresAt(Instant.now()).build();
        when(reservationRepository.findByOrderIdAndStatus(80L, ReservationStatus.ACTIVE))
                .thenReturn(List.of(reservation));
        when(variantRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(variant));

        service.commitReservations(Order.builder().id(80L).build());

        assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.COMMITTED);
        assertThat(variant.getStockQuantity()).isEqualTo(8);
        assertThat(variant.getReservedQuantity()).isEqualTo(1);
        verify(movementRepository).save(any(InventoryMovement.class));
    }

    @Test
    void releasingReservationsDoesNotTouchStock() {
        ProductVariant variant = variant(1L, 10, 3, true);
        StockReservation reservation = StockReservation.builder().id(6L).order(Order.builder().id(81L).build())
                .variant(variant).quantity(2).status(ReservationStatus.ACTIVE).expiresAt(Instant.now()).build();
        when(reservationRepository.findByOrderIdAndStatus(81L, ReservationStatus.ACTIVE))
                .thenReturn(List.of(reservation));
        when(variantRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(variant));

        service.releaseReservations(Order.builder().id(81L).build());

        assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.RELEASED);
        assertThat(variant.getStockQuantity()).isEqualTo(10);
        assertThat(variant.getReservedQuantity()).isEqualTo(1);
    }

    @Test
    void returningStockAddsBackAndRecordsReturnMovement() {
        ProductVariant variant = variant(1L, 10, 0, true);
        when(variantRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(variant));

        service.restockReturn(Order.builder().id(82L).build(), 1L, 3);

        assertThat(variant.getStockQuantity()).isEqualTo(13);
        ArgumentCaptor<InventoryMovement> captor = ArgumentCaptor.forClass(InventoryMovement.class);
        verify(movementRepository).save(captor.capture());
        assertThat(captor.getValue().getReason()).isEqualTo(InventoryReason.RETURN);
        assertThat(captor.getValue().getDelta()).isEqualTo(3);
    }

    @Test
    void cancelRestoresOnlyOrderMovements() {
        ProductVariant variant = variant(1L, 10, 0, true);
        Order order = Order.builder().id(83L).build();
        InventoryMovement orderMovement = InventoryMovement.builder().variant(variant).delta(-4)
                .reason(InventoryReason.ORDER).referenceType("ORDER").referenceId(83L).balanceAfter(6).build();
        InventoryMovement cancelMovement = InventoryMovement.builder().variant(variant).delta(4)
                .reason(InventoryReason.CANCEL).referenceType("ORDER").referenceId(83L).balanceAfter(10).build();
        when(reservationRepository.findByOrderIdAndStatus(83L, ReservationStatus.ACTIVE)).thenReturn(List.of());
        when(movementRepository.findByReferenceTypeAndReferenceId("ORDER", 83L))
                .thenReturn(List.of(orderMovement, cancelMovement));
        when(variantRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(variant));

        service.restoreOnCancel(order);

        assertThat(variant.getStockQuantity()).isEqualTo(14);
        verify(movementRepository, never()).save(cancelMovement);
    }

    @Test
    void availableMapsVariantDetails() {
        ProductVariant variant = variant(1L, 10, 2, true);
        variant.setLowStockThreshold(3);
        variant.setPriceOverride(new BigDecimal("650.00"));
        when(variantRepository.findById(1L)).thenReturn(Optional.of(variant));

        VariantResponse response = service.available(1L);

        assertThat(response.stockQuantity()).isEqualTo(10);
        assertThat(response.reservedQuantity()).isEqualTo(2);
        assertThat(response.available()).isEqualTo(8);
        assertThat(response.priceOverride()).isEqualByComparingTo("650.00");
    }

    @Test
    void availableFailsForUnknownVariant() {
        when(variantRepository.findById(2L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.available(2L)).isInstanceOf(ResourceNotFoundException.class);
    }

    private static ProductVariant variant(Long id, int stock, int reserved, boolean active) {
        return ProductVariant.builder().id(id)
                .product(com.stitchstyle.app.model.entity.Product.builder().id(id + 100)
                        .nameI18n(java.util.Map.of("en", "Product")).slug("p" + id).active(true).build())
                .sizeLabel("M").sku("SKU-" + id).stockQuantity(stock).reservedQuantity(reserved)
                .active(active).build();
    }
}
