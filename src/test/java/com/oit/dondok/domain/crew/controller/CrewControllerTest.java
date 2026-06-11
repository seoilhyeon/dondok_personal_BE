package com.oit.dondok.domain.crew.controller;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.oit.dondok.domain.crew.dto.response.CrewMemberResponse;
import com.oit.dondok.domain.crew.dto.response.CrewMembersResponse;
import com.oit.dondok.domain.crew.exception.CrewErrorCode;
import com.oit.dondok.domain.crew.service.CrewService;
import com.oit.dondok.global.exception.CustomException;
import com.oit.dondok.global.exception.GlobalExceptionHandler;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.Base64;
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

@WebMvcTest(CrewController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
class CrewControllerTest {

  private static final UUID MEMBER_UUID = UUID.fromString("018f4fd2-6d7a-7a41-9f58-6d07f5c3c901");
  private static final Long CREW_ID = 1L;

  @Autowired private MockMvc mockMvc;

  @MockBean private CrewService crewService;

  @BeforeEach
  void setUpAuthentication() {
    SecurityContextHolder.getContext()
        .setAuthentication(new UsernamePasswordAuthenticationToken(MEMBER_UUID, null, List.of()));
  }

  @AfterEach
  void clearAuthentication() {
    SecurityContextHolder.clearContext();
  }

  // ======================== GET /{crewId}/members ========================

  @Test
  void getCrewMembersReturnsItemListWithCorrectJsonShape() throws Exception {
    String joinedAt = "2026-05-08T13:00:00+09:00";
    UUID memberUuid2 = UUID.fromString("018f4fd2-6d7a-7a41-9f58-6d07f5c3c902");

    given(crewService.findCrewMembers(eq(CREW_ID), eq(MEMBER_UUID), isNull(), eq(50)))
        .willReturn(
            new CrewMembersResponse(
                List.of(
                    new CrewMemberResponse(
                        101L,
                        MEMBER_UUID,
                        "호스트닉네임",
                        null,
                        "HOST",
                        "LOCKED",
                        OffsetDateTime.parse(joinedAt)),
                    new CrewMemberResponse(
                        102L,
                        memberUuid2,
                        "멤버닉네임",
                        null,
                        "MEMBER",
                        "LOCKED",
                        OffsetDateTime.parse(joinedAt))),
                null));

    mockMvc
        .perform(get("/api/crews/{crewId}/members", CREW_ID))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items").isArray())
        .andExpect(jsonPath("$.items.length()").value(2))
        .andExpect(jsonPath("$.items[0].crew_participant_id").value(101))
        .andExpect(jsonPath("$.items[0].member_uuid").value(MEMBER_UUID.toString()))
        .andExpect(jsonPath("$.items[0].nickname").value("호스트닉네임"))
        .andExpect(jsonPath("$.items[0].role").value("HOST"))
        .andExpect(jsonPath("$.items[0].status").value("LOCKED"))
        .andExpect(jsonPath("$.items[0].joined_at").value(joinedAt))
        .andExpect(jsonPath("$.items[1].crew_participant_id").value(102))
        .andExpect(jsonPath("$.items[1].role").value("MEMBER"))
        .andExpect(jsonPath("$.next_cursor").doesNotExist());

    then(crewService).should().findCrewMembers(CREW_ID, MEMBER_UUID, null, 50);
  }

  @Test
  void getCrewMembersReturnsNextCursorWhenHasNextPage() throws Exception {
    String nextCursor = encodeCursor(999L);

    given(crewService.findCrewMembers(eq(CREW_ID), eq(MEMBER_UUID), isNull(), eq(1)))
        .willReturn(
            new CrewMembersResponse(
                List.of(
                    new CrewMemberResponse(
                        101L,
                        MEMBER_UUID,
                        "닉네임",
                        null,
                        "HOST",
                        "LOCKED",
                        OffsetDateTime.parse("2026-05-08T13:00:00+09:00"))),
                nextCursor));

    mockMvc
        .perform(get("/api/crews/{crewId}/members", CREW_ID).param("limit", "1"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items.length()").value(1))
        .andExpect(jsonPath("$.next_cursor").value(nextCursor));
  }

  @Test
  void getCrewMembersPassesCursorAndLimitToService() throws Exception {
    String cursor = encodeCursor(100L);

    given(crewService.findCrewMembers(eq(CREW_ID), eq(MEMBER_UUID), eq(cursor), eq(20)))
        .willReturn(new CrewMembersResponse(List.of(), null));

    mockMvc
        .perform(
            get("/api/crews/{crewId}/members", CREW_ID)
                .param("cursor", cursor)
                .param("limit", "20"))
        .andExpect(status().isOk());

    then(crewService).should().findCrewMembers(CREW_ID, MEMBER_UUID, cursor, 20);
  }

  @Test
  void getCrewMembersReturnsEmptyItemsWhenNoMembers() throws Exception {
    given(crewService.findCrewMembers(eq(CREW_ID), eq(MEMBER_UUID), isNull(), eq(50)))
        .willReturn(new CrewMembersResponse(List.of(), null));

    mockMvc
        .perform(get("/api/crews/{crewId}/members", CREW_ID))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items").isArray())
        .andExpect(jsonPath("$.items").isEmpty());
  }

  @Test
  void getCrewMembersReturns404WhenCrewNotFound() throws Exception {
    given(crewService.findCrewMembers(eq(CREW_ID), eq(MEMBER_UUID), isNull(), eq(50)))
        .willThrow(new CustomException(CrewErrorCode.CREW_NOT_FOUND));

    mockMvc
        .perform(get("/api/crews/{crewId}/members", CREW_ID))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("CREW_NOT_FOUND"));
  }

  @Test
  void getCrewMembersReturns403WhenAccessDenied() throws Exception {
    given(crewService.findCrewMembers(eq(CREW_ID), eq(MEMBER_UUID), isNull(), eq(50)))
        .willThrow(new CustomException(CrewErrorCode.CREW_ACCESS_DENIED));

    mockMvc
        .perform(get("/api/crews/{crewId}/members", CREW_ID))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("CREW_ACCESS_DENIED"));
  }

  private static String encodeCursor(Long id) {
    return Base64.getUrlEncoder()
        .withoutPadding()
        .encodeToString(String.valueOf(id).getBytes(StandardCharsets.UTF_8));
  }
}
