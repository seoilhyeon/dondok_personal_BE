package com.oit.dondok.domain.point.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.oit.dondok.global.validation.ValidChargeAmount;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record PointChargeRequest(
    @JsonProperty("payment_id") @NotBlank @Size(max = 200) String paymentId,
    @JsonProperty("order_id") @NotBlank @Pattern(regexp = "^[0-9A-Za-z_-]{6,64}$") String orderId,
    @ValidChargeAmount Long amount) {}
