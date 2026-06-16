package com.oit.dondok.domain.crew.service;

import com.oit.dondok.domain.crew.entity.Crew;
import com.oit.dondok.domain.crew.entity.CrewParticipant;
import com.oit.dondok.domain.crew.entity.CrewParticipantStatus;
import com.oit.dondok.domain.crew.entity.CrewStatus;
import com.oit.dondok.domain.crew.repository.CrewParticipantRepository;
import com.oit.dondok.domain.crew.repository.CrewRepository;
import com.oit.dondok.domain.notification.port.NotificationPayload;
import com.oit.dondok.domain.notification.port.NotificationSender;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class CrewCloseNotificationService {

  private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
  private static final int BATCH_SIZE = 500;
  private static final int DAYS_BEFORE_CLOSE = 3;

  private final CrewRepository crewRepository;
  private final CrewParticipantRepository crewParticipantRepository;
  private final NotificationSender notificationSender;

  // 종료 D-3일인 ACTIVE 크루의 전체 LOCKED 참여자에게 종료 예정 알림을 발송한다.
  @Transactional
  public void sendCloseReminders() {
    LocalDate today = LocalDate.now(SEOUL);
    LocalDate targetDate = today.plusDays(DAYS_BEFORE_CLOSE);
    LocalDateTime from = targetDate.atStartOfDay();
    LocalDateTime to = targetDate.plusDays(1).atStartOfDay();

    List<Crew> crews =
        crewRepository.findByStatusAndEndAtGreaterThanEqualAndEndAtLessThan(
            CrewStatus.ACTIVE, from, to);
    log.info("[알림] 크루 종료 예정 알림 대상 크루 수: {}", crews.size());

    for (Crew crew : crews) {
      sendToLockedParticipants(crew);
    }
  }

  private void sendToLockedParticipants(Crew crew) {
    Pageable pageable = PageRequest.of(0, BATCH_SIZE);
    long cursorId = 0L;
    List<CrewParticipant> batch;
    do {
      batch =
          crewParticipantRepository.findByCrewIdAndStatusAndIdGreaterThanOrderByIdAsc(
              crew.getId(), CrewParticipantStatus.LOCKED, cursorId, pageable);
      for (CrewParticipant participant : batch) {
        try {
          notificationSender.send(
              participant.getMember(),
              new NotificationPayload(
                  "CREW_CLOSE_SOON",
                  "crew",
                  String.valueOf(crew.getId()),
                  "dondok://crews/" + crew.getId(),
                  "'" + crew.getTitle() + "' 크루가 3일 후 종료됩니다."));
        } catch (RuntimeException e) {
          log.warn("[알림] 크루 종료 예정 알림 발송 실패 participantId={}", participant.getId(), e);
        }
        cursorId = participant.getId();
      }
    } while (batch.size() == BATCH_SIZE);
  }
}
