package com.oit.dondok.domain.dashboard.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

import com.oit.dondok.domain.crew.entity.Crew;
import com.oit.dondok.domain.crew.entity.CrewParticipant;
import com.oit.dondok.domain.crew.entity.CrewParticipantStatus;
import com.oit.dondok.domain.crew.entity.HostPolicyVersion;
import com.oit.dondok.domain.crew.exception.CrewErrorCode;
import com.oit.dondok.domain.crew.repository.CrewQueryRepository;
import com.oit.dondok.domain.dashboard.dto.response.DashboardResponse;
import com.oit.dondok.domain.dashboard.port.CrewBatchProjection;
import com.oit.dondok.domain.dashboard.port.DashboardProjectionPort;
import com.oit.dondok.domain.member.entity.Member;
import com.oit.dondok.global.exception.CustomException;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class DashboardServiceTest {

  private static final ZoneId SEOUL_ZONE = ZoneId.of("Asia/Seoul");
  private static final Long DEPOSIT = 10_000L;

  @Mock private CrewQueryRepository crewQueryRepository;
  @Mock private DashboardProjectionPort dashboardProjectionPort;

  @InjectMocks private DashboardService dashboardService;

  @Test
  @DisplayName("참여 크루 다수·projection 혼재: 합계/delta/rising/falling/max_delta/ratio를 집계한다")
  void aggregatesMultipleCrewsWithMixedProjections() {
    UUID memberUuid = UUID.randomUUID();
    Member viewer = viewer(memberUuid);
    Crew crew10 = crew(viewer, 10L, "아침 6시 기상");
    Crew crew11 = crew(viewer, 11L, "홀트 30분");
    Crew crew12 = crew(viewer, 12L, "독서 크루");
    Crew crew13 = crew(viewer, 13L, "모집 중 크루");

    given(crewQueryRepository.findMyLockedCrewParticipants(memberUuid))
        .willReturn(
            List.of(
                locked(crew10, viewer, 100L),
                locked(crew11, viewer, 101L),
                locked(crew12, viewer, 102L),
                locked(crew13, viewer, 103L)));

    Map<Long, CrewBatchProjection> projections = new HashMap<>();
    // 상승 크루
    projections.put(10L, projection(10L, new BigDecimal("0.410000"), 23_500L, 22_300L));
    // 하락 크루
    projections.put(11L, projection(11L, new BigDecimal("0.250000"), 14_160L, 15_000L));
    // 배치는 있으나 직전 배치 없음 → delta만 null
    projections.put(12L, projection(12L, new BigDecimal("0.235000"), 19_600L, null));
    // crew13: 배치 미실행(맵에 없음) → 세 필드 모두 null
    given(dashboardProjectionPort.findLatestProjectionsByCrew(any(), any()))
        .willReturn(projections);

    DashboardResponse result = dashboardService.getDashboard(memberUuid);

    // 합계: 23500 + 14160 + 19600 (+ crew13 null=0)
    assertThat(result.totalExpectedRefundAmount()).isEqualTo(57_260L);
    // delta 합: +1200 + (-840) (crew12 delta null 제외, crew13 제외)
    assertThat(result.todayDeltaAmount()).isEqualTo(360L);
    // 360 / 57260 = 0.00628.. → scale 3 HALF_UP
    assertThat(result.todayDeltaRatio()).isEqualTo("0.006");
    assertThat(result.risingCrewCount()).isEqualTo(1);
    assertThat(result.fallingCrewCount()).isEqualTo(1);

    assertThat(result.maxDeltaCrew()).isNotNull();
    assertThat(result.maxDeltaCrew().crewId()).isEqualTo(10L);
    assertThat(result.maxDeltaCrew().crewName()).isEqualTo("아침 6시 기상");
    assertThat(result.maxDeltaCrew().todayDeltaAmount()).isEqualTo(1_200L);

    assertThat(result.crews()).hasSize(4);
    // crew_id ASC 정렬 유지
    assertThat(result.crews()).extracting("crewId").containsExactly(10L, 11L, 12L, 13L);

    var c10 = result.crews().get(0);
    assertThat(c10.shareRatio()).isEqualTo("0.41");
    assertThat(c10.expectedRefundAmount()).isEqualTo(23_500L);
    assertThat(c10.todayDeltaAmount()).isEqualTo(1_200L);

    var c11 = result.crews().get(1);
    assertThat(c11.shareRatio()).isEqualTo("0.25");
    assertThat(c11.expectedRefundAmount()).isEqualTo(14_160L);
    assertThat(c11.todayDeltaAmount()).isEqualTo(-840L);

    var c12 = result.crews().get(2);
    assertThat(c12.shareRatio()).isEqualTo("0.235");
    assertThat(c12.expectedRefundAmount()).isEqualTo(19_600L);
    assertThat(c12.todayDeltaAmount()).isNull();

    var c13 = result.crews().get(3);
    assertThat(c13.shareRatio()).isNull();
    assertThat(c13.expectedRefundAmount()).isNull();
    assertThat(c13.todayDeltaAmount()).isNull();
  }

  @Test
  @DisplayName("배치 미실행 크루(projection 없음)는 share_ratio·expected_refund·today_delta가 모두 null이다")
  void setsAllNullFieldsForCrewWithoutProjection() {
    UUID memberUuid = UUID.randomUUID();
    Member viewer = viewer(memberUuid);
    Crew crew = crew(viewer, 10L, "모집 중 크루");

    given(crewQueryRepository.findMyLockedCrewParticipants(memberUuid))
        .willReturn(List.of(locked(crew, viewer, 100L)));
    given(dashboardProjectionPort.findLatestProjectionsByCrew(any(), any())).willReturn(Map.of());

    DashboardResponse result = dashboardService.getDashboard(memberUuid);

    assertThat(result.crews()).hasSize(1);
    assertThat(result.crews().get(0).shareRatio()).isNull();
    assertThat(result.crews().get(0).expectedRefundAmount()).isNull();
    assertThat(result.crews().get(0).todayDeltaAmount()).isNull();

    assertThat(result.totalExpectedRefundAmount()).isZero();
    assertThat(result.todayDeltaAmount()).isZero();
    assertThat(result.todayDeltaRatio()).isEqualTo("0");
    assertThat(result.risingCrewCount()).isZero();
    assertThat(result.fallingCrewCount()).isZero();
    assertThat(result.maxDeltaCrew()).isNull();
  }

  @Test
  @DisplayName("직전 배치가 없으면 expected_refund·share_ratio는 채우고 today_delta만 null이다")
  void setsOnlyDeltaNullWhenNoPreviousBatch() {
    UUID memberUuid = UUID.randomUUID();
    Member viewer = viewer(memberUuid);
    Crew crew = crew(viewer, 10L, "신규 크루");

    given(crewQueryRepository.findMyLockedCrewParticipants(memberUuid))
        .willReturn(List.of(locked(crew, viewer, 100L)));
    given(dashboardProjectionPort.findLatestProjectionsByCrew(any(), any()))
        .willReturn(Map.of(10L, projection(10L, new BigDecimal("0.500000"), 10_000L, null)));

    DashboardResponse result = dashboardService.getDashboard(memberUuid);

    assertThat(result.crews().get(0).shareRatio()).isEqualTo("0.5");
    assertThat(result.crews().get(0).expectedRefundAmount()).isEqualTo(10_000L);
    assertThat(result.crews().get(0).todayDeltaAmount()).isNull();

    assertThat(result.totalExpectedRefundAmount()).isEqualTo(10_000L);
    assertThat(result.todayDeltaAmount()).isZero();
    // total>0, delta=0 → "0.000" (빈 사용자 "0"과 구분)
    assertThat(result.todayDeltaRatio()).isEqualTo("0.000");
    assertThat(result.maxDeltaCrew()).isNull();
  }

  @Test
  @DisplayName("max_delta는 절댓값 동률이면 crew_id가 작은 크루를 선택한다")
  void picksSmallerCrewIdWhenMaxDeltaTie() {
    UUID memberUuid = UUID.randomUUID();
    Member viewer = viewer(memberUuid);
    Crew crew20 = crew(viewer, 20L, "크루20");
    Crew crew21 = crew(viewer, 21L, "크루21");

    given(crewQueryRepository.findMyLockedCrewParticipants(memberUuid))
        .willReturn(List.of(locked(crew20, viewer, 200L), locked(crew21, viewer, 201L)));

    Map<Long, CrewBatchProjection> projections = new HashMap<>();
    projections.put(20L, projection(20L, new BigDecimal("0.5"), 2_000L, 1_000L)); // +1000
    projections.put(21L, projection(21L, new BigDecimal("0.5"), 1_000L, 2_000L)); // -1000
    given(dashboardProjectionPort.findLatestProjectionsByCrew(any(), any()))
        .willReturn(projections);

    DashboardResponse result = dashboardService.getDashboard(memberUuid);

    assertThat(result.maxDeltaCrew().crewId()).isEqualTo(20L);
    assertThat(result.maxDeltaCrew().todayDeltaAmount()).isEqualTo(1_000L);
    assertThat(result.risingCrewCount()).isEqualTo(1);
    assertThat(result.fallingCrewCount()).isEqualTo(1);
  }

  @Test
  @DisplayName("max_delta는 절댓값이 가장 큰 크루를 선택하며 음수 delta도 후보다")
  void picksLargestAbsoluteDeltaIncludingNegativeAndFormatsNegativeRatio() {
    UUID memberUuid = UUID.randomUUID();
    Member viewer = viewer(memberUuid);
    Crew crew20 = crew(viewer, 20L, "크루20");
    Crew crew22 = crew(viewer, 22L, "크루22");

    given(crewQueryRepository.findMyLockedCrewParticipants(memberUuid))
        .willReturn(List.of(locked(crew20, viewer, 200L), locked(crew22, viewer, 202L)));

    Map<Long, CrewBatchProjection> projections = new HashMap<>();
    projections.put(20L, projection(20L, new BigDecimal("0.5"), 2_000L, 1_000L)); // +1000
    projections.put(22L, projection(22L, new BigDecimal("0.5"), 500L, 2_000L)); // -1500
    given(dashboardProjectionPort.findLatestProjectionsByCrew(any(), any()))
        .willReturn(projections);

    DashboardResponse result = dashboardService.getDashboard(memberUuid);

    assertThat(result.maxDeltaCrew().crewId()).isEqualTo(22L);
    assertThat(result.maxDeltaCrew().todayDeltaAmount()).isEqualTo(-1_500L);
    // total 2500, delta -500 → -0.2
    assertThat(result.totalExpectedRefundAmount()).isEqualTo(2_500L);
    assertThat(result.todayDeltaAmount()).isEqualTo(-500L);
    assertThat(result.todayDeltaRatio()).isEqualTo("-0.200");
  }

  @Test
  @DisplayName("expected_refund 합계가 0이면 delta가 있어도 ratio는 0으로 안전 처리한다")
  void returnsZeroRatioWhenTotalExpectedRefundIsZero() {
    UUID memberUuid = UUID.randomUUID();
    Member viewer = viewer(memberUuid);
    Crew crew = crew(viewer, 30L, "전원 실패 크루");

    given(crewQueryRepository.findMyLockedCrewParticipants(memberUuid))
        .willReturn(List.of(locked(crew, viewer, 300L)));
    // 최신 배치 expected 0, 직전 배치 500 → delta -500, total 0
    given(dashboardProjectionPort.findLatestProjectionsByCrew(any(), any()))
        .willReturn(Map.of(30L, projection(30L, null, 0L, 500L)));

    DashboardResponse result = dashboardService.getDashboard(memberUuid);

    assertThat(result.totalExpectedRefundAmount()).isZero();
    assertThat(result.todayDeltaAmount()).isEqualTo(-500L);
    assertThat(result.todayDeltaRatio()).isEqualTo("0"); // 분모 0 → "0" (DIV/0 방지)
    assertThat(result.risingCrewCount()).isZero();
    assertThat(result.fallingCrewCount()).isEqualTo(1);
    // expected 0은 null이 아니라 0으로 노출, share_ratio는 null
    assertThat(result.crews().get(0).expectedRefundAmount()).isEqualTo(0L);
    assertThat(result.crews().get(0).shareRatio()).isNull();
    assertThat(result.crews().get(0).todayDeltaAmount()).isEqualTo(-500L);
    assertThat(result.maxDeltaCrew().crewId()).isEqualTo(30L);
    assertThat(result.maxDeltaCrew().todayDeltaAmount()).isEqualTo(-500L);
  }

  @Test
  @DisplayName("share_ratio는 trailing zero를 제거한 plain string으로 직렬화한다")
  void formatsShareRatioStrippingTrailingZeros() {
    UUID memberUuid = UUID.randomUUID();
    Member viewer = viewer(memberUuid);
    Crew crew = crew(viewer, 40L, "단독 참여 크루");

    given(crewQueryRepository.findMyLockedCrewParticipants(memberUuid))
        .willReturn(List.of(locked(crew, viewer, 400L)));
    // 지분율 1.000000 → "1"
    given(dashboardProjectionPort.findLatestProjectionsByCrew(any(), any()))
        .willReturn(Map.of(40L, projection(40L, new BigDecimal("1.000000"), 5_000L, 5_000L)));

    DashboardResponse result = dashboardService.getDashboard(memberUuid);

    assertThat(result.crews().get(0).shareRatio()).isEqualTo("1");
  }

  @Test
  @DisplayName("LOCKED 참여 크루는 없지만 참여 이력이 있으면 빈 대시보드(0/빈 배열/null)를 반환한다")
  void returnsEmptyDashboardWhenNoLockedCrewsButHasHistory() {
    UUID memberUuid = UUID.randomUUID();

    given(crewQueryRepository.findMyLockedCrewParticipants(memberUuid)).willReturn(List.of());
    given(crewQueryRepository.hasAnyCrewParticipant(memberUuid)).willReturn(true);

    DashboardResponse result = dashboardService.getDashboard(memberUuid);

    assertThat(result.totalExpectedRefundAmount()).isZero();
    assertThat(result.todayDeltaAmount()).isZero();
    assertThat(result.todayDeltaRatio()).isEqualTo("0");
    assertThat(result.risingCrewCount()).isZero();
    assertThat(result.fallingCrewCount()).isZero();
    assertThat(result.maxDeltaCrew()).isNull();
    assertThat(result.crews()).isEmpty();
  }

  @Test
  @DisplayName("참여 이력이 전무하면 PARTICIPANT_NOT_FOUND를 던진다")
  void throwsParticipantNotFoundWhenNoParticipationHistory() {
    UUID memberUuid = UUID.randomUUID();

    given(crewQueryRepository.findMyLockedCrewParticipants(memberUuid)).willReturn(List.of());
    given(crewQueryRepository.hasAnyCrewParticipant(memberUuid)).willReturn(false);

    assertThatThrownBy(() -> dashboardService.getDashboard(memberUuid))
        .isInstanceOf(CustomException.class)
        .extracting("errorCode")
        .isEqualTo(CrewErrorCode.PARTICIPANT_NOT_FOUND);
  }

  // ======================== helpers ========================

  private Member viewer(UUID uuid) {
    Member member = Member.create("viewer@example.com", "password-hash", "대시보드뷰어");
    ReflectionTestUtils.setField(member, "id", 1L);
    ReflectionTestUtils.setField(member, "uuid", uuid);
    return member;
  }

  private Crew crew(Member hostMember, long id, String title) {
    Crew crew =
        Crew.create(
            hostMember,
            title,
            "크루 설명",
            "crew/image/key",
            "EXERCISE",
            "{}",
            HostPolicyVersion.HOST_POLICY_V1,
            LocalDateTime.now(SEOUL_ZONE),
            DEPOSIT,
            2,
            5,
            LocalDateTime.now(SEOUL_ZONE).plusDays(3),
            LocalDateTime.now(SEOUL_ZONE).plusDays(5),
            LocalDateTime.now(SEOUL_ZONE).plusDays(35));
    ReflectionTestUtils.setField(crew, "id", id);
    ReflectionTestUtils.setField(crew, "version", 0L);
    return crew;
  }

  private CrewParticipant locked(Crew crew, Member member, long id) {
    CrewParticipant participant =
        CrewParticipant.create(crew, member, DEPOSIT, LocalDateTime.now(SEOUL_ZONE));
    ReflectionTestUtils.setField(participant, "id", id);
    ReflectionTestUtils.setField(participant, "status", CrewParticipantStatus.LOCKED);
    ReflectionTestUtils.setField(participant, "version", 0L);
    return participant;
  }

  private CrewBatchProjection projection(
      long crewId,
      BigDecimal shareRatio,
      Long expectedRefundAmount,
      Long previousExpectedRefundAmount) {
    return new CrewBatchProjection(
        crewId, shareRatio, expectedRefundAmount, previousExpectedRefundAmount);
  }
}
