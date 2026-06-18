package com.oit.dondok.domain.settlement.dto.response;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.oit.dondok.domain.crew.entity.Crew;
import com.oit.dondok.domain.settlement.entity.Settlement;
import com.oit.dondok.global.util.SeoulDateTimeUtils;
import java.time.LocalDate;
import java.time.OffsetDateTime;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record SettlementMeResponse(
    Long settlementId,
    Long crewId,
    String crewName,
    LocalDate crewStartedAt,
    LocalDate crewEndedAt,
    String status,
    Integer retryCount,
    String failureCode,
    String failureMessage,
    OffsetDateTime startedAt,
    OffsetDateTime finishedAt,
    SettlementItemDetailResponse myItem) {

  public static SettlementMeResponse of(
      Settlement settlement, SettlementItemDetailResponse myItem) {
    Crew crew = settlement.getCrew();
    return new SettlementMeResponse(
        settlement.getId(),
        crew.getId(),
        crew.getTitle(),
        crew.getStartAt().toLocalDate(),
        crew.getEndAt().toLocalDate(),
        settlement.getStatus().name(),
        settlement.getRetryCount(),
        settlement.getFailureCode() == null ? null : settlement.getFailureCode().name(),
        settlement.getFailureMessage(),
        SeoulDateTimeUtils.toSeoulOffset(settlement.getStartedAt()),
        SeoulDateTimeUtils.toSeoulOffset(settlement.getFinishedAt()),
        myItem);
  }
}
