package com.oit.dondok.domain.member.service;

import com.oit.dondok.domain.member.dto.response.ActivityInfoResponse;
import com.oit.dondok.domain.member.dto.response.ActivityStatsResponse;
import com.oit.dondok.domain.member.dto.response.ActivitySummaryResponse;
import com.oit.dondok.domain.member.dto.response.CrewActivityInfoResponse;
import com.oit.dondok.domain.member.exception.MemberErrorCode;
import com.oit.dondok.domain.member.repository.ActivityStatsProjection;
import com.oit.dondok.domain.member.repository.CrewActivityInfoProjection;
import com.oit.dondok.domain.member.repository.MemberActivityQueryRepository;
import com.oit.dondok.global.exception.CustomException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MemberActivitySummaryService {

  private static final ZoneId SEOUL_ZONE_ID = ZoneId.of("Asia/Seoul");

  private final MemberActivityQueryRepository memberActivityQueryRepository;

  public ActivitySummaryResponse findActivitySummaryByMemberUuid(UUID memberUuid) {
    if (!memberActivityQueryRepository.existsByMemberUuid(memberUuid)) {
      throw new CustomException(MemberErrorCode.MEMBER_NOT_FOUND);
    }

    CrewActivityInfoProjection crew =
        memberActivityQueryRepository.findCrewActivityInfo(memberUuid);
    long totalVerificationCount = memberActivityQueryRepository.countTotalVerification(memberUuid);
    long unreadNotificationCount =
        memberActivityQueryRepository.countUnreadNotifications(memberUuid);
    ActivityStatsProjection stats = memberActivityQueryRepository.findActivityStats(memberUuid);

    return new ActivitySummaryResponse(
        memberUuid,
        new ActivityInfoResponse(
            new CrewActivityInfoResponse(
                crew.totalCrewCount(), crew.activeCrewCount(), crew.completedCrewCount()),
            totalVerificationCount,
            unreadNotificationCount),
        new ActivityStatsResponse(
            stats.totalRecognizedSuccessCount(),
            formatScale6(stats.highestShareRatio()),
            stats.highestShareRatioCrewId(),
            stats.highestShareRatioCrewTitle(),
            stats.averageSuccessRate()),
        OffsetDateTime.now(SEOUL_ZONE_ID));
  }

  private static String formatScale6(BigDecimal value) {
    if (value == null) {
      return null;
    }

    return value.setScale(6, RoundingMode.FLOOR).toPlainString();
  }
}
