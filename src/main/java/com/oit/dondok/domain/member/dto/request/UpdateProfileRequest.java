package com.oit.dondok.domain.member.dto.request;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.openapitools.jackson.nullable.JsonNullable;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record UpdateProfileRequest(
    JsonNullable<
            @NotBlank(message = "nickname은 필수입니다.")
            @Pattern(regexp = "\\S.*\\S|\\S", message = "nickname 앞뒤에는 공백을 사용할 수 없습니다.")
            @Size(min = 2, max = 10, message = "nickname은 2자 이상 10자 이하여야 합니다.") String>
        nickname,
    JsonNullable<String> profileImageS3Key,
    JsonNullable<@Size(max = 100, message = "status_message는 100자 이하여야 합니다.") String>
        statusMessage) {

  public UpdateProfileRequest {
    nickname = undefinedIfNull(nickname);
    profileImageS3Key = undefinedIfNull(profileImageS3Key);
    statusMessage = undefinedIfNull(statusMessage);
  }

  public boolean hasAnyIncludedField() {
    return includesNickname() || includesProfileImageS3Key() || includesStatusMessage();
  }

  public boolean includesNickname() {
    return nickname.isPresent();
  }

  public boolean includesProfileImageS3Key() {
    return profileImageS3Key.isPresent();
  }

  public boolean includesStatusMessage() {
    return statusMessage.isPresent();
  }

  public String nicknameValue() {
    return nickname.orElse(null);
  }

  public String profileImageS3KeyValue() {
    return profileImageS3Key.orElse(null);
  }

  public String statusMessageValue() {
    return statusMessage.orElse(null);
  }

  private static <T> JsonNullable<T> undefinedIfNull(JsonNullable<T> value) {
    return value == null ? JsonNullable.undefined() : value;
  }
}
