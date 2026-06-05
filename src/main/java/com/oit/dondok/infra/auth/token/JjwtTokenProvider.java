package com.oit.dondok.infra.auth.token;

import com.oit.dondok.domain.auth.exception.AuthErrorCode;
import com.oit.dondok.domain.auth.service.TokenPayload;
import com.oit.dondok.domain.auth.service.TokenProvider;
import com.oit.dondok.global.exception.CustomException;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Objects;
import java.util.UUID;
import javax.crypto.SecretKey;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class JjwtTokenProvider implements TokenProvider {

  private static final String TOKEN_TYPE_CLAIM = "type";
  private static final String ACCESS_TOKEN_TYPE = "access";
  private static final String REFRESH_TOKEN_TYPE = "refresh";
  private static final ZoneId TOKEN_ZONE = ZoneId.of("Asia/Seoul");

  private final JwtTokenProperties jwtTokenProperties;

  /** Creates an access token with the member UUID as the subject. */
  @Override
  public String createAccessToken(UUID memberUuid) {
    return createToken(memberUuid, ACCESS_TOKEN_TYPE, jwtTokenProperties.accessTokenExpiration());
  }

  /** Creates a refresh token with the member UUID as the subject. */
  @Override
  public String createRefreshToken(UUID memberUuid) {
    return createToken(memberUuid, REFRESH_TOKEN_TYPE, jwtTokenProperties.refreshTokenExpiration());
  }

  /** Parses an access token and validates its signature, issuer, expiration, and type. */
  @Override
  public TokenPayload parseAccessToken(String token) {
    return parseToken(token, ACCESS_TOKEN_TYPE);
  }

  /** Parses a refresh token and validates its signature, issuer, expiration, and type. */
  @Override
  public TokenPayload parseRefreshToken(String token) {
    return parseToken(token, REFRESH_TOKEN_TYPE);
  }

  /** Creates a signed JWT with common claims and a token type claim. */
  private String createToken(UUID memberUuid, String tokenType, Duration expiration) {
    Objects.requireNonNull(memberUuid, "memberUuid must not be null");

    LocalDateTime now = LocalDateTime.now(TOKEN_ZONE);
    LocalDateTime expiresAt = now.plus(expiration);

    return Jwts.builder()
        .issuer(jwtTokenProperties.issuer())
        .subject(memberUuid.toString())
        .issuedAt(toDate(now))
        .expiration(toDate(expiresAt))
        .id(UUID.randomUUID().toString())
        .claim(TOKEN_TYPE_CLAIM, tokenType)
        .signWith(secretKey())
        .compact();
  }

  /** Parses and validates a JWT, then converts it to a domain payload. */
  private TokenPayload parseToken(String token, String expectedTokenType) {
    if (token == null || token.isBlank()) {
      throw invalidTokenException(expectedTokenType, null);
    }

    try {
      Claims claims =
          Jwts.parser()
              .verifyWith(secretKey())
              .requireIssuer(jwtTokenProperties.issuer())
              .build()
              .parseSignedClaims(token)
              .getPayload();

      String tokenType = claims.get(TOKEN_TYPE_CLAIM, String.class);
      validateTokenType(tokenType, expectedTokenType);

      String subject = claims.getSubject();
      LocalDateTime issuedAt = toLocalDateTime(claims.getIssuedAt());
      LocalDateTime expiration = toLocalDateTime(claims.getExpiration());

      validateRequiredClaims(subject, issuedAt, expiration, expectedTokenType);

      return new TokenPayload(UUID.fromString(subject), issuedAt, expiration);
    } catch (ExpiredJwtException exception) {
      throw expiredTokenException(expectedTokenType, exception);
    } catch (JwtException | IllegalArgumentException exception) {
      throw invalidTokenException(expectedTokenType, exception);
    }
  }

  /** Ensures the type claim matches the expected access or refresh token type. */
  private void validateTokenType(String tokenType, String expectedTokenType) {
    if (!expectedTokenType.equals(tokenType)) {
      throw invalidTokenException(expectedTokenType, null);
    }
  }

  /** Creates the HMAC signing key from the configured secret. */
  private SecretKey secretKey() {
    return Keys.hmacShaKeyFor(jwtTokenProperties.secret().getBytes(StandardCharsets.UTF_8));
  }

  private java.util.Date toDate(LocalDateTime dateTime) {
    return java.util.Date.from(dateTime.atZone(TOKEN_ZONE).toInstant());
  }

  private LocalDateTime toLocalDateTime(java.util.Date date) {
    if (date == null) {
      return null;
    }

    return LocalDateTime.ofInstant(date.toInstant(), TOKEN_ZONE);
  }

  /** Creates the expiration exception for the current token type. */
  private CustomException expiredTokenException(String tokenType, Throwable cause) {
    if (ACCESS_TOKEN_TYPE.equals(tokenType)) {
      return new CustomException(AuthErrorCode.ACCESS_TOKEN_EXPIRED, cause);
    }
    return new CustomException(AuthErrorCode.REFRESH_TOKEN_EXPIRED, cause);
  }

  /** Creates the invalid-token exception for the current token type. */
  private CustomException invalidTokenException(String tokenType, Throwable cause) {
    if (ACCESS_TOKEN_TYPE.equals(tokenType)) {
      return new CustomException(AuthErrorCode.ACCESS_TOKEN_INVALID, cause);
    }
    return new CustomException(AuthErrorCode.REFRESH_TOKEN_INVALID, cause);
  }

  /** Ensures all required claims for token usage are present. */
  private void validateRequiredClaims(
      String subject, LocalDateTime issuedAt, LocalDateTime expiration, String expectedTokenType) {
    if (subject == null || subject.isBlank() || issuedAt == null || expiration == null) {
      throw invalidTokenException(expectedTokenType, null);
    }
  }
}
