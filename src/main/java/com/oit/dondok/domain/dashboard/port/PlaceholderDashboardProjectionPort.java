package com.oit.dondok.domain.dashboard.port;

import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

// prod 프로파일용 안전 placeholder 구현체.
// 실제 일일 배치 스냅샷 적재는 후속 이슈에서 구현 예정이며, 그 전까지 prod에서 DashboardService 주입 실패로
// 앱이 뜨지 않는 것을 막기 위해 빈 projection을 반환한다.
@Slf4j
@Component
@Profile("prod")
public class PlaceholderDashboardProjectionPort implements DashboardProjectionPort {

  @Override
  public Map<Long, CrewBatchProjection> findLatestProjectionsByCrew(
      Long memberId, List<Long> crewIds) {
    log.warn(
        "PlaceholderDashboardProjectionPort 사용 중 — 실제 배치 스냅샷 projection 미구현. "
            + "대시보드 projection 값(지분율/예상 환급금/변동)이 빈 상태로 응답됩니다.");
    return Map.of();
  }
}
