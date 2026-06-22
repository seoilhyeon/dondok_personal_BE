package com.oit.dondok.domain.mission.service;

import com.oit.dondok.domain.mission.repository.CrewRef;
import com.oit.dondok.domain.mission.repository.MissionLogQueryRepository;
import com.oit.dondok.domain.settlement.service.SettlementNotificationService;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
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
  private final SettlementNotificationService settlementNotificationService;

  // 후보 조회와 건별 처리를 조율하되, 각 로그 처리는 별도 트랜잭션에서 격리한다.
  @Transactional(propagation = Propagation.NOT_SUPPORTED)
  public void confirmDuePendingReviews() {
    LocalDateTime now = LocalDateTime.now(SEOUL);
    List<Long> missionLogIds =
        missionLogQueryRepository.findAutoCertificationCandidateIds(now, BATCH_SIZE);
    log.debug("[자동인증] 배치 시작 now={} 후보={}건 ids={}", now, missionLogIds.size(), missionLogIds);

    int processed = 0;
    List<Long> processedIds = new ArrayList<>();
    for (Long missionLogId : missionLogIds) {
      try {
        if (missionAutoCertificationProcessor.confirmOne(missionLogId, now)) {
          processed++;
          processedIds.add(missionLogId);
        }
      } catch (RuntimeException exception) {
        // 한 건의 실패가 나머지 자동 인증 처리를 막지 않도록 격리한다.
        log.warn("미션 자동 인증 처리 실패, missionLogId={}", missionLogId, exception);
      }
    }
    log.debug("[자동인증] 배치 완료 후보={}건 처리={}건", missionLogIds.size(), processed);

    sendExpectedRefundChangedNotifications(processedIds);
  }

  private void sendExpectedRefundChangedNotifications(List<Long> processedIds) {
    if (processedIds.isEmpty()) {
      return;
    }
    List<CrewRef> affectedCrews =
        missionLogQueryRepository.findDistinctCrewsByMissionLogIds(processedIds);
    for (CrewRef crew : affectedCrews) {
      try {
        settlementNotificationService.sendExpectedRefundChangedNotifications(
            crew.id(), crew.title());
      } catch (RuntimeException e) {
        log.warn("[자동인증] 예상 환급금 변동 알림 실패 crewId={}", crew.id(), e);
      }
    }
  }
}
