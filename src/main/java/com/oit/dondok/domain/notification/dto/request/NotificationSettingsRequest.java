package com.oit.dondok.domain.notification.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.oit.dondok.domain.notification.entity.NotificationCategory;
import jakarta.validation.constraints.Pattern;
import java.util.Map;

public record NotificationSettingsRequest(
    Map<NotificationCategory, Boolean> categories,
    @Pattern(regexp = "^([01][0-9]|2[0-3]):[0-5][0-9]$", message = "HH:mm 형식이어야 합니다.")
        @JsonProperty("quiet_start_time")
        String quietStartTime,
    @Pattern(regexp = "^([01][0-9]|2[0-3]):[0-5][0-9]$", message = "HH:mm 형식이어야 합니다.")
        @JsonProperty("quiet_end_time")
        String quietEndTime) {}
