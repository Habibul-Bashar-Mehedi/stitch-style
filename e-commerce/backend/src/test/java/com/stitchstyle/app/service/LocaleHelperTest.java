package com.stitchstyle.app.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class LocaleHelperTest {

    @Test
    void explicitLanguageWins() {
        assertThat(LocaleHelper.resolve("bn", "en-US,en;q=0.9")).isEqualTo("bn");
        assertThat(LocaleHelper.resolve("EN", null)).isEqualTo("en");
    }

    @Test
    void acceptLanguageIsUsedWhenLanguageIsBlank() {
        assertThat(LocaleHelper.resolve(null, "bn-BD,bn;q=0.9,en;q=0.8")).isEqualTo("bn");
        assertThat(LocaleHelper.resolve("  ", "fr-FR")).isEqualTo("en");
    }

    @Test
    void defaultsToEnglish() {
        assertThat(LocaleHelper.resolve(null, null)).isEqualTo("en");
        assertThat(LocaleHelper.resolve("", "")).isEqualTo("en");
    }
}
