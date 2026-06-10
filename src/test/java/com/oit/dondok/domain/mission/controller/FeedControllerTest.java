package com.oit.dondok.domain.mission.controller;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.oit.dondok.domain.crew.exception.CrewErrorCode;
import com.oit.dondok.domain.mission.dto.response.AvailableCrewResponse;
import com.oit.dondok.domain.mission.dto.response.FeedItemResponse;
import com.oit.dondok.domain.mission.dto.response.FeedResponse;
import com.oit.dondok.domain.mission.entity.CertificationStatus;
import com.oit.dondok.domain.mission.service.FeedService;
import com.oit.dondok.global.exception.CustomException;
import com.oit.dondok.global.exception.GlobalExceptionHandler;
import java.time.LocalDate;
import java.time.OffsetDateTime;
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
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(FeedController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
class FeedControllerTest {

  private static final UUID MEMBER_UUID = UUID.fromString("018f4fd2-6d7a-7a41-9f58-6d07f5c3c901");

  @Autowired private MockMvc mockMvc;

  @MockBean private FeedService feedService;

  @AfterEach
  void clearSecurityContext() {
    SecurityContextHolder.clearContext();
  }

  // 정상 조회: available_crews/feed_items/next_cursor를 명세의 snake_case body로 반환한다.
  @Test
  void getFeedSuccess() throws Exception {
    FeedItemResponse item =
        new FeedItemResponse(
            9001L,
            42L,
            "갓생 6시 기상",
            101L,
            MEMBER_UUID,
            "돈독러",
            "https://cdn/profile/9001",
            "https://cdn/mission/9001",
            "오늘도 미션 완료",
            OffsetDateTime.parse("2026-06-09T06:05:10+09:00"),
            CertificationStatus.SUCCESS,
            Map.of("clap", 2L),
            List.of("clap"));
    FeedResponse response =
        new FeedResponse(
            List.of(new AvailableCrewResponse(42L, "갓생 6시 기상")),
            List.of(item),
            "2026-06-09T06:05:10+09:00_9001");
    given(feedService.getFeed(any(), any(), any(), any(), any(), any())).willReturn(response);

    authenticate(MEMBER_UUID);

    mockMvc
        .perform(get("/api/feed"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.available_crews[0].crew_id").value(42))
        .andExpect(jsonPath("$.available_crews[0].crew_name").value("갓생 6시 기상"))
        .andExpect(jsonPath("$.feed_items[0].mission_log_id").value(9001))
        .andExpect(jsonPath("$.feed_items[0].crew_id").value(42))
        .andExpect(jsonPath("$.feed_items[0].crew_name").value("갓생 6시 기상"))
        .andExpect(jsonPath("$.feed_items[0].crew_participant_id").value(101))
        .andExpect(jsonPath("$.feed_items[0].member_uuid").value(MEMBER_UUID.toString()))
        .andExpect(jsonPath("$.feed_items[0].nickname").value("돈독러"))
        .andExpect(jsonPath("$.feed_items[0].profile_image_url").value("https://cdn/profile/9001"))
        .andExpect(jsonPath("$.feed_items[0].image_url").value("https://cdn/mission/9001"))
        .andExpect(jsonPath("$.feed_items[0].server_time").value("2026-06-09T06:05:10+09:00"))
        .andExpect(jsonPath("$.feed_items[0].certification_status").value("SUCCESS"))
        .andExpect(jsonPath("$.feed_items[0].reaction_counts.clap").value(2))
        .andExpect(jsonPath("$.feed_items[0].my_reactions[0]").value("clap"))
        .andExpect(jsonPath("$.next_cursor").value("2026-06-09T06:05:10+09:00_9001"));
  }

  // 쿼리 파라미터(crew_id/from/to/cursor/limit)를 파싱해 서비스로 그대로 전달한다.
  @Test
  void passesQueryParamsToService() throws Exception {
    given(feedService.getFeed(any(), any(), any(), any(), any(), any()))
        .willReturn(new FeedResponse(List.of(), List.of(), null));

    authenticate(MEMBER_UUID);

    mockMvc
        .perform(
            get("/api/feed")
                .param("crew_id", "42")
                .param("from", "2026-06-09")
                .param("to", "2026-06-11")
                .param("cursor", "2026-06-09T06:05:10+09:00_9001")
                .param("limit", "30"))
        .andExpect(status().isOk());

    then(feedService)
        .should()
        .getFeed(
            eq(MEMBER_UUID),
            eq(42L),
            eq(LocalDate.of(2026, 6, 9)),
            eq(LocalDate.of(2026, 6, 11)),
            eq("2026-06-09T06:05:10+09:00_9001"),
            eq(30));
  }

  // 파라미터 미지정이면 서비스에 모두 null로 전달한다(기본 동작은 서비스가 결정).
  @Test
  void passesNullsWhenNoParams() throws Exception {
    given(feedService.getFeed(any(), any(), any(), any(), any(), any()))
        .willReturn(new FeedResponse(List.of(), List.of(), null));

    authenticate(MEMBER_UUID);

    mockMvc.perform(get("/api/feed")).andExpect(status().isOk());

    then(feedService).should().getFeed(MEMBER_UUID, null, null, null, null, null);
  }

  // 비참여 크루 필터 등 서비스 도메인 예외는 공통 에러 응답(403)으로 변환된다.
  @Test
  void returnsForbiddenWhenAccessDenied() throws Exception {
    given(feedService.getFeed(any(), any(), any(), any(), any(), any()))
        .willThrow(new CustomException(CrewErrorCode.CREW_ACCESS_DENIED));

    authenticate(MEMBER_UUID);

    mockMvc
        .perform(get("/api/feed").param("crew_id", "99"))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("CREW_ACCESS_DENIED"));
  }

  // 빈 피드: feed_items 빈 배열, next_cursor null.
  @Test
  void emptyFeed() throws Exception {
    given(feedService.getFeed(any(), any(), any(), any(), any(), any()))
        .willReturn(
            new FeedResponse(List.of(new AvailableCrewResponse(42L, "갓생 6시 기상")), List.of(), null));

    authenticate(MEMBER_UUID);

    mockMvc
        .perform(get("/api/feed"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.available_crews", hasSize(1)))
        .andExpect(jsonPath("$.feed_items", hasSize(0)))
        .andExpect(jsonPath("$.next_cursor").value(nullValue()));
  }

  private static void authenticate(UUID memberUuid) {
    SecurityContextHolder.getContext()
        .setAuthentication(
            new UsernamePasswordAuthenticationToken(memberUuid, null, Collections.emptyList()));
  }
}
