package com.oit.dondok.domain.auth.exception;

import com.oit.dondok.global.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum AuthErrorCode implements ErrorCode {
  INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED, "이메일 또는 비밀번호가 올바르지 않습니다."),
  MEMBER_DEACTIVATED(HttpStatus.FORBIDDEN, "비활성화된 회원입니다."),
  ACCESS_TOKEN_INVALID(HttpStatus.UNAUTHORIZED, "유효하지 않은 access token입니다."),
  ACCESS_TOKEN_EXPIRED(HttpStatus.UNAUTHORIZED, "만료된 access token입니다."),
  REFRESH_TOKEN_INVALID(HttpStatus.UNAUTHORIZED, "유효하지 않은 refresh token입니다."),
  REFRESH_TOKEN_EXPIRED(HttpStatus.UNAUTHORIZED, "만료된 refresh token입니다.");

  private final HttpStatus status;
  private final String message;

  @Override
  public String getCode() {
    return name();
  }
}
