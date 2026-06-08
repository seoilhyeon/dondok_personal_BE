package com.oit.dondok.domain.point.controller;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.oit.dondok.domain.point.dto.response.PointBalanceResponse;
import com.oit.dondok.domain.point.dto.response.PointHistoryItemResponse;
import com.oit.dondok.domain.point.dto.response.PointHistoryListResponse;
import com.oit.dondok.domain.point.dto.response.PointReferenceMetaResponse;
import com.oit.dondok.domain.point.entity.PointReferenceType;
import com.oit.dondok.domain.point.entity.PointTransactionType;
import com.oit.dondok.domain.point.exception.PointErrorCode;
import com.oit.dondok.domain.point.service.PointQueryService;
import com.oit.dondok.global.exception.CustomException;
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
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(PointController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
class PointControllerTest {

  private static final UUID MEMBER_UUID = UUID.fromString("018f4fd2-6d7a-7a41-9f58-6d07f5c3c901");

  @Autowired private MockMvc mockMvc;

  @MockBean private PointQueryService pointQueryService;

  @BeforeEach
  void setUpAuthentication() {
    SecurityContextHolder.getContext().setAuthentication(memberAuthentication());
  }

  @AfterEach
  void clearAuthentication() {
    SecurityContextHolder.clearContext();
  }

  @Test
  void getBalanceReturnsWalletSummary() throws Exception {
    given(pointQueryService.findBalance(MEMBER_UUID))
        .willReturn(
            new PointBalanceResponse(
                10_000L,
                2_000L,
                5_000L,
                3_000L,
                8_000L,
                20_000L,
                OffsetDateTime.parse("2026-06-08T10:00:00+09:00")));

    mockMvc
        .perform(get("/api/points"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.available_balance").value(10_000))
        .andExpect(jsonPath("$.reserved_balance").value(2_000))
        .andExpect(jsonPath("$.active_locked_amount").value(5_000))
        .andExpect(jsonPath("$.settlement_pending_amount").value(3_000))
        .andExpect(jsonPath("$.locked_balance").value(8_000))
        .andExpect(jsonPath("$.total_balance").value(20_000))
        .andExpect(jsonPath("$.updated_at").value("2026-06-08T10:00:00+09:00"));

    then(pointQueryService).should().findBalance(MEMBER_UUID);
  }

  @Test
  void getHistoriesPassesFiltersAndReturnsHistoryList() throws Exception {
    String cursor = "2026-06-08T10:00:00+09:00_3001";
    given(pointQueryService.findHistories(MEMBER_UUID, 10, cursor, "deposit", "2026-06"))
        .willReturn(
            new PointHistoryListResponse(
                List.of(
                    new PointHistoryItemResponse(
                        3001L,
                        -10_000L,
                        90_000L,
                        PointTransactionType.CREW_DEPOSIT_RESERVE,
                        PointReferenceType.CREW_PARTICIPANT,
                        9001L,
                        new PointReferenceMetaResponse(42L, "새벽 기상 챌린지"),
                        OffsetDateTime.parse("2026-06-08T09:30:00+09:00"))),
                null));

    mockMvc
        .perform(
            get("/api/points/history")
                .param("limit", "10")
                .param("cursor", cursor)
                .param("type", "deposit")
                .param("month", "2026-06"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items[0].point_history_id").value(3001))
        .andExpect(jsonPath("$.items[0].amount").value(-10_000))
        .andExpect(jsonPath("$.items[0].balance_after").value(90_000))
        .andExpect(jsonPath("$.items[0].transaction_type").value("CREW_DEPOSIT_RESERVE"))
        .andExpect(jsonPath("$.items[0].reference_type").value("CREW_PARTICIPANT"))
        .andExpect(jsonPath("$.items[0].reference_id").value(9001))
        .andExpect(jsonPath("$.items[0].reference_meta.crew_id").value(42))
        .andExpect(jsonPath("$.items[0].reference_meta.crew_title").value("새벽 기상 챌린지"))
        .andExpect(jsonPath("$.items[0].created_at").value("2026-06-08T09:30:00+09:00"))
        .andExpect(jsonPath("$.next_cursor").doesNotExist());

    then(pointQueryService).should().findHistories(MEMBER_UUID, 10, cursor, "deposit", "2026-06");
  }

  @Test
  void getHistoriesReturnsErrorWhenServiceRejectsInvalidType() throws Exception {
    given(
            pointQueryService.findHistories(
                eq(MEMBER_UUID), eq(null), eq(null), eq("unknown"), eq(null)))
        .willThrow(new CustomException(PointErrorCode.INVALID_HISTORY_TYPE));

    mockMvc
        .perform(get("/api/points/history").param("type", "unknown"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("INVALID_HISTORY_TYPE"));
  }

  private UsernamePasswordAuthenticationToken memberAuthentication() {
    return new UsernamePasswordAuthenticationToken(MEMBER_UUID, null, List.of());
  }
}
