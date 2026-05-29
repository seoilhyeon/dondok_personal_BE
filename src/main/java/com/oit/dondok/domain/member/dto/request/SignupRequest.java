package com.oit.dondok.domain.member.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record SignupRequest(
    @NotBlank(message = "email은 필수입니다.")
        @Email(regexp = "^[^@\\s]+@[^@\\s]+\\.[^@\\s]{2,}$", message = "email 형식이 올바르지 않습니다.")
        @Size(max = 255, message = "email은 255자 이하여야 합니다.")
        String email,
    @NotBlank(message = "password는 필수입니다.")
        @Size(min = 8, max = 72, message = "password는 8자 이상 72자 이하여야 합니다.")
        String password,
    @NotBlank(message = "nickname은 필수입니다.")
        @Pattern(regexp = "\\S.*\\S|\\S", message = "nickname 앞뒤에는 공백을 사용할 수 없습니다.")
        @Size(min = 2, max = 10, message = "nickname은 2자 이상 10자 이하여야 합니다.")
        String nickname) {}
