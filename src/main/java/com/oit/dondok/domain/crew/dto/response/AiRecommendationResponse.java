package com.oit.dondok.domain.crew.dto.response;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import java.util.List;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record AiRecommendationResponse(
    DraftResponse draft, List<WarningResponse> validationWarnings) {

  @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
  public record DraftResponse(
      String title,
      String description,
      String frequencyType,
      List<String> missionScheduleDays,
      String dailySettlementType,
      long depositAmount,
      int durationDays) {}

  @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
  public record WarningResponse(String field, String message) {}
}
