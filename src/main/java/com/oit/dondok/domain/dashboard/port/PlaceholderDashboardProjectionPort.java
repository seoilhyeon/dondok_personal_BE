package com.oit.dondok.domain.dashboard.port;

import java.util.List;
import java.util.Map;

import com.oit.dondok.global.exception.CustomException;
import com.oit.dondok.global.exception.GlobalErrorCode;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

// prod 프로파일용 임시 구현체. 실제 일일 배치 스냅샷 projection은 후속 이슈에서 구현 예정이다.
@Component
@Profile("prod")
public class PlaceholderDashboardProjectionPort implements DashboardProjectionPort {

  @Override
  public Map<Long, CrewBatchProjection> findLatestProjectionsByCrew(
      Long memberId, List<Long> crewIds) {
    throw new CustomException(GlobalErrorCode.SERVER_ERROR);
  }
}
