package com.oit.dondok.domain.dashboard.repository;

import java.math.BigDecimal;

// 크루 상세 대시보드용 참여자 스냅샷 행
public record CrewDashboardParticipantRow(
    Long snapshotId,
    Long crewParticipantId,
    String nickname,
    Integer successCount,
    BigDecimal shareRatio,
    Long expectedRefundAmount) {}
