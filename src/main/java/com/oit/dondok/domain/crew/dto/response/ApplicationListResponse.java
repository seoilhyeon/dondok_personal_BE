package com.oit.dondok.domain.crew.dto.response;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import java.util.List;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record ApplicationListResponse(List<ParticipationSummaryResponse> items, String nextCursor) {

  public static ApplicationListResponse of(List<ParticipationSummaryResponse> items) {
    return new ApplicationListResponse(items, null);
  }
}
