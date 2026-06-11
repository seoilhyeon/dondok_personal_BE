package com.oit.dondok.domain.crew.service;

import com.oit.dondok.domain.crew.entity.Crew;
import com.oit.dondok.domain.crew.entity.CrewParticipant;
import com.oit.dondok.domain.crew.entity.CrewParticipantStatus;
import com.oit.dondok.domain.crew.exception.CrewErrorCode;
import com.oit.dondok.domain.crew.port.CrewPointPort;
import com.oit.dondok.domain.crew.repository.CrewParticipantRepository;
import com.oit.dondok.domain.crew.repository.CrewRepository;
import com.oit.dondok.global.exception.CustomException;
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

  private static final List<CrewParticipantStatus> REFUNDABLE_STATUSES =
      List.of(CrewParticipantStatus.LOCKED, CrewParticipantStatus.PENDING);

  private final CrewRepository crewRepository;
  private final CrewParticipantRepository crewParticipantRepository;
  private final CrewPointPort crewPointPort;

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void processOne(Long crewId, LocalDateTime now) {
    Crew crew =
        crewRepository
            .findById(crewId)
            .orElseThrow(() -> new CustomException(CrewErrorCode.CREW_NOT_FOUND));

    List<CrewParticipant> participants =
        crewParticipantRepository.findByCrewIdAndStatusIn(crewId, REFUNDABLE_STATUSES);

    long lockedCount =
        participants.stream().filter(p -> p.getStatus() == CrewParticipantStatus.LOCKED).count();

    if (lockedCount >= crew.getMinParticipants()) {
      crew.activate(now);
      log.info("[배치] 크루 활성화: crewId={}", crewId);
    } else {
      cancelCrew(crew, participants, now);
    }
  }

  private void cancelCrew(Crew crew, List<CrewParticipant> participants, LocalDateTime now) {
    log.info(
        "[배치] 크루 폐쇄: crewId={}, 환급 대상={}명, 최소인원={}명",
        crew.getId(),
        participants.size(),
        crew.getMinParticipants());

    crew.cancel(now);

    for (CrewParticipant participant : participants) {
      CrewParticipantStatus statusBeforeCancel = participant.getStatus();
      participant.cancelOnCrewCancelled(now);
      if (statusBeforeCancel == CrewParticipantStatus.LOCKED) {
        crewPointPort.releaseLockedDepositForCancelledCrew(participant);
      } else {
        crewPointPort.releasePendingReserve(participant);
      }
    }

    // TODO: FCM 알림 발송 - NOTIFY-001 완료 후 연동
  }
}
