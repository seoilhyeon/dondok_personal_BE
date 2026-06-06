package com.oit.dondok.domain.point.service;

import com.oit.dondok.domain.crew.entity.CrewParticipant;
import com.oit.dondok.domain.crew.port.CrewPointPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class CrewPointPortAdapter implements CrewPointPort {

  private final PointLedgerService pointLedgerService;

  @Override
  public void lockForHostParticipant(CrewParticipant crewParticipant) {
    pointLedgerService.lockHostDeposit(crewParticipant);
  }

  @Override
  public void reserveForPendingParticipant(CrewParticipant participant) {
    pointLedgerService.reservePendingDeposit(participant);
  }

  @Override
  public void lockForApprovedParticipant(CrewParticipant participant) {
    pointLedgerService.lockPendingReserve(participant);
  }

  @Override
  public void releasePendingReserve(CrewParticipant participant) {
    pointLedgerService.releasePendingReserve(participant);
  }

  @Override
  public void releaseRejectedReserve(CrewParticipant participant) {
    pointLedgerService.releasePendingReserve(participant);
  }

  @Override
  public void releaseExpiredReserve(CrewParticipant participant) {
    pointLedgerService.releasePendingReserve(participant);
  }
}
