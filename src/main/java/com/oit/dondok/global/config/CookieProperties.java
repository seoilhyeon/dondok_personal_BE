package com.oit.dondok.global.config;

import java.util.Set;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.cookie")
public record CookieProperties(boolean secure, String sameSite) {

  private static final Set<String> ALLOWED = Set.of("Strict", "Lax", "None");

  public CookieProperties {
    if (sameSite == null || sameSite.isEmpty()) {
      throw new IllegalArgumentException("app.cookie.same-site must not be null");
    }
    String normalized =
        Character.toUpperCase(sameSite.charAt(0)) + sameSite.substring(1).toLowerCase();
    if (!ALLOWED.contains(normalized)) {
      throw new IllegalArgumentException(
          "app.cookie.same-site must be one of Strict, Lax, None — got: " + sameSite);
    }
    if ("None".equals(normalized) && !secure) {
      throw new IllegalArgumentException(
          "app.cookie.same-site=None requires app.cookie.secure=true");
    }
    sameSite = normalized;
  }
}
