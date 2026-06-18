package com.oit.dondok.domain.point.scheduler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.doThrow;

import com.oit.dondok.domain.point.service.PointChargeRecoveryService;
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
class PointChargeRecoverySchedulerTest {

  @Mock private PointChargeRecoveryService pointChargeRecoveryService;

  @InjectMocks private PointChargeRecoveryScheduler scheduler;

  @Test
  void runPointChargeRecoveryBatchDelegatesService() {
    scheduler.runPointChargeRecoveryBatch();

    then(pointChargeRecoveryService).should().runRecoveryBatch();
  }

  @Test
  void runPointChargeRecoveryBatchDoesNotPropagateException() {
    doThrow(new CustomException(GlobalErrorCode.SERVER_ERROR))
        .when(pointChargeRecoveryService)
        .runRecoveryBatch();

    assertThatCode(() -> scheduler.runPointChargeRecoveryBatch()).doesNotThrowAnyException();
  }

  @Test
  void pointChargeRecoveryBatchRunsEveryFiveMinutes() throws NoSuchMethodException {
    Method method = PointChargeRecoveryScheduler.class.getMethod("runPointChargeRecoveryBatch");

    Scheduled scheduled = method.getAnnotation(Scheduled.class);

    assertThat(scheduled).isNotNull();
    assertThat(scheduled.zone()).isEqualTo("Asia/Seoul");
    assertThat(scheduled.cron()).isEqualTo("0 */5 * * * *");
  }
}
