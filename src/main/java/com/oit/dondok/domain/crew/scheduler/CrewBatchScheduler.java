package com.oit.dondok.domain.crew.scheduler;

import com.oit.dondok.domain.crew.service.CrewActivationBatchService;
import com.oit.dondok.domain.crew.service.CrewCloseBatchService;
import com.oit.dondok.domain.crew.service.PendingApplicationAutoRejectService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class CrewBatchScheduler {

  private final CrewActivationBatchService crewActivationBatchService;
  private final PendingApplicationAutoRejectService pendingApplicationAutoRejectService;
  private final CrewCloseBatchService crewCloseBatchService;

  @Scheduled(cron = "0 0 0 * * *", zone = "Asia/Seoul")
  public void runDailyBatch() {
    log.info("[배치] 일일 배치 시작");
    try {
      // 1. 기간 만료 신청 자동 거절 (PENDING → EXPIRED)
      pendingApplicationAutoRejectService.rejectExpiredApplications();
    } catch (Exception e) {
      log.error("[배치] PENDING 신청 자동 만료 중 예외 발생", e);
    }
    try {
      // 2. 모집 기간이 지난 크루 활성화 (RECRUITING → ACTIVE)
      crewActivationBatchService.activateCrews();
    } catch (Exception e) {
      log.error("[배치] 크루 활성화 중 예외 발생", e);
    }
    try {
      // 3. 미션 종료일이 지난 크루 CLOSED 전환 (ACTIVE → CLOSED)
      crewCloseBatchService.closeCrews();
    } catch (Exception e) {
      log.error("[배치] 크루 종료 처리 중 예외 발생", e);
    }
    log.info("[배치] 일일 배치 완료");
  }
}
