package com.oit.dondok.domain.member.controller;

import static org.hamcrest.Matchers.nullValue;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.oit.dondok.domain.member.dto.response.ActivityInfoResponse;
import com.oit.dondok.domain.member.dto.response.ActivityStatsResponse;
import com.oit.dondok.domain.member.dto.response.ActivitySummaryResponse;
import com.oit.dondok.domain.member.dto.response.CrewActivityInfoResponse;
import com.oit.dondok.domain.member.dto.response.ProfileResponse;
import com.oit.dondok.domain.member.entity.MemberStatus;
import com.oit.dondok.domain.member.exception.MemberErrorCode;
import com.oit.dondok.domain.member.service.MemberActivitySummaryService;
import com.oit.dondok.domain.member.service.MemberProfileService;
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

@WebMvcTest(MemberProfileController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
class MemberProfileControllerTest {

  @Autowired private MockMvc mockMvc;

  @MockBean private MemberProfileService memberProfileService;
  @MockBean private MemberActivitySummaryService memberActivitySummaryService;

  @AfterEach
  void clearSecurityContext() {
    SecurityContextHolder.clearContext();
  }

  @Test
  void getProfileSuccess() throws Exception {
    UUID memberUuid = UUID.fromString("018f4fd2-6d7a-7a41-9f58-6d07f5c3c901");
    ProfileResponse response =
        new ProfileResponse(
            memberUuid,
            "user@example.com",
            "돈독러",
            null,
            "꾸준히 하는 중",
            true,
            2L,
            MemberStatus.ACTIVE,
            OffsetDateTime.parse("2026-05-31T09:00:00+09:00"));
    given(memberProfileService.findProfileByMemberUuid(memberUuid)).willReturn(response);

    authenticate(memberUuid);

    mockMvc
        .perform(get("/api/me"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").doesNotExist())
        .andExpect(jsonPath("$.member_id").doesNotExist())
        .andExpect(jsonPath("$.profile_image_s3_key").doesNotExist())
        .andExpect(jsonPath("$.member_uuid").value(memberUuid.toString()))
        .andExpect(jsonPath("$.email").value("user@example.com"))
        .andExpect(jsonPath("$.nickname").value("돈독러"))
        .andExpect(jsonPath("$.profile_image_url").value(nullValue()))
        .andExpect(jsonPath("$.status_message").value("꾸준히 하는 중"))
        .andExpect(jsonPath("$.is_host_ever").value(true))
        .andExpect(jsonPath("$.hosted_crew_count").value(2))
        .andExpect(jsonPath("$.status").value("ACTIVE"))
        .andExpect(jsonPath("$.created_at").value("2026-05-31T09:00:00+09:00"));
  }

  @Test
  void getProfileFailWhenMemberDoesNotExist() throws Exception {
    UUID memberUuid = UUID.fromString("018f4fd2-6d7a-7a41-9f58-6d07f5c3c901");
    given(memberProfileService.findProfileByMemberUuid(memberUuid))
        .willThrow(new CustomException(MemberErrorCode.MEMBER_NOT_FOUND));

    authenticate(memberUuid);

    mockMvc
        .perform(get("/api/me"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("MEMBER_NOT_FOUND"));
  }

  @Test
  void getActivitySummarySuccess() throws Exception {
    UUID memberUuid = UUID.fromString("018f4fd2-6d7a-7a41-9f58-6d07f5c3c901");
    ActivitySummaryResponse response =
        new ActivitySummaryResponse(
            memberUuid,
            new ActivityInfoResponse(new CrewActivityInfoResponse(17L, 3L, 14L), 24L, 2L),
            new ActivityStatsResponse(450L, "0.250000", 42L, "아침 갓생 30일", null),
            OffsetDateTime.parse("2026-06-01T09:00:00+09:00"));
    given(memberActivitySummaryService.findActivitySummaryByMemberUuid(memberUuid))
        .willReturn(response);

    authenticate(memberUuid);

    mockMvc
        .perform(get("/api/me/activity-summary"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").doesNotExist())
        .andExpect(jsonPath("$.member_id").doesNotExist())
        .andExpect(jsonPath("$.participant_id").doesNotExist())
        .andExpect(jsonPath("$.activity_info.host_operation").doesNotExist())
        .andExpect(jsonPath("$.activity_info.pending_review_count").doesNotExist())
        .andExpect(jsonPath("$.activity_info.verification").doesNotExist())
        .andExpect(jsonPath("$.activity_info.notifications").doesNotExist())
        .andExpect(jsonPath("$.activity_stats.settled_crew_count").doesNotExist())
        .andExpect(jsonPath("$.member_uuid").value(memberUuid.toString()))
        .andExpect(jsonPath("$.activity_info.crew.total_crew_count").value(17))
        .andExpect(jsonPath("$.activity_info.crew.active_crew_count").value(3))
        .andExpect(jsonPath("$.activity_info.crew.completed_crew_count").value(14))
        .andExpect(jsonPath("$.activity_info.total_verification_count").value(24))
        .andExpect(jsonPath("$.activity_info.unread_notification_count").value(2))
        .andExpect(jsonPath("$.activity_stats.total_recognized_success_count").value(450))
        .andExpect(jsonPath("$.activity_stats.highest_share_ratio").value("0.250000"))
        .andExpect(jsonPath("$.activity_stats.highest_share_ratio_crew_id").value(42))
        .andExpect(jsonPath("$.activity_stats.highest_share_ratio_crew_title").value("아침 갓생 30일"))
        .andExpect(jsonPath("$.activity_stats.average_success_rate").value(nullValue()))
        .andExpect(jsonPath("$.generated_at").value("2026-06-01T09:00:00+09:00"));
  }

  @Test
  void getActivitySummaryFailWhenMemberDoesNotExist() throws Exception {
    UUID memberUuid = UUID.fromString("018f4fd2-6d7a-7a41-9f58-6d07f5c3c901");
    given(memberActivitySummaryService.findActivitySummaryByMemberUuid(memberUuid))
        .willThrow(new CustomException(MemberErrorCode.MEMBER_NOT_FOUND));

    authenticate(memberUuid);

    mockMvc
        .perform(get("/api/me/activity-summary"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("MEMBER_NOT_FOUND"));
  }

  private static void authenticate(UUID memberUuid) {
    SecurityContextHolder.getContext()
        .setAuthentication(
            new UsernamePasswordAuthenticationToken(memberUuid, null, Collections.emptyList()));
  }
}
