package com.oit.dondok.domain.crew.dto.response;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.oit.dondok.domain.crew.entity.Crew;
import com.oit.dondok.domain.crew.entity.CrewStatus;
import com.oit.dondok.domain.mission.entity.MissionFrequencyType;
import com.oit.dondok.domain.mission.entity.MissionRule;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
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
    MissionFrequencyType frequencyType,
    List<String> missionScheduleDays,
    OffsetDateTime recruitmentDeadline,
    OffsetDateTime startAt,
    OffsetDateTime activatedAt,
    OffsetDateTime endAt) {

  private static final ZoneId SEOUL_ZONE = ZoneId.of("Asia/Seoul");

  public static CrewSummaryResponse of(
      Crew crew, MissionRule missionRule, List<String> scheduleDays, String imageUrl) {
    return new CrewSummaryResponse(
        crew.getId(),
        crew.getTitle(),
        imageUrl,
        crew.getCategory(),
        crew.getStatus(),
        crew.getDepositAmount(),
        crew.getMinParticipants(),
        crew.getMaxParticipants(),
        missionRule.getFrequencyType(),
        scheduleDays,
        toSeoulOffset(crew.getRecruitmentDeadline()),
        toSeoulOffset(crew.getStartAt()),
        toSeoulOffset(crew.getActivatedAt()),
        toSeoulOffset(crew.getEndAt()));
  }

  private static OffsetDateTime toSeoulOffset(LocalDateTime ldt) {
    if (ldt == null) {
      return null;
    }
    return ldt.atZone(SEOUL_ZONE).toOffsetDateTime();
  }
}
