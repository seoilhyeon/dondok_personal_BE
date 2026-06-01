package com.oit.dondok.domain.member.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

import com.oit.dondok.domain.member.dto.response.ActivitySummaryResponse;
import com.oit.dondok.domain.member.exception.MemberErrorCode;
import com.oit.dondok.domain.member.repository.ActivityStatsProjection;
import com.oit.dondok.domain.member.repository.CrewActivityInfoProjection;
import com.oit.dondok.domain.member.repository.MemberActivityQueryRepository;
import com.oit.dondok.global.exception.CustomException;
import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MemberActivitySummaryServiceTest {

  @Mock private MemberActivityQueryRepository memberActivityQueryRepository;

  @InjectMocks private MemberActivitySummaryService memberActivitySummaryService;

  @Test
  void findActivitySummaryByMemberUuidMapsRepositoryProjectionsToResponse() {
    UUID memberUuid = UUID.fromString("018f4fd2-6d7a-7a41-9f58-6d07f5c3c901");
    givenActivitySummaryProjections(
        memberUuid,
        new CrewActivityInfoProjection(17L, 3L, 14L),
        24L,
        2L,
        new ActivityStatsProjection(450L, new BigDecimal("0.250000"), 42L, "아침 갓생 30일", null));

    ActivitySummaryResponse response =
        memberActivitySummaryService.findActivitySummaryByMemberUuid(memberUuid);

    assertThat(response.memberUuid()).isEqualTo(memberUuid);
    assertThat(response.activityInfo().crew().totalCrewCount()).isEqualTo(17L);
    assertThat(response.activityInfo().crew().activeCrewCount()).isEqualTo(3L);
    assertThat(response.activityInfo().crew().completedCrewCount()).isEqualTo(14L);
    assertThat(response.activityInfo().totalVerificationCount()).isEqualTo(24L);
    assertThat(response.activityInfo().unreadNotificationCount()).isEqualTo(2L);
    assertThat(response.activityStats().totalRecognizedSuccessCount()).isEqualTo(450L);
    assertThat(response.activityStats().highestShareRatio()).isEqualTo("0.250000");
    assertThat(response.activityStats().highestShareRatioCrewId()).isEqualTo(42L);
    assertThat(response.activityStats().highestShareRatioCrewTitle()).isEqualTo("아침 갓생 30일");
    assertThat(response.activityStats().averageSuccessRate()).isNull();
    assertThat(response.generatedAt().getOffset().getId()).isEqualTo("+09:00");
  }

  @Test
  void findActivitySummaryByMemberUuidFormatsHighestShareRatioToScaleSixWithFloorRounding() {
    UUID memberUuid = UUID.fromString("018f4fd2-6d7a-7a41-9f58-6d07f5c3c902");
    givenActivitySummaryProjections(
        memberUuid,
        new CrewActivityInfoProjection(0L, 0L, 0L),
        0L,
        0L,
        new ActivityStatsProjection(1L, new BigDecimal("0.1234567"), 1L, "테스트 크루", null));

    ActivitySummaryResponse response =
        memberActivitySummaryService.findActivitySummaryByMemberUuid(memberUuid);

    assertThat(response.activityStats().highestShareRatio()).isEqualTo("0.123456");
  }

  @Test
  void findActivitySummaryByMemberUuidKeepsOptionalStatsNullWhenProjectionHasNoStats() {
    UUID memberUuid = UUID.fromString("018f4fd2-6d7a-7a41-9f58-6d07f5c3c903");
    givenActivitySummaryProjections(
        memberUuid,
        new CrewActivityInfoProjection(0L, 0L, 0L),
        0L,
        0L,
        new ActivityStatsProjection(0L, null, null, null, null));

    ActivitySummaryResponse response =
        memberActivitySummaryService.findActivitySummaryByMemberUuid(memberUuid);

    assertThat(response.activityStats().totalRecognizedSuccessCount()).isZero();
    assertThat(response.activityStats().highestShareRatio()).isNull();
    assertThat(response.activityStats().highestShareRatioCrewId()).isNull();
    assertThat(response.activityStats().highestShareRatioCrewTitle()).isNull();
    assertThat(response.activityStats().averageSuccessRate()).isNull();
  }

  @Test
  void findActivitySummaryByMemberUuidThrowsWhenMemberDoesNotExist() {
    UUID memberUuid = UUID.randomUUID();
    given(memberActivityQueryRepository.existsByMemberUuid(memberUuid)).willReturn(false);

    assertThatThrownBy(
            () -> memberActivitySummaryService.findActivitySummaryByMemberUuid(memberUuid))
        .isInstanceOf(CustomException.class)
        .extracting("errorCode")
        .isEqualTo(MemberErrorCode.MEMBER_NOT_FOUND);

    then(memberActivityQueryRepository).should(never()).findCrewActivityInfo(memberUuid);
    then(memberActivityQueryRepository).should(never()).countTotalVerification(memberUuid);
    then(memberActivityQueryRepository).should(never()).countUnreadNotifications(memberUuid);
    then(memberActivityQueryRepository).should(never()).findActivityStats(memberUuid);
  }

  private void givenActivitySummaryProjections(
      UUID memberUuid,
      CrewActivityInfoProjection crew,
      long totalVerificationCount,
      long unreadNotificationCount,
      ActivityStatsProjection stats) {
    given(memberActivityQueryRepository.existsByMemberUuid(memberUuid)).willReturn(true);
    given(memberActivityQueryRepository.findCrewActivityInfo(memberUuid)).willReturn(crew);
    given(memberActivityQueryRepository.countTotalVerification(memberUuid))
        .willReturn(totalVerificationCount);
    given(memberActivityQueryRepository.countUnreadNotifications(memberUuid))
        .willReturn(unreadNotificationCount);
    given(memberActivityQueryRepository.findActivityStats(memberUuid)).willReturn(stats);
  }
}
