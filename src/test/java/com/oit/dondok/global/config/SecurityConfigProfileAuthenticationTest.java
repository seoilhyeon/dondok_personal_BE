package com.oit.dondok.global.config;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.oit.dondok.domain.auth.service.TokenPayload;
import com.oit.dondok.domain.auth.service.TokenProvider;
import com.oit.dondok.domain.member.controller.MemberProfileController;
import com.oit.dondok.domain.member.dto.response.ProfileResponse;
import com.oit.dondok.domain.member.entity.MemberStatus;
import com.oit.dondok.domain.member.service.MemberProfileService;
import com.oit.dondok.global.exception.GlobalExceptionHandler;
import com.oit.dondok.infrastructure.auth.config.SecurityConfig;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@ActiveProfiles("local")
@WebMvcTest(MemberProfileController.class)
@AutoConfigureMockMvc
@Import({SecurityConfig.class, GlobalExceptionHandler.class})
class SecurityConfigProfileAuthenticationTest {

  @Autowired private MockMvc mockMvc;

  @MockBean private MemberProfileService memberProfileService;

  @MockBean private TokenProvider tokenProvider;

  @Test
  void getProfileRequiresAuthentication() throws Exception {
    mockMvc
        .perform(get("/api/me"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));

    verifyNoInteractions(memberProfileService);
  }

  @Test
  void getProfilePermitsValidAccessToken() throws Exception {
    UUID memberUuid = UUID.fromString("018f4fd2-6d7a-7a41-9f58-6d07f5c3c901");
    given(tokenProvider.parseAccessToken("valid-token")).willReturn(tokenPayload(memberUuid));
    given(memberProfileService.findProfileByMemberUuid(memberUuid))
        .willReturn(profileResponse(memberUuid));

    mockMvc
        .perform(get("/api/me").header(HttpHeaders.AUTHORIZATION, "Bearer valid-token"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.member_uuid").value(memberUuid.toString()));
  }

  private static TokenPayload tokenPayload(UUID memberUuid) {
    return new TokenPayload(
        memberUuid,
        LocalDateTime.parse("2026-05-31T09:00:00"),
        LocalDateTime.parse("2026-05-31T09:30:00"));
  }

  private static ProfileResponse profileResponse(UUID memberUuid) {
    return new ProfileResponse(
        memberUuid,
        "user@example.com",
        "돈독러",
        null,
        null,
        false,
        0L,
        MemberStatus.ACTIVE,
        OffsetDateTime.parse("2026-05-31T09:00:00+09:00"));
  }
}
