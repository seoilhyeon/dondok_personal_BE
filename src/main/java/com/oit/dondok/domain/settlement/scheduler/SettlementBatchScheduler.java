package com.oit.dondok.domain.settlement.scheduler;

import com.oit.dondok.domain.mission.entity.DailySettlementType;
import com.oit.dondok.domain.settlement.service.DailySettlementBatchService;
import com.oit.dondok.domain.settlement.service.SettlementBatchService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class SettlementBatchScheduler {

  private final DailySettlementBatchService dailySettlementBatchService;
  private final SettlementBatchService settlementBatchService;

  @Scheduled(cron = "0 0 12 * * *", zone = "Asia/Seoul")
  public void runTypeADailySettlementBatch() {
    runDailySettlementBatch(DailySettlementType.A);
  }

  @Scheduled(cron = "0 10 12 * * *", zone = "Asia/Seoul")
  public void runTypeAFinalSettlementBatch() {
    runFinalSettlementBatch(DailySettlementType.A);
  }

  @Scheduled(cron = "0 0 0 * * *", zone = "Asia/Seoul")
  public void runTypeBDailySettlementBatch() {
    runDailySettlementBatch(DailySettlementType.B);
  }

  @Scheduled(cron = "0 10 0 * * *", zone = "Asia/Seoul")
  public void runTypeBFinalSettlementBatch() {
    runFinalSettlementBatch(DailySettlementType.B);
  }

  @Scheduled(cron = "0 0 12 * * *", zone = "Asia/Seoul")
  public void runTypeCDailySettlementBatch() {
    runDailySettlementBatch(DailySettlementType.C);
  }

  @Scheduled(cron = "0 20 12 * * *", zone = "Asia/Seoul")
  public void runTypeCFinalSettlementBatch() {
    runFinalSettlementBatch(DailySettlementType.C);
  }

  private void runDailySettlementBatch(DailySettlementType dailySettlementType) {
    log.info("[배치] 일일 정산 스냅샷 배치 시작. dailySettlementType={}", dailySettlementType);
    try {
      dailySettlementBatchService.runDailySettlementBatch(dailySettlementType);
    } catch (Exception e) {
      log.error("[배치] 일일 정산 스냅샷 배치 중 예외 발생. dailySettlementType={}", dailySettlementType, e);
    }
    log.info("[배치] 일일 정산 스냅샷 배치 완료. dailySettlementType={}", dailySettlementType);
  }

  private void runFinalSettlementBatch(DailySettlementType dailySettlementType) {
    log.info("[배치] 최종 정산 배치 시작. dailySettlementType={}", dailySettlementType);
    try {
      settlementBatchService.runFinalSettlementBatch(dailySettlementType);
    } catch (Exception e) {
      log.error("[배치] 최종 정산 배치 중 예외 발생. dailySettlementType={}", dailySettlementType, e);
    }
    log.info("[배치] 최종 정산 배치 완료. dailySettlementType={}", dailySettlementType);
  }
}
