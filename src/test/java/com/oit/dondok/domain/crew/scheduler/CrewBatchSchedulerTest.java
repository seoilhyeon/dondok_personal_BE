package com.oit.dondok.domain.crew.scheduler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.doThrow;

import com.oit.dondok.domain.crew.service.CrewActivationBatchService;
import com.oit.dondok.domain.crew.service.PendingApplicationAutoRejectService;
import com.oit.dondok.global.exception.CustomException;
import com.oit.dondok.global.exception.GlobalErrorCode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.scheduling.annotation.Scheduled;

@ExtendWith(MockitoExtension.class)
class CrewBatchSchedulerTest {

  @Mock private CrewActivationBatchService crewActivationBatchService;
  @Mock private PendingApplicationAutoRejectService pendingApplicationAutoRejectService;

  @InjectMocks private CrewBatchScheduler crewBatchScheduler;

  @Test
  void runDailyBatchCallsAutoRejectThenActivateCrews() {
    crewBatchScheduler.runDailyBatch();

    then(pendingApplicationAutoRejectService).should().rejectExpiredApplications();
    then(crewActivationBatchService).should().activateCrews();
    then(pendingApplicationAutoRejectService).shouldHaveNoMoreInteractions();
    then(crewActivationBatchService).shouldHaveNoMoreInteractions();
  }

  @Test
  void runDailyBatchContinuesToActivateCrewsWhenAutoRejectFails() {
    doThrow(new CustomException(GlobalErrorCode.SERVER_ERROR))
        .when(pendingApplicationAutoRejectService)
        .rejectExpiredApplications();

    assertThatCode(() -> crewBatchScheduler.runDailyBatch()).doesNotThrowAnyException();

    then(crewActivationBatchService).should().activateCrews();
  }

  @Test
  void runDailyBatchDoesNotPropagateActivationFailure() {
    doThrow(new CustomException(GlobalErrorCode.SERVER_ERROR))
        .when(crewActivationBatchService)
        .activateCrews();

    assertThatCode(() -> crewBatchScheduler.runDailyBatch()).doesNotThrowAnyException();

    then(pendingApplicationAutoRejectService).should().rejectExpiredApplications();
    then(crewActivationBatchService).should().activateCrews();
  }

  @Test
  void runDailyBatchUsesCrewLifecycleMidnightSchedule() throws NoSuchMethodException {
    Scheduled scheduled =
        CrewBatchScheduler.class.getMethod("runDailyBatch").getAnnotation(Scheduled.class);

    assertThat(scheduled).isNotNull();
    assertThat(scheduled.zone()).isEqualTo("Asia/Seoul");
    assertThat(scheduled.cron()).isEqualTo("0 0 0 * * *");
  }
}
