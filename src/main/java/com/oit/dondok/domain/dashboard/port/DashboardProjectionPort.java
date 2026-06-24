package com.oit.dondok.domain.dashboard.port;

import java.util.List;
import java.util.Map;

// 대시보드 projection 데이터 소스(직전 일일 정산 배치 스냅샷)에 대한 읽기 전용 포트.
// 실제 구현체 DefaultDashboardProjectionPort(@Profile("!test"))가 일일 정산 스냅샷을 읽으며,
// test 프로파일에서만 슬라이스 부팅용 FakeDashboardProjectionPort(빈 결과)가 등록된다.
// 스냅샷 적재는 DailySettlementSnapshotCreationService가 담당한다.
public interface DashboardProjectionPort {
  // 주어진 크루들에 대해 해당 회원의 최신 배치 기준 projection을 조회한다.
  Map<Long, CrewBatchProjection> findLatestProjectionsByCrew(Long memberId, List<Long> crewIds);
}
