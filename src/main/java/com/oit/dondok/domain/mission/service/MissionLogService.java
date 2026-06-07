package com.oit.dondok.domain.mission.service;

import com.oit.dondok.domain.crew.entity.Crew;
import com.oit.dondok.domain.crew.entity.CrewParticipant;
import com.oit.dondok.domain.crew.entity.CrewParticipantStatus;
import com.oit.dondok.domain.crew.exception.CrewErrorCode;
import com.oit.dondok.domain.crew.repository.CrewParticipantRepository;
import com.oit.dondok.domain.image.port.ImageObjectKeyPolicy;
import com.oit.dondok.domain.mission.dto.request.MissionLogCreateRequest;
import com.oit.dondok.domain.mission.dto.response.ImageVerifyResponse;
import com.oit.dondok.domain.mission.dto.response.MissionLogCreateResponse;
import com.oit.dondok.domain.mission.entity.CertificationStatus;
import com.oit.dondok.domain.mission.entity.MissionFrequencyType;
import com.oit.dondok.domain.mission.entity.MissionLog;
import com.oit.dondok.domain.mission.entity.MissionRule;
import com.oit.dondok.domain.mission.exception.MissionErrorCode;
import com.oit.dondok.domain.mission.port.ImageProcessingPort;
import com.oit.dondok.domain.mission.repository.MissionLogRepository;
import com.oit.dondok.domain.mission.repository.MissionRuleRepository;
import com.oit.dondok.domain.mission.repository.MissionScheduleDayRepository;
import com.oit.dondok.global.exception.CustomException;
import com.oit.dondok.global.exception.GlobalErrorCode;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class MissionLogService {

  private static final ZoneId SEOUL_ZONE = ZoneId.of("Asia/Seoul");

  private final CrewParticipantRepository crewParticipantRepository;
  private final MissionRuleRepository missionRuleRepository;
  private final MissionScheduleDayRepository missionScheduleDayRepository;
  private final MissionLogRepository missionLogRepository;
  private final MissionImageService missionImageService;
  private final ImageObjectKeyPolicy imageObjectKeyPolicy;
  private final ImageProcessingPort imageProcessingPort;

  @Transactional
  public MissionLogCreateResponse createMissionLog(
      UUID memberUuid, MissionLogCreateRequest request) {
    Long crewId = request.crewId();
    String s3Key = request.imageS3Key();
    OffsetDateTime serverTime = OffsetDateTime.now(SEOUL_ZONE); // 서버 수신 시각(KST)

    // pre-check: S3를 건드리지 않는 가벼운 검증을 먼저 통과시킨다
    CrewParticipant participant = findParticipant(memberUuid, crewId);
    validateKeyOwnership(crewId, participant.getId(), s3Key);
    validateEligible(participant);
    validateMissionPeriod(participant, serverTime);
    validateMissionDay(crewId, serverTime);
    validateNoDuplicateToday(participant.getId(), serverTime);

    // evidence 순서: 원본에서 추출 -> 로그 기록 -> reEncode(EXIF 제거)
    ImageVerifyResponse verify =
        missionImageService.getImageVerifyResponse(crewId, s3Key, serverTime);
    MissionLog saved =
        recordPendingReviewLog(participant, request.caption(), s3Key, verify, serverTime);
    imageProcessingPort.reEncode(s3Key);

    // image_url은 read 시 ImageDeliveryPort로 파생하므로 생성 응답에서는 null (nullable 계약)
    return MissionLogCreateResponse.from(saved, null);
  }

  // participant를 (crewId, memberUuid)로 서버 조회
  private CrewParticipant findParticipant(UUID memberUuid, Long crewId) {
    return crewParticipantRepository
        .findByCrewIdAndMemberUuid(crewId, memberUuid)
        .orElseThrow(() -> new CustomException(CrewErrorCode.PARTICIPANT_NOT_FOUND));
  }

  // 제출 key가 participant 네임스페이스인지 검증 (IDOR/변조 차단)
  private void validateKeyOwnership(Long crewId, Long participantId, String s3Key) {
    if (!imageObjectKeyPolicy.matchesMissionKey(crewId, participantId, s3Key)) {
      throw new CustomException(GlobalErrorCode.INVALID_INPUT);
    }
  }

  // LOCKED 참여자만 인증 제출 가능
  private void validateEligible(CrewParticipant participant) {
    if (participant.getStatus() != CrewParticipantStatus.LOCKED) {
      throw new CustomException(MissionErrorCode.PARTICIPANT_NOT_ELIGIBLE);
    }
  }

  // server_time(KST)이 크루 미션 기간 [start_at, end_at] 안에 있어야 함 (시작 전/종료 후 제출 차단)
  private void validateMissionPeriod(CrewParticipant participant, OffsetDateTime serverTime) {
    Crew crew = participant.getCrew();
    LocalDateTime now = serverTime.toLocalDateTime();
    if (now.isBefore(crew.getStartAt())) {
      throw new CustomException(MissionErrorCode.MISSION_NOT_STARTED);
    }
    if (now.isAfter(crew.getEndAt())) {
      throw new CustomException(MissionErrorCode.MISSION_ENDED);
    }
  }

  // SPECIFIC_DAYS 크루는 server_time(KST) 요일이 미션 가능일이어야 함
  private void validateMissionDay(Long crewId, OffsetDateTime serverTime) {
    MissionRule missionRule =
        missionRuleRepository
            .findByCrewId(crewId)
            .orElseThrow(() -> new CustomException(MissionErrorCode.MISSION_RULE_NOT_FOUND));
    if (missionRule.getFrequencyType() != MissionFrequencyType.SPECIFIC_DAYS) {
      return;
    }
    int dayOfWeek = serverTime.getDayOfWeek().getValue(); // 1(월)~7(일), IOS-8601
    boolean isMissionDay =
        missionScheduleDayRepository.existsByMissionRuleIdAndDayOfWeek(
            missionRule.getId(), dayOfWeek);
    if (!isMissionDay) {
      throw new CustomException(MissionErrorCode.NOT_MISSION_DAY);
    }
  }

  // 당일 cadence slot 중복 검사 - SUCCESS 우선, 그다음 PENDING_REVIEW
  private void validateNoDuplicateToday(Long participantId, OffsetDateTime serverTime) {
    LocalDateTime dayStart = serverTime.toLocalDate().atStartOfDay();
    LocalDateTime nextDayStart = dayStart.plusDays(1);
    if (missionLogRepository
        .existsByCrewParticipantIdAndCertificationStatusAndServerTimeGreaterThanEqualAndServerTimeLessThan(
            participantId, CertificationStatus.SUCCESS, dayStart, nextDayStart)) {
      throw new CustomException(MissionErrorCode.ALREADY_CERTIFIED_TODAY);
    }
    if (missionLogRepository
        .existsByCrewParticipantIdAndCertificationStatusAndServerTimeGreaterThanEqualAndServerTimeLessThan(
            participantId, CertificationStatus.PENDING_REVIEW, dayStart, nextDayStart)) {
      throw new CustomException(MissionErrorCode.CERTIFICATION_IN_REVIEW);
    }
  }

  // 원본 기준 hash/EXIF를 보존해 PENDING_REVIEW 로그로 기록 (image_url 컬럼은 비움)
  private MissionLog recordPendingReviewLog(
      CrewParticipant participant,
      String caption,
      String s3Key,
      ImageVerifyResponse verify,
      OffsetDateTime serverTime) {
    LocalDateTime exifTakenAt =
        verify.takenAt() == null
            ? null
            : verify.takenAt().atZoneSameInstant(SEOUL_ZONE).toLocalDateTime();
    return missionLogRepository.save(
        MissionLog.createPendingReview(
            participant,
            s3Key,
            caption,
            verify.imageHash(),
            exifTakenAt,
            serverTime.toLocalDateTime()));
  }
}
