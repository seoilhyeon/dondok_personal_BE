package com.oit.dondok.infra.auth.oauth2;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.oit.dondok.global.config.CookieProperties;
import com.oit.dondok.infra.auth.token.JwtTokenProperties;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.Arrays;
import java.util.Base64;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.security.oauth2.client.web.AuthorizationRequestRepository;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;

public class CookieOAuth2AuthorizationRequestRepository
    implements AuthorizationRequestRepository<OAuth2AuthorizationRequest> {

  private static final String COOKIE_NAME = "oauth2_auth_request";
  private static final Duration COOKIE_TTL = Duration.ofMinutes(3);
  private static final String HMAC_ALGORITHM = "HmacSHA256";

  private final ObjectMapper objectMapper;
  private final JwtTokenProperties jwtTokenProperties;
  private final CookieProperties cookieProperties;

  public CookieOAuth2AuthorizationRequestRepository(
      ObjectMapper objectMapper,
      JwtTokenProperties jwtTokenProperties,
      CookieProperties cookieProperties) {
    this.objectMapper = objectMapper;
    this.jwtTokenProperties = jwtTokenProperties;
    this.cookieProperties = cookieProperties;
  }

  /** OAuth 인증 요청 정보를 서명된 JSON 쿠키에서 읽어 복원한다. */
  @Override
  public OAuth2AuthorizationRequest loadAuthorizationRequest(HttpServletRequest request) {
    return findCookie(request).map(Cookie::getValue).map(this::deserialize).orElse(null);
  }

  /** OAuth 인증 요청 정보를 짧은 수명의 HttpOnly 쿠키에 저장한다. */
  @Override
  public void saveAuthorizationRequest(
      OAuth2AuthorizationRequest authorizationRequest,
      HttpServletRequest request,
      HttpServletResponse response) {
    if (authorizationRequest == null) {
      deleteCookie(response);
      return;
    }

    response.addHeader(
        HttpHeaders.SET_COOKIE, authorizationRequestCookie(authorizationRequest).toString());
  }

  /** OAuth 콜백 처리 후 인증 요청 쿠키를 제거하고 저장된 요청을 반환한다. */
  @Override
  public OAuth2AuthorizationRequest removeAuthorizationRequest(
      HttpServletRequest request, HttpServletResponse response) {
    OAuth2AuthorizationRequest authorizationRequest = loadAuthorizationRequest(request);
    deleteCookie(response);
    return authorizationRequest;
  }

  /** 요청 쿠키 목록에서 OAuth 인증 요청 쿠키를 찾는다. */
  private Optional<Cookie> findCookie(HttpServletRequest request) {
    Cookie[] cookies = request.getCookies();
    if (cookies == null) {
      return Optional.empty();
    }
    return Arrays.stream(cookies)
        .filter(cookie -> COOKIE_NAME.equals(cookie.getName()))
        .findFirst();
  }

  /** OAuth 인증 요청에서 필요한 필드만 JSON으로 직렬화하고 서명한다. */
  private String serialize(OAuth2AuthorizationRequest authorizationRequest) {
    OAuth2AuthorizationRequestCookieValue cookieValue =
        OAuth2AuthorizationRequestCookieValue.from(authorizationRequest);
    try {
      String payload = base64Url(objectMapper.writeValueAsBytes(cookieValue));
      return payload + "." + base64Url(sign(payload));
    } catch (JsonProcessingException exception) {
      throw new IllegalStateException(
          "Failed to serialize OAuth2 authorization request.", exception);
    }
  }

  /** 서명 검증을 통과한 JSON 쿠키 값을 OAuth 인증 요청으로 복원한다. */
  private OAuth2AuthorizationRequest deserialize(String value) {
    try {
      String[] parts = value.split("\\.", -1);
      if (parts.length != 2 || !isValidSignature(parts[0], parts[1])) {
        return null;
      }

      byte[] json = Base64.getUrlDecoder().decode(parts[0]);
      OAuth2AuthorizationRequestCookieValue cookieValue =
          objectMapper.readValue(json, OAuth2AuthorizationRequestCookieValue.class);
      return cookieValue.toAuthorizationRequest();
    } catch (RuntimeException | IOException exception) {
      return null;
    }
  }

  /** OAuth 인증 요청 쿠키를 SameSite가 명시된 Set-Cookie 헤더로 생성한다. */
  private ResponseCookie authorizationRequestCookie(
      OAuth2AuthorizationRequest authorizationRequest) {
    return ResponseCookie.from(COOKIE_NAME, serialize(authorizationRequest))
        .httpOnly(true)
        .secure(cookieProperties.secure())
        .sameSite(cookieProperties.sameSite())
        .path("/")
        .maxAge(COOKIE_TTL)
        .build();
  }

  /** OAuth 인증 요청 쿠키를 만료시킨다. */
  private void deleteCookie(HttpServletResponse response) {
    ResponseCookie cookie =
        ResponseCookie.from(COOKIE_NAME, "")
            .httpOnly(true)
            .secure(cookieProperties.secure())
            .sameSite(cookieProperties.sameSite())
            .path("/")
            .maxAge(0)
            .build();
    response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
  }

  /** 입력 문자열에 HMAC-SHA256 서명을 생성한다. */
  private byte[] sign(String value) {
    try {
      Mac mac = Mac.getInstance(HMAC_ALGORITHM);
      mac.init(
          new SecretKeySpec(
              jwtTokenProperties.secret().getBytes(StandardCharsets.UTF_8), HMAC_ALGORITHM));
      return mac.doFinal(value.getBytes(StandardCharsets.UTF_8));
    } catch (Exception exception) {
      throw new IllegalStateException("Failed to sign OAuth2 authorization request.", exception);
    }
  }

  /** 쿠키 payload와 signature가 일치하는지 상수 시간 비교로 확인한다. */
  private boolean isValidSignature(String payload, String signature) {
    byte[] expected = sign(payload);
    byte[] actual = Base64.getUrlDecoder().decode(signature);
    return MessageDigest.isEqual(expected, actual);
  }

  /** 쿠키에 안전하게 담을 수 있는 URL-safe Base64 문자열로 변환한다. */
  private String base64Url(byte[] bytes) {
    return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
  }

  private record OAuth2AuthorizationRequestCookieValue(
      String authorizationUri,
      String authorizationRequestUri,
      String clientId,
      String redirectUri,
      Set<String> scopes,
      String state,
      Map<String, Object> additionalParameters,
      Map<String, Object> attributes) {

    /** Spring OAuth 인증 요청에서 쿠키 저장용 값 객체를 만든다. */
    private static OAuth2AuthorizationRequestCookieValue from(
        OAuth2AuthorizationRequest authorizationRequest) {
      return new OAuth2AuthorizationRequestCookieValue(
          authorizationRequest.getAuthorizationUri(),
          authorizationRequest.getAuthorizationRequestUri(),
          authorizationRequest.getClientId(),
          authorizationRequest.getRedirectUri(),
          authorizationRequest.getScopes(),
          authorizationRequest.getState(),
          authorizationRequest.getAdditionalParameters(),
          authorizationRequest.getAttributes());
    }

    /** 쿠키 저장용 값 객체를 Spring OAuth 인증 요청으로 복원한다. */
    private OAuth2AuthorizationRequest toAuthorizationRequest() {
      return OAuth2AuthorizationRequest.authorizationCode()
          .authorizationUri(authorizationUri)
          .authorizationRequestUri(authorizationRequestUri)
          .clientId(clientId)
          .redirectUri(redirectUri)
          .scopes(scopes)
          .state(state)
          .additionalParameters(additionalParameters)
          .attributes(attributes)
          .build();
    }
  }
}
