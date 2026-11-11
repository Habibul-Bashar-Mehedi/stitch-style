package com.stitchstyle.app.service;

import com.stitchstyle.app.config.CacheEvictionService;
import com.stitchstyle.app.config.CacheNames;
import com.stitchstyle.app.dto.request.DeliveryAreaRequest;
import com.stitchstyle.app.dto.response.DeliveryAreaResponse;
import com.stitchstyle.app.exception.ResourceNotFoundException;
import com.stitchstyle.app.model.entity.DeliveryArea;
import com.stitchstyle.app.repository.DeliveryAreaRepository;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Service
public class DeliveryAreaService {

    private final DeliveryAreaRepository repository;
    private final CacheEvictionService cacheEvictionService;

    public DeliveryAreaService(DeliveryAreaRepository repository, CacheEvictionService cacheEvictionService) {
        this.repository = repository;
        this.cacheEvictionService = cacheEvictionService;
    }

    @Cacheable(cacheNames = CacheNames.DELIVERY_AREAS, key = "'active'")
    public List<DeliveryAreaResponse> active() {
        return repository.findByActiveTrueOrderByDisplayOrderAsc().stream().map(this::toResponse).toList();
    }

    public List<DeliveryAreaResponse> all() {
        return repository.findAllByOrderByDisplayOrderAsc().stream().map(this::toResponse).toList();
    }

    public DeliveryArea getRequired(Long id) {
        return repository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Delivery area not found: " + id));
    }

    public BigDecimal deliveryCharge(DeliveryArea area, BigDecimal subtotal) {
        if (area.getFreeShippingThreshold() != null && subtotal.compareTo(area.getFreeShippingThreshold()) >= 0) {
            return BigDecimal.ZERO;
        }
        return area.getDeliveryCharge() == null ? BigDecimal.ZERO : area.getDeliveryCharge();
    }

    @Transactional
    public DeliveryAreaResponse create(DeliveryAreaRequest request) {
        DeliveryArea area = new DeliveryArea();
        apply(area, request);
        repository.save(area);
        cacheEvictionService.evictAfterCommit(CacheNames.DELIVERY_AREAS);
        return toResponse(area);
    }

    @Transactional
    public DeliveryAreaResponse update(Long id, DeliveryAreaRequest request) {
        DeliveryArea area = getRequired(id);
        apply(area, request);
        repository.save(area);
        cacheEvictionService.evictAfterCommit(CacheNames.DELIVERY_AREAS);
        return toResponse(area);
    }

    @Transactional
    public void delete(Long id) {
        repository.delete(getRequired(id));
        cacheEvictionService.evictAfterCommit(CacheNames.DELIVERY_AREAS);
    }

    private void apply(DeliveryArea area, DeliveryAreaRequest request) {
        area.setName(request.name());
        area.setDistrict(request.district());
        area.setDeliveryCharge(request.deliveryCharge() == null ? BigDecimal.ZERO : request.deliveryCharge());
        area.setFreeShippingThreshold(request.freeShippingThreshold());
        area.setSlaDays(request.slaDays());
        area.setActive(request.active() == null || request.active());
        area.setDisplayOrder(request.displayOrder() == null ? 0 : request.displayOrder());
    }

    private DeliveryAreaResponse toResponse(DeliveryArea area) {
        return new DeliveryAreaResponse(area.getId(), area.getName(), area.getDistrict(), area.getDeliveryCharge(),
                area.getFreeShippingThreshold(), area.getSlaDays(), area.getDisplayOrder());
    }
}
