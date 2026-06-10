package com.oit.dondok.domain.crew.service;

import com.oit.dondok.domain.crew.entity.CrewParticipant;
import com.oit.dondok.domain.crew.port.CrewPointPort;
import com.oit.dondok.domain.crew.repository.CrewParticipantRepository;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class PendingParticipantExpireProcessor {

  private final CrewParticipantRepository crewParticipantRepository;
  private final CrewPointPort crewPointPort;

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void processOne(Long participantId, LocalDateTime now) {
    CrewParticipant participant =
        crewParticipantRepository
            .findById(participantId)
            .orElseThrow(() -> new IllegalStateException("참가자를 찾을 수 없습니다: " + participantId));
    participant.expire(now);
    crewParticipantRepository.saveAndFlush(participant);
    crewPointPort.releaseExpiredReserve(participant);
  }
}
