package com.oit.dondok.domain.crew.dto.response;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.oit.dondok.domain.crew.entity.Crew;
import com.oit.dondok.domain.crew.entity.CrewStatus;
import com.oit.dondok.domain.crew.entity.HostPolicyVersion;
import com.oit.dondok.domain.mission.entity.DailySettlementType;
import com.oit.dondok.domain.mission.entity.MissionFrequencyType;
import com.oit.dondok.domain.mission.entity.MissionRule;
import com.oit.dondok.global.util.SeoulDateTimeUtils;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record CrewDetailResponse(
    Long crewId,
    UUID hostMemberUuid,
    String hostNickname,
    String title,
    String description,
    String imageUrl,
    String category,
    CrewStatus status,
    String settlementStatus,
    Long depositAmount,
    Integer minParticipants,
    Integer maxParticipants,
    Integer currentParticipants,
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

  public static CrewDetailResponse of(
      Crew crew,
      MissionRule missionRule,
      List<String> scheduleDays,
      String settlementStatus,
      MyParticipationResponse myParticipation,
      String imageUrl,
      String hostNickname,
      Integer currentParticipants) {
    return new CrewDetailResponse(
        crew.getId(),
        crew.getHostMember().getUuid(),
        hostNickname,
        crew.getTitle(),
        crew.getDescription(),
        imageUrl,
        crew.getCategory(),
        crew.getStatus(),
        settlementStatus,
        crew.getDepositAmount(),
        crew.getMinParticipants(),
        crew.getMaxParticipants(),
        currentParticipants,
        missionRule.getFrequencyType(),
        scheduleDays,
        missionRule.getDailySettlementType(),
        crew.getHostAgreementVersion(),
        SeoulDateTimeUtils.toSeoulOffset(crew.getHostAgreedAt()),
        SeoulDateTimeUtils.toSeoulOffset(crew.getRecruitmentDeadline()),
        SeoulDateTimeUtils.toSeoulOffset(crew.getStartAt()),
        SeoulDateTimeUtils.toSeoulOffset(crew.getActivatedAt()),
        SeoulDateTimeUtils.toSeoulOffset(crew.getEndAt()),
        myParticipation);
  }
}
