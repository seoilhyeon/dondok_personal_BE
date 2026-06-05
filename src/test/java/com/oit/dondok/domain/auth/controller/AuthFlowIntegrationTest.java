package com.oit.dondok.domain.auth.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.oit.dondok.domain.auth.repository.MemberRefreshTokenRepository;
import com.oit.dondok.domain.crew.port.CrewPointPort;
import com.oit.dondok.domain.member.entity.Member;
import com.oit.dondok.domain.member.entity.MemberStatus;
import com.oit.dondok.domain.member.repository.MemberRepository;
import jakarta.servlet.http.Cookie;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
class AuthFlowIntegrationTest {

  private static final String ORIGIN = "http://localhost:3000";

  @Autowired private MockMvc mockMvc;

  @Autowired private ObjectMapper objectMapper;

  @Autowired private MemberRepository memberRepository;

  @Autowired private MemberRefreshTokenRepository memberRefreshTokenRepository;

  @Autowired private PasswordEncoder passwordEncoder;

  @MockBean private CrewPointPort crewPointPort;

  @BeforeEach
  void deleteRows() {
    memberRefreshTokenRepository.deleteAll();
    memberRepository.deleteAll();
  }

  // 회원가입부터 로그아웃까지 실제 인증 흐름과 refresh token rotation을 검증한다.
  @Test
  void signupLoginRefreshAndLogoutFlow() throws Exception {
    String email = uniqueEmail();
    String password = "password1234";
    String nickname = uniqueNickname();

    mockMvc
        .perform(
            post("/api/member/signup")
                .contentType(MediaType.APPLICATION_JSON)
                .content(signupBody(email, password, nickname)))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.email").value(email))
        .andExpect(jsonPath("$.nickname").value(nickname))
        .andExpect(jsonPath("$.status").value("ACTIVE"));

    MvcResult loginResult =
        mockMvc
            .perform(
                post("/api/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(loginBody(email, password)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.access_token").exists())
            .andExpect(jsonPath("$.token_type").value("Bearer"))
            .andExpect(jsonPath("$.member.email").value(email))
            .andExpect(cookie().exists("refreshToken"))
            .andExpect(cookie().httpOnly("refreshToken", true))
            .andExpect(cookie().secure("refreshToken", false))
            .andExpect(
                header()
                    .string(
                        HttpHeaders.CACHE_CONTROL,
                        "no-store, no-cache, must-revalidate, max-age=0"))
            .andExpect(header().string(HttpHeaders.PRAGMA, "no-cache"))
            .andExpect(header().string(HttpHeaders.EXPIRES, "0"))
            .andExpect(
                header()
                    .string(
                        HttpHeaders.SET_COOKIE,
                        org.hamcrest.Matchers.containsString("SameSite=Lax")))
            .andReturn();

    String accessToken = accessToken(loginResult);
    Cookie refreshCookie = refreshCookie(loginResult);
    assertThat(memberRefreshTokenRepository.findAll()).hasSize(1);

    MvcResult refreshResult =
        mockMvc
            .perform(
                post("/api/auth/refresh").header(HttpHeaders.ORIGIN, ORIGIN).cookie(refreshCookie))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.access_token").exists())
            .andExpect(cookie().exists("refreshToken"))
            .andExpect(cookie().httpOnly("refreshToken", true))
            .andExpect(cookie().secure("refreshToken", false))
            .andExpect(
                header()
                    .string(
                        HttpHeaders.SET_COOKIE,
                        org.hamcrest.Matchers.containsString("SameSite=Lax")))
            .andReturn();

    Cookie rotatedRefreshCookie = refreshCookie(refreshResult);
    assertThat(rotatedRefreshCookie.getValue()).isNotEqualTo(refreshCookie.getValue());

    mockMvc
        .perform(post("/api/auth/refresh").header(HttpHeaders.ORIGIN, ORIGIN).cookie(refreshCookie))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("REFRESH_TOKEN_INVALID"));

    mockMvc
        .perform(
            post("/api/auth/logout")
                .header(HttpHeaders.ORIGIN, ORIGIN)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                .cookie(rotatedRefreshCookie))
        .andExpect(status().isNoContent())
        .andExpect(cookie().value("refreshToken", ""))
        .andExpect(cookie().maxAge("refreshToken", 0))
        .andExpect(cookie().httpOnly("refreshToken", true))
        .andExpect(cookie().secure("refreshToken", false))
        .andExpect(
            header()
                .string(
                    HttpHeaders.SET_COOKIE, org.hamcrest.Matchers.containsString("SameSite=Lax")));

