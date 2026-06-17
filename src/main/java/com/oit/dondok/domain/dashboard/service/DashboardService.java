package com.oit.dondok.domain.dashboard.service;

import com.oit.dondok.domain.crew.entity.Crew;
import com.oit.dondok.domain.crew.entity.CrewParticipant;
import com.oit.dondok.domain.crew.exception.CrewErrorCode;
import com.oit.dondok.domain.crew.repository.CrewQueryRepository;
import com.oit.dondok.domain.dashboard.dto.response.DashboardCrewResponse;
import com.oit.dondok.domain.dashboard.dto.response.DashboardResponse;
import com.oit.dondok.domain.dashboard.dto.response.MaxDeltaCrewResponse;
import com.oit.dondok.domain.dashboard.port.CrewBatchProjection;
import com.oit.dondok.domain.dashboard.port.DashboardProjectionPort;
import com.oit.dondok.domain.image.port.ImageDeliveryPort;
import com.oit.dondok.domain.image.port.ImageObjectKey;
import com.oit.dondok.global.exception.CustomException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class DashboardService {

  private static final int RATIO_SCALE = 3;
  private static final Duration IMAGE_URL_TTL = Duration.ofMinutes(10);

  private final CrewQueryRepository crewQueryRepository;
  private final DashboardProjectionPort dashboardProjectionPort;
  private final ImageDeliveryPort imageDeliveryPort;

  @Transactional(readOnly = true)
  public DashboardResponse getDashboard(UUID memberUuid) {
    List<CrewParticipant> lockedParticipants =
        crewQueryRepository.findMyLockedCrewParticipants(memberUuid);

    if (lockedParticipants.isEmpty()) {
      ensureParticipationHistoryExists(memberUuid);
      return emptyDashboard();
    }
    return buildDashboard(lockedParticipants);
  }

  // LOCKED 참여 크루가 없을 때, 참여 이력조차 없으면 404. 이력이 있으면(빈 대시보드) 통과.
  private void ensureParticipationHistoryExists(UUID memberUuid) {
    if (!crewQueryRepository.hasAnyCrewParticipant(memberUuid)) {
      throw new CustomException(CrewErrorCode.PARTICIPANT_NOT_FOUND);
    }
  }

  private DashboardResponse buildDashboard(List<CrewParticipant> lockedParticipants) {
    Long memberId = lockedParticipants.get(0).getMember().getId();
    List<Long> crewIds = lockedParticipants.stream().map(p -> p.getCrew().getId()).toList();
    Map<Long, CrewBatchProjection> projections =
        dashboardProjectionPort.findLatestProjectionsByCrew(memberId, crewIds);

    // crew_id ASC 순서 유지(repo 정렬) → max_delta 동률 tie-break(crew_id ASC)와 일치한다.
    List<DashboardCrewResponse> crews =
        lockedParticipants.stream()
            .map(
                participant ->
                    toCrewResponse(participant, projections.get(participant.getCrew().getId())))
            .toList();

    return aggregate(crews);
  }

  private DashboardCrewResponse toCrewResponse(
      CrewParticipant participant, CrewBatchProjection projection) {
    Crew crew = participant.getCrew();

    String shareRatio = null;
    Long expectedRefundAmount = null;
    Long todayDeltaAmount = null;

    if (projection != null) {
      shareRatio = formatShareRatio(projection.shareRatio());
      expectedRefundAmount = projection.expectedRefundAmount();
      if (expectedRefundAmount != null && projection.previousExpectedRefundAmount() != null) {
        todayDeltaAmount = expectedRefundAmount - projection.previousExpectedRefundAmount();
      }
    }
    return new DashboardCrewResponse(
        crew.getId(),
        crew.getTitle(),
        crew.getCategory(),
        resolveImageUrl(crew.getImageS3Key()),
        shareRatio,
        expectedRefundAmount,
        todayDeltaAmount);
  }

  private DashboardResponse aggregate(List<DashboardCrewResponse> crews) {
    long totalExpectedRefund = 0L;
    long totalDelta = 0L;
    int rising = 0;
    int falling = 0;
    MaxDeltaCrewResponse maxDeltaCrew = null;
    // 0으로 시작 -> 실제 변동(절댓값 >= 1)이 있는 크루만 후보. 모두 0이면 max_delta_crew는 null.
    long maxAbsDelta = 0L;

    for (DashboardCrewResponse crew : crews) {
      if (crew.expectedRefundAmount() != null) {
        totalExpectedRefund += crew.expectedRefundAmount();
      }
      Long delta = crew.todayDeltaAmount();
      if (delta == null) {
        continue;
      }
      totalDelta += delta;
      if (delta > 0) {
        rising++;
      } else if (delta < 0) {
        falling++;
      }
      long absDelta = Math.abs(delta);
      if (absDelta > maxAbsDelta) {
        maxAbsDelta = absDelta;
        maxDeltaCrew = new MaxDeltaCrewResponse(crew.crewId(), crew.crewName(), delta);
      }
    }
    return new DashboardResponse(
        totalExpectedRefund,
        totalDelta,
        formatRatio(totalDelta, totalExpectedRefund),
        rising,
        falling,
        maxDeltaCrew,
        crews);
  }

  private DashboardResponse emptyDashboard() {
    return new DashboardResponse(0L, 0L, "0", 0, 0, null, List.of());
  }

  private String resolveImageUrl(String imageS3Key) {
    if (!StringUtils.hasText(imageS3Key)) {
      return null;
    }
    return imageDeliveryPort.createDeliveryUrl(new ImageObjectKey(imageS3Key), IMAGE_URL_TTL).url();
  }

  private String formatShareRatio(BigDecimal shareRatio) {
    if (shareRatio == null) {
      return null;
    }
    return shareRatio.stripTrailingZeros().toPlainString();
  }

  private String formatRatio(long delta, long total) {
    // 변동이 없거나(0) 분모가 0이면 일관되게 "0"으로 반환한다(빈 대시보드와 동일 표현).
    if (delta == 0L || total == 0L) {
      return "0";
    }
    return BigDecimal.valueOf(delta)
        .divide(BigDecimal.valueOf(total), RATIO_SCALE, RoundingMode.HALF_UP)
        .toPlainString();
  }
}
