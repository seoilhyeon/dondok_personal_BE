package com.oit.dondok.domain.member.controller;

import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.oit.dondok.domain.crew.entity.CrewParticipantRole;
import com.oit.dondok.domain.crew.entity.CrewParticipantStatus;
import com.oit.dondok.domain.crew.entity.CrewStatus;
import com.oit.dondok.domain.member.dto.request.UpdateProfileRequest;
import com.oit.dondok.domain.member.dto.response.ActivityInfoResponse;
import com.oit.dondok.domain.member.dto.response.ActivityStatsResponse;
import com.oit.dondok.domain.member.dto.response.ActivitySummaryResponse;
import com.oit.dondok.domain.member.dto.response.CrewActivityInfoResponse;
import com.oit.dondok.domain.member.dto.response.HostOperationSummaryResponse;
import com.oit.dondok.domain.member.dto.response.MeCrewItemResponse;
import com.oit.dondok.domain.member.dto.response.MeCrewListResponse;
import com.oit.dondok.domain.member.dto.response.ProfileResponse;
import com.oit.dondok.domain.member.dto.response.ProfileUpdateResponse;
import com.oit.dondok.domain.member.entity.MemberStatus;
import com.oit.dondok.domain.member.exception.MemberErrorCode;
import com.oit.dondok.domain.member.service.HostOperationSummaryService;
import com.oit.dondok.domain.member.service.MeCrewService;
import com.oit.dondok.domain.member.service.MemberActivitySummaryService;
import com.oit.dondok.domain.member.service.MemberProfileService;
import com.oit.dondok.global.config.JsonNullableConfig;
import com.oit.dondok.global.exception.CustomException;
import com.oit.dondok.global.exception.GlobalErrorCode;
import com.oit.dondok.global.exception.GlobalExceptionHandler;
import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.List;
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