    assertThat(memberRefreshTokenRepository.findAll())
        .singleElement()
        .satisfies(
            token -> {
              assertThat(token.getRevokedAt()).isNotNull();
            });

    mockMvc
        .perform(
            post("/api/auth/refresh")
                .header(HttpHeaders.ORIGIN, ORIGIN)
                .cookie(rotatedRefreshCookie))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("REFRESH_TOKEN_INVALID"));
  }

  // 회원가입 시 이메일과 닉네임 중복을 각각 거절하는지 검증한다.
  @Test
  void signupRejectsDuplicateEmailAndNickname() throws Exception {
    String email = uniqueEmail();
    String nickname = uniqueNickname();

    mockMvc
        .perform(
            post("/api/member/signup")
                .contentType(MediaType.APPLICATION_JSON)
                .content(signupBody(email, "password1234", nickname)))
        .andExpect(status().isCreated());

    mockMvc
        .perform(
            post("/api/member/signup")
                .contentType(MediaType.APPLICATION_JSON)
                .content(signupBody(email.toUpperCase(), "password1234", uniqueNickname())))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("EMAIL_ALREADY_EXISTS"));

    mockMvc
        .perform(
            post("/api/member/signup")
                .contentType(MediaType.APPLICATION_JSON)
                .content(signupBody(uniqueEmail(), "password1234", nickname)))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("NICKNAME_ALREADY_EXISTS"));
  }

  // 로그인 시 잘못된 비밀번호와 비활성 회원을 거절하는지 검증한다.
  @Test
  void loginRejectsWrongPasswordAndDeactivatedMember() throws Exception {
    String email = uniqueEmail();
    String password = "password1234";
    Member member =
        memberRepository.save(
            Member.create(email, passwordEncoder.encode(password), uniqueNickname()));

    mockMvc
        .perform(
            post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(loginBody(email, "wrong-password")))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));

    ReflectionTestUtils.setField(member, "status", MemberStatus.DEACTIVATED);
    memberRepository.saveAndFlush(member);

    mockMvc
        .perform(
            post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(loginBody(email, password)))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("MEMBER_DEACTIVATED"));
  }

  // 로그인 요청 이메일 형식이 잘못되면 입력 검증 오류를 반환하는지 검증한다.
  @Test
  void loginRejectsInvalidEmail() throws Exception {
    mockMvc
        .perform(
            post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(loginBody("user@localhost", "password1234")))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
  }

  // refresh 요청에서 쿠키 누락과 유효하지 않은 토큰을 거절하는지 검증한다.
  @Test
  void refreshRejectsMissingCookieAndInvalidToken() throws Exception {
    mockMvc
        .perform(post("/api/auth/refresh").header(HttpHeaders.ORIGIN, ORIGIN))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("REFRESH_TOKEN_INVALID"));

    mockMvc
        .perform(
            post("/api/auth/refresh")
                .header(HttpHeaders.ORIGIN, ORIGIN)
                .cookie(new Cookie("refreshToken", "invalid-refresh-token")))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("REFRESH_TOKEN_INVALID"));
  }

  // 로그아웃 요청은 access token 인증이 필요하다는 것을 검증한다.
  @Test
  void logoutRequiresAccessToken() throws Exception {
    mockMvc
        .perform(
            post("/api/auth/logout")
                .header(HttpHeaders.ORIGIN, ORIGIN)
                .cookie(new Cookie("refreshToken", "refresh-token")))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
  }

  private String signupBody(String email, String password, String nickname) throws Exception {
    return objectMapper.writeValueAsString(
        Map.of("email", email, "password", password, "nickname", nickname));
  }

  private String loginBody(String email, String password) throws Exception {
    return objectMapper.writeValueAsString(Map.of("email", email, "password", password));
  }

  private String accessToken(MvcResult result) throws Exception {
    JsonNode responseBody = objectMapper.readTree(result.getResponse().getContentAsString());
    return responseBody.get("access_token").asText();
  }

  private Cookie refreshCookie(MvcResult result) {
    Cookie cookie = result.getResponse().getCookie("refreshToken");
    assertThat(cookie).isNotNull();
    return cookie;
  }

  private String uniqueEmail() {
    return "auth-" + UUID.randomUUID() + "@example.com";
  }

  private String uniqueNickname() {
    return "n" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
  }
}
