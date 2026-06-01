package com.oit.dondok.domain.crew.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.oit.dondok.domain.mission.entity.DailySettlementType;
import com.oit.dondok.domain.mission.entity.MissionFrequencyType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

public record CrewCreateRequest(
    @NotBlank(message = "title은 필수입니다.")
        @Size(max = 100, message = "title은 100자 이하여야 합니다.")
        @JsonProperty("title")
        String title,
    @NotBlank(message = "description은 필수입니다.") @JsonProperty("description") String description,
    @JsonProperty("image_s3_key") String imageS3Key,
    @NotBlank(message = "category는 필수입니다.") @JsonProperty("category") String category,
    @NotNull(message = "deposit_amount는 필수입니다.") @JsonProperty("deposit_amount") Long depositAmount,
    @Min(value = 2, message = "min_participants는 2 이상이어야 합니다.") @JsonProperty("min_participants")
        Integer minParticipants,
    @NotNull(message = "max_participants는 필수입니다.")
        @Min(value = 2, message = "max_participants는 2 이상이어야 합니다.")
        @Max(value = 15, message = "max_participants는 15 이하여야 합니다.")
        @JsonProperty("max_participants")
        Integer maxParticipants,
    @NotNull(message = "frequency_type은 필수입니다.") @JsonProperty("frequency_type")
        MissionFrequencyType frequencyType,
    @JsonProperty("mission_schedule_days") List<String> missionScheduleDays,
    @NotNull(message = "daily_settlement_type은 필수입니다.") @JsonProperty("daily_settlement_type")
        DailySettlementType dailySettlementType,
    @NotNull(message = "host_agreement는 필수입니다.") @Valid @JsonProperty("host_agreement")
        HostAgreementRequest hostAgreement,
    @NotNull(message = "recruitment_deadline는 필수입니다.") @JsonProperty("recruitment_deadline")
        OffsetDateTime recruitmentDeadline,
    @NotNull(message = "start_date는 필수입니다.") @JsonProperty("start_date") LocalDate startDate,
    @NotNull(message = "end_date는 필수입니다.") @JsonProperty("end_date") LocalDate endDate) {}
