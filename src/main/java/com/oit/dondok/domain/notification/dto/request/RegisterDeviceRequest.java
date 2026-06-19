package com.oit.dondok.domain.notification.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.oit.dondok.domain.notification.entity.NotificationPlatform;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record RegisterDeviceRequest(
    @NotNull NotificationPlatform platform,
    @NotBlank @JsonProperty("fcm_token") String fcmToken,
    @NotBlank @JsonProperty("device_id") String deviceId,
    @JsonProperty("app_version") String appVersion) {}
