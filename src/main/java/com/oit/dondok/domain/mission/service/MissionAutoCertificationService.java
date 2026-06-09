package com.oit.dondok.domain.mission.service;

import com.oit.dondok.domain.mission.repository.MissionLogQueryRepository;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class MissionAutoCertificationService {

  private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
  private static final int BATCH_SIZE = 500;

  private final MissionLogQueryRepository missionLogQueryRepository;
  private final MissionAutoCertificationProcessor missionAutoCertificationProcessor;

  // 후보 조회와 건별 처리를 조율하되, 각 로그 처리는 별도 트랜잭션에서 격리한다.
  @Transactional(propagation = Propagation.NOT_SUPPORTED)
  public void confirmDuePendingReviews() {
    LocalDateTime now = LocalDateTime.now(SEOUL);
    List<Long> missionLogIds =
        missionLogQueryRepository.findAutoCertificationCandidateIds(now, BATCH_SIZE);

    for (Long missionLogId : missionLogIds) {
      try {
        missionAutoCertificationProcessor.confirmOne(missionLogId, now);
      } catch (RuntimeException exception) {
        // 한 건의 실패가 나머지 자동 인증 처리를 막지 않도록 격리한다.
        log.warn("미션 자동 인증 처리 실패, missionLogId={}", missionLogId, exception);
      }
    }
  }
}
