package com.oit.dondok.domain.member.service;

import com.oit.dondok.domain.image.port.ImageDeliveryPort;
import com.oit.dondok.domain.image.port.ImageObjectKey;
import com.oit.dondok.domain.member.dto.response.ActivityStatsResponse;
import com.oit.dondok.domain.member.dto.response.CrewActivityInfoResponse;
import com.oit.dondok.domain.member.dto.response.MemberPublicProfileResponse;
import com.oit.dondok.domain.member.dto.response.PublicActivityInfoResponse;
import com.oit.dondok.domain.member.exception.MemberErrorCode;
import com.oit.dondok.domain.member.repository.ActivityStatsProjection;
import com.oit.dondok.domain.member.repository.CrewActivityInfoProjection;
import com.oit.dondok.domain.member.repository.MemberActivityQueryRepository;
import com.oit.dondok.domain.member.repository.MemberProfileProjection;
import com.oit.dondok.domain.member.repository.MemberProfileQueryRepository;
import com.oit.dondok.global.exception.CustomException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MemberPublicProfileService {

  private static final Duration PROFILE_IMAGE_URL_TTL = Duration.ofMinutes(10);

  private final MemberProfileQueryRepository memberProfileQueryRepository;
  private final MemberActivityQueryRepository memberActivityQueryRepository;
  private final ImageDeliveryPort imageDeliveryPort;

  public MemberPublicProfileResponse findPublicProfileByMemberUuid(UUID memberUuid) {
    MemberProfileProjection profile =
        memberProfileQueryRepository
            .findByMemberUuid(memberUuid)
            .orElseThrow(() -> new CustomException(MemberErrorCode.MEMBER_NOT_FOUND));

    CrewActivityInfoProjection crew =
        memberActivityQueryRepository.findCrewActivityInfo(memberUuid);
    long totalVerificationCount = memberActivityQueryRepository.countTotalVerification(memberUuid);
    ActivityStatsProjection stats = memberActivityQueryRepository.findActivityStats(memberUuid);

    return new MemberPublicProfileResponse(
        profile.memberUuid(),
        profile.nickname(),
        resolveProfileImageUrl(profile.profileImageS3Key()),
        profile.statusMessage(),
        profile.createdAt(),
        new PublicActivityInfoResponse(
            new CrewActivityInfoResponse(
                crew.totalCrewCount(), crew.activeCrewCount(), crew.completedCrewCount()),
            totalVerificationCount),
        new ActivityStatsResponse(
            stats.totalRecognizedSuccessCount(),
            formatScale6(stats.highestShareRatio()),
            stats.highestShareRatioCrewId(),
            stats.highestShareRatioCrewTitle(),
            stats.averageSuccessRate()));
  }

  private String resolveProfileImageUrl(String profileImageS3Key) {
    if (!StringUtils.hasText(profileImageS3Key)) {
      return null;
    }
    return imageDeliveryPort
        .createDeliveryUrl(new ImageObjectKey(profileImageS3Key), PROFILE_IMAGE_URL_TTL)
        .url();
  }

  private static String formatScale6(BigDecimal value) {
    if (value == null) {
      return null;
    }
    return value.setScale(6, RoundingMode.FLOOR).toPlainString();
  }
}
