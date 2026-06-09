package com.oit.dondok.domain.mission.service;

import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class MissionAutoCertificationScheduler {

  private final MissionAutoCertificationService missionAutoCertificationService;

  // 정각 누락을 방지하기 위해 3분마다 처리 대상이 된 인증 로그를 따라잡는다.
  // 다중 인스턴스 배포에서는 이 스케줄러가 단일 인스턴스에서만 실행되도록 운영에서 보장한다.
  @Scheduled(cron = "0 */3 * * * *", zone = "Asia/Seoul")
  public void run() {
    missionAutoCertificationService.confirmDuePendingReviews();
  }
}
