package com.oit.dondok.domain.settlement.scheduler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.doThrow;

import com.oit.dondok.domain.settlement.service.SettlementBatchService;
import com.oit.dondok.global.exception.CustomException;
import com.oit.dondok.global.exception.GlobalErrorCode;
import java.lang.reflect.Method;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.scheduling.annotation.Scheduled;

@ExtendWith(MockitoExtension.class)
class SettlementFinalBatchSchedulerTest {

  @Mock private SettlementBatchService settlementBatchService;

  @InjectMocks private SettlementFinalBatchScheduler settlementFinalBatchScheduler;

  @Test
  void runFinalSettlementBatchDelegatesToSettlementBatchService() {
    settlementFinalBatchScheduler.runFinalSettlementBatch();

    then(settlementBatchService).should().runFinalSettlementBatch();
    then(settlementBatchService).shouldHaveNoMoreInteractions();
  }

  @Test
  void runFinalSettlementBatchDoesNotPropagateException() {
    doThrow(new CustomException(GlobalErrorCode.SERVER_ERROR))
        .when(settlementBatchService)
        .runFinalSettlementBatch();

    assertThatCode(() -> settlementFinalBatchScheduler.runFinalSettlementBatch())
        .doesNotThrowAnyException();

    then(settlementBatchService).should().runFinalSettlementBatch();
  }

  @Test
  void runFinalSettlementBatchIsScheduledAfterCrewLifecycleBatch() throws NoSuchMethodException {
    Method method = SettlementFinalBatchScheduler.class.getMethod("runFinalSettlementBatch");
    Scheduled scheduled = method.getAnnotation(Scheduled.class);

    assertThat(scheduled).isNotNull();
    assertThat(scheduled.zone()).isEqualTo("Asia/Seoul");
    assertThat(scheduled.cron()).isEqualTo("0 10 0 * * *");
    assertThat(scheduled.cron()).isNotEqualTo("0 0 0 * * *");
  }
}
