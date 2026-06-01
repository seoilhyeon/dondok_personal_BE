package com.oit.dondok.domain.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.oit.dondok.domain.auth.entity.MemberRefreshToken;
import com.oit.dondok.domain.auth.exception.AuthErrorCode;
import com.oit.dondok.domain.auth.repository.MemberRefreshTokenRepository;
import com.oit.dondok.domain.member.entity.Member;
import com.oit.dondok.domain.member.entity.MemberStatus;
import com.oit.dondok.domain.member.repository.MemberRepository;
import com.oit.dondok.global.exception.CustomException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.password.PasswordEncoder;

class AuthServiceTest {

  private final MemberRepository memberRepository =
      org.mockito.Mockito.mock(MemberRepository.class);
  private final MemberRefreshTokenRepository memberRefreshTokenRepository =
      org.mockito.Mockito.mock(MemberRefreshTokenRepository.class);
  private final PasswordEncoder passwordEncoder = org.mockito.Mockito.mock(PasswordEncoder.class);
  private final TokenProvider tokenProvider = org.mockito.Mockito.mock(TokenProvider.class);

  private final AuthService authService =
      new AuthService(
          memberRepository, memberRefreshTokenRepository, passwordEncoder, tokenProvider);

  @Test
  void loginIssuesTokensAndStoresHashedRefreshToken() {
    Member member = Member.create("user@example.com", "encoded-password", "tester");
    LocalDateTime issuedAt = LocalDateTime.parse("2026-05-31T00:00:00");
    String accessToken = "access-token";
    String refreshToken = "refresh-token";

    given(memberRepository.findByEmail("user@example.com")).willReturn(Optional.of(member));
    given(passwordEncoder.matches("raw-password", "encoded-password")).willReturn(true);
    given(tokenProvider.createAccessToken(member.getUuid())).willReturn(accessToken);
    given(tokenProvider.createRefreshToken(member.getUuid())).willReturn(refreshToken);
    given(tokenProvider.parseAccessToken(accessToken))
        .willReturn(new TokenPayload(member.getUuid(), issuedAt, issuedAt.plusMinutes(30)));
    given(tokenProvider.parseRefreshToken(refreshToken))
        .willReturn(new TokenPayload(member.getUuid(), issuedAt, issuedAt.plusDays(7)));

    LoginResult result = authService.login(" USER@example.com ", "raw-password");

    assertThat(result.accessToken()).isEqualTo(accessToken);
    assertThat(result.refreshToken()).isEqualTo(refreshToken);
    assertThat(result.accessTokenExpiresIn()).isEqualTo(1800);
    assertThat(result.refreshTokenMaxAge()).isEqualTo(604800);
    assertThat(result.memberUuid()).isEqualTo(member.getUuid());

    ArgumentCaptor<MemberRefreshToken> tokenCaptor =
        ArgumentCaptor.forClass(MemberRefreshToken.class);
    verify(memberRefreshTokenRepository).save(tokenCaptor.capture());
    assertThat(tokenCaptor.getValue().getTokenHash()).hasSize(64).isNotEqualTo(refreshToken);
  }

  @Test
  void loginRejectsInvalidPassword() {
    Member member = Member.create("user@example.com", "encoded-password", "tester");

    given(memberRepository.findByEmail("user@example.com")).willReturn(Optional.of(member));
    given(passwordEncoder.matches("wrong-password", "encoded-password")).willReturn(false);

    assertThatThrownBy(() -> authService.login("user@example.com", "wrong-password"))
        .isInstanceOfSatisfying(
            CustomException.class,
            exception ->
                assertThat(exception.getErrorCode()).isEqualTo(AuthErrorCode.INVALID_CREDENTIALS));
    verify(memberRefreshTokenRepository, never()).save(any());
  }

  @Test
  void loginRejectsUnknownEmail() {
    given(memberRepository.findByEmail("unknown@example.com")).willReturn(Optional.empty());

    assertThatThrownBy(() -> authService.login("unknown@example.com", "raw-password"))
        .isInstanceOfSatisfying(
            CustomException.class,
            exception ->
                assertThat(exception.getErrorCode()).isEqualTo(AuthErrorCode.INVALID_CREDENTIALS));
    verify(memberRefreshTokenRepository, never()).save(any());
  }

  @Test
  void loginRejectsDeactivatedMember() {
    Member member = org.mockito.Mockito.mock(Member.class);
    UUID memberUuid = UUID.randomUUID();

    given(memberRepository.findByEmail("user@example.com")).willReturn(Optional.of(member));
    given(member.getUuid()).willReturn(memberUuid);
    given(member.getPasswordHash()).willReturn("encoded-password");
    given(member.getStatus()).willReturn(MemberStatus.DEACTIVATED);
    given(passwordEncoder.matches("raw-password", "encoded-password")).willReturn(true);

    assertThatThrownBy(() -> authService.login("user@example.com", "raw-password"))
        .isInstanceOfSatisfying(
            CustomException.class,
            exception ->
                assertThat(exception.getErrorCode()).isEqualTo(AuthErrorCode.MEMBER_DEACTIVATED));
    verify(memberRefreshTokenRepository, never()).save(any());
  }

