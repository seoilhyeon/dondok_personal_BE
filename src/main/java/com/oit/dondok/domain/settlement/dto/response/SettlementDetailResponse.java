package com.oit.dondok.domain.settlement.dto.response;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.oit.dondok.domain.settlement.entity.Settlement;
import com.oit.dondok.global.util.SeoulDateTimeUtils;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record SettlementDetailResponse(
    Long settlementId,
    Long crewId,
    String crewName,
    LocalDate crewStartedAt,
    LocalDate crewEndedAt,
    Integer missionDays,
    String crewSuccessRate,
    String status,
    Integer retryCount,
    Integer totalParticipants,
    Long totalLockedAmount,
    Integer totalRecognizedSuccess,
    Long totalBaseRefundAmount,
    Long totalRemainderAmount,
    String remainderPolicy,
    String failureCode,
    String failureMessage,
    OffsetDateTime startedAt,
    OffsetDateTime finishedAt,
    Integer myRank,
    List<SettlementItemDetailResponse> items) {

  public static SettlementDetailResponse of(
      Settlement settlement,
      String crewSuccessRate,
      Integer myRank,
      List<SettlementItemDetailResponse> items) {
    return new SettlementDetailResponse(
        settlement.getId(),
        settlement.getCrew().getId(),
        settlement.getCrewName(),
        settlement.getCrewStartedAt() == null ? null : settlement.getCrewStartedAt().toLocalDate(),
        settlement.getCrewEndedAt() == null ? null : settlement.getCrewEndedAt().toLocalDate(),
        settlement.getMissionDays(),
        crewSuccessRate,
        settlement.getStatus().name(),
        settlement.getRetryCount(),
        settlement.getTotalParticipants(),
        settlement.getTotalLockedAmount(),
        settlement.getTotalRecognizedSuccess(),
        settlement.getTotalBaseRefundAmount(),
        settlement.getTotalRemainderAmount(),
        settlement.getRemainderPolicy().name(),
        settlement.getFailureCode() == null ? null : settlement.getFailureCode().name(),
        settlement.getFailureMessage(),
        SeoulDateTimeUtils.toSeoulOffset(settlement.getStartedAt()),
        SeoulDateTimeUtils.toSeoulOffset(settlement.getFinishedAt()),
        myRank,
        items);
  }
}
