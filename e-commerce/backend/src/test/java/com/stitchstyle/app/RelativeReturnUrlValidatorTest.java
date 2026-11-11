package com.stitchstyle.app;

import com.stitchstyle.app.security.RelativeReturnUrlValidator;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RelativeReturnUrlValidatorTest {

    @Test
    void acceptsOnlyRelativeApplicationPaths() {
        assertThat(RelativeReturnUrlValidator.isSafe("/cart")).isTrue();
        assertThat(RelativeReturnUrlValidator.isSafe("/products?page=2")).isTrue();
        assertThat(RelativeReturnUrlValidator.isSafe(" /orders ")).isTrue();
    }

    @Test
    void rejectsExternalAndProtocolRelativeTargets() {
        assertThat(RelativeReturnUrlValidator.isSafe("https://evil.example")).isFalse();
        assertThat(RelativeReturnUrlValidator.isSafe("//evil.example")).isFalse();
        assertThat(RelativeReturnUrlValidator.isSafe("\\\\evil.example")).isFalse();
        assertThat(RelativeReturnUrlValidator.isSafe("javascript:alert(1)")).isFalse();
        assertThat(RelativeReturnUrlValidator.isSafe("JavaScript:alert(1)")).isFalse();
        assertThat(RelativeReturnUrlValidator.isSafe("data:text/html,<script>alert(1)</script>")).isFalse();
    }

    @Test
    void rejectsNulAndOtherControlCharacters() {
        // %00 is decoded to U+0000 before this validator runs.
        assertThat(RelativeReturnUrlValidator.isSafe("/cart\u0000")).isFalse();
        assertThat(RelativeReturnUrlValidator.isSafe("/cart\r\nSet-Cookie: x=y")).isFalse();
        assertThat(RelativeReturnUrlValidator.isSafe("/cart\tfoo")).isFalse();
    }

    @Test
    void fallsBackWhenInvalid() {
        assertThat(RelativeReturnUrlValidator.safeOrDefault("/cart", "/")).isEqualTo("/cart");
        assertThat(RelativeReturnUrlValidator.safeOrDefault("/cart\u0000", "/")).isEqualTo("/");
    }
}
