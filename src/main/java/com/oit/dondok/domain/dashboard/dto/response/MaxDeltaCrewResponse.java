package com.oit.dondok.domain.dashboard.dto.response;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

// 오늘 절댓값 기준 변동액이 가장 큰 크루. 동률이면 crew_id ASC.
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record MaxDeltaCrewResponse(Long crewId, String crewName, long todayDeltaAmount) {}
