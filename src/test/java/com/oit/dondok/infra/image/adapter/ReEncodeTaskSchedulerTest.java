package com.oit.dondok.infra.image.adapter;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.verify;

import com.oit.dondok.domain.image.entity.ImageReEncodeTask;
import com.oit.dondok.domain.image.repository.ImageReEncodeTaskClaimRepository;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class ReEncodeTaskSchedulerTest {

  @Mock private ImageReEncodeTaskClaimRepository claimRepository;
  @Mock private ReEncodeTaskProcessor processor;

  @InjectMocks private ReEncodeTaskScheduler scheduler;

  // claim 단계로 선점한 각 작업을 processor로 재처리한다.
  @Test
  void processesEachClaimedTask() {
    ImageReEncodeTask first = taskWithId(1L);
    ImageReEncodeTask second = taskWithId(2L);
    given(claimRepository.claimPendingTasks(any(LocalDateTime.class), anyInt()))
        .willReturn(List.of(first, second));

    scheduler.retryPending();

    verify(processor).process(first);
    verify(processor).process(second);
  }

  // 한 작업 처리가 예외를 던져도 같은 사이클의 나머지 작업은 계속 처리한다.
  @Test
  void continuesProcessingWhenOneTaskThrows() {
    ImageReEncodeTask first = taskWithId(1L);
    ImageReEncodeTask second = taskWithId(2L);
    given(claimRepository.claimPendingTasks(any(LocalDateTime.class), anyInt()))
        .willReturn(List.of(first, second));
    willThrow(new RuntimeException("commit failure")).given(processor).process(first);

    scheduler.retryPending();

    verify(processor).process(first);
    verify(processor).process(second);
  }

  private ImageReEncodeTask taskWithId(Long id) {
    ImageReEncodeTask task =
        ImageReEncodeTask.pending(100L, "mission/42/101/key-" + id, LocalDateTime.now());
    ReflectionTestUtils.setField(task, "id", id);
    return task;
  }
}
