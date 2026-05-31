package com.oit.dondok.domain.auth.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record LoginRequest(
    @NotBlank(message = "email은 필수입니다.")
        @Email(regexp = "^[^@\\s]+@[^@\\s]+\\.[^@\\s]{2,}$", message = "email 형식이 올바르지 않습니다.")
        @Size(max = 255, message = "email은 255자 이하여야 합니다.")
        String email,
    @NotBlank(message = "password는 필수입니다.") String password) {}
