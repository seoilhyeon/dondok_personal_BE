package com.oit.dondok.domain.dashboard.port;

import com.oit.dondok.domain.dashboard.repository.DashboardProjectionQueryRepository;
import com.oit.dondok.domain.dashboard.repository.DashboardProjectionRow;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

// prod 실제 구현체. PROVISIONAL·SUCCEEDED 스냅샷을 crew별 mission_date 내림차순으로 받아
// 최신(0번째)=지분율/예상환급금, 직전(1번째)=직전 예상환급금으로 매핑한다.
@Component
@Profile("prod")
@RequiredArgsConstructor
public class DefaultDashboardProjectionPort implements DashboardProjectionPort {

  private final DashboardProjectionQueryRepository dashboardProjectionQueryRepository;

  @Override
  public Map<Long, CrewBatchProjection> findLatestProjectionsByCrew(
      Long memberId, List<Long> crewIds) {
    Map<Long, List<DashboardProjectionRow>> rowsByCrew =
        dashboardProjectionQueryRepository.findProvisionalProjectionRows(memberId, crewIds).stream()
            .collect(
                Collectors.groupingBy(
                    DashboardProjectionRow::crewId, LinkedHashMap::new, Collectors.toList()));

    Map<Long, CrewBatchProjection> projections = new LinkedHashMap<>();
    rowsByCrew.forEach((crewId, rows) -> projections.put(crewId, toProjection(rows)));
    return projections;
  }

  // rows는 해당 crew의 mission_date 내림차순. 최신=get(0), 직전=get(1)
  private CrewBatchProjection toProjection(List<DashboardProjectionRow> rows) {
    DashboardProjectionRow latest = rows.get(0);
    Long previousExpectedRefundAmount = rows.size() > 1 ? rows.get(1).expectedRefundAmount() : null;
    return new CrewBatchProjection(
        latest.shareRatio(), latest.expectedRefundAmount(), previousExpectedRefundAmount);
  }
}
