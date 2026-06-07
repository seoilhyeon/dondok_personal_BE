package com.oit.dondok.infra.ai.adapter;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record OpenAiDraftResult(
    String title,
    String description,
    @JsonProperty("frequency_type") String frequencyType,
    @JsonProperty("mission_schedule_days") List<String> missionScheduleDays,
    @JsonProperty("daily_settlement_type") String dailySettlementType,
    @JsonProperty("deposit_amount") long depositAmount,
    @JsonProperty("duration_days") int durationDays) {}
