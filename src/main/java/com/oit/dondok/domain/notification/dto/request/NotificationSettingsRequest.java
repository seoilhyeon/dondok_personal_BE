package com.oit.dondok.domain.notification.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.oit.dondok.domain.notification.entity.NotificationCategory;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.util.Map;
import org.openapitools.jackson.nullable.JsonNullable;

public record NotificationSettingsRequest(
    Map<NotificationCategory, @NotNull(message = "categories 값은 null일 수 없습니다.") Boolean> categories,
    @JsonProperty("quiet_start_time")
        JsonNullable<
                @Pattern(regexp = "^([01][0-9]|2[0-3]):[0-5][0-9]$", message = "HH:mm 형식이어야 합니다.")
                String>
            quietStartTime,
    @JsonProperty("quiet_end_time")
        JsonNullable<
                @Pattern(regexp = "^([01][0-9]|2[0-3]):[0-5][0-9]$", message = "HH:mm 형식이어야 합니다.")
                String>
            quietEndTime) {

  public NotificationSettingsRequest {
    quietStartTime = undefinedIfNull(quietStartTime);
    quietEndTime = undefinedIfNull(quietEndTime);
  }

  public boolean includesQuietStartTime() {
    return quietStartTime.isPresent();
  }

  public boolean includesQuietEndTime() {
    return quietEndTime.isPresent();
  }

  public String quietStartTimeValue() {
    return quietStartTime.orElse(null);
  }

  public String quietEndTimeValue() {
    return quietEndTime.orElse(null);
  }

  private static <T> JsonNullable<T> undefinedIfNull(JsonNullable<T> value) {
    return value == null ? JsonNullable.undefined() : value;
  }
}
