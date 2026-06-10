package com.oit.dondok.domain.crew.dto.request;

import jakarta.validation.constraints.NotBlank;

public record CreateNoticeRequest(@NotBlank String title, @NotBlank String content) {}
