package com.stitchstyle.app;

import com.stitchstyle.app.service.ProductService;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class ProductServiceCacheKeyTest {

    @Test
    void everyResultChangingParameterIsPartOfTheKey() {
        String base = ProductService.listKey("men", "panjabi", new BigDecimal("1000"), new BigDecimal("2000"),
                "M", 0, 20, "newest", "en");
        assertThat(base).isEqualTo(ProductService.listKey("men", "panjabi", new BigDecimal("1000.00"), new BigDecimal("2000.00"),
                "M", 0, 20, "newest", "en"));
        assertThat(base).isNotEqualTo(ProductService.listKey("women", "panjabi", new BigDecimal("1000"), new BigDecimal("2000"),
                "M", 0, 20, "newest", "en"));
        assertThat(base).isNotEqualTo(ProductService.listKey("men", "sharee", new BigDecimal("1000"), new BigDecimal("2000"),
                "M", 0, 20, "newest", "en"));
        assertThat(base).isNotEqualTo(ProductService.listKey("men", "panjabi", new BigDecimal("1100"), new BigDecimal("2000"),
                "M", 0, 20, "newest", "en"));
        assertThat(base).isNotEqualTo(ProductService.listKey("men", "panjabi", new BigDecimal("1000"), new BigDecimal("2100"),
                "M", 0, 20, "newest", "en"));
        assertThat(base).isNotEqualTo(ProductService.listKey("men", "panjabi", new BigDecimal("1000"), new BigDecimal("2000"),
                "L", 0, 20, "newest", "en"));
        assertThat(base).isNotEqualTo(ProductService.listKey("men", "panjabi", new BigDecimal("1000"), new BigDecimal("2000"),
                "M", 1, 20, "newest", "en"));
        assertThat(base).isNotEqualTo(ProductService.listKey("men", "panjabi", new BigDecimal("1000"), new BigDecimal("2000"),
                "M", 0, 40, "newest", "en"));
        assertThat(base).isNotEqualTo(ProductService.listKey("men", "panjabi", new BigDecimal("1000"), new BigDecimal("2000"),
                "M", 0, 20, "price_asc", "en"));
        assertThat(base).isNotEqualTo(ProductService.listKey("men", "panjabi", new BigDecimal("1000"), new BigDecimal("2000"),
                "M", 0, 20, "newest", "bn"));
    }
}
