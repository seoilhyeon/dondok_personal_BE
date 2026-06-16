package com.oit.dondok.domain.dashboard.repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

// 대시보드 projection 조회용 행.
// shareRatio/expectedRefundAmount는 해당 스냅샷에서의 회원 participant 값이며, 회원이 해당 스냅샷에 없으면 null이다(LEFT JOIN).
public record DashboardProjectionRow(
    Long crewId,
    LocalDate missionDate,
    LocalDateTime frozenAt,
    BigDecimal shareRatio,
    Long expectedRefundAmount) {}
