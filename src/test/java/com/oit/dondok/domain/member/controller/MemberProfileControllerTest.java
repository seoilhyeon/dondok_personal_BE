package com.oit.dondok.domain.member.controller;

import static org.hamcrest.Matchers.nullValue;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.oit.dondok.domain.member.dto.response.ProfileResponse;
import com.oit.dondok.domain.member.entity.MemberStatus;
import com.oit.dondok.domain.member.exception.MemberErrorCode;
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

  private static void authenticate(UUID memberUuid) {
    SecurityContextHolder.getContext()
        .setAuthentication(
            new UsernamePasswordAuthenticationToken(memberUuid, null, Collections.emptyList()));
  }
}
