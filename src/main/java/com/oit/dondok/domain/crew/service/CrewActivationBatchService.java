package com.oit.dondok.domain.crew.service;

import com.oit.dondok.domain.crew.entity.Crew;
import com.oit.dondok.domain.crew.entity.CrewParticipant;
import com.oit.dondok.domain.crew.entity.CrewParticipantStatus;
import com.oit.dondok.domain.crew.entity.CrewStatus;
import com.oit.dondok.domain.crew.port.CrewPointPort;
import com.oit.dondok.domain.crew.repository.CrewParticipantRepository;
import com.oit.dondok.domain.crew.repository.CrewRepository;
import java.time.Duration;
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
public class CrewActivationBatchService {

  private static final ZoneId SEOUL_ZONE = ZoneId.of("Asia/Seoul");

  private final CrewRepository crewRepository;
  private final CrewParticipantRepository crewParticipantRepository;
  private final CrewPointPort crewPointPort;

  @Transactional
  public void activateCrews() {
    LocalDateTime startTime = LocalDateTime.now(SEOUL_ZONE);
    log.info("[배치] 크루 활성화 시작: {}", startTime);
    try {
      List<Crew> crews =
          crewRepository.findByStatusAndStartAtBefore(CrewStatus.RECRUITING, startTime);
      log.info("[배치] 활성화 대상 크루 수: {}", crews.size());

      int activatedCount = 0;
      int cancelledCount = 0;

      for (Crew crew : crews) {
        long lockedCount =
            crewParticipantRepository.countByCrewIdAndStatus(
                crew.getId(), CrewParticipantStatus.LOCKED);

        if (lockedCount >= crew.getMinParticipants()) {
          crew.activate(startTime);
          activatedCount++;
        } else {
          cancelCrew(crew, startTime);
          cancelledCount++;
        }
      }

      long elapsedMs = Duration.between(startTime, LocalDateTime.now(SEOUL_ZONE)).toMillis();
      log.info(
          "[배치] 크루 처리 완료: 활성화 {}건, 폐쇄 {}건, 소요시간: {}ms", activatedCount, cancelledCount, elapsedMs);
    } catch (Exception e) {
      log.error("[배치] 크루 활성화 중 예외 발생", e);
      throw e;
    }
  }

  private void cancelCrew(Crew crew, LocalDateTime now) {
    log.info("[배치] 크루 폐쇄 처리: crewId={}, 최소인원={}", crew.getId(), crew.getMinParticipants());
    crew.cancel(now);

    List<CrewParticipant> lockedParticipants =
        crewParticipantRepository.findByCrewIdAndStatus(crew.getId(), CrewParticipantStatus.LOCKED);

    for (CrewParticipant participant : lockedParticipants) {
      crewPointPort.releaseLockedDepositForCancelledCrew(participant);
    }

    // TODO: FCM 알림 발송 - NOTIFY-001 완료 후 연동
  }
}
