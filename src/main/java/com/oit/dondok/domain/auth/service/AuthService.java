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
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.Locale;
import java.util.UUID;
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

  /** 저장된 refresh token을 검증하고 rotation한 뒤 새 access token을 발급한다. */
  @Transactional
  public RefreshTokenResult refresh(String refreshToken) {
    if (refreshToken == null || refreshToken.isBlank()) {
      throw new CustomException(AuthErrorCode.REFRESH_TOKEN_INVALID);
    }

    TokenPayload refreshPayload = tokenProvider.parseRefreshToken(refreshToken);
    String tokenHash = hashToken(refreshToken);
    MemberRefreshToken savedToken =
        memberRefreshTokenRepository
            .findByTokenHash(tokenHash)
            .orElseThrow(() -> new CustomException(AuthErrorCode.REFRESH_TOKEN_INVALID));

    // Ensure the JWT subject matches the server-side refresh token owner.
    Member member = savedToken.getMember();
    if (!refreshPayload.memberUuid().equals(member.getUuid())) {
      throw new CustomException(AuthErrorCode.REFRESH_TOKEN_INVALID);
    }

    if (isRevoked(savedToken)) {
      throw new CustomException(AuthErrorCode.REFRESH_TOKEN_INVALID);
    }

    if (isExpired(savedToken, LocalDateTime.now())) {
      throw new CustomException(AuthErrorCode.REFRESH_TOKEN_EXPIRED);
    }

    if (member.getStatus() == MemberStatus.DEACTIVATED) {
      throw new CustomException(AuthErrorCode.MEMBER_DEACTIVATED);
    }

    String newAccessToken = tokenProvider.createAccessToken(member.getUuid());
    String newRefreshToken = tokenProvider.createRefreshToken(member.getUuid());
    TokenPayload newRefreshPayload = tokenProvider.parseRefreshToken(newRefreshToken);

    // Rotate only when the stored hash still matches the presented refresh token.
    rotate(savedToken, tokenHash, hashToken(newRefreshToken), newRefreshPayload.expiresAt());

    return new RefreshTokenResult(
        newAccessToken, newRefreshToken, secondsBetween(newRefreshPayload));
  }

  private void rotate(
      MemberRefreshToken refreshToken,
      String oldTokenHash,
      String newTokenHash,
      LocalDateTime expiresAt) {
    if (oldTokenHash == null || oldTokenHash.isBlank()) {
      throw new IllegalArgumentException("oldTokenHash must not be null or blank");
    }
    if (newTokenHash == null || newTokenHash.isBlank()) {
      throw new IllegalArgumentException("newTokenHash must not be null or blank");
    }
    if (expiresAt == null) {
      throw new IllegalArgumentException("expiresAt must not be null");
    }
    if (!expiresAt.isAfter(LocalDateTime.now())) {
      throw new IllegalArgumentException("expiresAt must be a future time");
    }

    int updatedRows =
        memberRefreshTokenRepository.rotateById(
            refreshToken.getId(), oldTokenHash, newTokenHash, expiresAt);

    if (updatedRows != 1) {
      throw new CustomException(AuthErrorCode.REFRESH_TOKEN_INVALID);
    }
  }

  @Transactional
  public void logout(UUID memberUuid, String refreshToken) {
    if (memberUuid == null || refreshToken == null || refreshToken.isBlank()) {
      return;
    }

    String tokenHash = hashToken(refreshToken);
    MemberRefreshToken savedToken =
        memberRefreshTokenRepository.findByTokenHash(tokenHash).orElse(null);

    if (savedToken == null) {
      return;
    }

    // Logout is access-token based; only revoke a refresh token owned by the authenticated member.
    Member member = savedToken.getMember();
    if (!memberUuid.equals(member.getUuid()) || isRevoked(savedToken)) {
      return;
    }

    memberRefreshTokenRepository.revokeById(savedToken.getId(), LocalDateTime.now());
  }

  private boolean isExpired(MemberRefreshToken refreshToken, LocalDateTime now) {
    if (now == null) {
      throw new IllegalArgumentException("now must not be null");
    }

    return !refreshToken.getExpiresAt().isAfter(now);
  }

  private boolean isRevoked(MemberRefreshToken refreshToken) {
    return refreshToken.getRevokedAt() != null;
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
