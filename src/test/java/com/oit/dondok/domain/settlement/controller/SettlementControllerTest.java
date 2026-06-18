package com.oit.dondok.domain.settlement.controller;

import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.oit.dondok.domain.crew.exception.CrewErrorCode;
import com.oit.dondok.domain.settlement.dto.response.SettlementDetailResponse;
import com.oit.dondok.domain.settlement.dto.response.SettlementItemDetailResponse;
import com.oit.dondok.domain.settlement.dto.response.SettlementMeResponse;
import com.oit.dondok.domain.settlement.dto.response.SettlementSummaryResponse;
import com.oit.dondok.domain.settlement.entity.ParticipantStatusSnapshot;
import com.oit.dondok.domain.settlement.service.SettlementQueryService;
import com.oit.dondok.global.exception.CustomException;
import com.oit.dondok.global.exception.GlobalExceptionHandler;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(SettlementController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
class SettlementControllerTest {

  private static final UUID MEMBER_UUID = UUID.fromString("018f4fd2-6d7a-7a41-9f58-6d07f5c3c901");

  @Autowired private MockMvc mockMvc;

  @MockBean private SettlementQueryService settlementQueryService;

  private final ObjectMapper objectMapper = new ObjectMapper();

  @AfterEach
  void clearSecurityContext() {
    SecurityContextHolder.clearContext();
  }

  // ======================== GET /api/crews/{crewId}/settlement ========================

  @Test
  void getCrewSettlementReturnsProjectionNoneForMissingRow() throws Exception {
    given(settlementQueryService.getSettlementSummary(42L, MEMBER_UUID))
        .willReturn(new SettlementSummaryResponse(42L, null, "NONE", 0, null, null, null, null));

    authenticate();

    mockMvc
        .perform(get("/api/crews/{crewId}/settlement", 42L))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.crew_id").value(42))
        .andExpect(jsonPath("$.settlement_id").value((Object) null))
        .andExpect(jsonPath("$.status").value("NONE"))
        .andExpect(jsonPath("$.retry_count").value(0))
        .andExpect(jsonPath("$.failure_code").value((Object) null))
        .andExpect(jsonPath("$.failure_message").value((Object) null))
        .andExpect(jsonPath("$.started_at").value((Object) null))
        .andExpect(jsonPath("$.finished_at").value((Object) null));

    then(settlementQueryService).should().getSettlementSummary(42L, MEMBER_UUID);
  }

  @Test
  void getCrewSettlementReturnsPersistedRow() throws Exception {
    OffsetDateTime startedAt = OffsetDateTime.parse("2026-06-01T13:12:10+09:00");
    OffsetDateTime finishedAt = OffsetDateTime.parse("2026-06-01T13:12:18+09:00");

    given(settlementQueryService.getSettlementSummary(42L, MEMBER_UUID))
        .willReturn(
            new SettlementSummaryResponse(
                42L, 501L, "RUNNING", 1, "INPUT_LOAD_FAILED", "temporary", startedAt, finishedAt));

    authenticate();

    mockMvc
        .perform(get("/api/crews/{crewId}/settlement", 42L))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.crew_id").value(42))
        .andExpect(jsonPath("$.settlement_id").value(501))
        .andExpect(jsonPath("$.status").value("RUNNING"))
        .andExpect(jsonPath("$.retry_count").value(1))
        .andExpect(jsonPath("$.failure_code").value("INPUT_LOAD_FAILED"))
        .andExpect(jsonPath("$.failure_message").value("temporary"))
        .andExpect(jsonPath("$.started_at").value("2026-06-01T13:12:10+09:00"))
        .andExpect(jsonPath("$.finished_at").value("2026-06-01T13:12:18+09:00"));
  }

  @Test
  void getCrewSettlementReturns404WhenCrewNotFound() throws Exception {
    given(settlementQueryService.getSettlementSummary(42L, MEMBER_UUID))
        .willThrow(new CustomException(CrewErrorCode.CREW_NOT_FOUND));

    authenticate();

    mockMvc
        .perform(get("/api/crews/{crewId}/settlement", 42L))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("CREW_NOT_FOUND"));
  }

  @Test
  void getCrewSettlementReturns403WhenAccessDenied() throws Exception {
    given(settlementQueryService.getSettlementSummary(42L, MEMBER_UUID))
        .willThrow(new CustomException(CrewErrorCode.CREW_ACCESS_DENIED));

    authenticate();

    mockMvc
        .perform(get("/api/crews/{crewId}/settlement", 42L))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("CREW_ACCESS_DENIED"));
  }

  // ======================== GET /api/settlements/{settlementId} ========================

  @Test
  void getSettlementReturnsDetailShapeWithItems() throws Exception {
    JsonNode calculationReason =
        objectMapper.readTree(
            """
        {
          "included_dates": ["2026-05-01", "2026-05-02"],
          "excluded_logs": [{"server_time": "2026-05-02T07:10:11+09:00", "code": "DAILY_DUPLICATE"}]
        }
        """);
    SettlementDetailResponse response =
        new SettlementDetailResponse(
            501L,
            42L,
            "아침 갓생 30일",
            LocalDate.of(2026, 5, 1),
            LocalDate.of(2026, 5, 30),
            30,
            "0.6500",
            "SUCCEEDED",
            1,
            5,
            500_000L,
            390,
            499_996L,
            4L,
            "HOST_REMAINDER",
            null,
            "success",
            OffsetDateTime.parse("2026-06-01T13:12:10+09:00"),
            OffsetDateTime.parse("2026-06-01T13:12:18+09:00"),
            2,
            List.of(
                new SettlementItemDetailResponse(
                    7001L,
                    101L,
                    "갓생러",
                    true,
                    ParticipantStatusSnapshot.LOCKED,
                    100_000L,
                    92,
                    90,
                    30,
                    2,
                    "0.230769",
                    1,
                    115_384L,
                    4L,
                    115_388L,
                    99_001L,
                    calculationReason)));

    given(settlementQueryService.getSettlementDetail(501L, MEMBER_UUID)).willReturn(response);

    authenticate();

    mockMvc
        .perform(get("/api/settlements/{settlementId}", 501L))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.settlement_id").value(501))
        .andExpect(jsonPath("$.crew_id").value(42))
        .andExpect(jsonPath("$.crew_name").value("아침 갓생 30일"))
        .andExpect(jsonPath("$.crew_started_at").value("2026-05-01"))
        .andExpect(jsonPath("$.crew_ended_at").value("2026-05-30"))
        .andExpect(jsonPath("$.mission_days").value(30))
        .andExpect(jsonPath("$.crew_success_rate").value("0.6500"))
        .andExpect(jsonPath("$.my_rank").value(2))
        .andExpect(jsonPath("$.status").value("SUCCEEDED"))
        .andExpect(jsonPath("$.retry_count").value(1))
        .andExpect(jsonPath("$.total_participants").value(5))
        .andExpect(jsonPath("$.total_locked_amount").value(500000))
        .andExpect(jsonPath("$.total_recognized_success").value(390))
        .andExpect(jsonPath("$.total_base_refund_amount").value(499996))
        .andExpect(jsonPath("$.total_remainder_amount").value(4))
        .andExpect(jsonPath("$.remainder_policy").value("HOST_REMAINDER"))
        .andExpect(jsonPath("$.failure_code").value((Object) null))
        .andExpect(jsonPath("$.failure_message").value("success"))
        .andExpect(jsonPath("$.started_at").value("2026-06-01T13:12:10+09:00"))
        .andExpect(jsonPath("$.finished_at").value("2026-06-01T13:12:18+09:00"))
        .andExpect(jsonPath("$.my_item").doesNotExist())
        .andExpect(jsonPath("$.items").isArray())
        .andExpect(jsonPath("$.items[0].settlement_item_id").value(7001))
        .andExpect(jsonPath("$.items[0].crew_participant_id").value(101))
        .andExpect(jsonPath("$.items[0].participant_status_snapshot").value("LOCKED"))
        .andExpect(jsonPath("$.items[0].deposit_amount").value(100000))
        .andExpect(jsonPath("$.items[0].success_count_raw").value(92))
        .andExpect(jsonPath("$.items[0].recognized_success_count").value(90))
        .andExpect(jsonPath("$.items[0].recognized_dates_count").value(30))
        .andExpect(jsonPath("$.items[0].excluded_success_count").value(2))
        .andExpect(jsonPath("$.items[0].share_ratio").value("0.230769"))
        .andExpect(jsonPath("$.items[0].base_refund_amount").value(115384))
        .andExpect(jsonPath("$.items[0].remainder_bonus_amount").value(4))
        .andExpect(jsonPath("$.items[0].refund_amount").value(115388))
        .andExpect(jsonPath("$.items[0].point_history_id").value(99001))
        .andExpect(jsonPath("$.items[0].nickname").value("갓생러"))
        .andExpect(jsonPath("$.items[0].rank").value(1))
        .andExpect(jsonPath("$.items[0].is_me").value(true))
        .andExpect(jsonPath("$.items[0].calculation_reason.included_dates[0]").value("2026-05-01"))
        .andExpect(
            jsonPath("$.items[0].calculation_reason.excluded_logs[0].server_time")
                .value("2026-05-02T07:10:11+09:00"))
        .andExpect(jsonPath("$.id").doesNotExist())
        .andExpect(jsonPath("$.member_id").doesNotExist())
        .andExpect(jsonPath("$.remainder_winner_crew_participant_id").doesNotExist())
        .andExpect(jsonPath("$.remainder_winner_member_id").doesNotExist())
        .andExpect(jsonPath("$.settlement_type").doesNotExist())
        .andExpect(jsonPath("$.items[0].remainder_winner_member_id").doesNotExist())
        .andExpect(jsonPath("$.items[0].settlement_type").doesNotExist());

    then(settlementQueryService).should().getSettlementDetail(501L, MEMBER_UUID);
  }

  @Test
  void getMySettlementReturnsPersonalItemOnly() throws Exception {
    JsonNode calculationReason =
        objectMapper.readTree(
            """
        {
          "included_dates": ["2026-05-01"],
          "excluded_logs": []
        }
        """);
    SettlementItemDetailResponse myItem =
        new SettlementItemDetailResponse(
            7002L,
            102L,
            null,
            true,
            ParticipantStatusSnapshot.LOCKED,
            100_000L,
            92,
            90,
            30,
            2,
            "0.230769",
            null,
            115_384L,
            4L,
            115_388L,
            99_002L,
            calculationReason);
    SettlementMeResponse response =
        new SettlementMeResponse(
            501L,
            42L,
            "SUCCEEDED",
            1,
            null,
            null,
            OffsetDateTime.parse("2026-06-01T13:12:10+09:00"),
            OffsetDateTime.parse("2026-06-01T13:12:18+09:00"),
            myItem);

    given(settlementQueryService.getSettlementMe(501L, MEMBER_UUID)).willReturn(response);

    authenticate();

    mockMvc
        .perform(get("/api/settlements/{settlementId}/me", 501L))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.settlement_id").value(501))
        .andExpect(jsonPath("$.crew_id").value(42))
        .andExpect(jsonPath("$.status").value("SUCCEEDED"))
        .andExpect(jsonPath("$.retry_count").value(1))
        .andExpect(jsonPath("$.items").doesNotExist())
        .andExpect(jsonPath("$.my_item.settlement_item_id").value(7002))
        .andExpect(jsonPath("$.my_item.crew_participant_id").value(102))
        .andExpect(jsonPath("$.my_item.refund_amount").value(115388))
        .andExpect(jsonPath("$.my_item.base_refund_amount").value(115384))
        .andExpect(jsonPath("$.my_item.remainder_bonus_amount").value(4))
        .andExpect(jsonPath("$.my_item.share_ratio").value("0.230769"))
        .andExpect(jsonPath("$.my_item.calculation_reason.included_dates[0]").value("2026-05-01"))
        .andExpect(jsonPath("$.my_item.calculation_reason.excluded_logs").isArray())
        .andExpect(jsonPath("$.my_item.member_id").doesNotExist())
        .andExpect(jsonPath("$.my_item.member_uuid").doesNotExist())
        .andExpect(jsonPath("$.my_item.id").doesNotExist());

    then(settlementQueryService).should().getSettlementMe(501L, MEMBER_UUID);
  }

  @Test
  void getMySettlementAllowsNullMyItem() throws Exception {
    SettlementMeResponse response =
        new SettlementMeResponse(
            501L,
            42L,
            "SUCCEEDED",
            1,
            null,
            null,
            OffsetDateTime.parse("2026-06-01T13:12:10+09:00"),
            OffsetDateTime.parse("2026-06-01T13:12:18+09:00"),
            null);

    given(settlementQueryService.getSettlementMe(501L, MEMBER_UUID)).willReturn(response);

    authenticate();

    mockMvc
        .perform(get("/api/settlements/{settlementId}/me", 501L))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.my_item").value(nullValue()))
        .andExpect(jsonPath("$.items").doesNotExist())
        .andExpect(content().string(not(org.hamcrest.Matchers.containsString("member_id"))));
  }

  @Test
  void getSettlementReturns403WhenAccessDenied() throws Exception {
    given(settlementQueryService.getSettlementDetail(501L, MEMBER_UUID))
        .willThrow(new CustomException(CrewErrorCode.CREW_ACCESS_DENIED));

    authenticate();

    mockMvc
        .perform(get("/api/settlements/{settlementId}", 501L))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("CREW_ACCESS_DENIED"));
  }

  @Test
  void getSettlementAllowsNullPointHistoryId() throws Exception {
    JsonNode calculationReason = objectMapper.createObjectNode();
    SettlementDetailResponse response =
        new SettlementDetailResponse(
            501L,
            42L,
            "크루",
            LocalDate.of(2026, 5, 1),
            LocalDate.of(2026, 5, 30),
            30,
            "0",
            "FAILED",
            1,
            5,
            500_000L,
            390,
            499_996L,
            4L,
            null,
            null,
            null,
            OffsetDateTime.parse("2026-06-01T13:12:10+09:00"),
            OffsetDateTime.parse("2026-06-01T13:12:18+09:00"),
            null,
            List.of(
                new SettlementItemDetailResponse(
                    7001L,
                    101L,
                    null,
                    false,
                    ParticipantStatusSnapshot.LOCKED,
                    100_000L,
                    92,
                    90,
                    30,
                    2,
                    BigDecimal.ONE.toPlainString(),
                    null,
                    115_384L,
                    0L,
                    115_384L,
                    null,
                    calculationReason)));

    given(settlementQueryService.getSettlementDetail(501L, MEMBER_UUID)).willReturn(response);

    authenticate();

    mockMvc
        .perform(get("/api/settlements/{settlementId}", 501L))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items[0].point_history_id").value(nullValue()));
  }

  private static void authenticate() {
    SecurityContextHolder.getContext()
        .setAuthentication(new UsernamePasswordAuthenticationToken(MEMBER_UUID, null, List.of()));
  }
}
