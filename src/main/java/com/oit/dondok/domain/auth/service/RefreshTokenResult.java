package com.oit.dondok.domain.auth.service;

public record RefreshTokenResult(
    String accessToken, String refreshToken, long refreshTokenMaxAge) {}
