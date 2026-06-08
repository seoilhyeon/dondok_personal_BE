package com.oit.dondok.infra.image.adapter;

import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willAnswer;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.oit.dondok.domain.image.entity.ImageReEncodeTask;
import com.oit.dondok.domain.image.repository.ImageReEncodeTaskClaimRepository;
import com.oit.dondok.infra.image.event.ReEncodeTaskCreatedEvent;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.task.TaskExecutor;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class ReEncodeTaskEventListenerTest {

  private static final Long TASK_ID = 7L;

  @Mock private ImageReEncodeTaskClaimRepository claimRepository;
  @Mock private ReEncodeTaskProcessor processor;
  @Mock private TaskExecutor reEncodeTaskExecutor;

  @InjectMocks private ReEncodeTaskEventListener listener;

  // 제출된 작업은 executor 스레드에서 claim 후 processor로 처리된다(executor 인라인 실행으로 시뮬레이션).
  @Test
  void claimsAndProcessesSubmittedTask() {
    runSubmittedRunnableInline();
    ImageReEncodeTask task = task();
    given(claimRepository.claimById(eq(TASK_ID), any(LocalDateTime.class)))
        .willReturn(Optional.of(task));

    listener.onCreated(new ReEncodeTaskCreatedEvent(TASK_ID));

    verify(processor).process(task);
  }

  // executor 포화로 제출이 거부돼도 예외를 전파하지 않는다(요청 성공 유지). claim도 호출되지 않는다.
  @Test
  void swallowsRejectionWhenExecutorSaturated() {
    willThrow(new TaskRejectedException("saturated")).given(reEncodeTaskExecutor).execute(any());

    assertThatNoException()
        .isThrownBy(() -> listener.onCreated(new ReEncodeTaskCreatedEvent(TASK_ID)));

    verify(claimRepository, never()).claimById(anyLong(), any());
    verify(processor, never()).process(any());
  }

  private void runSubmittedRunnableInline() {
    willAnswer(
            invocation -> {
              Runnable submitted = invocation.getArgument(0);
              submitted.run();
              return null;
            })
        .given(reEncodeTaskExecutor)
        .execute(any());
  }

  private ImageReEncodeTask task() {
    ImageReEncodeTask task =
        ImageReEncodeTask.pending(10L, "mission/42/101/key", LocalDateTime.now());
    ReflectionTestUtils.setField(task, "id", TASK_ID);
    return task;
  }
}
