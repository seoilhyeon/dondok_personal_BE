package com.oit.dondok.domain.mission.controller;

import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.oit.dondok.domain.mission.dto.response.MissionModerationResponse;
import com.oit.dondok.domain.mission.entity.CertificationStatus;
import com.oit.dondok.domain.mission.entity.ModerationDecisionType;
import com.oit.dondok.domain.mission.exception.MissionErrorCode;
import com.oit.dondok.domain.mission.service.MissionModerationService;
import com.oit.dondok.global.exception.CustomException;
import com.oit.dondok.global.exception.GlobalExceptionHandler;
import java.time.OffsetDateTime;
import java.util.Collections;
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

@WebMvcTest(MissionModerationController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
class MissionModerationControllerTest {

  private static final UUID MEMBER_UUID = UUID.fromString("018f4fd2-6d7a-7a41-9f58-6d07f5c3c901");
  private static final Long MISSION_LOG_ID = 1001L;

  @Autowired private MockMvc mockMvc;

  @MockBean private MissionModerationService missionModerationService;

  @AfterEach
  void clearSecurityContext() {
    SecurityContextHolder.clearContext();
  }

  // 승인 API는 인증 principal과 path variable을 서비스에 전달하고 명세의 응답 body를 반환한다.
  @Test
  void approveSuccess() throws Exception {
    MissionModerationResponse response =
        new MissionModerationResponse(
            MISSION_LOG_ID,
            42L,
            101L,
            CertificationStatus.SUCCESS,
            ModerationDecisionType.MANUAL_APPROVE,
            null,
            OffsetDateTime.parse("2026-06-08T11:00:00+09:00"),
            9001L);
    given(missionModerationService.approve(MEMBER_UUID, MISSION_LOG_ID)).willReturn(response);

    authenticate(MEMBER_UUID);

    mockMvc
        .perform(post("/api/mission-logs/{missionLogId}/moderation/approve", MISSION_LOG_ID))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.mission_log_id").value(MISSION_LOG_ID))
        .andExpect(jsonPath("$.crew_id").value(42))
        .andExpect(jsonPath("$.crew_participant_id").value(101))
        .andExpect(jsonPath("$.certification_status").value("SUCCESS"))
        .andExpect(jsonPath("$.decision_type").value("MANUAL_APPROVE"))
        .andExpect(jsonPath("$.reject_reason_code").value(nullValue()))
        .andExpect(jsonPath("$.decided_at").value("2026-06-08T11:00:00+09:00"))
        .andExpect(jsonPath("$.moderation_history_id").value(9001));

    then(missionModerationService).should().approve(eq(MEMBER_UUID), eq(MISSION_LOG_ID));
  }

  // 서비스에서 발생한 도메인 예외는 공통 에러 응답으로 변환된다.
  @Test
  void approveFailWhenRequesterIsNotHost() throws Exception {
    given(missionModerationService.approve(MEMBER_UUID, MISSION_LOG_ID))
        .willThrow(new CustomException(MissionErrorCode.FORBIDDEN_NOT_HOST));

    authenticate(MEMBER_UUID);

    mockMvc
        .perform(post("/api/mission-logs/{missionLogId}/moderation/approve", MISSION_LOG_ID))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("FORBIDDEN_NOT_HOST"));
  }

  private static void authenticate(UUID memberUuid) {
    SecurityContextHolder.getContext()
        .setAuthentication(
            new UsernamePasswordAuthenticationToken(memberUuid, null, Collections.emptyList()));
  }
}
