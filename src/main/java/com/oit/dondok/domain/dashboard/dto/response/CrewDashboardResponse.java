package com.oit.dondok.domain.dashboard.dto.response;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.oit.dondok.domain.crew.entity.CrewStatus;
import java.time.OffsetDateTime;
import java.util.List;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record CrewDashboardResponse(
    Long crewId,
    String crewName,
    Long crewParticipantId,
    Long settlementId, // SETTLEMENT_SUCCEEDED 이전 null
    CrewStatus crewStatus,
    String settlementStatus, // NONE | PENDING | RUNNING | SUCCEEDED | FAILED | RETRY_WAIT
    ProjectionStatus projectionStatus,
    ProjectionNotice projectionNotice,
    Integer daysUntilEnd,
    Long myDepositAmount,
    Integer mySuccessCount,
    Long myExpectedRefundAmount,
    Long myExpectedRefundDeltaAmount,
    Integer rank,
    Integer rankTotal,
    Integer rankDelta,
    OffsetDateTime nextSettlementAt,
    List<CrewDashboardParticipantResponse> participants,
    OffsetDateTime updatedAt) {}
