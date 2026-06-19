package com.oit.dondok.domain.notification.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.oit.dondok.domain.notification.entity.NotificationDevice;
import com.oit.dondok.domain.notification.entity.NotificationPlatform;
import java.time.OffsetDateTime;
import java.time.ZoneId;

public record RegisterDeviceResponse(
    @JsonProperty("device_id") String deviceId,
    NotificationPlatform platform,
    boolean enabled,
    @JsonProperty("created_at") OffsetDateTime createdAt) {

  private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

  public static RegisterDeviceResponse from(NotificationDevice device) {
    return new RegisterDeviceResponse(
        device.getDeviceId(),
        device.getPlatform(),
        device.getEnabled(),
        device.getCreatedAt().atZone(SEOUL).toOffsetDateTime());
  }
}
