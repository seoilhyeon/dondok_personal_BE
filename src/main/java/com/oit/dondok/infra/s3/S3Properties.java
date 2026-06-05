package com.oit.dondok.infra.s3;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.aws.s3")
public record S3Properties(
    String bucket, String basePrefix, String healthcheckKey, Duration healthcheckTimeout) {

  private static final String DEFAULT_HEALTHCHECK_KEY = "healthcheck/s3-readiness";
  private static final Duration DEFAULT_HEALTHCHECK_TIMEOUT = Duration.ofSeconds(3);

  public S3Properties {
    if (bucket == null || bucket.isBlank()) {
      throw new IllegalArgumentException("app.aws.s3.bucket must not be blank");
    }

    basePrefix = normalizePrefix(basePrefix);
    healthcheckKey = normalizeHealthcheckKey(healthcheckKey);
    healthcheckTimeout = normalizeHealthcheckTimeout(healthcheckTimeout);
  }

  public String resolveKey(String key) {
    String normalizedKey = normalizeKey(key, "key");
    if (basePrefix == null || basePrefix.isBlank()) {
      return normalizedKey;
    }
    return basePrefix + "/" + normalizedKey;
  }

  public String resolvedHealthcheckKey() {
    return resolveKey(healthcheckKey);
  }

  private static String normalizePrefix(String value) {
    if (value == null) {
      return "";
    }
    return trimSlashes(value.trim());
  }

  private static String normalizeKey(String value, String propertyName) {
    if (value == null) {
      throw new IllegalArgumentException(propertyName + " must not be blank");
    }

    String key = trimSlashes(value.trim());
    if (key.isEmpty()) {
      throw new IllegalArgumentException(propertyName + " must not be blank");
    }

    return key;
  }

  private static String normalizeHealthcheckKey(String value) {
    if (value == null) {
      return DEFAULT_HEALTHCHECK_KEY;
    }

    String key = trimSlashes(value.trim());
    if (key.isEmpty()) {
      return DEFAULT_HEALTHCHECK_KEY;
    }

    return normalizeKey(key, "healthcheck-key");
  }

  private static Duration normalizeHealthcheckTimeout(Duration value) {
    if (value == null) {
      return DEFAULT_HEALTHCHECK_TIMEOUT;
    }
    if (value.isZero() || value.isNegative()) {
      throw new IllegalArgumentException("app.aws.s3.healthcheck-timeout must be positive");
    }
    return value;
  }

  private static String trimSlashes(String value) {
    int start = 0;
    int end = value.length();

    while (start < end && value.charAt(start) == '/') {
      start++;
    }
    while (end > start && value.charAt(end - 1) == '/') {
      end--;
    }

    return value.substring(start, end);
  }
}