@WebMvcTest(MemberProfileController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import({GlobalExceptionHandler.class, JsonNullableConfig.class})
class MemberProfileControllerTest {

  @Autowired private MockMvc mockMvc;

  @MockBean private MemberProfileService memberProfileService;
  @MockBean private MemberActivitySummaryService memberActivitySummaryService;
  @MockBean private HostOperationSummaryService hostOperationSummaryService;
  @MockBean private MeCrewService meCrewService;

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
            "https://cdn.example.com/profile/avatar.jpg",
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
        .andExpect(
            jsonPath("$.profile_image_url").value("https://cdn.example.com/profile/avatar.jpg"))
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
  void updateProfileSuccess() throws Exception {
    UUID memberUuid = UUID.fromString("018f4fd2-6d7a-7a41-9f58-6d07f5c3c901");
    ProfileUpdateResponse response =
        new ProfileUpdateResponse(
            memberUuid,
            "user@example.com",
            "돈독러",
            "https://cdn.example.com/profile/avatar.jpg",
            "오늘도 한 걸음 더",
            OffsetDateTime.parse("2026-06-02T11:00:00+09:00"));
    given(memberProfileService.updateProfile(eq(memberUuid), any(UpdateProfileRequest.class)))
        .willReturn(response);

    authenticate(memberUuid);

    mockMvc
        .perform(
            patch("/api/me/profile")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "nickname": "돈독러",
                      "profile_image_s3_key": "profile/avatar.jpg",
                      "status_message": "오늘도 한 걸음 더"
                    }
                    """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").doesNotExist())
        .andExpect(jsonPath("$.member_id").doesNotExist())
        .andExpect(jsonPath("$.profile_image_s3_key").doesNotExist())
        .andExpect(jsonPath("$.is_host_ever").doesNotExist())
        .andExpect(jsonPath("$.hosted_crew_count").doesNotExist())
        .andExpect(jsonPath("$.status").doesNotExist())
        .andExpect(jsonPath("$.created_at").doesNotExist())
        .andExpect(jsonPath("$.member_uuid").value(memberUuid.toString()))
        .andExpect(jsonPath("$.email").value("user@example.com"))
        .andExpect(jsonPath("$.nickname").value("돈독러"))
        .andExpect(
            jsonPath("$.profile_image_url").value("https://cdn.example.com/profile/avatar.jpg"))
        .andExpect(jsonPath("$.status_message").value("오늘도 한 걸음 더"))
        .andExpect(jsonPath("$.updated_at").value("2026-06-02T11:00:00+09:00"));

    then(memberProfileService)
        .should()
        .updateProfile(eq(memberUuid), any(UpdateProfileRequest.class));
  }

  @Test
  void updateProfileSuccessWhenNullableFieldsAreExplicitNull() throws Exception {
    UUID memberUuid = UUID.fromString("018f4fd2-6d7a-7a41-9f58-6d07f5c3c901");
    ProfileUpdateResponse response =
        new ProfileUpdateResponse(
            memberUuid,
            "user@example.com",
            "돈독러",
            null,
            null,
            OffsetDateTime.parse("2026-06-02T11:00:00+09:00"));
    given(memberProfileService.updateProfile(eq(memberUuid), any(UpdateProfileRequest.class)))
        .willReturn(response);

    authenticate(memberUuid);

    mockMvc
        .perform(
            patch("/api/me/profile")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "profile_image_s3_key": null,
                      "status_message": null
                    }
                    """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.member_uuid").value(memberUuid.toString()))
        .andExpect(jsonPath("$.profile_image_url").value(nullValue()))
        .andExpect(jsonPath("$.status_message").value(nullValue()))
        .andExpect(jsonPath("$.updated_at").value("2026-06-02T11:00:00+09:00"));

    then(memberProfileService)
        .should()
        .updateProfile(eq(memberUuid), any(UpdateProfileRequest.class));
  }

  @Test
  void updateProfileFailWhenNicknameValidationFails() throws Exception {
    UUID memberUuid = UUID.fromString("018f4fd2-6d7a-7a41-9f58-6d07f5c3c901");

    authenticate(memberUuid);

    mockMvc
        .perform(
            patch("/api/me/profile")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "nickname": "돈"
                    }
                    """))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("INVALID_INPUT"));

    then(memberProfileService).shouldHaveNoInteractions();
  }

  @Test
  void updateProfileFailWhenNoUpdatableFieldIsIncluded() throws Exception {
    UUID memberUuid = UUID.fromString("018f4fd2-6d7a-7a41-9f58-6d07f5c3c901");
    given(memberProfileService.updateProfile(eq(memberUuid), any(UpdateProfileRequest.class)))
        .willThrow(new CustomException(GlobalErrorCode.INVALID_INPUT));

    authenticate(memberUuid);

    mockMvc
        .perform(patch("/api/me/profile").contentType(MediaType.APPLICATION_JSON).content("{}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
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
        .andExpect(jsonPath("$.host_operation").doesNotExist())
        .andExpect(jsonPath("$.total_pending_count").doesNotExist())
        .andExpect(jsonPath("$.activity_info.host_operation").doesNotExist())
        .andExpect(jsonPath("$.activity_info.pending_review_count").doesNotExist())
        .andExpect(jsonPath("$.activity_info.pending_application_count").doesNotExist())
        .andExpect(jsonPath("$.activity_info.total_pending_count").doesNotExist())
        .andExpect(jsonPath("$.activity_info.success_count").doesNotExist())
        .andExpect(jsonPath("$.activity_info.failed_count").doesNotExist())
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

  @Test
  void getHostOperationSummarySuccess() throws Exception {
    UUID memberUuid = UUID.fromString("018f4fd2-6d7a-7a41-9f58-6d07f5c3c901");
    HostOperationSummaryResponse response =
        new HostOperationSummaryResponse(
            memberUuid, 6L, OffsetDateTime.parse("2026-06-01T09:00:00+09:00"));
    given(hostOperationSummaryService.findHostOperationSummaryByMemberUuid(memberUuid))
        .willReturn(response);

    authenticate(memberUuid);

    mockMvc
        .perform(get("/api/me/host-operation-summary"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").doesNotExist())
        .andExpect(jsonPath("$.member_id").doesNotExist())
        .andExpect(jsonPath("$.participant_id").doesNotExist())
        .andExpect(jsonPath("$.crew_participant_id").doesNotExist())
        .andExpect(jsonPath("$.mission_log_id").doesNotExist())
        .andExpect(jsonPath("$.reject_memo").doesNotExist())
        .andExpect(jsonPath("$.pending_review_count").doesNotExist())
        .andExpect(jsonPath("$.pending_application_count").doesNotExist())
        .andExpect(jsonPath("$.host_operation").doesNotExist())
        .andExpect(jsonPath("$.member_uuid").value(memberUuid.toString()))
        .andExpect(jsonPath("$.total_pending_count").value(6))
        .andExpect(jsonPath("$.generated_at").value("2026-06-01T09:00:00+09:00"));
  }

  @Test
  void getMyCrewsSuccess() throws Exception {
    UUID memberUuid = UUID.fromString("018f4fd2-6d7a-7a41-9f58-6d07f5c3c901");
    MeCrewListResponse response =
        new MeCrewListResponse(
            List.of(
                new MeCrewItemResponse(
                    1L,
                    "아침 달리기 크루",
                    "https://cdn.example.com/crew.jpg",
                    "EXERCISE",
                    CrewStatus.ACTIVE,
                    10000L,
                    "HOST",
                    "LOCKED",
                    OffsetDateTime.parse("2026-06-01T00:00:00+09:00"),
                    OffsetDateTime.parse("2026-06-30T23:59:59+09:00"))),
            "Mg");
    given(meCrewService.findMyCrews(memberUuid, null, null, null, 20)).willReturn(response);

    authenticate(memberUuid);

    mockMvc
        .perform(get("/api/me/crews"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items[0].crew_id").value(1))
        .andExpect(jsonPath("$.items[0].title").value("아침 달리기 크루"))
        .andExpect(jsonPath("$.items[0].image_url").value("https://cdn.example.com/crew.jpg"))
        .andExpect(jsonPath("$.items[0].my_role").value("HOST"))
        .andExpect(jsonPath("$.items[0].my_status").value("LOCKED"))
        .andExpect(jsonPath("$.items[0].deposit_amount").value(10000))
        .andExpect(jsonPath("$.next_cursor").value("Mg"));
  }

  @Test
  void getMyCrewsWithRoleFilter() throws Exception {
    UUID memberUuid = UUID.fromString("018f4fd2-6d7a-7a41-9f58-6d07f5c3c901");
    given(meCrewService.findMyCrews(memberUuid, CrewParticipantRole.HOST, null, null, 20))
        .willReturn(new MeCrewListResponse(List.of(), null));

    authenticate(memberUuid);

    mockMvc
        .perform(get("/api/me/crews").param("role", "HOST"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items").isEmpty())
        .andExpect(jsonPath("$.next_cursor").value(nullValue()));
  }

  @Test
  void getMyCrewsWithStatusFilter() throws Exception {
    UUID memberUuid = UUID.fromString("018f4fd2-6d7a-7a41-9f58-6d07f5c3c901");
    given(meCrewService.findMyCrews(memberUuid, null, CrewParticipantStatus.PENDING, null, 20))
        .willReturn(new MeCrewListResponse(List.of(), null));

    authenticate(memberUuid);

    mockMvc
        .perform(get("/api/me/crews").param("myStatus", "PENDING"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items").isEmpty())
        .andExpect(jsonPath("$.next_cursor").value(nullValue()));
  }

  @Test
  void getMyCrewsWithInvalidStatusReturnsBadRequest() throws Exception {
    UUID memberUuid = UUID.fromString("018f4fd2-6d7a-7a41-9f58-6d07f5c3c901");

    authenticate(memberUuid);

    mockMvc
        .perform(get("/api/me/crews").param("myStatus", "INVALID"))
        .andExpect(status().isBadRequest());
  }

  @Test
  void getMyCrewsWithInvalidRoleReturnsBadRequest() throws Exception {
    UUID memberUuid = UUID.fromString("018f4fd2-6d7a-7a41-9f58-6d07f5c3c901");

    authenticate(memberUuid);

    mockMvc
        .perform(get("/api/me/crews").param("role", "INVALID"))
        .andExpect(status().isBadRequest());
  }

  @Test
  void getHostOperationSummaryFailWhenMemberDoesNotExist() throws Exception {
    UUID memberUuid = UUID.fromString("018f4fd2-6d7a-7a41-9f58-6d07f5c3c901");
    given(hostOperationSummaryService.findHostOperationSummaryByMemberUuid(memberUuid))
        .willThrow(new CustomException(MemberErrorCode.MEMBER_NOT_FOUND));

    authenticate(memberUuid);

    mockMvc
        .perform(get("/api/me/host-operation-summary"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("MEMBER_NOT_FOUND"));
  }

  private static void authenticate(UUID memberUuid) {
    SecurityContextHolder.getContext()
        .setAuthentication(
            new UsernamePasswordAuthenticationToken(memberUuid, null, Collections.emptyList()));
  }
}
