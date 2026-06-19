package com.oit.dondok.domain.notification.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.oit.dondok.domain.notification.entity.NotificationPlatform;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record RegisterDeviceRequest(
    @NotNull NotificationPlatform platform,
    @NotBlank @Size(max = 512) @JsonProperty("fcm_token") String fcmToken,
    @NotBlank @Size(max = 100) @JsonProperty("device_id") String deviceId,
    @Size(max = 50) @JsonProperty("app_version") String appVersion) {}
