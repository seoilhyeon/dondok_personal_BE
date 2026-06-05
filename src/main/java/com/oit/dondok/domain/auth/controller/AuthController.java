package com.oit.dondok.domain.auth.controller;

import com.oit.dondok.domain.auth.dto.request.LoginRequest;
import com.oit.dondok.domain.auth.dto.response.LoginMemberResponse;
import com.oit.dondok.domain.auth.dto.response.LoginResponse;
import com.oit.dondok.domain.auth.dto.response.RefreshTokenResponse;
import com.oit.dondok.domain.auth.service.AuthService;
import com.oit.dondok.domain.auth.service.LoginResult;
import com.oit.dondok.domain.auth.service.RefreshTokenResult;
import com.oit.dondok.global.config.CookieProperties;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/auth")
@Tag(name = "인증", description = "인증 관련 API")
public class AuthController {

  private static final String REFRESH_TOKEN_COOKIE_NAME = "refreshToken";

  private final AuthService authService;
  private final CookieProperties cookieProperties;

  /** 회원을 로그인시키고 refresh token은 HttpOnly 쿠키로 전달한다. */
  @Operation(summary = "로그인", description = "회원 이메일과 비밀번호로 로그인하고 토큰을 발급합니다.")
  @PostMapping("/login")
  public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
    LoginResult result = authService.login(request.email(), request.password());

    LoginResponse response =
        LoginResponse.bearer(
            result.accessToken(),
            result.accessTokenExpiresIn(),
            new LoginMemberResponse(result.memberUuid(), result.email(), result.nickname()));

    return ResponseEntity.ok()
        .header(
            HttpHeaders.SET_COOKIE,
            refreshTokenCookie(result.refreshToken(), result.refreshTokenMaxAge()).toString())
        .header(HttpHeaders.CACHE_CONTROL, "no-store, no-cache, must-revalidate, max-age=0")
        .header(HttpHeaders.PRAGMA, "no-cache")
        .header(HttpHeaders.EXPIRES, "0")
        .body(response);
  }

  /** refresh token cookie를 검증하고 새 access token과 rotated refresh token을 발급한다. */
  @Operation(summary = "토큰 재발급", description = "리프레시 토큰 쿠키를 검증하고 새로운 액세스 토큰을 발급합니다.")
  @PostMapping("/refresh")
  public ResponseEntity<RefreshTokenResponse> refresh(
      @CookieValue(name = REFRESH_TOKEN_COOKIE_NAME, required = false) String refreshToken) {
    RefreshTokenResult result = authService.refresh(refreshToken);

    return ResponseEntity.ok()
        .header(
            HttpHeaders.SET_COOKIE,
            refreshTokenCookie(result.refreshToken(), result.refreshTokenMaxAge()).toString())
        .header(HttpHeaders.CACHE_CONTROL, "no-store, no-cache, must-revalidate, max-age=0")
        .header(HttpHeaders.PRAGMA, "no-cache")
        .header(HttpHeaders.EXPIRES, "0")
        .body(new RefreshTokenResponse(result.accessToken()));
  }

  @Operation(summary = "로그아웃", description = "현재 회원의 리프레시 토큰을 만료하고 쿠키를 삭제합니다.")
  @PostMapping("/logout")
  public ResponseEntity<Void> logout(
      @AuthenticationPrincipal UUID memberUuid,
      @CookieValue(name = REFRESH_TOKEN_COOKIE_NAME, required = false) String refreshToken) {
    authService.logout(memberUuid, refreshToken);

    return ResponseEntity.noContent()
        .header(HttpHeaders.SET_COOKIE, deleteRefreshTokenCookie().toString())
        .build();
  }

  private ResponseCookie refreshTokenCookie(String refreshToken, long maxAge) {
    return ResponseCookie.from(REFRESH_TOKEN_COOKIE_NAME, refreshToken)
        .httpOnly(true)
        .secure(cookieProperties.secure())
        .sameSite(cookieProperties.sameSite())
        .path("/")
        .maxAge(maxAge)
        .build();
  }

  private ResponseCookie deleteRefreshTokenCookie() {
    return ResponseCookie.from(REFRESH_TOKEN_COOKIE_NAME, "")
        .httpOnly(true)
        .secure(cookieProperties.secure())
        .sameSite(cookieProperties.sameSite())
        .path("/")
        .maxAge(0)
        .build();
  }
}
