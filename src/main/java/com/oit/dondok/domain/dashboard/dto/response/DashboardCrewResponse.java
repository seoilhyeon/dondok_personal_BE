package com.oit.dondok.domain.dashboard.dto.response;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

// 참여 중인 크루 1건의 대시보드 항목
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record DashboardCrewResponse(
    Long crewId,
    String crewName,
    String category,
    String imageUrl,
    String shareRatio,
    Long expectedRefundAmount,
    Long todayDeltaAmount) {}
