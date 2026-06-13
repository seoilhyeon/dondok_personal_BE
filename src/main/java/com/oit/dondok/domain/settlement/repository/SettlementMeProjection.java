package com.oit.dondok.domain.settlement.repository;

import com.oit.dondok.domain.settlement.entity.ParticipantStatusSnapshot;
import com.oit.dondok.domain.settlement.entity.SettlementFailureCode;
import com.oit.dondok.domain.settlement.entity.SettlementStatus;
import java.math.BigDecimal;
import java.time.LocalDateTime;

public record SettlementMeProjection(
    Long settlementId,
    Long crewId,
    SettlementStatus status,
    Integer retryCount,
    SettlementFailureCode failureCode,
    String failureMessage,
    LocalDateTime startedAt,
    LocalDateTime finishedAt,
    Long settlementItemId,
    Long crewParticipantId,
    ParticipantStatusSnapshot participantStatusSnapshot,
    Long depositAmount,
    Integer successCountRaw,
    Integer recognizedSuccessCount,
    Integer recognizedDatesCount,
    Integer excludedSuccessCount,
    BigDecimal shareRatio,
    Long baseRefundAmount,
    Long remainderBonusAmount,
    Long refundAmount,
    Long pointHistoryId,
    String calculationReason) {}
