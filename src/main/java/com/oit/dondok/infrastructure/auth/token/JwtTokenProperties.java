package com.oit.dondok.infrastructure.auth.token;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "app.jwt")
public record JwtTokenProperties(
    @NotBlank String issuer,
    @NotNull Duration accessTokenExpiration,
    @NotNull Duration refreshTokenExpiration,
    @NotBlank String secret) {

  private static final int MIN_SECRET_BYTES = 32;

  public JwtTokenProperties {
    validatePositive(accessTokenExpiration, "accessTokenExpiration");
    validatePositive(refreshTokenExpiration, "refreshTokenExpiration");
    validateSecret(secret);
  }

  private static void validatePositive(Duration duration, String propertyName) {
    if (duration == null) {
      return;
    }

    if (duration.isZero() || duration.isNegative()) {
      throw new IllegalArgumentException(propertyName + " must be positive.");
    }
  }

  private static void validateSecret(String secret) {
    if (secret == null || secret.isBlank()) {
      return;
    }

    if (secret.getBytes(StandardCharsets.UTF_8).length < MIN_SECRET_BYTES) {
      throw new IllegalArgumentException("secret must be at least 32 bytes.");
    }
  }
}
