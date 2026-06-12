package com.oit.dondok.domain.settlement.dto.response;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.oit.dondok.domain.settlement.entity.Settlement;
import com.oit.dondok.global.util.SeoulDateTimeUtils;
import java.time.OffsetDateTime;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record SettlementSummaryResponse(
    Long crewId,
    Long settlementId,
    String status,
    Integer retryCount,
    String failureCode,
    String failureMessage,
    OffsetDateTime startedAt,
    OffsetDateTime finishedAt) {

  public static SettlementSummaryResponse ofCrewSettlementMissing(Long crewId) {
    return new SettlementSummaryResponse(crewId, null, "NONE", 0, null, null, null, null);
  }

  public static SettlementSummaryResponse of(Settlement settlement) {
    return new SettlementSummaryResponse(
        settlement.getCrew().getId(),
        settlement.getId(),
        settlement.getStatus().name(),
        settlement.getRetryCount(),
        settlement.getFailureCode() == null ? null : settlement.getFailureCode().name(),
        settlement.getFailureMessage(),
        SeoulDateTimeUtils.toSeoulOffset(settlement.getStartedAt()),
        SeoulDateTimeUtils.toSeoulOffset(settlement.getFinishedAt()));
  }
}
