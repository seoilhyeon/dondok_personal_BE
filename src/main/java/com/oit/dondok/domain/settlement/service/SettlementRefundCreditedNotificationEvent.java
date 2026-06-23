package com.oit.dondok.domain.settlement.service;

public record SettlementRefundCreditedNotificationEvent(
    Long memberId, Long settlementId, Long crewId, Long refundAmount, String crewTitle) {}
