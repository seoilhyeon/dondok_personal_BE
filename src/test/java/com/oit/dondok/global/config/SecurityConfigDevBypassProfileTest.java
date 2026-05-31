package com.oit.dondok.global.config;

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.oit.dondok.domain.member.controller.MemberProfileController;
import com.oit.dondok.domain.member.dto.response.ProfileResponse;
import com.oit.dondok.domain.member.entity.MemberStatus;
import com.oit.dondok.domain.member.service.MemberProfileService;
import com.oit.dondok.global.exception.GlobalExceptionHandler;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@ActiveProfiles({"local", "dev"})
@WebMvcTest(MemberProfileController.class)
@AutoConfigureMockMvc
@Import({SecurityConfig.class, GlobalExceptionHandler.class})
class SecurityConfigDevBypassProfileTest {

  @Autowired private MockMvc mockMvc;

  @MockBean private MemberProfileService memberProfileService;

  @Test
  void getProfilePermitsDevMemberUuidBypassInLocalOrDevProfiles() throws Exception {
    UUID memberUuid = UUID.fromString("018f4fd2-6d7a-7a41-9f58-6d07f5c3c901");
    ProfileResponse response =
        new ProfileResponse(
            memberUuid,
            "user@example.com",
            "돈독러",
            null,
            null,
            false,
            0L,
            MemberStatus.ACTIVE,
            OffsetDateTime.parse("2026-05-31T09:00:00+09:00"));
    given(memberProfileService.findProfileByMemberUuid(memberUuid)).willReturn(response);

    mockMvc
        .perform(get("/api/me").header(MemberProfileController.DEV_MEMBER_UUID_HEADER, memberUuid))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.member_uuid").value(memberUuid.toString()));
  }
}
