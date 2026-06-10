package com.oit.dondok.domain.point.controller;

import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.oit.dondok.domain.point.dto.request.PointChargeRequest;
import com.oit.dondok.domain.point.dto.response.PointChargeResponse;
import com.oit.dondok.domain.point.entity.PointTransactionType;
import com.oit.dondok.domain.point.service.PointChargeResult;
import com.oit.dondok.domain.point.service.PointChargeService;
import com.oit.dondok.global.exception.GlobalExceptionHandler;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(PointController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
class PointChargeControllerTest {

  private static final UUID MEMBER_UUID = UUID.fromString("018f4fd2-6d7a-7a41-9f58-6d07f5c3c901");

  @Autowired private MockMvc mockMvc;

  @MockBean private PointChargeService pointChargeService;
  @MockBean private com.oit.dondok.domain.point.service.PointQueryService pointQueryService;

  @BeforeEach
  void setUpAuthentication() {
    SecurityContextHolder.getContext()
        .setAuthentication(new UsernamePasswordAuthenticationToken(MEMBER_UUID, null, List.of()));
  }

  @AfterEach
  void clearAuthentication() {
    SecurityContextHolder.clearContext();
  }

  @Test
  void chargePointsReturnsCreatedOnFirstConfirmation() throws Exception {
    PointChargeRequest request = new PointChargeRequest("payment-key", "order-id", 10_000L);
    given(pointChargeService.charge(MEMBER_UUID, request))
        .willReturn(
            new PointChargeResult(
                true,
                new PointChargeResponse(
                    3001L,
                    MEMBER_UUID,
                    10_000L,
                    25_000L,
                    PointTransactionType.POINT_CHARGE,
                    OffsetDateTime.parse("2026-06-10T16:20:05+09:00"))));

    mockMvc
        .perform(
            post("/api/points/charges")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"payment_id\":\"payment-key\",\"order_id\":\"order-id\",\"amount\":10000}"))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.point_history_id").value(3001))
        .andExpect(jsonPath("$.member_uuid").value(MEMBER_UUID.toString()))
        .andExpect(jsonPath("$.amount").value(10_000))
        .andExpect(jsonPath("$.balance_after").value(25_000))
        .andExpect(jsonPath("$.transaction_type").value("POINT_CHARGE"))
        .andExpect(jsonPath("$.created_at").value("2026-06-10T16:20:05+09:00"));

    then(pointChargeService).should().charge(MEMBER_UUID, request);
  }

  @Test
  void chargePointsReturnsOkOnIdempotentDuplicate() throws Exception {
    PointChargeRequest request = new PointChargeRequest("payment-key", "order-id", 10_000L);
    given(pointChargeService.charge(MEMBER_UUID, request))
        .willReturn(
            new PointChargeResult(
                false,
                new PointChargeResponse(
                    3001L,
                    MEMBER_UUID,
                    10_000L,
                    25_000L,
                    PointTransactionType.POINT_CHARGE,
                    OffsetDateTime.parse("2026-06-10T16:20:05+09:00"))));

    mockMvc
        .perform(
            post("/api/points/charges")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"payment_id\":\"payment-key\",\"order_id\":\"order-id\",\"amount\":10000}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.point_history_id").value(3001));
  }

  @Test
  void chargePointsRejectsInvalidAmount() throws Exception {
    mockMvc
        .perform(
            post("/api/points/charges")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"payment_id\":\"payment-key\",\"order_id\":\"order-id\",\"amount\":500}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
  }

  @Test
  void chargePointsRejectsNonStepAmountAtRequestValidation() throws Exception {
    mockMvc
        .perform(
            post("/api/points/charges")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"payment_id\":\"payment-key\",\"order_id\":\"order-id\",\"amount\":1500}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
  }
}
