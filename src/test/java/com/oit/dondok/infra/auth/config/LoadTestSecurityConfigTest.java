package com.oit.dondok.infra.auth.config;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.oit.dondok.domain.auth.code.OAuth2LoginCodeStore;
import com.oit.dondok.domain.auth.service.OAuth2LoginService;
import com.oit.dondok.domain.auth.service.TokenProvider;
import com.oit.dondok.global.exception.GlobalExceptionHandler;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

@ActiveProfiles("load-test")
@TestPropertySource(
    properties = {
      "CORS_ALLOWED_ORIGINS=http://localhost:3000",
      "COOKIE_SECURE=false",
      "COOKIE_SAME_SITE=Lax",
      "JWT_ISSUER=dondok",
      "JWT_ACCESS_TOKEN_EXPIRATION=30m",
      "JWT_REFRESH_TOKEN_EXPIRATION=7d",
      "JWT_SECRET=12345678901234567890123456789012",
      "OAUTH2_SUCCESS_REDIRECT_URI=http://localhost:3000/oauth2/success",
      "OAUTH2_FAILURE_REDIRECT_URI=http://localhost:3000/oauth2/failure"
    })
@WebMvcTest(LoadTestSecurityConfigTest.FixtureController.class)
@AutoConfigureMockMvc
@Import({
  SecurityConfig.class,
  GlobalExceptionHandler.class,
  LoadTestSecurityConfigTest.FixtureController.class
})
class LoadTestSecurityConfigTest {

  @Autowired private MockMvc mockMvc;

  @MockBean private TokenProvider tokenProvider;
  @MockBean private OAuth2LoginService oAuth2LoginService;
  @MockBean private OAuth2LoginCodeStore oAuth2LoginCodeStore;

  @Test
  void loadTestPermitsOnlyDeclaredFixtureRoutesWithoutToken() throws Exception {
    mockMvc.perform(post("/api/load-test/reset")).andExpect(status().isOk());
    mockMvc.perform(post("/api/load-test/anything-else")).andExpect(status().isUnauthorized());
  }

  @RestController
  static class FixtureController {
    @PostMapping("/api/load-test/reset")
    void reset() {}

    @PostMapping("/api/load-test/anything-else")
    void anythingElse() {}
  }
}
