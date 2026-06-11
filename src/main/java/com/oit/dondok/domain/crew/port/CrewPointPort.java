package com.oit.dondok.domain.crew.port;

import com.oit.dondok.domain.crew.entity.CrewParticipant;

public interface CrewPointPort {

  void lockForHostParticipant(CrewParticipant crewParticipant);

  void reserveForPendingParticipant(CrewParticipant participant);

  void lockForApprovedParticipant(CrewParticipant participant);

  void releasePendingReserve(CrewParticipant participant);

  void releaseRejectedReserve(CrewParticipant participant);

  void releaseExpiredReserve(CrewParticipant participant);

  void releaseLockedDepositForCancelledCrew(CrewParticipant participant);
}
