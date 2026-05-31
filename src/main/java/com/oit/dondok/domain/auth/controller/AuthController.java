package com.oit.dondok.domain.auth.controller;

import com.oit.dondok.domain.auth.dto.request.LoginRequest;
import com.oit.dondok.domain.auth.dto.response.LoginMemberResponse;
import com.oit.dondok.domain.auth.dto.response.LoginResponse;
import com.oit.dondok.domain.auth.service.AuthService;
import com.oit.dondok.domain.auth.service.LoginResult;
import com.oit.dondok.global.config.CookieProperties;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/auth")
public class AuthController {

  private static final String REFRESH_TOKEN_COOKIE_NAME = "refreshToken";

  private final AuthService authService;
  private final CookieProperties cookieProperties;

  /** 회원을 로그인시키고 refresh token은 HttpOnly 쿠키로 전달한다. */
  @PostMapping("/login")
  public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
    LoginResult result = authService.login(request.email(), request.password());

    LoginResponse response =
        LoginResponse.bearer(
            result.accessToken(),
            result.accessTokenExpiresIn(),
            new LoginMemberResponse(result.memberUuid(), result.email(), result.nickname()));

    return ResponseEntity.ok()
        .header(HttpHeaders.SET_COOKIE, refreshTokenCookie(result).toString())
        .header(HttpHeaders.CACHE_CONTROL, "no-store, no-cache, must-revalidate, max-age=0")
        .header(HttpHeaders.PRAGMA, "no-cache")
        .header(HttpHeaders.EXPIRES, "0")
        .body(response);
  }

  private ResponseCookie refreshTokenCookie(LoginResult result) {
    return ResponseCookie.from(REFRESH_TOKEN_COOKIE_NAME, result.refreshToken())
        .httpOnly(true)
        .secure(cookieProperties.secure())
        .sameSite(cookieProperties.sameSite())
        .path("/")
        .maxAge(result.refreshTokenMaxAge())
        .build();
  }
}
