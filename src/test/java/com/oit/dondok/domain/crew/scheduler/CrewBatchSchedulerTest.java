package com.oit.dondok.domain.crew.scheduler;

import static org.mockito.BDDMockito.then;

import com.oit.dondok.domain.crew.service.CrewActivationBatchService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CrewBatchSchedulerTest {

  @Mock private CrewActivationBatchService crewActivationBatchService;

  @InjectMocks private CrewBatchScheduler crewBatchScheduler;

  @Test
  void runDailyBatch_activateCrews_호출확인() {
    // when
    crewBatchScheduler.runDailyBatch();

    // then
    then(crewActivationBatchService).should().activateCrews();
    then(crewActivationBatchService).shouldHaveNoMoreInteractions();
  }
}
