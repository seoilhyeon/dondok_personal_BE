package com.oit.dondok.domain.auth.controller;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.oit.dondok.domain.auth.dto.request.LoginRequest;
import com.oit.dondok.domain.auth.exception.AuthErrorCode;
import com.oit.dondok.domain.auth.service.AuthService;
import com.oit.dondok.domain.auth.service.LoginResult;
import com.oit.dondok.domain.auth.service.RefreshTokenResult;
import com.oit.dondok.global.config.CookieProperties;
import com.oit.dondok.global.exception.CustomException;
import com.oit.dondok.global.exception.GlobalExceptionHandler;
import jakarta.servlet.http.Cookie;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(AuthController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
class AuthControllerTest {

  @Autowired private MockMvc mockMvc;

  @Autowired private ObjectMapper objectMapper;

  @MockBean private AuthService authService;

  @MockBean private CookieProperties cookieProperties;

  @Test
  void loginSuccess() throws Exception {
    LoginRequest request = new LoginRequest("user@example.com", "password1234");
    UUID memberUuid = UUID.fromString("018f4fd2-6d7a-7a41-9f58-6d07f5c3c901");

    given(cookieProperties.secure()).willReturn(true);
    given(cookieProperties.sameSite()).willReturn("Lax");
    given(authService.login(anyString(), anyString()))
        .willReturn(
            new LoginResult(
                "access-token",
                "refresh-token",
                1800,
                604800,
                memberUuid,
                "user@example.com",
                "tester"));

    mockMvc
        .perform(
            post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.access_token").value("access-token"))
        .andExpect(jsonPath("$.token_type").value("Bearer"))
        .andExpect(jsonPath("$.expires_in").value(1800))
        .andExpect(jsonPath("$.member.member_uuid").value(memberUuid.toString()))
        .andExpect(jsonPath("$.member.email").value("user@example.com"))
        .andExpect(jsonPath("$.member.nickname").value("tester"))
        .andExpect(cookie().value("refreshToken", "refresh-token"))
        .andExpect(cookie().httpOnly("refreshToken", true))
        .andExpect(cookie().secure("refreshToken", true))
        .andExpect(
            header()
                .string(
                    HttpHeaders.CACHE_CONTROL, "no-store, no-cache, must-revalidate, max-age=0"))
        .andExpect(header().string(HttpHeaders.PRAGMA, "no-cache"))
        .andExpect(header().string(HttpHeaders.EXPIRES, "0"))
        .andExpect(
            header()
                .string(
                    HttpHeaders.SET_COOKIE, org.hamcrest.Matchers.containsString("SameSite=Lax")));
  }

  @Test
  void loginUsesCookieProperties() throws Exception {
    LoginRequest request = new LoginRequest("user@example.com", "password1234");
    UUID memberUuid = UUID.fromString("018f4fd2-6d7a-7a41-9f58-6d07f5c3c901");

    given(cookieProperties.secure()).willReturn(false);
    given(cookieProperties.sameSite()).willReturn("Strict");
    given(authService.login(anyString(), anyString()))
        .willReturn(
            new LoginResult(
                "access-token",
                "refresh-token",
                1800,
                604800,
                memberUuid,
                "user@example.com",
                "tester"));

    mockMvc
        .perform(
            post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isOk())
        .andExpect(cookie().secure("refreshToken", false))
        .andExpect(
            header()
                .string(
                    HttpHeaders.SET_COOKIE,
                    org.hamcrest.Matchers.containsString("SameSite=Strict")));
  }

  @Test
  void loginRejectsInvalidEmail() throws Exception {
    LoginRequest request = new LoginRequest("user@localhost", "password1234");

    mockMvc
        .perform(
            post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
  }

  @Test
  void refreshSuccess() throws Exception {
    given(cookieProperties.secure()).willReturn(true);
    given(cookieProperties.sameSite()).willReturn("Lax");
    given(authService.refresh("refresh-token"))
        .willReturn(new RefreshTokenResult("new-access-token", "new-refresh-token", 604800));

    mockMvc
        .perform(post("/api/auth/refresh").cookie(new Cookie("refreshToken", "refresh-token")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.access_token").value("new-access-token"))
        .andExpect(cookie().value("refreshToken", "new-refresh-token"))
        .andExpect(cookie().httpOnly("refreshToken", true))
        .andExpect(cookie().secure("refreshToken", true))
        .andExpect(
            header()
                .string(
                    HttpHeaders.CACHE_CONTROL, "no-store, no-cache, must-revalidate, max-age=0"))
        .andExpect(header().string(HttpHeaders.PRAGMA, "no-cache"))
        .andExpect(header().string(HttpHeaders.EXPIRES, "0"))
        .andExpect(
            header()
                .string(
                    HttpHeaders.SET_COOKIE, org.hamcrest.Matchers.containsString("SameSite=Lax")));
  }

  @Test
  void refreshRejectsMissingCookie() throws Exception {
    given(authService.refresh(null))
        .willThrow(new CustomException(AuthErrorCode.REFRESH_TOKEN_INVALID));

    mockMvc
        .perform(post("/api/auth/refresh"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("REFRESH_TOKEN_INVALID"));
  }

  @Test
  void refreshRejectsExpiredToken() throws Exception {
    given(authService.refresh("expired-refresh-token"))
        .willThrow(new CustomException(AuthErrorCode.REFRESH_TOKEN_EXPIRED));

    mockMvc
        .perform(
            post("/api/auth/refresh").cookie(new Cookie("refreshToken", "expired-refresh-token")))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("REFRESH_TOKEN_EXPIRED"));
  }
}
