package com.oit.dondok.infra.payment;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.toss-payments")
public record TossPaymentsProperties(
    String baseUrl, String secretKey, Duration connectTimeout, Duration readTimeout) {

  public TossPaymentsProperties {
    if (baseUrl == null || baseUrl.isBlank()) {
      baseUrl = "https://api.tosspayments.com";
    }
    if (connectTimeout == null) {
      connectTimeout = Duration.ofSeconds(3);
    }
    if (readTimeout == null) {
      readTimeout = Duration.ofSeconds(5);
    }
  }
}
