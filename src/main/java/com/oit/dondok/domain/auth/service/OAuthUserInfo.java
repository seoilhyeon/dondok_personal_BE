package com.oit.dondok.domain.auth.service;

import com.oit.dondok.domain.member.entity.OAuthProvider;

public record OAuthUserInfo(
    OAuthProvider provider,
    String providerId,
    String email,
    boolean emailVerified,
    String name,
    String pictureUrl) {}
