package com.oit.dondok.domain.auth.service;

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
import java.time.Duration;
import java.util.HexFormat;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuthService {

  private final MemberRepository memberRepository;
  private final MemberRefreshTokenRepository memberRefreshTokenRepository;
  private final PasswordEncoder passwordEncoder;
  private final TokenProvider tokenProvider;

  /** 로그인 정보를 검증하고 JWT를 발급한 뒤 refresh token hash를 저장한다. */
  @Transactional
  public LoginResult login(String email, String password) {
    Member member = findActiveMemberByCredentials(email, password);

    String accessToken = tokenProvider.createAccessToken(member.getUuid());
    String refreshToken = tokenProvider.createRefreshToken(member.getUuid());
    TokenPayload accessPayload = tokenProvider.parseAccessToken(accessToken);
    TokenPayload refreshPayload = tokenProvider.parseRefreshToken(refreshToken);

    memberRefreshTokenRepository.save(
        MemberRefreshToken.create(member, hashToken(refreshToken), refreshPayload.expiresAt()));

    return new LoginResult(
        accessToken,
        refreshToken,
        secondsBetween(accessPayload),
        secondsBetween(refreshPayload),
        member.getUuid(),
        member.getEmail(),
        member.getNickname());
  }

  /** 이메일과 비밀번호 중 어느 값이 틀렸는지 숨긴 채 로그인 대상 회원을 찾는다. */
  private Member findActiveMemberByCredentials(String email, String password) {
    String normalizedEmail = email.trim().toLowerCase(Locale.ROOT);
    Member member =
        memberRepository
            .findByEmail(normalizedEmail)
            .orElseThrow(() -> new CustomException(AuthErrorCode.INVALID_CREDENTIALS));

    if (member.getPasswordHash() == null
        || !passwordEncoder.matches(password, member.getPasswordHash())) {
      throw new CustomException(AuthErrorCode.INVALID_CREDENTIALS);
    }

    if (member.getStatus() == MemberStatus.DEACTIVATED) {
      throw new CustomException(AuthErrorCode.MEMBER_DEACTIVATED);
    }

    return member;
  }

  /** 원문 refresh token이 저장되지 않도록 저장 전에 SHA-256으로 해시한다. */
  private String hashToken(String token) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      byte[] hash = digest.digest(token.getBytes(StandardCharsets.UTF_8));
      return HexFormat.of().formatHex(hash);
    } catch (NoSuchAlgorithmException exception) {
      throw new IllegalStateException("SHA-256 algorithm is not available.", exception);
    }
  }

  private long secondsBetween(TokenPayload payload) {
    return Duration.between(payload.issuedAt(), payload.expiresAt()).getSeconds();
  }
}
