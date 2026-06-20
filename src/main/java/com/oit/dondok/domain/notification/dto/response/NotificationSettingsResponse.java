package com.oit.dondok.domain.notification.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.oit.dondok.domain.notification.entity.NotificationCategory;
import com.oit.dondok.domain.notification.entity.NotificationSettings;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;

public record NotificationSettingsResponse(
    Map<NotificationCategory, Boolean> categories,
    @JsonProperty("quiet_start_time") String quietStartTime,
    @JsonProperty("quiet_end_time") String quietEndTime) {

  private static final DateTimeFormatter HH_MM = DateTimeFormatter.ofPattern("HH:mm");

  public static NotificationSettingsResponse from(NotificationSettings settings) {
    return new NotificationSettingsResponse(
        settings.categoryMap(),
        formatTime(settings.getQuietStartTime()),
        formatTime(settings.getQuietEndTime()));
  }

  public static NotificationSettingsResponse defaults() {
    return new NotificationSettingsResponse(
        Map.of(
            NotificationCategory.EMOJI_REACTION, true,
            NotificationCategory.HOST_VERIFICATION, true,
            NotificationCategory.DEADLINE_APPROACHING, true,
            NotificationCategory.DAILY_RESULT, true,
            NotificationCategory.SETTLEMENT, true,
            NotificationCategory.CREW_DISBANDED, true,
            NotificationCategory.CREW_NEWS, true),
        null,
        null);
  }

  private static String formatTime(LocalTime time) {
    return time == null ? null : time.format(HH_MM);
  }
}
