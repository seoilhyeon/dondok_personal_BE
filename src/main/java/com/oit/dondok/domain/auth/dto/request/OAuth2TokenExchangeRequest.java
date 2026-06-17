package com.oit.dondok.domain.auth.dto.request;

import jakarta.validation.constraints.NotBlank;

public record OAuth2TokenExchangeRequest(@NotBlank String code) {}
