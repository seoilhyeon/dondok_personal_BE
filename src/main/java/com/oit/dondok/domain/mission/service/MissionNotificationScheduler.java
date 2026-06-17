package com.oit.dondok.domain.mission.service;

import com.oit.dondok.domain.mission.entity.DailySettlementType;
import java.time.LocalDate;
import java.time.ZoneId;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class MissionNotificationScheduler {

  private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

  private final MissionDeadlineNotificationService missionDeadlineNotificationService;
  private final UnreviewedMissionNotificationService unreviewedMissionNotificationService;

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

  // DailySettlementType A: 일일 정산 12:00 → 30분 전 11:30 발송
  @Scheduled(cron = "0 30 11 * * *", zone = "Asia/Seoul")
  public void remindTypeAUnreviewed() {
    log.info("[알림] 타입A 미검토 인증 알림 시작");
    try {
      unreviewedMissionNotificationService.sendUnreviewedReminders(
          DailySettlementType.A, LocalDate.now(SEOUL));
    } catch (Exception e) {
      log.error("[알림] 타입A 미검토 인증 알림 중 예외 발생", e);
    }
  }

  // DailySettlementType B: 일일 정산 익일 00:00 → 30분 전 23:30 발송 (당일 missionDate 기준)
  @Scheduled(cron = "0 30 23 * * *", zone = "Asia/Seoul")
  public void remindTypeBUnreviewed() {
    log.info("[알림] 타입B 미검토 인증 알림 시작");
    try {
      unreviewedMissionNotificationService.sendUnreviewedReminders(
          DailySettlementType.B, LocalDate.now(SEOUL));
    } catch (Exception e) {
      log.error("[알림] 타입B 미검토 인증 알림 중 예외 발생", e);
    }
  }

  // DailySettlementType C: 일일 정산 익일 12:05 → 30분 전 익일 11:35 발송 (전날 missionDate 기준)
  @Scheduled(cron = "0 35 11 * * *", zone = "Asia/Seoul")
  public void remindTypeCUnreviewed() {
    log.info("[알림] 타입C 미검토 인증 알림 시작");
    try {
      unreviewedMissionNotificationService.sendUnreviewedReminders(
          DailySettlementType.C, LocalDate.now(SEOUL).minusDays(1));
    } catch (Exception e) {
      log.error("[알림] 타입C 미검토 인증 알림 중 예외 발생", e);
    }
  }
}
