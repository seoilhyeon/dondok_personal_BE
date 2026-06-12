package com.oit.dondok.domain.settlement.service.model;

import com.oit.dondok.domain.settlement.entity.RemainderPolicy;
import java.util.List;

public record SettlementCalculationResult(
    int totalParticipants,
    long totalLockedAmount,
    int totalRecognizedSuccess,
    long totalBaseRefundAmount,
    long totalRemainderAmount,
    RemainderPolicy remainderPolicy,
    List<SettlementParticipantResult> participants) {}
