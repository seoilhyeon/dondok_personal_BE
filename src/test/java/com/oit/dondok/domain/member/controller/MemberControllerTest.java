package com.oit.dondok.domain.member.controller;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.oit.dondok.domain.member.dto.request.SignupRequest;
import com.oit.dondok.domain.member.entity.Member;
import com.oit.dondok.domain.member.entity.MemberStatus;
import com.oit.dondok.domain.member.exception.MemberErrorCode;
import com.oit.dondok.domain.member.service.MemberService;
import com.oit.dondok.global.exception.CustomException;
import com.oit.dondok.global.exception.GlobalExceptionHandler;
import java.time.LocalDateTime;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(MemberController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
class MemberControllerTest {

  @Autowired private MockMvc mockMvc;

  @Autowired private ObjectMapper objectMapper;

  @MockBean private MemberService memberService;

  @Test
  void signupSuccess() throws Exception {
    SignupRequest request = new SignupRequest("user@example.com", "password1234", "돈독러");

    Member member = mock(Member.class);
    given(member.getUuid()).willReturn(UUID.fromString("018f4fd2-6d7a-7a41-9f58-6d07f5c3c901"));
    given(member.getEmail()).willReturn("user@example.com");
    given(member.getNickname()).willReturn("돈독러");
    given(member.getStatus()).willReturn(MemberStatus.ACTIVE);
    given(member.getCreatedAt()).willReturn(LocalDateTime.parse("2026-05-07T09:00:00"));

    given(memberService.signup(anyString(), anyString(), anyString())).willReturn(member);

    mockMvc
        .perform(
            post("/api/member/signup")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.member_uuid").value("018f4fd2-6d7a-7a41-9f58-6d07f5c3c901"))
        .andExpect(jsonPath("$.email").value("user@example.com"))
        .andExpect(jsonPath("$.nickname").value("돈독러"))
        .andExpect(jsonPath("$.status").value("ACTIVE"))
        .andExpect(jsonPath("$.created_at").value("2026-05-07T09:00:00"));
  }

  @Test
  void signupFailWhenEmailAlreadyExists() throws Exception {
    SignupRequest request = new SignupRequest("user@example.com", "password1234", "돈독러");

    given(memberService.signup(anyString(), anyString(), anyString()))
        .willThrow(new CustomException(MemberErrorCode.EMAIL_ALREADY_EXISTS));

    mockMvc
        .perform(
            post("/api/member/signup")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("EMAIL_ALREADY_EXISTS"));
  }

  @Test
  void signupFailWhenNicknameAlreadyExists() throws Exception {
    SignupRequest request = new SignupRequest("user@example.com", "password1234", "돈독러");

    given(memberService.signup(anyString(), anyString(), anyString()))
        .willThrow(new CustomException(MemberErrorCode.NICKNAME_ALREADY_EXISTS));

    mockMvc
        .perform(
            post("/api/member/signup")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("NICKNAME_ALREADY_EXISTS"));
  }

  @Test
  void signupFailWhenRequestIsInvalid() throws Exception {
    SignupRequest request = new SignupRequest("not-email", "short", "a");

    mockMvc
        .perform(
            post("/api/member/signup")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
  }

  @Test
  void signupFailWhenEmailHasNoTopLevelDomain() throws Exception {
    SignupRequest request = new SignupRequest("user@localhost", "password1234", "돈독러");

    mockMvc
        .perform(
            post("/api/member/signup")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
  }

  @Test
  void signupFailWhenNicknameHasLeadingOrTrailingWhitespace() throws Exception {
    SignupRequest request = new SignupRequest("user@example.com", "password1234", " 돈독러 ");

    mockMvc
        .perform(
            post("/api/member/signup")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
  }
}
