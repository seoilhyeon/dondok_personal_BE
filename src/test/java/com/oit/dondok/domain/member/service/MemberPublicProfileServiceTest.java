package com.oit.dondok.domain.member.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

import com.oit.dondok.domain.image.port.ImageDeliveryPort;
import com.oit.dondok.domain.image.port.ImageDeliveryUrl;
import com.oit.dondok.domain.image.port.ImageObjectKey;
import com.oit.dondok.domain.member.dto.response.MemberPublicProfileResponse;
import com.oit.dondok.domain.member.entity.MemberStatus;
import com.oit.dondok.domain.member.exception.MemberErrorCode;
import com.oit.dondok.domain.member.repository.ActivityStatsProjection;
import com.oit.dondok.domain.member.repository.CrewActivityInfoProjection;
import com.oit.dondok.domain.member.repository.MemberActivityQueryRepository;
import com.oit.dondok.domain.member.repository.MemberProfileProjection;
import com.oit.dondok.domain.member.repository.MemberProfileQueryRepository;
import com.oit.dondok.global.exception.CustomException;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MemberPublicProfileServiceTest {

  @Mock private MemberProfileQueryRepository memberProfileQueryRepository;
  @Mock private MemberActivityQueryRepository memberActivityQueryRepository;
  @Mock private ImageDeliveryPort imageDeliveryPort;

  @InjectMocks private MemberPublicProfileService memberPublicProfileService;

  @Test
  void findPublicProfileByMemberUuidReturnsFullProfileForExistingMember() {
    UUID memberUuid = UUID.fromString("018f4fd2-6d7a-7a41-9f58-6d07f5c3c901");
    OffsetDateTime joinedAt = OffsetDateTime.parse("2026-05-01T12:00:00+09:00");
    String profileImageS3Key = "profile/member.png";
    String profileImageUrl = "https://cdn.example.com/profile/member.png";
    MemberProfileProjection profile =
        new MemberProfileProjection(
            memberUuid,
            "user@example.com",
            "돈독러",
            profileImageS3Key,
            "오늘도 한 걸음 더",
            1L,
            MemberStatus.ACTIVE,
            joinedAt);
    given(memberProfileQueryRepository.findByMemberUuid(memberUuid))
        .willReturn(Optional.of(profile));
    given(memberActivityQueryRepository.findCrewActivityInfo(memberUuid))
        .willReturn(new CrewActivityInfoProjection(17L, 3L, 14L));
    given(memberActivityQueryRepository.countTotalVerification(memberUuid)).willReturn(450L);
    given(memberActivityQueryRepository.findActivityStats(memberUuid))
        .willReturn(
            new ActivityStatsProjection(420L, new BigDecimal("0.250000"), 42L, "아침 갓생 30일", null));
    given(
            imageDeliveryPort.createDeliveryUrl(
                new ImageObjectKey(profileImageS3Key), Duration.ofMinutes(10)))
        .willReturn(
            new ImageDeliveryUrl(
                profileImageUrl, OffsetDateTime.parse("2026-05-01T12:10:00+09:00")));

    MemberPublicProfileResponse response =
        memberPublicProfileService.findPublicProfileByMemberUuid(memberUuid);

    assertThat(response.memberUuid()).isEqualTo(memberUuid);
    assertThat(response.nickname()).isEqualTo("돈독러");
    assertThat(response.profileImageUrl()).isEqualTo(profileImageUrl);
    assertThat(response.statusMessage()).isEqualTo("오늘도 한 걸음 더");
    assertThat(response.joinedAt()).isEqualTo(joinedAt);
    assertThat(response.activityInfo().crew().totalCrewCount()).isEqualTo(17L);
    assertThat(response.activityInfo().crew().activeCrewCount()).isEqualTo(3L);
    assertThat(response.activityInfo().crew().completedCrewCount()).isEqualTo(14L);
    assertThat(response.activityInfo().totalVerificationCount()).isEqualTo(450L);
    assertThat(response.activityStats().totalRecognizedSuccessCount()).isEqualTo(420L);
    assertThat(response.activityStats().highestShareRatio()).isEqualTo("0.250000");
    assertThat(response.activityStats().highestShareRatioCrewId()).isEqualTo(42L);
    assertThat(response.activityStats().highestShareRatioCrewTitle()).isEqualTo("아침 갓생 30일");
  }

  @Test
  void findPublicProfileByMemberUuidThrowsMemberNotFoundWhenMemberDoesNotExist() {
    UUID memberUuid = UUID.randomUUID();
    given(memberProfileQueryRepository.findByMemberUuid(memberUuid)).willReturn(Optional.empty());

    assertThatThrownBy(() -> memberPublicProfileService.findPublicProfileByMemberUuid(memberUuid))
        .isInstanceOf(CustomException.class)
        .extracting("errorCode")
        .isEqualTo(MemberErrorCode.MEMBER_NOT_FOUND);

    then(memberActivityQueryRepository).should(never()).findCrewActivityInfo(memberUuid);
    then(memberActivityQueryRepository).should(never()).countTotalVerification(memberUuid);
    then(memberActivityQueryRepository).should(never()).findActivityStats(memberUuid);
    then(imageDeliveryPort).shouldHaveNoInteractions();
  }

  @Test
  void findPublicProfileByMemberUuidReturnsNullActivityStatsWhenNoSettlementItems() {
    UUID memberUuid = UUID.fromString("018f4fd2-6d7a-7a41-9f58-6d07f5c3c902");
    OffsetDateTime joinedAt = OffsetDateTime.parse("2026-05-01T12:00:00+09:00");
    MemberProfileProjection profile =
        new MemberProfileProjection(
            memberUuid, "user2@example.com", "신규회원", null, null, 0L, MemberStatus.ACTIVE, joinedAt);
    given(memberProfileQueryRepository.findByMemberUuid(memberUuid))
        .willReturn(Optional.of(profile));
    given(memberActivityQueryRepository.findCrewActivityInfo(memberUuid))
        .willReturn(new CrewActivityInfoProjection(0L, 0L, 0L));
    given(memberActivityQueryRepository.countTotalVerification(memberUuid)).willReturn(0L);
    given(memberActivityQueryRepository.findActivityStats(memberUuid))
        .willReturn(new ActivityStatsProjection(0L, null, null, null, null));

    MemberPublicProfileResponse response =
        memberPublicProfileService.findPublicProfileByMemberUuid(memberUuid);

    assertThat(response.profileImageUrl()).isNull();
    assertThat(response.activityInfo().totalVerificationCount()).isZero();
    assertThat(response.activityStats().totalRecognizedSuccessCount()).isZero();
    assertThat(response.activityStats().highestShareRatio()).isNull();
    assertThat(response.activityStats().highestShareRatioCrewId()).isNull();
    assertThat(response.activityStats().highestShareRatioCrewTitle()).isNull();
    assertThat(response.activityStats().averageSuccessRate()).isNull();
    then(imageDeliveryPort).shouldHaveNoInteractions();
  }
}
