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
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

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

    // pre-check: S3를 건드리지 않는 가벼운 검증을 먼저 통과시킨다.
    // 당일 중복 검사는 동시 요청에 대해 best-effort다 — insert-level unique 제약/행 잠금이 없어
    // 거의 동시에 들어온 두 제출이 함께 PENDING_REVIEW로 생성되는 race가 가능하다.
    // 데이터 정합은 깨지지 않으며, 동일 cadence slot 중복 인정의 최종 방어선은 정산 단계의
    // settlement_item.calculation_reason 중복 제외다. (락 기반 강화는 후속 과제)
    CrewParticipant participant = findParticipant(memberUuid, crewId);
    validateKeyOwnership(crewId, participant.getId(), s3Key);
    validateEligible(participant);
    validateMissionPeriod(participant, serverTime);
    validateMissionDay(crewId, serverTime);
    validateNoDuplicateToday(participant.getId(), serverTime);

    // evidence 순서: 원본에서 추출 -> 로그 기록 -> (커밋 후) reEncode(EXIF 제거)
    ImageVerifyResponse verify =
        missionImageService.getImageVerifyResponse(crewId, s3Key, serverTime);
    MissionLog saved =
        recordPendingReviewLog(participant, request.caption(), s3Key, verify, serverTime);
    reEncodeAfterCommit(s3Key);

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
      throw new CustomException(MissionErrorCode.INVALID_IMAGE_KEY);
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

  // 당일 cadence slot 중복 검사 - SUCCESS 우선, 그다음 PENDING_REVIEW.
  // best-effort 읽기 검사라 동시 요청 race는 막지 못한다(최종 방어선은 정산 단계 dedup, createMissionLog 주석 참고).
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
            verify.exifRisk(),
            verify.duplicate(),
            serverTime.toLocalDateTime()));
  }

  // reEncode는 원본 EXIF를 지우는 S3 덮어쓰기(GET+PUT)이자 커밋 이후의 부가 처리다.
  // 커밋이 성공한 뒤에만 실행해 "로그는 롤백됐는데 원본은 이미 파괴" 불일치와 S3 왕복 동안의 DB 커넥션 점유를 막는다.
  // 커밋 후 실패는 이미 커밋된 인증 로그(=성공한 create)를 5xx로 뒤집지 않도록 삼킨다
  // (클라가 실패로 보고 재시도하면 CERTIFICATION_IN_REVIEW로 막히는 문제 방지).
  // 실패한 원본의 EXIF 제거 보장(reEncode 상태 관리 + 재처리 배치)은 후속 이슈에서 다룬다.
  // 활성 트랜잭션이 없으면(예: 단위 테스트) 곧바로 실행한다.
  private void reEncodeAfterCommit(String s3Key) {
    Runnable reEncode =
        () -> {
          try {
            imageProcessingPort.reEncode(s3Key);
          } catch (RuntimeException ignored) {
            // 후속 이슈(상태 관리 + 재처리 배치)에서 복구. 여기서는 삼켜 create 응답을 깨지 않는다.
          }
        };
    if (!TransactionSynchronizationManager.isSynchronizationActive()) {
      reEncode.run();
      return;
    }
    TransactionSynchronizationManager.registerSynchronization(
        new TransactionSynchronization() {
          @Override
          public void afterCommit() {
            reEncode.run();
          }
        });
  }
}
