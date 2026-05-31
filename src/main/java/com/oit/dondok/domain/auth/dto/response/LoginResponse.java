package com.oit.dondok.domain.auth.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;

public record LoginResponse(
    @JsonProperty("access_token") String accessToken,
    @JsonProperty("token_type") String tokenType,
    @JsonProperty("expires_in") long expiresIn,
    LoginMemberResponse member) {

  public static LoginResponse bearer(
      String accessToken, long expiresIn, LoginMemberResponse member) {
    return new LoginResponse(accessToken, "Bearer", expiresIn, member);
  }
}
