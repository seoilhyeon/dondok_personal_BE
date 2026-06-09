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
import com.oit.dondok.global.util.SeoulDateTimeUtils;
import java.time.OffsetDateTime;
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
    List<String> missionScheduleDays,
    DailySettlementType dailySettlementType,
    HostPolicyVersion hostAgreementVersion,
    OffsetDateTime hostAgreedAt,
    OffsetDateTime recruitmentDeadline,
    OffsetDateTime startAt,
    OffsetDateTime activatedAt,
    OffsetDateTime endAt,
    OffsetDateTime createdAt,
    MyParticipationResponse myParticipation) {

  public static CrewCreateResponse of(
      Crew crew,
      MissionRule missionRule,
      List<String> scheduleDays,
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
        SeoulDateTimeUtils.toSeoulOffset(crew.getHostAgreedAt()),
        SeoulDateTimeUtils.toSeoulOffset(crew.getRecruitmentDeadline()),
        SeoulDateTimeUtils.toSeoulOffset(crew.getStartAt()),
        SeoulDateTimeUtils.toSeoulOffset(crew.getActivatedAt()),
        SeoulDateTimeUtils.toSeoulOffset(crew.getEndAt()),
        SeoulDateTimeUtils.toSeoulOffset(crew.getCreatedAt()),
        MyParticipationResponse.from(participant));
  }
}
