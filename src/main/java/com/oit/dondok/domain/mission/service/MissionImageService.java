package com.oit.dondok.domain.mission.service;

import com.oit.dondok.domain.crew.entity.CrewParticipant;
import com.oit.dondok.domain.crew.exception.CrewErrorCode;
import com.oit.dondok.domain.crew.repository.CrewParticipantRepository;
import com.oit.dondok.domain.mission.dto.response.ImageVerifyResponse;
import com.oit.dondok.domain.mission.entity.DailySettlementType;
import com.oit.dondok.domain.mission.entity.ExifRisk;
import com.oit.dondok.domain.mission.entity.MissionRule;
import com.oit.dondok.domain.mission.exception.MissionErrorCode;
import com.oit.dondok.domain.mission.port.ImageMetadata;
import com.oit.dondok.domain.mission.port.ImageMetadataPort;
import com.oit.dondok.domain.mission.repository.MissionLogRepository;
import com.oit.dondok.domain.mission.repository.MissionRuleRepository;
import com.oit.dondok.global.exception.CustomException;
import java.time.OffsetDateTime;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class MissionImageService {

  private final CrewParticipantRepository crewParticipantRepository;
  private final ImageMetadataPort imageMetadataPort;
  private final MissionLogRepository missionLogRepository;
  private final MissionRuleRepository missionRuleRepository;

  // 미션 이미지 업로드 소유권 검증
  @Transactional(readOnly = true)
  public CrewParticipant getOwnedParticipant(UUID memberUuid, Long crewId, Long crewParticipantId) {
    CrewParticipant participant =
        crewParticipantRepository
            .findById(crewParticipantId)
            .orElseThrow(() -> new CustomException(CrewErrorCode.PARTICIPANT_NOT_FOUND));

    boolean ownedByMember = participant.getMember().getUuid().equals(memberUuid);
    boolean belongsToCrew = participant.getCrew().getId().equals(crewId);
    if (!ownedByMember || !belongsToCrew) {
      throw new CustomException(CrewErrorCode.PARTICIPANT_NOT_FOUND);
    }

    return participant;
  }

  // 원본 이미지에서 EXIF 시각, 해시를 추출하고 risk signal(ExifRisk, 중복)을 산출
  @Transactional(readOnly = true)
  public ImageVerifyResponse getImageVerifyResponse(
      Long crewId, String s3Key, OffsetDateTime serverTime) {
    ImageMetadata metadata = imageMetadataPort.extract(s3Key);

    // 크루의 미션 규칙에서 인증마감 기준을 가져온다
    MissionRule missionRule =
        missionRuleRepository
            .findByCrewId(crewId)
            .orElseThrow(() -> new CustomException(MissionErrorCode.MISSION_RULE_NOT_FOUND));

    ExifRisk exifRisk =
        classifyExifRisk(metadata.takenAt(), serverTime, missionRule.getDailySettlementType());
    boolean duplicate =
        missionLogRepository.existsByCrewParticipantCrewIdAndImageHash(crewId, metadata.sha256());
    return new ImageVerifyResponse(metadata.takenAt(), metadata.sha256(), exifRisk, duplicate);
  }

  private ExifRisk classifyExifRisk(
      OffsetDateTime takenAt, OffsetDateTime serverTime, DailySettlementType type) {
    if (takenAt == null) {
      return ExifRisk.MISSING;
    }
    // 인증 대상 날짜는 server_time(Asia/Seoul) 기준. 윈도우 = [당일 00:00, 당일 인증마감]
    OffsetDateTime windowStart =
        serverTime.toLocalDate().atStartOfDay().atOffset(serverTime.getOffset());
    OffsetDateTime deadline =
        serverTime
            .toLocalDate()
            .atTime(type.getCertificationDeadline())
            .atOffset(serverTime.getOffset());
    // 마감 이후 또는 server_time(제출 시각) 이후에 찍힌 사진은 인정 윈도우 밖
    if (takenAt.isBefore(windowStart) || takenAt.isAfter(deadline) || takenAt.isAfter(serverTime)) {
      return ExifRisk.TIME_INVALID;
    }
    return ExifRisk.NORMAL;
  }
}
