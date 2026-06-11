package com.oit.dondok.domain.mission.controller;

import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.oit.dondok.domain.mission.dto.response.ReactionResponse;
import com.oit.dondok.domain.mission.exception.MissionErrorCode;
import com.oit.dondok.domain.mission.service.MissionLogReactionService;
import com.oit.dondok.global.exception.CustomException;
import com.oit.dondok.global.exception.GlobalExceptionHandler;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
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

@WebMvcTest(MissionLogReactionController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
class MissionLogReactionControllerTest {

  private static final UUID MEMBER_UUID = UUID.fromString("018f4fd2-6d7a-7a41-9f58-6d07f5c3c901");
  private static final Long MISSION_LOG_ID = 9001L;
  private static final String CLAP = "👏";
  private static final String FIRE = "🔥";

  @Autowired private MockMvc mockMvc;

  @MockBean private MissionLogReactionService missionLogReactionService;

  @AfterEach
  void clearSecurityContext() {
    SecurityContextHolder.clearContext();
  }

  // 추가 성공: 200 + 명세의 snake_case body, 인증 회원/경로/토큰을 서비스로 전달한다.
  @Test
  void addReactionSuccess() throws Exception {
    given(missionLogReactionService.addReaction(any(), any(), any()))
        .willReturn(
            new ReactionResponse(MISSION_LOG_ID, List.of(CLAP, FIRE), Map.of(CLAP, 2L, FIRE, 1L)));
    authenticate(MEMBER_UUID);

    mockMvc
        .perform(
            post("/api/mission-logs/{missionLogId}/reactions", MISSION_LOG_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .characterEncoding("UTF-8")
                .content("{\"reaction_type\":\"" + CLAP + "\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.mission_log_id").value(9001))
        .andExpect(jsonPath("$.my_reactions", hasSize(2)))
        .andExpect(jsonPath("$.my_reactions[0]").value(CLAP))
        .andExpect(jsonPath("$.reaction_counts['" + CLAP + "']").value(2))
        .andExpect(jsonPath("$.reaction_counts['" + FIRE + "']").value(1));

    then(missionLogReactionService).should().addReaction(MEMBER_UUID, MISSION_LOG_ID, CLAP);
  }

  // 삭제 성공: 200 + reaction_type query param/경로/인증 회원을 서비스로 전달한다.
  @Test
  void removeReactionSuccess() throws Exception {
    given(missionLogReactionService.removeReaction(any(), any(), any()))
        .willReturn(new ReactionResponse(MISSION_LOG_ID, List.of(FIRE), Map.of(FIRE, 1L)));
    authenticate(MEMBER_UUID);

    mockMvc
        .perform(
            delete("/api/mission-logs/{missionLogId}/reactions/me", MISSION_LOG_ID)
                .param("reaction_type", CLAP))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.mission_log_id").value(9001))
        .andExpect(jsonPath("$.my_reactions", hasSize(1)))
        .andExpect(jsonPath("$.reaction_counts['" + FIRE + "']").value(1));

    then(missionLogReactionService).should().removeReaction(MEMBER_UUID, MISSION_LOG_ID, CLAP);
  }

  // 서비스 도메인 예외(REACTION_NOT_ALLOWED)는 공통 에러 응답(403)으로 변환된다.
  @Test
  void addReactionReturnsForbiddenWhenNotAllowed() throws Exception {
    given(missionLogReactionService.addReaction(any(), any(), any()))
        .willThrow(new CustomException(MissionErrorCode.REACTION_NOT_ALLOWED));
    authenticate(MEMBER_UUID);

    mockMvc
        .perform(
            post("/api/mission-logs/{missionLogId}/reactions", MISSION_LOG_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .characterEncoding("UTF-8")
                .content("{\"reaction_type\":\"" + CLAP + "\"}"))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("REACTION_NOT_ALLOWED"));
  }

  // 잘못된 reaction_type(INVALID_REACTION_TYPE)은 400으로 변환된다.
  @Test
  void addReactionReturnsBadRequestWhenInvalidType() throws Exception {
    given(missionLogReactionService.addReaction(any(), any(), any()))
        .willThrow(new CustomException(MissionErrorCode.INVALID_REACTION_TYPE));
    authenticate(MEMBER_UUID);

    mockMvc
        .perform(
            post("/api/mission-logs/{missionLogId}/reactions", MISSION_LOG_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .characterEncoding("UTF-8")
                .content("{\"reaction_type\":\"  \"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("INVALID_REACTION_TYPE"));
  }

  // DELETE의 reaction_type은 필수 query param이다. 누락 시 400이며 서비스는 호출되지 않는다.
  @Test
  void removeReactionRequiresReactionTypeParam() throws Exception {
    authenticate(MEMBER_UUID);

    mockMvc
        .perform(delete("/api/mission-logs/{missionLogId}/reactions/me", MISSION_LOG_ID))
        .andExpect(status().isBadRequest());

    then(missionLogReactionService).shouldHaveNoInteractions();
  }

  private static void authenticate(UUID memberUuid) {
    SecurityContextHolder.getContext()
        .setAuthentication(
            new UsernamePasswordAuthenticationToken(memberUuid, null, Collections.emptyList()));
  }
}
