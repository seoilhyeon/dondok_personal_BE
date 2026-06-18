package com.oit.dondok.domain.point.scheduler;

import com.oit.dondok.domain.point.service.PointChargeRecoveryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class PointChargeRecoveryScheduler {

  private final PointChargeRecoveryService pointChargeRecoveryService;

  @Scheduled(cron = "0 */5 * * * *", zone = "Asia/Seoul")
  public void runPointChargeRecoveryBatch() {
    log.info("[배치] 포인트 충전 복구 배치 시작.");
    try {
      pointChargeRecoveryService.runRecoveryBatch();
      log.info("[배치] 포인트 충전 복구 배치 완료.");
    } catch (Exception e) {
      log.error("[배치] 포인트 충전 복구 배치 실패.", e);
    }
  }
}
