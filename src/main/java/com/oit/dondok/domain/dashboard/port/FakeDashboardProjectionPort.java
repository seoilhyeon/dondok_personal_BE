package com.oit.dondok.domain.dashboard.port;

import java.util.List;
import java.util.Map;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

// test 슬라이스 컨텍스트 부팅용 stub. 실제 스냅샷 조회는 DefaultDashboardProjectionPort(@Profile("!test"))가 담당한다.
@Component
@Profile("test")
public class FakeDashboardProjectionPort implements DashboardProjectionPort {

  @Override
  public Map<Long, CrewBatchProjection> findLatestProjectionsByCrew(
      Long memberId, List<Long> crewIds) {
    return Map.of();
  }
}
