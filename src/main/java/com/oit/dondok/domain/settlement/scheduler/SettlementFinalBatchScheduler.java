package com.oit.dondok.domain.settlement.scheduler;

import com.oit.dondok.domain.settlement.service.SettlementBatchService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class SettlementFinalBatchScheduler {

  private final SettlementBatchService settlementBatchService;

  // Crew lifecycle batch runs at 00:00 KST. Keep final settlement on a post-activation
  // offset so the previous single-method order intention is preserved without coupling domains.
  @Scheduled(cron = "0 10 0 * * *", zone = "Asia/Seoul")
  public void runFinalSettlementBatch() {
    log.info("[배치] 최종 정산 배치 시작");
    try {
      settlementBatchService.runFinalSettlementBatch();
    } catch (Exception e) {
      log.error("[배치] 최종 정산 중 예외 발생", e);
    }
    log.info("[배치] 최종 정산 배치 완료");
  }
}
