package com.oit.dondok.domain.dashboard.controller;

import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.oit.dondok.domain.crew.entity.CrewStatus;
import com.oit.dondok.domain.crew.exception.CrewErrorCode;
import com.oit.dondok.domain.dashboard.dto.response.CrewDashboardParticipantResponse;
import com.oit.dondok.domain.dashboard.dto.response.CrewDashboardResponse;
import com.oit.dondok.domain.dashboard.dto.response.CrewDashboardResponse.ProjectionNotice;
import com.oit.dondok.domain.dashboard.dto.response.CrewDashboardResponse.ProjectionStatus;
import com.oit.dondok.domain.dashboard.service.CrewDashboardService;
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

@WebMvcTest(CrewDashboardController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
class CrewDashboardControllerTest {

  private static final UUID MEMBER_UUID = UUID.fromString("018f4fd2-6d7a-7a41-9f58-6d07f5c3c901");
  private static final Long CREW_ID = 42L;

  @Autowired private MockMvc mockMvc;

  @MockBean private CrewDashboardService crewDashboardService;

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
  void getCrewDashboardReturnsContractJsonShape() throws Exception {
    given(crewDashboardService.getCrewDashboard(eq(MEMBER_UUID), eq(CREW_ID)))
        .willReturn(
            new CrewDashboardResponse(
                CREW_ID,
                "아침 갓생 30일",
                101L,
                CrewStatus.ACTIVE,
                "NONE",
                ProjectionStatus.LIVE,
                ProjectionNotice.ESTIMATED_NOT_FINAL,
                2,
                100_000L,
                5,
                103_226L,
                3_226L,
                2,
                5,
                -1,
                OffsetDateTime.parse("2026-05-21T12:00:00+09:00"),
                List.of(new CrewDashboardParticipantResponse(101L, "갓생러", "0.235", true)),
                OffsetDateTime.parse("2026-05-21T12:00:00+09:00")));

    mockMvc
        .perform(get("/api/crews/{crewId}/dashboard", CREW_ID))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.crew_id").value(CREW_ID))
        .andExpect(jsonPath("$.crew_name").value("아침 갓생 30일"))
        .andExpect(jsonPath("$.crew_participant_id").value(101))
        .andExpect(jsonPath("$.crew_status").value("ACTIVE"))
        .andExpect(jsonPath("$.settlement_status").value("NONE"))
        .andExpect(jsonPath("$.projection_status").value("LIVE"))
        .andExpect(jsonPath("$.projection_notice").value("ESTIMATED_NOT_FINAL"))
        .andExpect(jsonPath("$.my_success_count").value(5))
        .andExpect(jsonPath("$.my_expected_refund_amount").value(103226))
        .andExpect(jsonPath("$.my_expected_refund_delta_amount").value(3226))
        .andExpect(jsonPath("$.rank").value(2))
        .andExpect(jsonPath("$.participant_count").value(5))
        .andExpect(jsonPath("$.rank_delta").value(-1))
        .andExpect(jsonPath("$.next_settlement_at").value("2026-05-21T12:00:00+09:00"))
        .andExpect(jsonPath("$.participants").isArray())
        .andExpect(jsonPath("$.participants[0].crew_participant_id").value(101))
        .andExpect(jsonPath("$.participants[0].share_ratio").value("0.235"))
        .andExpect(jsonPath("$.participants[0].is_me").value(true))
        .andExpect(jsonPath("$.participants[0].success_count").doesNotExist());

    then(crewDashboardService).should().getCrewDashboard(MEMBER_UUID, CREW_ID);
  }

  @Test
  void getCrewDashboardSerializesNotApplicableFieldsAsNull() throws Exception {
    given(crewDashboardService.getCrewDashboard(eq(MEMBER_UUID), eq(CREW_ID)))
        .willReturn(
            new CrewDashboardResponse(
                CREW_ID,
                "모집 중 크루",
                101L,
                CrewStatus.RECRUITING,
                "NONE",
                ProjectionStatus.NOT_STARTED,
                ProjectionNotice.NOT_STARTED,
                null,
                100_000L,
                0,
                null,
                null,
                null,
                0,
                null,
                null,
                List.of(),
                OffsetDateTime.parse("2026-05-21T12:00:00+09:00")));

    mockMvc
        .perform(get("/api/crews/{crewId}/dashboard", CREW_ID))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.projection_status").value("NOT_STARTED"))
        .andExpect(jsonPath("$.my_expected_refund_amount").value(nullValue()))
        .andExpect(jsonPath("$.rank").value(nullValue()))
        .andExpect(jsonPath("$.next_settlement_at").value(nullValue()))
        .andExpect(jsonPath("$.participants").isEmpty());
  }

  @Test
  void getCrewDashboardReturns404WhenCrewNotFound() throws Exception {
    given(crewDashboardService.getCrewDashboard(eq(MEMBER_UUID), eq(CREW_ID)))
        .willThrow(new CustomException(CrewErrorCode.CREW_NOT_FOUND));

    mockMvc
        .perform(get("/api/crews/{crewId}/dashboard", CREW_ID))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("CREW_NOT_FOUND"));
  }

  @Test
  void getCrewDashboardReturns404WhenSettlementCompleted() throws Exception {
    given(crewDashboardService.getCrewDashboard(eq(MEMBER_UUID), eq(CREW_ID)))
        .willThrow(new CustomException(CrewErrorCode.CREW_DASHBOARD_NOT_AVAILABLE));

    mockMvc
        .perform(get("/api/crews/{crewId}/dashboard", CREW_ID))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("CREW_DASHBOARD_NOT_AVAILABLE"));
  }

  @Test
  void getCrewDashboardReturns403WhenAccessDenied() throws Exception {
    given(crewDashboardService.getCrewDashboard(eq(MEMBER_UUID), eq(CREW_ID)))
        .willThrow(new CustomException(CrewErrorCode.CREW_ACCESS_DENIED));

    mockMvc
        .perform(get("/api/crews/{crewId}/dashboard", CREW_ID))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("CREW_ACCESS_DENIED"));
  }

  @Test
  void getCrewDashboardReturns404WhenParticipantNotFound() throws Exception {
    given(crewDashboardService.getCrewDashboard(eq(MEMBER_UUID), eq(CREW_ID)))
        .willThrow(new CustomException(CrewErrorCode.PARTICIPANT_NOT_FOUND));

    mockMvc
        .perform(get("/api/crews/{crewId}/dashboard", CREW_ID))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("PARTICIPANT_NOT_FOUND"));
  }
}
