package com.oit.dondok.domain.crew.dto.response;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.oit.dondok.domain.crew.entity.Crew;
import com.oit.dondok.domain.crew.entity.CrewStatus;
import com.oit.dondok.domain.crew.entity.HostPolicyVersion;
import com.oit.dondok.domain.mission.entity.DailySettlementType;
import com.oit.dondok.domain.mission.entity.MissionFrequencyType;
import com.oit.dondok.domain.mission.entity.MissionRule;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record CrewDetailResponse(
    Long crewId,
    UUID hostMemberUuid,
    String title,
    String description,
    String imageUrl,
    String category,
    CrewStatus status,
    String settlementStatus,
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
    MyParticipationResponse myParticipation) {

  private static final ZoneId SEOUL_ZONE = ZoneId.of("Asia/Seoul");

  public static CrewDetailResponse of(
      Crew crew,
      MissionRule missionRule,
      List<String> scheduleDays,
      String settlementStatus,
      MyParticipationResponse myParticipation,
      String imageUrl) {
    return new CrewDetailResponse(
        crew.getId(),
        crew.getHostMember().getUuid(),
        crew.getTitle(),
        crew.getDescription(),
        imageUrl,
        crew.getCategory(),
        crew.getStatus(),
        settlementStatus,
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
        myParticipation);
  }

  private static OffsetDateTime toSeoulOffset(LocalDateTime ldt) {
    if (ldt == null) {
      return null;
    }
    return ldt.atZone(SEOUL_ZONE).toOffsetDateTime();
  }
}
