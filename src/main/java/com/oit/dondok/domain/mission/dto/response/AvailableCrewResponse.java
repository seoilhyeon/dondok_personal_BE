package com.oit.dondok.domain.mission.dto.response;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.oit.dondok.domain.crew.entity.CrewStatus;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record AvailableCrewResponse(Long crewId, String crewName, CrewStatus status) {}
