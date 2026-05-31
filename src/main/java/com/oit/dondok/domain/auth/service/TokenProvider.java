package com.oit.dondok.domain.auth.service;

import java.util.UUID;

public interface TokenProvider {

  String createAccessToken(UUID memberUuid);

  String createRefreshToken(UUID memberUuid);

  TokenPayload parseAccessToken(String token);

  TokenPayload parseRefreshToken(String token);
}
