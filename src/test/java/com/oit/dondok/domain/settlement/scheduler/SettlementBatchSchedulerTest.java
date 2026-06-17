package com.oit.dondok.domain.settlement.scheduler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.doThrow;

import com.oit.dondok.domain.mission.entity.DailySettlementType;
import com.oit.dondok.domain.settlement.service.DailySettlementBatchService;
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
class SettlementBatchSchedulerTest {

  @Mock private DailySettlementBatchService dailySettlementBatchService;
  @Mock private SettlementBatchService settlementBatchService;

  @InjectMocks private SettlementBatchScheduler scheduler;

  @Test
  void runTypeADailySettlementBatchDelegatesTypeA() {
    scheduler.runTypeADailySettlementBatch();

    then(dailySettlementBatchService).should().runDailySettlementBatch(DailySettlementType.A);
  }

  @Test
  void runTypeBDailySettlementBatchDelegatesTypeB() {
    scheduler.runTypeBDailySettlementBatch();

    then(dailySettlementBatchService).should().runDailySettlementBatch(DailySettlementType.B);
  }

  @Test
  void runTypeCDailySettlementBatchDelegatesTypeC() {
    scheduler.runTypeCDailySettlementBatch();

    then(dailySettlementBatchService).should().runDailySettlementBatch(DailySettlementType.C);
  }

  @Test
  void runTypeAFinalSettlementBatchDelegatesTypeA() {
    scheduler.runTypeAFinalSettlementBatch();

    then(settlementBatchService).should().runFinalSettlementBatch(DailySettlementType.A);
  }

  @Test
  void runTypeBFinalSettlementBatchDelegatesTypeB() {
    scheduler.runTypeBFinalSettlementBatch();

    then(settlementBatchService).should().runFinalSettlementBatch(DailySettlementType.B);
  }

  @Test
  void runTypeCFinalSettlementBatchDelegatesTypeC() {
    scheduler.runTypeCFinalSettlementBatch();

    then(settlementBatchService).should().runFinalSettlementBatch(DailySettlementType.C);
  }

  @Test
  void runRetrySettlementBatchDelegatesRetryBatch() {
    scheduler.runRetrySettlementBatch();

    then(settlementBatchService).should().runRetrySettlementBatch();
  }

  @Test
  void dailySettlementBatchDoesNotPropagateException() {
    doThrow(new CustomException(GlobalErrorCode.SERVER_ERROR))
        .when(dailySettlementBatchService)
        .runDailySettlementBatch(DailySettlementType.A);

    assertThatCode(() -> scheduler.runTypeADailySettlementBatch()).doesNotThrowAnyException();
  }

  @Test
  void finalSettlementBatchDoesNotPropagateException() {
    doThrow(new CustomException(GlobalErrorCode.SERVER_ERROR))
        .when(settlementBatchService)
        .runFinalSettlementBatch(DailySettlementType.C);

    assertThatCode(() -> scheduler.runTypeCFinalSettlementBatch()).doesNotThrowAnyException();
  }

  @Test
  void retrySettlementBatchDoesNotPropagateException() {
    doThrow(new CustomException(GlobalErrorCode.SERVER_ERROR))
        .when(settlementBatchService)
        .runRetrySettlementBatch();

    assertThatCode(() -> scheduler.runRetrySettlementBatch()).doesNotThrowAnyException();
  }

  @Test
  void settlementBatchSchedulersUseTypeSpecificCrons() throws NoSuchMethodException {
    assertSchedule("runTypeADailySettlementBatch", "0 0 12 * * *");
    assertSchedule("runTypeAFinalSettlementBatch", "0 10 12 * * *");
    assertSchedule("runTypeBDailySettlementBatch", "0 0 0 * * *");
    assertSchedule("runTypeBFinalSettlementBatch", "0 10 0 * * *");
    assertSchedule("runTypeCDailySettlementBatch", "0 5 12 * * *");
    assertSchedule("runTypeCFinalSettlementBatch", "0 20 12 * * *");
    assertSchedule("runRetrySettlementBatch", "0 */30 * * * *");
  }

  private void assertSchedule(String methodName, String cron) throws NoSuchMethodException {
    Method method = SettlementBatchScheduler.class.getMethod(methodName);
    Scheduled scheduled = method.getAnnotation(Scheduled.class);

    assertThat(scheduled).isNotNull();
    assertThat(scheduled.zone()).isEqualTo("Asia/Seoul");
    assertThat(scheduled.cron()).isEqualTo(cron);
  }
}