  @Test
  void refreshRotatesRefreshTokenAndIssuesNewAccessToken() {
    Member member = Member.create("user@example.com", "encoded-password", "tester");
    LocalDateTime issuedAt = LocalDateTime.now().minusMinutes(1);
    String refreshToken = "refresh-token";
    String newAccessToken = "new-access-token";
    String newRefreshToken = "new-refresh-token";
    MemberRefreshToken savedToken = mock(MemberRefreshToken.class);

    given(tokenProvider.parseRefreshToken(refreshToken))
        .willReturn(new TokenPayload(member.getUuid(), issuedAt, issuedAt.plusDays(7)));
    given(memberRefreshTokenRepository.findByTokenHash(hashToken(refreshToken)))
        .willReturn(Optional.of(savedToken));
    given(savedToken.getId()).willReturn(1L);
    given(savedToken.getMember()).willReturn(member);
    given(savedToken.getExpiresAt()).willReturn(issuedAt.plusDays(7));
    given(tokenProvider.createAccessToken(member.getUuid())).willReturn(newAccessToken);
    given(tokenProvider.createRefreshToken(member.getUuid())).willReturn(newRefreshToken);
    given(tokenProvider.parseRefreshToken(newRefreshToken))
        .willReturn(new TokenPayload(member.getUuid(), issuedAt, issuedAt.plusDays(14)));
    given(
            memberRefreshTokenRepository.rotateById(
                1L, hashToken(newRefreshToken), issuedAt.plusDays(14)))
        .willReturn(1);

    RefreshTokenResult result = authService.refresh(refreshToken);

    assertThat(result.accessToken()).isEqualTo(newAccessToken);
    assertThat(result.refreshToken()).isEqualTo(newRefreshToken);
    assertThat(result.refreshTokenMaxAge()).isEqualTo(1209600);
    verify(memberRefreshTokenRepository)
        .rotateById(1L, hashToken(newRefreshToken), issuedAt.plusDays(14));
  }

  @Test
  void refreshRejectsDeactivatedMember() {
    Member member = mock(Member.class);
    MemberRefreshToken savedToken = mock(MemberRefreshToken.class);
    LocalDateTime issuedAt = LocalDateTime.now().minusMinutes(1);
    UUID memberUuid = UUID.randomUUID();
    String refreshToken = "refresh-token";

    given(tokenProvider.parseRefreshToken(refreshToken))
        .willReturn(new TokenPayload(memberUuid, issuedAt, issuedAt.plusDays(7)));
    given(memberRefreshTokenRepository.findByTokenHash(hashToken(refreshToken)))
        .willReturn(Optional.of(savedToken));
    given(savedToken.getMember()).willReturn(member);
    given(savedToken.getExpiresAt()).willReturn(issuedAt.plusDays(7));
    given(member.getStatus()).willReturn(MemberStatus.DEACTIVATED);

    assertThatThrownBy(() -> authService.refresh(refreshToken))
        .isInstanceOfSatisfying(
            CustomException.class,
            exception ->
                assertThat(exception.getErrorCode()).isEqualTo(AuthErrorCode.MEMBER_DEACTIVATED));

    verify(memberRefreshTokenRepository, never()).rotateById(any(), any(), any());
  }

  @Test
  void refreshRejectsBlankRefreshToken() {
    assertThatThrownBy(() -> authService.refresh(" "))
        .isInstanceOfSatisfying(
            CustomException.class,
            exception ->
                assertThat(exception.getErrorCode())
                    .isEqualTo(AuthErrorCode.REFRESH_TOKEN_INVALID));

    verify(memberRefreshTokenRepository, never()).findByTokenHash(any());
  }

  @Test
  void refreshRejectsUnknownRefreshTokenHash() {
    LocalDateTime issuedAt = LocalDateTime.parse("2026-05-31T00:00:00");
    UUID memberUuid = UUID.randomUUID();
    String refreshToken = "refresh-token";

    given(tokenProvider.parseRefreshToken(refreshToken))
        .willReturn(new TokenPayload(memberUuid, issuedAt, issuedAt.plusDays(7)));
    given(memberRefreshTokenRepository.findByTokenHash(hashToken(refreshToken)))
        .willReturn(Optional.empty());

    assertThatThrownBy(() -> authService.refresh(refreshToken))
        .isInstanceOfSatisfying(
            CustomException.class,
            exception ->
                assertThat(exception.getErrorCode())
                    .isEqualTo(AuthErrorCode.REFRESH_TOKEN_INVALID));
  }

  @Test
  void refreshRejectsExpiredRefreshToken() {
    String refreshToken = "expired-refresh-token";

    given(tokenProvider.parseRefreshToken(refreshToken))
        .willThrow(new CustomException(AuthErrorCode.REFRESH_TOKEN_EXPIRED));

    assertThatThrownBy(() -> authService.refresh(refreshToken))
        .isInstanceOfSatisfying(
            CustomException.class,
            exception ->
                assertThat(exception.getErrorCode())
                    .isEqualTo(AuthErrorCode.REFRESH_TOKEN_EXPIRED));

    verify(memberRefreshTokenRepository, never()).findByTokenHash(any());
  }

  private String hashToken(String token) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      byte[] hash = digest.digest(token.getBytes(StandardCharsets.UTF_8));
      return HexFormat.of().formatHex(hash);
    } catch (NoSuchAlgorithmException exception) {
      throw new IllegalStateException("SHA-256 algorithm is not available.", exception);
    }
  }
}
