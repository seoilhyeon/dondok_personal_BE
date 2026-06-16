package com.oit.dondok.domain.dashboard.repository;

import java.time.LocalDate;
import java.time.LocalDateTime;

// 크루 상세 대시보드용 스냅샷 헤더 행
public record CrewDashboardSnapshotRow(
    Long snapshotId, LocalDate missionDate, LocalDateTime frozenAt) {}
