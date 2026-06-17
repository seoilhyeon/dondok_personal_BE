package com.oit.dondok.domain.auth.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;

public record OAuth2TokenExchangeResponse(
    @JsonProperty("access_token") String accessToken,
    @JsonProperty("token_type") String tokenType,
    @JsonProperty("expires_in") long expiresIn,
    LoginMemberResponse member) {

  /** OAuth 로그인 코드 교환 응답을 Bearer 토큰 형식으로 생성한다. */
  public static OAuth2TokenExchangeResponse bearer(
      String accessToken, long expiresIn, LoginMemberResponse member) {
    return new OAuth2TokenExchangeResponse(accessToken, "Bearer", expiresIn, member);
  }
}
