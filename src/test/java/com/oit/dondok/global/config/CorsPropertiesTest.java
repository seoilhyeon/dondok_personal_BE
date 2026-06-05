package com.oit.dondok.global.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Test;

class CorsPropertiesTest {

  @Test
  void createTrimsAllowedOrigins() {
    CorsProperties properties =
        new CorsProperties(List.of(" http://localhost:3000 ", "https://dondok-fe.vercel.app"));

    assertThat(properties.allowedOrigins())
        .containsExactly("http://localhost:3000", "https://dondok-fe.vercel.app");
  }

  @Test
  void createRejectsNullAllowedOrigins() {
    assertThatThrownBy(() -> new CorsProperties(null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("allowed-origins");
  }

  @Test
  void createRejectsEmptyAllowedOrigins() {
    assertThatThrownBy(() -> new CorsProperties(List.of()))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("allowed-origins");
  }

  @Test
  void createRejectsBlankAllowedOrigins() {
    assertThatThrownBy(() -> new CorsProperties(List.of(" ", "")))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("allowed-origins");
  }
}
