package com.oit.dondok.domain.crew.scheduler;

import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.doThrow;

import com.oit.dondok.domain.crew.service.CrewActivationBatchService;
import com.oit.dondok.domain.crew.service.PendingApplicationAutoRejectService;
import com.oit.dondok.domain.settlement.service.SettlementBatchService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CrewBatchSchedulerTest {

  @Mock private CrewActivationBatchService crewActivationBatchService;
  @Mock private PendingApplicationAutoRejectService pendingApplicationAutoRejectService;
  @Mock private SettlementBatchService settlementBatchService;

  @InjectMocks private CrewBatchScheduler crewBatchScheduler;

  @Test
  void runDailyBatchCallsAutoRejectThenActivateCrews() {
    crewBatchScheduler.runDailyBatch();

    then(pendingApplicationAutoRejectService).should().rejectExpiredApplications();
    then(crewActivationBatchService).should().activateCrews();
    then(settlementBatchService).should().runFinalSettlementBatch();
    then(pendingApplicationAutoRejectService).shouldHaveNoMoreInteractions();
    then(crewActivationBatchService).shouldHaveNoMoreInteractions();
    then(settlementBatchService).shouldHaveNoMoreInteractions();
  }

  @Test
  void runDailyBatchContinuesToActivateCrewsWhenAutoRejectFails() {
    doThrow(new RuntimeException("auto-reject error"))
        .when(pendingApplicationAutoRejectService)
        .rejectExpiredApplications();

    crewBatchScheduler.runDailyBatch();

    then(crewActivationBatchService).should().activateCrews();
    then(settlementBatchService).should().runFinalSettlementBatch();
  }
}
