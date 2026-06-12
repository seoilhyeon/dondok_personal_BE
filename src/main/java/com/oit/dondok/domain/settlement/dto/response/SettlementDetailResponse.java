package com.oit.dondok.domain.settlement.dto.response;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.oit.dondok.domain.settlement.entity.RemainderPolicy;
import com.oit.dondok.domain.settlement.entity.Settlement;
import com.oit.dondok.global.util.SeoulDateTimeUtils;
import java.time.OffsetDateTime;
import java.util.List;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record SettlementDetailResponse(
    Long settlementId,
    Long crewId,
    String status,
    Integer retryCount,
    Integer totalParticipants,
    Long totalLockedAmount,
    Integer totalRecognizedSuccess,
    Long totalBaseRefundAmount,
    Long totalRemainderAmount,
    RemainderPolicy remainderPolicy,
    String failureCode,
    String failureMessage,
    OffsetDateTime startedAt,
    OffsetDateTime finishedAt,
    List<SettlementItemDetailResponse> items) {

  public static SettlementDetailResponse of(
      Settlement settlement, List<SettlementItemDetailResponse> items) {
    return new SettlementDetailResponse(
        settlement.getId(),
        settlement.getCrew().getId(),
        settlement.getStatus().name(),
        settlement.getRetryCount(),
        settlement.getTotalParticipants(),
        settlement.getTotalLockedAmount(),
        settlement.getTotalRecognizedSuccess(),
        settlement.getTotalBaseRefundAmount(),
        settlement.getTotalRemainderAmount(),
        settlement.getRemainderPolicy(),
        settlement.getFailureCode() == null ? null : settlement.getFailureCode().name(),
        settlement.getFailureMessage(),
        SeoulDateTimeUtils.toSeoulOffset(settlement.getStartedAt()),
        SeoulDateTimeUtils.toSeoulOffset(settlement.getFinishedAt()),
        items);
  }
}
