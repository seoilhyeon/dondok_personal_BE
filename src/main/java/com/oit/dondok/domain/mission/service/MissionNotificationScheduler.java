package com.oit.dondok.domain.mission.service;

import com.oit.dondok.domain.mission.entity.DailySettlementType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class MissionNotificationScheduler {

  private final MissionDeadlineNotificationService missionDeadlineNotificationService;

  // DailySettlementType A: 인증 마감 09:00 → 30분 전 08:30 발송
  @Scheduled(cron = "0 30 8 * * *", zone = "Asia/Seoul")
  public void remindTypeADeadline() {
    log.info("[알림] 타입A 마감 임박 알림 시작");
    try {
      missionDeadlineNotificationService.sendDeadlineReminders(DailySettlementType.A);
    } catch (Exception e) {
      log.error("[알림] 타입A 마감 임박 알림 중 예외 발생", e);
    }
  }

  // DailySettlementType B: 인증 마감 21:00 → 30분 전 20:30 발송
  @Scheduled(cron = "0 30 20 * * *", zone = "Asia/Seoul")
  public void remindTypeBDeadline() {
    log.info("[알림] 타입B 마감 임박 알림 시작");
    try {
      missionDeadlineNotificationService.sendDeadlineReminders(DailySettlementType.B);
    } catch (Exception e) {
      log.error("[알림] 타입B 마감 임박 알림 중 예외 발생", e);
    }
  }

  // DailySettlementType C: 인증 마감 23:59:59 → 30분 전 23:30 발송
  @Scheduled(cron = "0 30 23 * * *", zone = "Asia/Seoul")
  public void remindTypeCDeadline() {
    log.info("[알림] 타입C 마감 임박 알림 시작");
    try {
      missionDeadlineNotificationService.sendDeadlineReminders(DailySettlementType.C);
    } catch (Exception e) {
      log.error("[알림] 타입C 마감 임박 알림 중 예외 발생", e);
    }
  }
}
