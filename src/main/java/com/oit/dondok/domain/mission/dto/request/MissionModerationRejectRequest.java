package com.oit.dondok.domain.mission.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.oit.dondok.domain.mission.entity.RejectReasonCode;
import jakarta.validation.constraints.NotNull;

public record MissionModerationRejectRequest(
    @JsonProperty("reject_reason_code") @NotNull(message = "reject_reason_code는 필수입니다.")
        RejectReasonCode rejectReasonCode,
    @JsonProperty("reject_memo") String rejectMemo) {}
