package com.oit.dondok.domain.crew.dto.response;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.oit.dondok.domain.crew.entity.Crew;
import com.oit.dondok.domain.crew.entity.CrewStatus;
import com.oit.dondok.domain.mission.entity.MissionFrequencyType;
import com.oit.dondok.domain.mission.entity.MissionRule;
import com.oit.dondok.global.util.SeoulDateTimeUtils;
import java.time.OffsetDateTime;
import java.util.List;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record CrewSummaryResponse(
    Long crewId,
    String title,
    String imageUrl,
    String category,
    CrewStatus status,
    Long depositAmount,
    Integer minParticipants,
    Integer maxParticipants,
    Integer currentParticipants,
    MissionFrequencyType frequencyType,
    List<String> missionScheduleDays,
    OffsetDateTime recruitmentDeadline,
    OffsetDateTime startAt,
    OffsetDateTime activatedAt,
    OffsetDateTime endAt) {

  public static CrewSummaryResponse of(
      Crew crew,
      MissionRule missionRule,
      List<String> scheduleDays,
      String imageUrl,
      int currentParticipants) {
    return new CrewSummaryResponse(
        crew.getId(),
        crew.getTitle(),
        imageUrl,
        crew.getCategory(),
        crew.getStatus(),
        crew.getDepositAmount(),
        crew.getMinParticipants(),
        crew.getMaxParticipants(),
        currentParticipants,
        missionRule.getFrequencyType(),
        scheduleDays,
        SeoulDateTimeUtils.toSeoulOffset(crew.getRecruitmentDeadline()),
        SeoulDateTimeUtils.toSeoulOffset(crew.getStartAt()),
        SeoulDateTimeUtils.toSeoulOffset(crew.getActivatedAt()),
        SeoulDateTimeUtils.toSeoulOffset(crew.getEndAt()));
  }
}
