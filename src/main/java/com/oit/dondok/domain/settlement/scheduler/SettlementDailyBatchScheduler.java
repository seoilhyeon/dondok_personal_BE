package com.oit.dondok.domain.settlement.scheduler;

import com.oit.dondok.domain.mission.entity.DailySettlementType;
import com.oit.dondok.domain.settlement.service.DailySettlementBatchService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class SettlementDailyBatchScheduler {

  private final DailySettlementBatchService dailySettlementBatchService;

  @Scheduled(cron = "0 0 12 * * *", zone = "Asia/Seoul")
  public void runTypeADailySettlementBatch() {
    runDailySettlementBatch(DailySettlementType.A);
  }

  @Scheduled(cron = "0 0 0 * * *", zone = "Asia/Seoul")
  public void runTypeBDailySettlementBatch() {
    runDailySettlementBatch(DailySettlementType.B);
  }

  @Scheduled(cron = "0 0 12 * * *", zone = "Asia/Seoul")
  public void runTypeCDailySettlementBatch() {
    runDailySettlementBatch(DailySettlementType.C);
  }

  private void runDailySettlementBatch(DailySettlementType dailySettlementType) {
    log.info("[배치] 일일 정산 스냅샷 배치 시작. type={}", dailySettlementType);
    try {
      dailySettlementBatchService.runDailySettlementBatch(dailySettlementType);
    } catch (Exception e) {
      log.error("[배치] 일일 정산 스냅샷 배치 중 예외 발생. type={}", dailySettlementType, e);
    }
    log.info("[배치] 일일 정산 스냅샷 배치 완료. type={}", dailySettlementType);
  }
}
