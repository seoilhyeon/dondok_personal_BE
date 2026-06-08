package com.oit.dondok.infra.image.adapter;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import com.oit.dondok.domain.image.entity.ImageReEncodeTask;
import com.oit.dondok.domain.image.entity.ReEncodeTaskStatus;
import com.oit.dondok.domain.image.repository.ImageReEncodeTaskRepository;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class ReEncodeTaskSchedulerTest {

  @Mock private ImageReEncodeTaskRepository repository;
  @Mock private ReEncodeTaskProcessor processor;

  @InjectMocks private ReEncodeTaskScheduler scheduler;

  // 기한이 도래한 PENDING 작업들을 조회해 각 작업을 processor로 재처리한다.
  @Test
  void processesEachDuePendingTask() {
    List<ImageReEncodeTask> due = List.of(taskWithId(1L), taskWithId(2L));
    given(
            repository.findByStatusAndNextAttemptAtLessThanEqualOrderByNextAttemptAt(
                eq(ReEncodeTaskStatus.PENDING), any(LocalDateTime.class), any(Pageable.class)))
        .willReturn(due);

    scheduler.retryPending();

    verify(processor).process(1L);
    verify(processor).process(2L);
  }

  private ImageReEncodeTask taskWithId(Long id) {
    ImageReEncodeTask task =
        ImageReEncodeTask.pending(100L, "mission/42/101/key-" + id, LocalDateTime.now());
    ReflectionTestUtils.setField(task, "id", id);
    return task;
  }
}
