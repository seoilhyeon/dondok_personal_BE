package com.oit.dondok.domain.mission.service;

import com.oit.dondok.domain.crew.entity.Crew;
import com.oit.dondok.domain.mission.entity.DailySettlementType;
import com.oit.dondok.domain.mission.repository.MissionLogQueryRepository;
import com.oit.dondok.domain.notification.port.NotificationPayload;
import com.oit.dondok.domain.notification.port.NotificationSender;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class UnreviewedMissionNotificationService {

  private static final int BATCH_SIZE = 500;

  private final MissionLogQueryRepository missionLogQueryRepository;
  private final NotificationSender notificationSender;

  // 일일 정산 30분 전, 검토 대기 중인 인증이 있는 크루의 방장에게 알림을 발송한다.
  // slotStart = (missionDate - 1).atTime(certDeadline), slotEnd = missionDate.atTime(certDeadline)
  @Transactional
  public void sendUnreviewedReminders(DailySettlementType settlementType, LocalDate missionDate) {
    LocalDateTime slotStart =
        missionDate.minusDays(1).atTime(settlementType.getCertificationDeadline());
    LocalDateTime slotEnd = missionDate.atTime(settlementType.getCertificationDeadline());

    log.info(
        "[알림] 미검토 인증 알림 시작. settlementType={}, slotStart={}, slotEnd={}",
        settlementType,
        slotStart,
        slotEnd);

    long cursorId = 0L;
    List<Crew> batch;
    do {
      batch =
          missionLogQueryRepository.findUnreviewedHostTargets(
              settlementType, slotStart, slotEnd, cursorId, BATCH_SIZE);
      for (Crew crew : batch) {
        try {
          notificationSender.send(
              crew.getHostMember(),
              new NotificationPayload(
                  "UNREVIEWED_MISSION_LOG",
                  "crew",
                  String.valueOf(crew.getId()),
                  "dondok://crews/" + crew.getId() + "/host-console?tab=verification",
                  crew.getTitle() + " 크루에 검토 대기 중인 인증이 있습니다.",
                  crew.getTitle()));
        } catch (Exception e) {
          log.warn("[알림] 미검토 인증 알림 발송 실패 crewId={}", crew.getId(), e);
        }
        cursorId = crew.getId();
      }
    } while (batch.size() == BATCH_SIZE);
  }
}
