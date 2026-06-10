package com.oit.dondok.domain.mission.service;

import com.oit.dondok.domain.crew.entity.Crew;
import com.oit.dondok.domain.crew.exception.CrewErrorCode;
import com.oit.dondok.domain.crew.repository.CrewRepository;
import com.oit.dondok.domain.image.port.ImageDeliveryPort;
import com.oit.dondok.domain.image.port.ImageObjectKey;
import com.oit.dondok.domain.mission.dto.response.HostMissionLogReviewCountsResponse;
import com.oit.dondok.domain.mission.dto.response.HostMissionLogReviewItemResponse;
import com.oit.dondok.domain.mission.dto.response.HostMissionLogReviewListResponse;
import com.oit.dondok.domain.mission.entity.MissionLog;
import com.oit.dondok.domain.mission.entity.MissionLogReviewBucket;
import com.oit.dondok.domain.mission.entity.MissionRule;
import com.oit.dondok.domain.mission.exception.MissionErrorCode;
import com.oit.dondok.domain.mission.repository.MissionLogQueryRepository;
import com.oit.dondok.domain.mission.repository.MissionRuleRepository;
import com.oit.dondok.domain.settlement.repository.SettlementRepository;
import com.oit.dondok.global.exception.CustomException;
import com.oit.dondok.global.exception.GlobalErrorCode;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class HostMissionLogReviewService {

  private static final ZoneId SEOUL_ZONE = ZoneId.of("Asia/Seoul");
  private static final Duration HOST_REVIEW_GRACE_DURATION = Duration.ofHours(72);
  private static final Duration IMAGE_URL_TTL = Duration.ofMinutes(10);
  private static final int DEFAULT_LIMIT = 20;
  private static final int MAX_LIMIT = 50;

  private final CrewRepository crewRepository;
  private final MissionRuleRepository missionRuleRepository;
  private final MissionLogQueryRepository missionLogQueryRepository;
  private final SettlementRepository settlementRepository;
  private final ImageDeliveryPort imageDeliveryPort;

  // 방장이 검토 가능한 인증 목록을 bucket과 cursor 기준으로 조회한다.
  @Transactional(readOnly = true)
  public HostMissionLogReviewListResponse getReviewableMissionLogs(
      UUID memberUuid, Long crewId, String bucketValue, String cursor, Integer limit) {
    MissionLogReviewBucket requestedBucket = MissionLogReviewBucket.from(bucketValue);
    int effectiveLimit = normalizeLimit(limit);

    validateHostCrew(crewId, memberUuid);

    MissionRule missionRule =
        missionRuleRepository
            .findByCrewId(crewId)
            .orElseThrow(() -> new CustomException(MissionErrorCode.MISSION_RULE_NOT_FOUND));

    if (settlementRepository.findByCrewId(crewId).isPresent()) {
      return emptyResponse();
    }

    LocalDateTime now = LocalDateTime.now(SEOUL_ZONE);
    Cursor decodedCursor = decodeCursor(cursor, requestedBucket);
    HostMissionLogReviewCountsResponse counts = countBuckets(crewId, now);

    List<ReviewCandidate> pageCandidates =
        missionLogQueryRepository
            .findReviewablePageByCrewId(
                crewId,
                requestedBucket,
                decodedCursor == null ? null : decodedCursor.sortTime(),
                decodedCursor == null ? null : decodedCursor.missionLogId(),
                effectiveLimit + 1,
                now)
            .stream()
            .map(log -> toCandidate(log, missionRule, requestedBucket))
            .toList();

    boolean hasNext = pageCandidates.size() > effectiveLimit;
    List<ReviewCandidate> page =
        hasNext ? pageCandidates.subList(0, effectiveLimit) : pageCandidates;
    String nextCursor = hasNext ? encodeCursor(page.get(page.size() - 1)) : null;

    return new HostMissionLogReviewListResponse(
        page.stream().map(this::toResponse).toList(), nextCursor, hasNext, counts);
  }

  // 목록 응답과 권한 검증에서 사용하는 방장 여부를 확인한다.
  private void validateHostCrew(Long crewId, UUID memberUuid) {
    if (crewRepository.existsByIdAndHostMemberUuid(crewId, memberUuid)) {
      return;
    }
    if (!crewRepository.existsById(crewId)) {
      throw new CustomException(CrewErrorCode.CREW_NOT_FOUND);
    }
    throw new CustomException(CrewErrorCode.FORBIDDEN_NOT_HOST);
  }

  // 조회 limit의 기본값과 상한을 적용한다.
  private int normalizeLimit(Integer limit) {
    if (limit == null) {
      return DEFAULT_LIMIT;
    }
    if (limit < 1) {
      throw new CustomException(GlobalErrorCode.INVALID_INPUT);
    }
    return Math.min(limit, MAX_LIMIT);
  }

  // DB count 쿼리로 전체 검토 가능 후보의 bucket별 카운트를 계산한다.
  private HostMissionLogReviewCountsResponse countBuckets(Long crewId, LocalDateTime now) {
    return new HostMissionLogReviewCountsResponse(
        toIntCount(
            missionLogQueryRepository.countReviewableByCrewIdAndBucket(
                crewId, MissionLogReviewBucket.URGENT, now)),
        toIntCount(
            missionLogQueryRepository.countReviewableByCrewIdAndBucket(
                crewId, MissionLogReviewBucket.WARNING, now)),
        toIntCount(
            missionLogQueryRepository.countReviewableByCrewIdAndBucket(
                crewId, MissionLogReviewBucket.NORMAL, now)));
  }

  // long count 값을 응답 DTO의 int 값으로 변환한다.
  private int toIntCount(long count) {
    return Math.toIntExact(count);
  }

  // DB에서 이미 선택된 bucket과 미션 규칙을 조합해 cursor와 응답에 필요한 값을 계산한다.
  private ReviewCandidate toCandidate(
      MissionLog missionLog, MissionRule missionRule, MissionLogReviewBucket bucket) {
    LocalDateTime hostReviewableUntil =
        missionRule
            .getDailySettlementType()
            .autoCertificationAt(missionLog.getServerTime().toLocalDate())
            .plus(HOST_REVIEW_GRACE_DURATION);
    LocalDateTime sortTime =
        bucket == MissionLogReviewBucket.URGENT ? hostReviewableUntil : missionLog.getServerTime();
    return new ReviewCandidate(missionLog, bucket, hostReviewableUntil, sortTime);
  }

  // 다음 페이지 요청에 사용할 opaque cursor를 생성한다.
  private String encodeCursor(ReviewCandidate candidate) {
    String payload =
        candidate.bucket().value()
            + "|"
            + candidate.sortTime()
            + "|"
            + candidate.missionLog().getId();
    return Base64.getUrlEncoder()
        .withoutPadding()
        .encodeToString(payload.getBytes(StandardCharsets.UTF_8));
  }

  // 클라이언트가 전달한 opaque cursor를 정렬 키로 복원한다.
  private Cursor decodeCursor(String cursor, MissionLogReviewBucket requestedBucket) {
    if (!StringUtils.hasText(cursor)) {
      return null;
    }
    try {
      String payload = new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8);
      String[] parts = payload.split("\\|");
      if (parts.length != 3) {
        throw new IllegalArgumentException("invalid cursor");
      }
      MissionLogReviewBucket cursorBucket = MissionLogReviewBucket.from(parts[0]);
      if (cursorBucket != requestedBucket) {
        throw new IllegalArgumentException("bucket mismatch");
      }
      return new Cursor(LocalDateTime.parse(parts[1]), Long.parseLong(parts[2]));
    } catch (RuntimeException exception) {
      throw new CustomException(GlobalErrorCode.INVALID_INPUT);
    }
  }

  // 검토 후보를 프론트 계약에 맞는 응답 DTO로 변환한다.
  private HostMissionLogReviewItemResponse toResponse(ReviewCandidate candidate) {
    MissionLog missionLog = candidate.missionLog();
    Crew crew = missionLog.getCrewParticipant().getCrew();
    return new HostMissionLogReviewItemResponse(
        missionLog.getId(),
        crew.getId(),
        missionLog.getCrewParticipant().getId(),
        missionLog.getCrewParticipant().getMember().getUuid(),
        missionLog.getCrewParticipant().getMember().getNickname(),
        resolveImageUrl(missionLog.getCrewParticipant().getMember().getProfileImageS3Key()),
        resolveImageUrl(missionLog.getImageS3Key()),
        missionLog.getCaption(),
        toSeoulOffset(missionLog.getServerTime()),
        toSeoulOffset(missionLog.getExifTakenAt()),
        missionLog.getExifRisk(),
        missionLog.isDuplicateHash(),
        candidate.bucket().value(),
        missionLog.getCertificationStatus(),
        missionLog.getDecisionType(),
        missionLog.getRejectReasonCode(),
        toSeoulOffset(candidate.hostReviewableUntil()));
  }

  private OffsetDateTime toSeoulOffset(LocalDateTime ldt) {
    return ldt == null ? null : ldt.atZone(SEOUL_ZONE).toOffsetDateTime();
  }

  // 저장된 S3 key를 표시용 임시 URL로 변환한다.
  private String resolveImageUrl(String imageS3Key) {
    if (!StringUtils.hasText(imageS3Key)) {
      return null;
    }
    return imageDeliveryPort.createDeliveryUrl(new ImageObjectKey(imageS3Key), IMAGE_URL_TTL).url();
  }

  private HostMissionLogReviewListResponse emptyResponse() {
    return new HostMissionLogReviewListResponse(
        List.of(), null, false, new HostMissionLogReviewCountsResponse(0, 0, 0));
  }

  private record ReviewCandidate(
      MissionLog missionLog,
      MissionLogReviewBucket bucket,
      LocalDateTime hostReviewableUntil,
      LocalDateTime sortTime) {}

  private record Cursor(LocalDateTime sortTime, Long missionLogId) {}
}
