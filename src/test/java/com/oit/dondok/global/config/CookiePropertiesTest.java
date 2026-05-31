package com.oit.dondok.global.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class CookiePropertiesTest {

  @ParameterizedTest
  @ValueSource(strings = {"Strict", "Lax", "strict", "lax", "LAX"})
  void createSucceedsWithAllowedSameSiteValues(String sameSite) {
    CookieProperties props = new CookieProperties(false, sameSite);
    assertThat(props.sameSite()).isIn("Strict", "Lax");
  }

  @Test
  void createSucceedsWithNoneWhenSecureTrue() {
    CookieProperties props = new CookieProperties(true, "None");
    assertThat(props.sameSite()).isEqualTo("None");
    assertThat(props.secure()).isTrue();
  }

  @Test
  void createNormalizesSameSiteCase() {
    assertThat(new CookieProperties(false, "strict").sameSite()).isEqualTo("Strict");
    assertThat(new CookieProperties(false, "LAX").sameSite()).isEqualTo("Lax");
    assertThat(new CookieProperties(true, "NONE").sameSite()).isEqualTo("None");
  }

  @Test
  void createRejectsInvalidSameSiteValue() {
    assertThatThrownBy(() -> new CookieProperties(false, "Invalid"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Strict, Lax, None");
  }

  @Test
  void createRejectsNullSameSite() {
    assertThatThrownBy(() -> new CookieProperties(false, null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("same-site");
  }

  @Test
  void createRejectsEmptySameSite() {
    assertThatThrownBy(() -> new CookieProperties(false, ""))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("same-site");
  }

  @Test
  void createRejectsNoneWithSecureFalse() {
    assertThatThrownBy(() -> new CookieProperties(false, "None"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("secure=true");
  }
}
