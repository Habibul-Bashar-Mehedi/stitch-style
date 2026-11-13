package com.stitchstyle.app.service;

import com.stitchstyle.app.exception.ResourceNotFoundException;
import com.stitchstyle.app.integration.courier.CourierGateway;
import com.stitchstyle.app.model.entity.Order;
import com.stitchstyle.app.repository.OrderRepository;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CourierServiceTest {

    private final OrderRepository orderRepository = mock(OrderRepository.class);
    private final AuditService auditService = mock(AuditService.class);

    @Test
    void createsShipmentThroughTheRequestedGateway() {
        CourierGateway gateway = mock(CourierGateway.class);
        when(gateway.code()).thenReturn("PATHAO");
        when(gateway.displayName()).thenReturn("Pathao");
        when(gateway.createShipment(any())).thenReturn(new CourierGateway.Shipment("TRACK-1", "OK", null));
        Order order = Order.builder().id(1L).orderNumber("ORD-1").build();
        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));
        CourierService service = new CourierService(List.of(gateway), orderRepository, auditService);

        Order updated = service.createShipment(1L, "pathao");

        assertThat(updated.getCourierName()).isEqualTo("Pathao");
        assertThat(updated.getTrackingNumber()).isEqualTo("TRACK-1");
        verify(orderRepository).save(order);
        verify(auditService).record(any(), any(), any(), any(), any());
    }

    @Test
    void unknownCourierAndOrderAreRejected() {
        CourierService service = new CourierService(List.of(), orderRepository, auditService);

        assertThatThrownBy(() -> service.createShipment(1L, "DHL"))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Courier not found");

        CourierGateway gateway = mock(CourierGateway.class);
        when(gateway.code()).thenReturn("REDX");
        when(orderRepository.findById(2L)).thenReturn(Optional.empty());
        CourierService withGateway = new CourierService(List.of(gateway), orderRepository, auditService);
        assertThatThrownBy(() -> withGateway.createShipment(2L, "REDX"))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Order not found");
    }

    @Test
    void nullCourierCodeIsTreatedAsUnknown() {
        CourierService service = new CourierService(List.of(), orderRepository, auditService);

        assertThatThrownBy(() -> service.createShipment(1L, null))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
