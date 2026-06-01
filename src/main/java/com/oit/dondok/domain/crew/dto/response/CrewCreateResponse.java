package com.oit.dondok.domain.crew.dto.response;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.oit.dondok.domain.crew.entity.Crew;
import com.oit.dondok.domain.crew.entity.CrewParticipant;
import com.oit.dondok.domain.crew.entity.CrewStatus;
import com.oit.dondok.domain.crew.entity.HostPolicyVersion;
import com.oit.dondok.domain.mission.entity.DailySettlementType;
import com.oit.dondok.domain.mission.entity.MissionFrequencyType;
import com.oit.dondok.domain.mission.entity.MissionRule;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record CrewCreateResponse(
    Long crewId,
    String title,
    String description,
    String imageUrl,
    String category,
    CrewStatus status,
    Long depositAmount,
    Integer minParticipants,
    Integer maxParticipants,
    MissionFrequencyType frequencyType,
    List<Integer> missionScheduleDays,
    DailySettlementType dailySettlementType,
    HostPolicyVersion hostAgreementVersion,
    OffsetDateTime hostAgreedAt,
    OffsetDateTime recruitmentDeadline,
    OffsetDateTime startAt,
    OffsetDateTime activatedAt,
    OffsetDateTime endAt,
    OffsetDateTime createdAt,
    MyParticipationResponse myParticipation) {

  private static final ZoneId SEOUL_ZONE = ZoneId.of("Asia/Seoul");

  public static CrewCreateResponse of(
      Crew crew,
      MissionRule missionRule,
      List<Integer> scheduleDays,
      CrewParticipant participant,
      String imageUrl) {
    return new CrewCreateResponse(
        crew.getId(),
        crew.getTitle(),
        crew.getDescription(),
        imageUrl,
        crew.getCategory(),
        crew.getStatus(),
        crew.getDepositAmount(),
        crew.getMinParticipants(),
        crew.getMaxParticipants(),
        missionRule.getFrequencyType(),
        scheduleDays,
        missionRule.getDailySettlementType(),
        crew.getHostAgreementVersion(),
        toSeoulOffset(crew.getHostAgreedAt()),
        toSeoulOffset(crew.getRecruitmentDeadline()),
        toSeoulOffset(crew.getStartAt()),
        toSeoulOffset(crew.getActivatedAt()),
        toSeoulOffset(crew.getEndAt()),
        toSeoulOffset(crew.getCreatedAt()),
        MyParticipationResponse.from(participant));
  }

  private static OffsetDateTime toSeoulOffset(LocalDateTime ldt) {
    if (ldt == null) {
      return null;
    }
    return ldt.atZone(SEOUL_ZONE).toOffsetDateTime();
  }
}
