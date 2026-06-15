package com.oit.dondok.domain.crew.dto.response;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.oit.dondok.domain.crew.entity.Crew;
import com.oit.dondok.domain.crew.entity.CrewStatus;
import com.oit.dondok.global.util.SeoulDateTimeUtils;
import java.time.OffsetDateTime;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record CrewDisbandResponse(Long crewId, CrewStatus status, OffsetDateTime cancelledAt) {

  public static CrewDisbandResponse of(Crew crew) {
    return new CrewDisbandResponse(
        crew.getId(), crew.getStatus(), SeoulDateTimeUtils.toSeoulOffset(crew.getCancelledAt()));
  }
}
