package com.oit.dondok.infrastructure.auth.token;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.oit.dondok.domain.auth.exception.AuthErrorCode;
import com.oit.dondok.domain.auth.service.TokenPayload;
import com.oit.dondok.global.exception.CustomException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;
import javax.crypto.SecretKey;
import org.junit.jupiter.api.Test;

class JjwtTokenProviderTest {

  private static final String ISSUER = "dondok-test";
  private static final Duration ACCESS_TOKEN_EXPIRATION = Duration.ofMinutes(30);
  private static final Duration REFRESH_TOKEN_EXPIRATION = Duration.ofDays(7);
  private static final String SECRET = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";
  private static final String TYPE_CLAIM = "type";

  private final JwtTokenProperties jwtTokenProperties =
      new JwtTokenProperties(ISSUER, ACCESS_TOKEN_EXPIRATION, REFRESH_TOKEN_EXPIRATION, SECRET);
  private final JjwtTokenProvider tokenProvider = new JjwtTokenProvider(jwtTokenProperties);

  @Test
  void createAccessTokenAndParseAccessToken() {
    UUID memberUuid = UUID.randomUUID();

    String accessToken = tokenProvider.createAccessToken(memberUuid);
    TokenPayload tokenPayload = tokenProvider.parseAccessToken(accessToken);

    assertThat(tokenPayload.memberUuid()).isEqualTo(memberUuid);
    assertThat(tokenPayload.issuedAt()).isNotNull();
    assertThat(tokenPayload.expiresAt()).isAfter(tokenPayload.issuedAt());
  }

  @Test
  void createRefreshTokenAndParseRefreshToken() {
    UUID memberUuid = UUID.randomUUID();

    String refreshToken = tokenProvider.createRefreshToken(memberUuid);
    TokenPayload tokenPayload = tokenProvider.parseRefreshToken(refreshToken);

    assertThat(tokenPayload.memberUuid()).isEqualTo(memberUuid);
    assertThat(tokenPayload.issuedAt()).isNotNull();
    assertThat(tokenPayload.expiresAt()).isAfter(tokenPayload.issuedAt());
  }

  @Test
  void parseAccessTokenRejectsRefreshToken() {
    String refreshToken = tokenProvider.createRefreshToken(UUID.randomUUID());

    assertThatThrownBy(() -> tokenProvider.parseAccessToken(refreshToken))
        .isInstanceOfSatisfying(
            CustomException.class,
            exception ->
                assertThat(exception.getErrorCode()).isEqualTo(AuthErrorCode.ACCESS_TOKEN_INVALID));
  }

  @Test
  void parseRefreshTokenRejectsAccessToken() {
    String accessToken = tokenProvider.createAccessToken(UUID.randomUUID());

    assertThatThrownBy(() -> tokenProvider.parseRefreshToken(accessToken))
        .isInstanceOfSatisfying(
            CustomException.class,
            exception ->
                assertThat(exception.getErrorCode())
                    .isEqualTo(AuthErrorCode.REFRESH_TOKEN_INVALID));
  }

  @Test
  void parseAccessTokenRejectsMalformedToken() {
    assertThatThrownBy(() -> tokenProvider.parseAccessToken("malformed-token"))
        .isInstanceOfSatisfying(
            CustomException.class,
            exception ->
                assertThat(exception.getErrorCode()).isEqualTo(AuthErrorCode.ACCESS_TOKEN_INVALID));
  }

  @Test
  void parseAccessTokenRejectsExpiredToken() {
    String expiredAccessToken =
        createToken(
            UUID.randomUUID(),
            "access",
            Instant.now().minus(Duration.ofHours(1)),
            Instant.now().minus(Duration.ofMinutes(30)));

    assertThatThrownBy(() -> tokenProvider.parseAccessToken(expiredAccessToken))
        .isInstanceOfSatisfying(
            CustomException.class,
            exception ->
                assertThat(exception.getErrorCode()).isEqualTo(AuthErrorCode.ACCESS_TOKEN_EXPIRED));
  }

  @Test
  void parseAccessTokenRejectsMissingRequiredClaims() {
    String tokenWithoutRequiredClaims =
        Jwts.builder().issuer(ISSUER).claim(TYPE_CLAIM, "access").signWith(secretKey()).compact();

    assertThatThrownBy(() -> tokenProvider.parseAccessToken(tokenWithoutRequiredClaims))
        .isInstanceOfSatisfying(
            CustomException.class,
            exception ->
                assertThat(exception.getErrorCode()).isEqualTo(AuthErrorCode.ACCESS_TOKEN_INVALID));
  }

  @Test
  void jwtTokenPropertiesRejectsShortSecret() {
    assertThatThrownBy(
            () ->
                new JwtTokenProperties(
                    ISSUER, ACCESS_TOKEN_EXPIRATION, REFRESH_TOKEN_EXPIRATION, "short-secret"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("secret must be at least 32 bytes.");
  }

  private String createToken(
      UUID memberUuid, String tokenType, Instant issuedAt, Instant expiration) {
    return Jwts.builder()
        .issuer(ISSUER)
        .subject(memberUuid.toString())
        .issuedAt(Date.from(issuedAt))
        .expiration(Date.from(expiration))
        .claim(TYPE_CLAIM, tokenType)
        .signWith(secretKey())
        .compact();
  }

  private SecretKey secretKey() {
    return Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));
  }
}
