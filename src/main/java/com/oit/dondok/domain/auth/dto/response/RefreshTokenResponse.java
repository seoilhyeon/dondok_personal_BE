package com.oit.dondok.domain.auth.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;

public record RefreshTokenResponse(@JsonProperty("access_token") String accessToken) {}
