package com.stitchstyle.app.service;

import com.stitchstyle.app.config.CacheEvictionService;
import com.stitchstyle.app.dto.request.DeliveryAreaRequest;
import com.stitchstyle.app.dto.response.DeliveryAreaResponse;
import com.stitchstyle.app.exception.ResourceNotFoundException;
import com.stitchstyle.app.model.entity.DeliveryArea;
import com.stitchstyle.app.repository.DeliveryAreaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DeliveryAreaServiceTest {

    private DeliveryAreaRepository repository;
    private CacheEvictionService cacheEvictionService;
    private DeliveryAreaService service;

    @BeforeEach
    void setUp() {
        repository = mock(DeliveryAreaRepository.class);
        cacheEvictionService = mock(CacheEvictionService.class);
        service = new DeliveryAreaService(repository, cacheEvictionService);
    }

    @Test
    void deliveryIsFreeAtOrAboveTheThreshold() {
        DeliveryArea area = DeliveryArea.builder().deliveryCharge(new BigDecimal("60.00"))
                .freeShippingThreshold(new BigDecimal("1000.00")).build();

        assertThat(service.deliveryCharge(area, new BigDecimal("999.99"))).isEqualByComparingTo("60.00");
        assertThat(service.deliveryCharge(area, new BigDecimal("1000.00"))).isEqualByComparingTo("0");
    }

    @Test
    void nullChargeAndThresholdAreHandled() {
        DeliveryArea area = new DeliveryArea();
        assertThat(service.deliveryCharge(area, BigDecimal.TEN)).isEqualByComparingTo("0");

        area.setFreeShippingThreshold(new BigDecimal("1.00"));
        assertThat(service.deliveryCharge(area, BigDecimal.TEN)).isEqualByComparingTo("0");
    }

    @Test
    void activeListsOnlyActiveAreas() {
        when(repository.findByActiveTrueOrderByDisplayOrderAsc()).thenReturn(List.of(
                DeliveryArea.builder().id(1L).name("Dhaka").deliveryCharge(BigDecimal.ZERO).displayOrder(0).build()));

        List<DeliveryAreaResponse> responses = service.active();

        assertThat(responses).hasSize(1);
        assertThat(responses.get(0).name()).isEqualTo("Dhaka");
    }

    @Test
    void createAppliesDefaultsAndEvictsCache() {
        when(repository.save(any(DeliveryArea.class))).thenAnswer(invocation -> {
            DeliveryArea area = invocation.getArgument(0);
            area.setId(7L);
            return area;
        });

        DeliveryAreaResponse response = service.create(new DeliveryAreaRequest(
                "Chattogram", "Chattogram", null, null, null, null, null));

        assertThat(response.id()).isEqualTo(7L);
        assertThat(response.deliveryCharge()).isEqualByComparingTo("0");
        assertThat(response.displayOrder()).isZero();
        verify(cacheEvictionService).evictAfterCommit(com.stitchstyle.app.config.CacheNames.DELIVERY_AREAS);
    }

    @Test
    void updateAndDeleteRequireAnExistingArea() {
        when(repository.findById(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.update(1L, new DeliveryAreaRequest("X", null, BigDecimal.ZERO,
                null, null, true, 0)))
                .isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> service.delete(1L)).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void updateCopiesAllEditableFields() {
        DeliveryArea area = DeliveryArea.builder().id(2L).name("Old").build();
        when(repository.findById(2L)).thenReturn(Optional.of(area));

        DeliveryAreaResponse response = service.update(2L, new DeliveryAreaRequest(
                "New", "Sylhet", new BigDecimal("80.00"), new BigDecimal("2000.00"), 3, false, 9));

        assertThat(response.name()).isEqualTo("New");
        assertThat(response.district()).isEqualTo("Sylhet");
        assertThat(response.deliveryCharge()).isEqualByComparingTo("80.00");
        assertThat(response.freeShippingThreshold()).isEqualByComparingTo("2000.00");
        assertThat(response.slaDays()).isEqualTo(3);
        assertThat(response.displayOrder()).isEqualTo(9);
        verify(repository).save(area);
    }

    @Test
    void allAreasAreReturnedInOrder() {
        when(repository.findAllByOrderByDisplayOrderAsc()).thenReturn(List.of());
        assertThat(service.all()).isEmpty();
    }

    @Test
    void getRequiredThrowsWhenMissing() {
        when(repository.findById(9L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.getRequired(9L)).isInstanceOf(ResourceNotFoundException.class);
    }
}
