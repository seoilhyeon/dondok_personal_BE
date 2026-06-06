package com.oit.dondok.domain.point.service;

import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import com.oit.dondok.domain.crew.entity.CrewParticipant;
import org.junit.jupiter.api.Test;

class CrewPointPortAdapterTest {

  private final PointLedgerService pointLedgerService = mock(PointLedgerService.class);
  private final CrewPointPortAdapter adapter = new CrewPointPortAdapter(pointLedgerService);

  @Test
  void lockForHostParticipantDelegatesToHostDepositLock() {
    CrewParticipant participant = mock(CrewParticipant.class);

    adapter.lockForHostParticipant(participant);

    then(pointLedgerService).should().lockHostDeposit(participant);
    verifyNoMoreInteractions(pointLedgerService);
  }

  @Test
  void reserveForPendingParticipantDelegatesToPendingDepositReserve() {
    CrewParticipant participant = mock(CrewParticipant.class);

    adapter.reserveForPendingParticipant(participant);

    then(pointLedgerService).should().reservePendingDeposit(participant);
    verifyNoMoreInteractions(pointLedgerService);
  }

  @Test
  void lockForApprovedParticipantDelegatesToPendingReserveLock() {
    CrewParticipant participant = mock(CrewParticipant.class);

    adapter.lockForApprovedParticipant(participant);

    then(pointLedgerService).should().lockPendingReserve(participant);
    verifyNoMoreInteractions(pointLedgerService);
  }

  @Test
  void releasePendingReserveDelegatesToPendingReserveRelease() {
    CrewParticipant participant = mock(CrewParticipant.class);

    adapter.releasePendingReserve(participant);

    then(pointLedgerService).should().releasePendingReserve(participant);
    verifyNoMoreInteractions(pointLedgerService);
  }

  @Test
  void releaseRejectedReserveDelegatesToPendingReserveRelease() {
    CrewParticipant participant = mock(CrewParticipant.class);

    adapter.releaseRejectedReserve(participant);

    then(pointLedgerService).should().releasePendingReserve(participant);
    verifyNoMoreInteractions(pointLedgerService);
  }

  @Test
  void releaseExpiredReserveDelegatesToPendingReserveRelease() {
    CrewParticipant participant = mock(CrewParticipant.class);

    adapter.releaseExpiredReserve(participant);

    then(pointLedgerService).should().releasePendingReserve(participant);
    verifyNoMoreInteractions(pointLedgerService);
  }
}
