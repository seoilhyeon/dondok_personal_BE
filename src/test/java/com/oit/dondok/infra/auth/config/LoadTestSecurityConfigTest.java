package com.oit.dondok.infra.auth.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.oit.dondok.domain.auth.code.OAuth2LoginCodeStore;
import com.oit.dondok.domain.auth.service.OAuth2LoginService;
import com.oit.dondok.domain.auth.service.TokenProvider;
import com.oit.dondok.global.exception.GlobalExceptionHandler;
import com.oit.dondok.infra.loadtest.controller.LoadTestFixtureController;
import com.oit.dondok.infra.loadtest.service.LoadTestFixtureService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

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
@WebMvcTest(LoadTestFixtureController.class)
@AutoConfigureMockMvc
@Import({SecurityConfig.class, GlobalExceptionHandler.class})
class LoadTestSecurityConfigTest {

  @Autowired private MockMvc mockMvc;

  @MockBean private TokenProvider tokenProvider;
  @MockBean private OAuth2LoginService oAuth2LoginService;
  @MockBean private OAuth2LoginCodeStore oAuth2LoginCodeStore;
  @MockBean private LoadTestFixtureService fixtureService;

  @Test
  void loadTestPermitsOnlyDeclaredFixtureRoutesWithoutToken() throws Exception {
    assertThat(SecurityConfig.LOAD_TEST_POST_PERMIT_ALL_PATTERNS)
        .containsExactly(
            "/api/load-test/seed",
            "/api/load-test/reset",
            "/api/load-test/point-charge",
            "/api/load-test/recovery",
            "/api/load-test/settlement/final",
            "/api/load-test/settlement/retry",
            "/api/load-test/runs/point/prepare",
            "/api/load-test/runs/point/tokens",
            "/api/load-test/runs/settlement/final/preflight",
            "/api/load-test/runs/settlement/final/prepare",
            "/api/load-test/runs/settlement/final/trigger");

    given(fixtureService.preparePointRun(anyString(), anyInt()))
        .willReturn(new LoadTestFixtureService.PreparedRun("load-run", 1, 1));
    given(fixtureService.pointTokens(anyString()))
        .willReturn(new LoadTestFixtureService.PointTokens("load-run", java.util.List.of()));
    given(fixtureService.preflightFinalSettlements())
        .willReturn(new LoadTestFixtureService.SettlementPreflight(0, 0, 0, 0, true));
    given(fixtureService.prepareFinalSettlementRun(anyString(), anyInt()))
        .willReturn(new LoadTestFixtureService.PreparedRun("load-run", 1, 1));
    given(fixtureService.triggerFinalSettlementRun(anyString()))
        .willReturn(
            new LoadTestFixtureService.SettlementRunResult(
                "load-run", 1, 1, 1, 1, 0, 0, 2, 2, null, null));

    mockMvc.perform(post("/api/load-test/seed")).andExpect(status().isOk());
    mockMvc.perform(post("/api/load-test/reset")).andExpect(status().isOk());
    mockMvc
        .perform(
            post("/api/load-test/point-charge")
                .param("paymentId", "payment")
                .param("orderId", "order"))
        .andExpect(status().isNoContent());
    mockMvc.perform(post("/api/load-test/recovery")).andExpect(status().isNoContent());
    mockMvc.perform(post("/api/load-test/settlement/final")).andExpect(status().isNoContent());
    mockMvc.perform(post("/api/load-test/settlement/retry")).andExpect(status().isNoContent());
    mockMvc
        .perform(
            post("/api/load-test/runs/point/prepare")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"runId\":\"load-run\",\"accounts\":1}"))
        .andExpect(status().isOk());
    mockMvc
        .perform(
            post("/api/load-test/runs/point/tokens")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"runId\":\"load-run\"}"))
        .andExpect(status().isOk());
    mockMvc
        .perform(
            post("/api/load-test/runs/settlement/final/preflight")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"runId\":\"load-run\"}"))
        .andExpect(status().isOk());
    mockMvc
        .perform(
            post("/api/load-test/runs/settlement/final/prepare")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"runId\":\"load-run\",\"settlements\":1}"))
        .andExpect(status().isOk());
    mockMvc
        .perform(
            post("/api/load-test/runs/settlement/final/trigger")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"runId\":\"load-run\"}"))
        .andExpect(status().isOk());
    mockMvc.perform(post("/api/load-test/anything-else")).andExpect(status().isUnauthorized());
  }

  @Test
  void unsafeSettlementPreparationReturnsConflictWithoutRunningTheBatch() throws Exception {
    given(fixtureService.prepareFinalSettlementRun(anyString(), anyInt()))
        .willThrow(
            new LoadTestFixtureService.UnsafeSettlementPreflightException(
                new LoadTestFixtureService.SettlementPreflight(1, 0, 0, 0, false)));

    mockMvc
        .perform(
            post("/api/load-test/runs/settlement/final/prepare")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"runId\":\"load-run\",\"settlements\":1}"))
        .andExpect(status().isConflict());
  }

  @Test
  void unsafeSettlementPreflightReturnsConflict() throws Exception {
    given(fixtureService.preflightFinalSettlements())
        .willReturn(new LoadTestFixtureService.SettlementPreflight(1, 0, 0, 0, false));

    mockMvc
        .perform(
            post("/api/load-test/runs/settlement/final/preflight")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"runId\":\"load-run\"}"))
        .andExpect(status().isConflict());
  }
}
