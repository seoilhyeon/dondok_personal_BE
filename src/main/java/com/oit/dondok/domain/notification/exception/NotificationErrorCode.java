package com.oit.dondok.domain.notification.exception;

import com.oit.dondok.global.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum NotificationErrorCode implements ErrorCode {
  INVALID_LIMIT(HttpStatus.BAD_REQUEST, "조회 개수가 올바르지 않습니다."),
  INVALID_CURSOR(HttpStatus.BAD_REQUEST, "커서가 올바르지 않습니다."),
  NOTIFICATION_NOT_FOUND(HttpStatus.NOT_FOUND, "알림을 찾을 수 없습니다."),
  INVALID_QUIET_HOURS(HttpStatus.BAD_REQUEST, "방해금지 시작 시간과 종료 시간은 함께 설정해야 합니다.");

  private final HttpStatus status;
  private final String message;

  @Override
  public String getCode() {
    return name();
  }
}
