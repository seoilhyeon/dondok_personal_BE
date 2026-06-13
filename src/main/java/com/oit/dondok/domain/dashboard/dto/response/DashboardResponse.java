package com.oit.dondok.domain.dashboard.dto.response;

// 전체 대시보드 집계 응답

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import java.util.List;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record DashboardResponse(
    long totalExpectedRefundAmound,
    long todayDeltaAmount,
    String todayDeltaRatio,
    int risingCrewCount,
    int fallingCrewCount,
    MaxDeltaCrewResponse maxDeltaCrew,
    List<DashboardCrewResponse> crews) {}
