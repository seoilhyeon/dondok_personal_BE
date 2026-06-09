package com.oit.dondok.domain.crew.scheduler;

import com.oit.dondok.domain.crew.service.CrewActivationBatchService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class CrewBatchScheduler {

  private final CrewActivationBatchService crewActivationBatchService;

  @Scheduled(cron = "0 0 0 * * *", zone = "Asia/Seoul")
  public void runDailyBatch() {
    log.info("[배치] 일일 배치 시작");
    try {
      // 1. 모집중 → 진행중 전환
      crewActivationBatchService.activateCrews();

      // 2. HOST-004 자동 거절 로직 연결 포인트 (추후 추가)
      // autoRejectService.rejectExpiredApplications();

      log.info("[배치] 일일 배치 완료");
    } catch (Exception e) {
      log.error("[배치] 일일 배치 중 예외 발생", e);
    }
  }
}
