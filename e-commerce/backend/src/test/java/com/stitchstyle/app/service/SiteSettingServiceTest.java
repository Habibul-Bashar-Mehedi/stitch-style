package com.stitchstyle.app.service;

import com.stitchstyle.app.config.CacheEvictionService;
import com.stitchstyle.app.config.CacheNames;
import com.stitchstyle.app.dto.request.SettingRequest;
import com.stitchstyle.app.model.entity.Role;
import com.stitchstyle.app.model.entity.SiteSetting;
import com.stitchstyle.app.model.entity.User;
import com.stitchstyle.app.repository.SiteSettingRepository;
import com.stitchstyle.app.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SiteSettingServiceTest {

    private SiteSettingRepository repository;
    private UserRepository userRepository;
    private CacheEvictionService cacheEvictionService;
    private SiteSettingService service;

    @BeforeEach
    void setUp() {
        repository = mock(SiteSettingRepository.class);
        userRepository = mock(UserRepository.class);
        cacheEvictionService = mock(CacheEvictionService.class);
        service = new SiteSettingService(repository, userRepository, cacheEvictionService);
    }

    @Test
    void getAllOrdersKeysAlphabeticallyAndIsUnmodifiable() {
        when(repository.findAllByOrderBySettingKeyAsc()).thenReturn(List.of(
                site("business.name", "Stitch & Style"),
                site("business.phone", "017")));

        var all = service.getAll();

        assertThat(all).containsExactly(
                java.util.Map.entry("business.name", "Stitch & Style"),
                java.util.Map.entry("business.phone", "017"));
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> all.put("x", "y"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void getFallsBackWhenKeyIsMissing() {
        when(repository.findAllByOrderBySettingKeyAsc()).thenReturn(List.of());

        assertThat(service.get("missing", "fallback")).isEqualTo("fallback");
    }

    @Test
    void paymentMethodsRespectSettingFlagsAndDefaults() {
        when(repository.findAllByOrderBySettingKeyAsc()).thenReturn(List.of(
                site("payment.cod.enabled", "false"),
                site("payment.bkash.enabled", "TRUE"),
                site("payment.sslcommerz.enabled", "false")));

        var methods = service.paymentMethods();

        assertThat(methods).hasSize(3);
        assertThat(methods).filteredOn(method -> method.code().equals("COD")).singleElement()
                .extracting(method -> method.enabled()).isEqualTo(false);
        assertThat(methods).filteredOn(method -> method.code().equals("BKASH")).singleElement()
                .extracting(method -> method.enabled()).isEqualTo(true);
    }

    @Test
    void paymentMethodsDefaultToCodOnly() {
        when(repository.findAllByOrderBySettingKeyAsc()).thenReturn(List.of());

        assertThat(service.paymentMethods())
                .filteredOn(method -> method.enabled())
                .extracting(method -> method.code())
                .containsExactly("COD");
    }

    @Test
    void updateCreatesOrReplacesSettingAndEvicts() {
        User actor = User.builder().id(1L).email("admin@x.com").fullName("Admin")
                .role(Role.ADMIN).active(true).build();
        when(repository.findBySettingKey("brand.name")).thenReturn(Optional.empty());
        when(userRepository.findByEmailIgnoreCase("admin@x.com")).thenReturn(Optional.of(actor));
        when(repository.save(any(SiteSetting.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var response = service.update(new SettingRequest("brand.name", "New", null), "admin@x.com");

        assertThat(response.key()).isEqualTo("brand.name");
        assertThat(response.value()).isEqualTo("New");
        assertThat(response.valueType()).isEqualTo("STRING");
        verify(cacheEvictionService).evictAfterCommit(CacheNames.SITE_SETTINGS, CacheNames.PAYMENT_METHODS);
    }

    @Test
    void updateKeepsExistingRowAndAttachesActor() {
        SiteSetting setting = site("brand.name", "Old");
        User actor = User.builder().id(1L).email("admin@x.com").fullName("Admin")
                .role(Role.ADMIN).active(true).build();
        when(repository.findBySettingKey("brand.name")).thenReturn(Optional.of(setting));
        when(userRepository.findByEmailIgnoreCase("admin@x.com")).thenReturn(Optional.of(actor));

        service.update(new SettingRequest("brand.name", "New", "NUMBER"), "admin@x.com");

        assertThat(setting.getSettingValue()).isEqualTo("New");
        assertThat(setting.getValueType()).isEqualTo("NUMBER");
        assertThat(setting.getUpdatedBy()).isSameAs(actor);
    }

    @Test
    void listMapsKeysAndTypes() {
        when(repository.findAllByOrderBySettingKeyAsc()).thenReturn(List.of(site("a", "b")));

        assertThat(service.list()).singleElement()
                .satisfies(response -> {
                    assertThat(response.key()).isEqualTo("a");
                    assertThat(response.valueType()).isEqualTo("STRING");
                });
    }

    private static SiteSetting site(String key, String value) {
        return SiteSetting.builder().settingKey(key).settingValue(value).valueType("STRING").build();
    }
}
