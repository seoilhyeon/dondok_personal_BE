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
  REFRESH_TOKEN_EXPIRED(HttpStatus.UNAUTHORIZED, "만료된 refresh token입니다."),
  OAUTH_EMAIL_NOT_VERIFIED(HttpStatus.UNAUTHORIZED, "Google 이메일 인증이 필요합니다."),
  OAUTH_ACCOUNT_CONFLICT(HttpStatus.CONFLICT, "이미 다른 OAuth 계정이 연결된 회원입니다."),
  OAUTH_LOGIN_CODE_INVALID(HttpStatus.UNAUTHORIZED, "유효하지 않은 OAuth 로그인 코드입니다."),
  OAUTH_LOGIN_FAILED(HttpStatus.UNAUTHORIZED, "OAuth 로그인에 실패했습니다."),
  OAUTH_SUCCESS_REDIRECT_URI_INVALID(
      HttpStatus.INTERNAL_SERVER_ERROR, "OAuth 성공 redirect URI 설정이 올바르지 않습니다."),
  OAUTH_FAILURE_REDIRECT_URI_INVALID(
      HttpStatus.INTERNAL_SERVER_ERROR, "OAuth 실패 redirect URI 설정이 올바르지 않습니다.");

  private final HttpStatus status;
  private final String message;

  @Override
  public String getCode() {
    return name();
  }
}
