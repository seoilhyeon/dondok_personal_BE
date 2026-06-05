package com.oit.dondok.global.config;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.cors")
public record CorsProperties(List<String> allowedOrigins) {

  public CorsProperties {
    if (allowedOrigins == null) {
      throw new IllegalArgumentException("app.cors.allowed-origins must not be empty");
    }

    allowedOrigins =
        allowedOrigins.stream()
            .filter(origin -> origin != null)
            .map(String::trim)
            .map(origin -> origin.replaceAll("/+$", ""))
            .filter(origin -> !origin.isBlank())
            .toList();

    if (allowedOrigins.isEmpty()) {
      throw new IllegalArgumentException("app.cors.allowed-origins must not be empty");
    }
  }
}
