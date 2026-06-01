package com.oit.dondok.infra.image.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import java.time.OffsetDateTime;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record PresignedUrlResponse(String uploadUrl, String s3Key, OffsetDateTime expiresAt) {

  public static PresignedUrlResponse of(String uploadUrl, String s3Key, OffsetDateTime expiresAt) {
    return new PresignedUrlResponse(uploadUrl, s3Key, expiresAt);
  }
}
