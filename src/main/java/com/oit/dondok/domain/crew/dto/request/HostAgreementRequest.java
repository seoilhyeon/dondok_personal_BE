package com.oit.dondok.domain.crew.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.oit.dondok.domain.crew.entity.HostPolicyVersion;
import jakarta.validation.constraints.NotNull;
import java.time.OffsetDateTime;

public record HostAgreementRequest(
    @NotNull(message = "host_agreement.version은 필수입니다.") @JsonProperty("version")
        HostPolicyVersion version,
    @NotNull(message = "host_agreement.agreed_at은 필수입니다.") @JsonProperty("agreed_at")
        OffsetDateTime agreedAt) {}
