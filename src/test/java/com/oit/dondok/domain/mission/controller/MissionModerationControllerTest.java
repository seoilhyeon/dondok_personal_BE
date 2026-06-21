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
import com.oit.dondok.domain.mission.entity.RejectReasonCode;
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

  // 거절 API는 인증 principal, path variable, 요청 body를 서비스에 전달한다.
  @Test
  void rejectSuccess() throws Exception {
    MissionModerationResponse response =
        new MissionModerationResponse(
            MISSION_LOG_ID,
            42L,
            101L,
            CertificationStatus.FAILED,
            ModerationDecisionType.MANUAL_REJECT,
            RejectReasonCode.MISSION_MISMATCH,
            OffsetDateTime.parse("2026-06-08T11:00:00+09:00"),
            9001L);
    given(
            missionModerationService.reject(
                MEMBER_UUID, MISSION_LOG_ID, RejectReasonCode.MISSION_MISMATCH, "사진이 미션과 다릅니다"))
        .willReturn(response);

    authenticate(MEMBER_UUID);

    mockMvc
        .perform(
            post("/api/mission-logs/{missionLogId}/moderation/reject", MISSION_LOG_ID)
                .contentType("application/json")
                .content(
                    """
                    {
                      "reject_reason_code": "MISSION_MISMATCH",
                      "reject_memo": "사진이 미션과 다릅니다"
                    }
                    """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.mission_log_id").value(MISSION_LOG_ID))
        .andExpect(jsonPath("$.certification_status").value("FAILED"))
        .andExpect(jsonPath("$.decision_type").value("MANUAL_REJECT"))
        .andExpect(jsonPath("$.reject_reason_code").value("MISSION_MISMATCH"))
        .andExpect(jsonPath("$.moderation_history_id").value(9001));

    then(missionModerationService)
        .should()
        .reject(
            eq(MEMBER_UUID),
            eq(MISSION_LOG_ID),
            eq(RejectReasonCode.MISSION_MISMATCH),
            eq("사진이 미션과 다릅니다"));
  }

  // reject_reason_code가 없으면 Bean Validation이 요청을 거절하고 서비스는 호출되지 않는다.
  @Test
  void rejectFailWhenReasonCodeIsMissing() throws Exception {
    authenticate(MEMBER_UUID);

    mockMvc
        .perform(
            post("/api/mission-logs/{missionLogId}/moderation/reject", MISSION_LOG_ID)
                .contentType("application/json")
                .content(
                    """
                    {
                      "reject_memo": "사진이 미션과 다릅니다"
                    }
                    """))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("INVALID_INPUT"));

    then(missionModerationService).shouldHaveNoInteractions();
  }

  // 정의되지 않은 거절 사유 코드는 enum 역직렬화 단계에서 거절되고 서비스는 호출되지 않는다.
  @Test
  void rejectFailWhenReasonCodeIsInvalid() throws Exception {
    authenticate(MEMBER_UUID);

    mockMvc
        .perform(
            post("/api/mission-logs/{missionLogId}/moderation/reject", MISSION_LOG_ID)
                .contentType("application/json")
                .content(
                    """
                    {
                      "reject_reason_code": "NOT_A_REASON",
                      "reject_memo": "사진이 미션과 다릅니다"
                    }
                    """))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("INVALID_INPUT"));

    then(missionModerationService).shouldHaveNoInteractions();
  }

  // 서비스에서 발생한 거절 도메인 예외는 공통 에러 응답으로 변환된다.
  @Test
  void rejectFailWhenRequesterIsNotHost() throws Exception {
    given(
            missionModerationService.reject(
                MEMBER_UUID, MISSION_LOG_ID, RejectReasonCode.MISSION_MISMATCH, null))
        .willThrow(new CustomException(MissionErrorCode.FORBIDDEN_NOT_HOST));

    authenticate(MEMBER_UUID);

    mockMvc
        .perform(
            post("/api/mission-logs/{missionLogId}/moderation/reject", MISSION_LOG_ID)
                .contentType("application/json")
                .content(
                    """
                    {
                      "reject_reason_code": "MISSION_MISMATCH"
                    }
                    """))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("FORBIDDEN_NOT_HOST"));
  }

  // 되돌리기 API는 인증 principal과 path variable을 서비스에 전달하고 PENDING_REVIEW 응답을 반환한다.
  @Test
  void revertSuccess() throws Exception {
    MissionModerationResponse response =
        new MissionModerationResponse(
            MISSION_LOG_ID,
            42L,
            101L,
            CertificationStatus.PENDING_REVIEW,
            null,
            null,
            OffsetDateTime.parse("2026-06-08T11:00:00+09:00"),
            9001L);
    given(missionModerationService.revert(MEMBER_UUID, MISSION_LOG_ID)).willReturn(response);

    authenticate(MEMBER_UUID);

    mockMvc
        .perform(post("/api/mission-logs/{missionLogId}/moderation/revert", MISSION_LOG_ID))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.mission_log_id").value(MISSION_LOG_ID))
        .andExpect(jsonPath("$.certification_status").value("PENDING_REVIEW"))
        .andExpect(jsonPath("$.decision_type").value(nullValue()))
        .andExpect(jsonPath("$.reject_reason_code").value(nullValue()))
        .andExpect(jsonPath("$.moderation_history_id").value(9001));

    then(missionModerationService).should().revert(eq(MEMBER_UUID), eq(MISSION_LOG_ID));
  }

  // 되돌릴 수 없는 상태에서 발생한 예외는 공통 에러 응답으로 변환된다.
  @Test
  void revertFailWhenNotRevertible() throws Exception {
    given(missionModerationService.revert(MEMBER_UUID, MISSION_LOG_ID))
        .willThrow(new CustomException(MissionErrorCode.MISSION_LOG_NOT_REVERTIBLE));

    authenticate(MEMBER_UUID);

    mockMvc
        .perform(post("/api/mission-logs/{missionLogId}/moderation/revert", MISSION_LOG_ID))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("MISSION_LOG_NOT_REVERTIBLE"));
  }

  private static void authenticate(UUID memberUuid) {
    SecurityContextHolder.getContext()
        .setAuthentication(
            new UsernamePasswordAuthenticationToken(memberUuid, null, Collections.emptyList()));
  }
}
