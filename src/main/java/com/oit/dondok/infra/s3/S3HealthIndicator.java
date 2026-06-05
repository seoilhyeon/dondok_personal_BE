package com.oit.dondok.infra.s3;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;

@Component("s3")
@RequiredArgsConstructor
public class S3HealthIndicator implements HealthIndicator {

  private final S3Client s3Client;
  private final S3Properties s3Properties;

  @Override
  public Health health() {
    try {
      s3Client.headObject(
          HeadObjectRequest.builder()
              .bucket(s3Properties.bucket())
              .key(s3Properties.resolvedHealthcheckKey())
              .overrideConfiguration(
                  config ->
                      config
                          .apiCallTimeout(s3Properties.healthcheckTimeout())
                          .apiCallAttemptTimeout(s3Properties.healthcheckTimeout()))
              .build());

      return Health.up().build();
    } catch (Exception exception) {
      return Health.down().build();
    }
  }
}
