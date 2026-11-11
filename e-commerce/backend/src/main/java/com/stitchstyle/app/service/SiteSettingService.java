package com.stitchstyle.app.service;

import com.stitchstyle.app.config.CacheEvictionService;
import com.stitchstyle.app.config.CacheNames;
import com.stitchstyle.app.dto.request.SettingRequest;
import com.stitchstyle.app.dto.response.PaymentMethodResponse;
import com.stitchstyle.app.dto.response.SiteSettingResponse;
import com.stitchstyle.app.model.entity.SiteSetting;
import com.stitchstyle.app.repository.SiteSettingRepository;
import com.stitchstyle.app.repository.UserRepository;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class SiteSettingService {

    private final SiteSettingRepository repository;
    private final UserRepository userRepository;
    private final CacheEvictionService cacheEvictionService;

    public SiteSettingService(SiteSettingRepository repository, UserRepository userRepository,
                              CacheEvictionService cacheEvictionService) {
        this.repository = repository;
        this.userRepository = userRepository;
        this.cacheEvictionService = cacheEvictionService;
    }

    @Cacheable(cacheNames = CacheNames.SITE_SETTINGS, key = "'all'")
    public Map<String, String> getAll() {
        Map<String, String> values = new LinkedHashMap<>();
        repository.findAllByOrderBySettingKeyAsc().forEach(s -> values.put(s.getSettingKey(), s.getSettingValue()));
        return java.util.Collections.unmodifiableMap(new LinkedHashMap<>(values));
    }

    public String get(String key, String fallback) {
        return getAll().getOrDefault(key, fallback);
    }

    public List<SiteSettingResponse> list() {
        return repository.findAllByOrderBySettingKeyAsc().stream()
                .map(s -> new SiteSettingResponse(s.getSettingKey(), s.getSettingValue(), s.getValueType()))
                .toList();
    }

    @Transactional
    public SiteSettingResponse update(SettingRequest request, String actorEmail) {
        SiteSetting setting = repository.findBySettingKey(request.key()).orElseGet(SiteSetting::new);
        setting.setSettingKey(request.key());
        setting.setSettingValue(request.value());
        setting.setValueType(request.valueType() == null ? "STRING" : request.valueType());
        if (actorEmail != null) userRepository.findByEmailIgnoreCase(actorEmail).ifPresent(setting::setUpdatedBy);
        repository.save(setting);
        cacheEvictionService.evictAfterCommit(CacheNames.SITE_SETTINGS, CacheNames.PAYMENT_METHODS);
        return new SiteSettingResponse(setting.getSettingKey(), setting.getSettingValue(), setting.getValueType());
    }

    @Cacheable(cacheNames = CacheNames.PAYMENT_METHODS, key = "'enabled'")
    public List<PaymentMethodResponse> paymentMethods() {
        Map<String, String> settings = getAll();
        return List.of(
                new PaymentMethodResponse("COD", "Cash on Delivery", !"false".equalsIgnoreCase(settings.getOrDefault("payment.cod.enabled", "true"))),
                new PaymentMethodResponse("BKASH", "bKash", !"false".equalsIgnoreCase(settings.getOrDefault("payment.bkash.enabled", "false"))),
                new PaymentMethodResponse("SSLCOMMERZ", "SSLCommerz", !"false".equalsIgnoreCase(settings.getOrDefault("payment.sslcommerz.enabled", "false")))
        );
    }
}
