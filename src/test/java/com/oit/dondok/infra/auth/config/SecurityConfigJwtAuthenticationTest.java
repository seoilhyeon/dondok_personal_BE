package com.oit.dondok.infra.auth.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.oit.dondok.domain.auth.exception.AuthErrorCode;
import com.oit.dondok.domain.auth.service.TokenPayload;
import com.oit.dondok.domain.auth.service.TokenProvider;
import com.oit.dondok.global.exception.CustomException;
import com.oit.dondok.global.exception.GlobalExceptionHandler;
import com.oit.dondok.infra.auth.handler.SecurityErrorHandler;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

@ActiveProfiles("prod")
@TestPropertySource(
    properties = "CORS_ALLOWED_ORIGINS=http://localhost:3000,https://dondok-fe.vercel.app")
@WebMvcTest(SecurityConfigJwtAuthenticationTest.TestController.class)
@AutoConfigureMockMvc
@Import({
  SecurityConfig.class,
  GlobalExceptionHandler.class,
  SecurityConfigJwtAuthenticationTest.TestController.class
})
class SecurityConfigJwtAuthenticationTest {

  private static final UUID MEMBER_UUID = UUID.fromString("018f4fd2-6d7a-7a41-9f58-6d07f5c3c901");

  @Autowired private MockMvc mockMvc;

  @Autowired private ObjectMapper objectMapper;

  @Autowired private SecurityErrorHandler securityErrorHandler;

  @MockBean private TokenProvider tokenProvider;

  // 공개 API는 Authorization 헤더 없이도 Security 필터 체인을 통과하는지 검증한다.
  // 공개 API는 토큰 없이도 접근 가능한지 검증한다.
  @Test
  void publicApiPermitsRequestWithoutToken() throws Exception {
    mockMvc.perform(post("/api/auth/login")).andExpect(status().isOk());
  }

  // 보호 API에 토큰 없이 접근하면 401 ErrorResponse가 반환되는지 검증한다.
  // 보호 API는 토큰이 없으면 401 응답을 반환하는지 검증한다.
  @Test
  void protectedApiRejectsRequestWithoutToken() throws Exception {
    mockMvc
        .perform(get("/api/security-test/protected"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
        .andExpect(jsonPath("$.message").exists());
  }

  // 로그아웃 API는 access token 없이 호출하면 401 응답을 반환하는지 검증한다.
  @Test
  void logoutRejectsRequestWithoutToken() throws Exception {
    mockMvc
        .perform(post("/api/auth/logout").header(HttpHeaders.ORIGIN, "http://localhost:3000"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
        .andExpect(jsonPath("$.message").exists());
  }

  // 잘못된 Access Token은 JWT 필터에서 인증 실패로 처리되고 기존 ErrorResponse 형식으로 반환된다.
  // 잘못된 access token은 기존 ErrorResponse 형식으로 거절하는지 검증한다.
  @Test
  void protectedApiRejectsInvalidToken() throws Exception {
    given(tokenProvider.parseAccessToken("invalid-token"))
        .willThrow(new CustomException(AuthErrorCode.ACCESS_TOKEN_INVALID));

    mockMvc
        .perform(
            get("/api/security-test/protected")
                .header(HttpHeaders.AUTHORIZATION, "Bearer invalid-token"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("ACCESS_TOKEN_INVALID"))
        .andExpect(jsonPath("$.message").exists());
  }

  // 유효한 Access Token이면 memberUuid가 principal로 저장되어 보호 API에 접근할 수 있다.
  // 유효한 access token이면 principal에 memberUuid가 설정되는지 검증한다.
  @Test
  void protectedApiPermitsValidTokenAndSetsAuthentication() throws Exception {
    given(tokenProvider.parseAccessToken("valid-token"))
        .willReturn(
            new TokenPayload(
                MEMBER_UUID,
                LocalDateTime.parse("2026-05-31T09:00:00"),
                LocalDateTime.parse("2026-05-31T09:30:00")));

    mockMvc
        .perform(
            get("/api/security-test/protected")
                .header(HttpHeaders.AUTHORIZATION, "Bearer valid-token"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.member_uuid").value(MEMBER_UUID.toString()));
  }

  // 권한 부족 상황은 AccessDeniedHandler가 403 ErrorResponse로 변환하는지 검증한다.
  // 접근 권한 부족 예외가 403 ErrorResponse로 변환되는지 검증한다.
  @Test
  void accessDeniedHandlerReturnsForbiddenErrorResponse() throws Exception {
    MockHttpServletResponse response = new MockHttpServletResponse();

    securityErrorHandler.handle(
        new MockHttpServletRequest(), response, new AccessDeniedException("forbidden"));

    JsonNode responseBody = objectMapper.readTree(response.getContentAsString());
    assertThat(response.getStatus()).isEqualTo(403);
    assertThat(responseBody.get("code").asText()).isEqualTo("ACCESS_DENIED");
    assertThat(responseBody.get("message").asText()).isNotBlank();
  }

  @RestController
  public static class TestController {

    @PostMapping("/api/auth/login")
    void login() {}

    @PostMapping("/api/auth/logout")
    void logout() {}

    @GetMapping("/api/security-test/protected")
    Map<String, String> protectedApi() {
      Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
      return Map.of("member_uuid", ((UUID) authentication.getPrincipal()).toString());
    }
  }
}
