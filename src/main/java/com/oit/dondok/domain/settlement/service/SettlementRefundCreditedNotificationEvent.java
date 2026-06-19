package com.oit.dondok.domain.settlement.service;

import com.oit.dondok.domain.member.entity.Member;

public record SettlementRefundCreditedNotificationEvent(
    Member member, Long settlementId, Long refundAmount, String crewTitle) {}
