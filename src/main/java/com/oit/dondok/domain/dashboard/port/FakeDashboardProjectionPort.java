package com.oit.dondok.domain.dashboard.port;

import java.util.List;
import java.util.Map;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile({"local", "dev", "test", "integration"})
public class FakeDashboardProjectionPort implements DashboardProjectionPort {

  @Override
  public Map<Long, CrewBatchProjection> findLatestProjectionsByCrew(
      Long memberId, List<Long> crewIds) {
    return Map.of();
  }
}
