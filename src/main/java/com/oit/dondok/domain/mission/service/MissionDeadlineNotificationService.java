package com.oit.dondok.domain.mission.service;

import com.oit.dondok.domain.crew.entity.Crew;
import com.oit.dondok.domain.crew.entity.CrewParticipant;
import com.oit.dondok.domain.mission.entity.DailySettlementType;
import com.oit.dondok.domain.mission.repository.MissionLogQueryRepository;
import com.oit.dondok.domain.notification.port.NotificationPayload;
import com.oit.dondok.domain.notification.port.NotificationSender;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class MissionDeadlineNotificationService {

  private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
  private static final int BATCH_SIZE = 500;

  private final MissionLogQueryRepository missionLogQueryRepository;
  private final NotificationSender notificationSender;

  // 지정 타입 크루에서 오늘 아직 인증하지 않은 LOCKED 참여자에게 마감 임박 알림을 발송한다.
  // 500건씩 cursor-based로 반복 조회하여 대상 전체를 처리한다.
  @Transactional
  public void sendDeadlineReminders(DailySettlementType settlementType) {
    LocalDate today = LocalDate.now(SEOUL);
    LocalDateTime todayStart = today.atStartOfDay();
    LocalDateTime todayEnd = today.plusDays(1).atStartOfDay();

    long cursorId = 0L;
    List<CrewParticipant> batch;
    do {
      batch =
          missionLogQueryRepository.findDeadlineReminderTargets(
              settlementType, todayStart, todayEnd, cursorId, BATCH_SIZE);
      for (CrewParticipant participant : batch) {
        try {
          Crew crew = participant.getCrew();
          notificationSender.send(
              participant.getMember(),
              new NotificationPayload(
                  "MISSION_DEADLINE_APPROACHING",
                  "crew",
                  String.valueOf(crew.getId()),
                  "dondok://crews/" + crew.getId(),
                  crew.getTitle() + " 크루 미션 인증 마감이 30분 남았습니다."));
        } catch (RuntimeException e) {
          log.warn("[알림] 마감 임박 알림 발송 실패 participantId={}", participant.getId(), e);
        }
        cursorId = participant.getId();
      }
    } while (batch.size() == BATCH_SIZE);
  }
}
