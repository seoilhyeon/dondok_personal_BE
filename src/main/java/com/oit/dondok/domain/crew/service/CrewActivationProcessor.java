package com.oit.dondok.domain.crew.service;

import com.oit.dondok.domain.crew.entity.Crew;
import com.oit.dondok.domain.crew.entity.CrewParticipant;
import com.oit.dondok.domain.crew.entity.CrewParticipantStatus;
import com.oit.dondok.domain.crew.port.CrewPointPort;
import com.oit.dondok.domain.crew.repository.CrewParticipantRepository;
import com.oit.dondok.domain.crew.repository.CrewRepository;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Component
@RequiredArgsConstructor
public class CrewActivationProcessor {

  private final CrewRepository crewRepository;
  private final CrewParticipantRepository crewParticipantRepository;
  private final CrewPointPort crewPointPort;

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void processOne(Long crewId, LocalDateTime now) {
    Crew crew =
        crewRepository
            .findById(crewId)
            .orElseThrow(() -> new IllegalStateException("크루를 찾을 수 없습니다: " + crewId));

    List<CrewParticipant> lockedParticipants =
        crewParticipantRepository.findByCrewIdAndStatus(crewId, CrewParticipantStatus.LOCKED);

    if (lockedParticipants.size() >= crew.getMinParticipants()) {
      crew.activate(now);
      log.info("[배치] 크루 활성화: crewId={}", crewId);
    } else {
      cancelCrew(crew, lockedParticipants, now);
    }
  }

  private void cancelCrew(Crew crew, List<CrewParticipant> lockedParticipants, LocalDateTime now) {
    log.info(
        "[배치] 크루 폐쇄: crewId={}, LOCKED={}명, 최소인원={}명",
        crew.getId(),
        lockedParticipants.size(),
        crew.getMinParticipants());

    crew.cancel(now);

    for (CrewParticipant participant : lockedParticipants) {
      participant.cancelOnCrewCancelled(now);
      crewPointPort.releaseLockedDepositForCancelledCrew(participant);
    }

    List<CrewParticipant> pendingParticipants =
        crewParticipantRepository.findByCrewIdAndStatus(
            crew.getId(), CrewParticipantStatus.PENDING);

    for (CrewParticipant participant : pendingParticipants) {
      participant.cancelOnCrewCancelled(now);
      crewPointPort.releasePendingReserve(participant);
    }

    // TODO: FCM 알림 발송 - NOTIFY-001 완료 후 연동
  }
}
