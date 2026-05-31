package com.oit.dondok.domain.auth.service;

import java.util.UUID;

public record LoginResult(
    String accessToken,
    String refreshToken,
    long accessTokenExpiresIn,
    long refreshTokenMaxAge,
    UUID memberUuid,
    String email,
    String nickname) {}
