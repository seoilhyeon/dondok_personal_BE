package com.oit.dondok.domain.dashboard.port;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

import com.oit.dondok.domain.dashboard.repository.DashboardProjectionQueryRepository;
import com.oit.dondok.domain.dashboard.repository.DashboardProjectionRow;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DefaultDashboardProjectionPortTest {

  private static final Long MEMBER_ID = 7L;
  private static final LocalDateTime FROZEN_AT = LocalDateTime.of(2026, 6, 10, 9, 0);

  @Mock private DashboardProjectionQueryRepository dashboardProjectionQueryRepository;

  @InjectMocks private DefaultDashboardProjectionPort port;

  // 최신=지분율/예상환급금, 직전=직전 예상환급금으로 매핑한다.
  @Test
  void mapsLatestAndPreviousPerCrew() {
    List<Long> crewIds = List.of(10L);
    given(dashboardProjectionQueryRepository.findProvisionalProjectionRows(MEMBER_ID, crewIds))
        .willReturn(
            List.of(
                row(10L, LocalDate.of(2026, 6, 10), "0.400000", 2000L),
                row(10L, LocalDate.of(2026, 6, 9), "0.300000", 1500L)));

    Map<Long, CrewBatchProjection> result = port.findLatestProjectionsByCrew(MEMBER_ID, crewIds);

    assertThat(result).containsOnlyKeys(10L);
    CrewBatchProjection projection = result.get(10L);
    assertThat(projection.shareRatio()).isEqualByComparingTo("0.400000");
    assertThat(projection.expectedRefundAmount()).isEqualTo(2000L);
    assertThat(projection.previousExpectedRefundAmount()).isEqualTo(1500L);
  }

  // 스냅샷이 1개뿐이면 직전 예상환급금은 null.
  @Test
  void previousIsNullWhenOnlyOneSnapshot() {
    given(dashboardProjectionQueryRepository.findProvisionalProjectionRows(MEMBER_ID, List.of(10L)))
        .willReturn(List.of(row(10L, LocalDate.of(2026, 6, 10), "0.400000", 2000L)));

    CrewBatchProjection projection =
        port.findLatestProjectionsByCrew(MEMBER_ID, List.of(10L)).get(10L);

    assertThat(projection.expectedRefundAmount()).isEqualTo(2000L);
    assertThat(projection.previousExpectedRefundAmount()).isNull();
  }

  // 최신 스냅샷에 회원 참여 행이 없으면 지분율/예상환급금은 null, 직전 값은 그대로 매핑.
  @Test
  void mapsNullMemberValuesWhenAbsentInLatestSnapshot() {
    given(dashboardProjectionQueryRepository.findProvisionalProjectionRows(MEMBER_ID, List.of(10L)))
        .willReturn(
            List.of(
                row(10L, LocalDate.of(2026, 6, 10), null, null),
                row(10L, LocalDate.of(2026, 6, 9), "0.300000", 1500L)));

    CrewBatchProjection projection =
        port.findLatestProjectionsByCrew(MEMBER_ID, List.of(10L)).get(10L);

    assertThat(projection.shareRatio()).isNull();
    assertThat(projection.expectedRefundAmount()).isNull();
    assertThat(projection.previousExpectedRefundAmount()).isEqualTo(1500L);
  }

  // 스냅샷 행이 없는 크루는 map에 포함하지 않는다.
  @Test
  void excludesCrewWithoutSnapshots() {
    given(
            dashboardProjectionQueryRepository.findProvisionalProjectionRows(
                MEMBER_ID, List.of(10L, 11L)))
        .willReturn(List.of(row(10L, LocalDate.of(2026, 6, 10), "0.400000", 2000L)));

    Map<Long, CrewBatchProjection> result =
        port.findLatestProjectionsByCrew(MEMBER_ID, List.of(10L, 11L));

    assertThat(result).containsOnlyKeys(10L);
  }

  private static DashboardProjectionRow row(
      Long crewId, LocalDate missionDate, String shareRatio, Long expectedRefundAmount) {
    return new DashboardProjectionRow(
        crewId,
        missionDate,
        FROZEN_AT,
        shareRatio == null ? null : new BigDecimal(shareRatio),
        expectedRefundAmount);
  }
}
