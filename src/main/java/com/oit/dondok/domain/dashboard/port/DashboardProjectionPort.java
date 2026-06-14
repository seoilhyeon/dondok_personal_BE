package com.oit.dondok.domain.dashboard.port;

import java.util.List;
import java.util.Map;

// 대시보드 projection 데이터 소스(직전 일일 정산 배치 스냅샷)에 대한 읽기 전용 포트.
// Todo: 실제 일일 배치 스냅샷 적재는 후속 이슈에서 구현.
// local/dev/test/integration 프로파일에서 placeholder로 동작하며, prod 프로파일에서는 실제 구현체가 반드시 존재해야 한다.
public interface DashboardProjectionPort {
  // 주어진 크루들에 대해 해당 회원의 최신 배치 기준 projection을 조회한다.
  Map<Long, CrewBatchProjection> findLatestProjectionsByCrew(Long memberId, List<Long> crewIds);
}
