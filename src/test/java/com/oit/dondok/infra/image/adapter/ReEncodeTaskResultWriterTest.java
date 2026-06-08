package com.oit.dondok.infra.image.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.mockito.BDDMockito.given;

import com.oit.dondok.domain.image.entity.ImageReEncodeTask;
import com.oit.dondok.domain.image.entity.ReEncodeTaskStatus;
import com.oit.dondok.domain.image.repository.ImageReEncodeTaskRepository;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ReEncodeTaskResultWriterTest {

  private static final Long TASK_ID = 1L;
  private static final String OBJECT_PATH = "mission/42/101/abc";

  @Mock private ImageReEncodeTaskRepository repository;

  @InjectMocks private ReEncodeTaskResultWriter resultWriter;

  // complete: DONE으로 전이하고 lastError를 비운다.
  @Test
  void completeMarksDone() {
    ImageReEncodeTask task = pendingTask();
    given(repository.findById(TASK_ID)).willReturn(Optional.of(task));

    resultWriter.complete(TASK_ID);

    assertThat(task.getStatus()).isEqualTo(ReEncodeTaskStatus.DONE);
    assertThat(task.getLastError()).isNull();
  }

  // 실패 1회: retryCount 증가, PENDING 유지, lastError 기록.
  @Test
  void failBelowMaxStaysPending() {
    ImageReEncodeTask task = pendingTask();
    given(repository.findById(TASK_ID)).willReturn(Optional.of(task));

    resultWriter.fail(TASK_ID, new RuntimeException("S3 down"));

    assertThat(task.getStatus()).isEqualTo(ReEncodeTaskStatus.PENDING);
    assertThat(task.getRetryCount()).isEqualTo(1);
    assertThat(task.getLastError()).contains("S3 down");
  }

  // 3회 연속 실패하면 FAILED로 종료한다.
  @Test
  void failReachingMaxTransitionsToFailed() {
    ImageReEncodeTask task = pendingTask();
    given(repository.findById(TASK_ID)).willReturn(Optional.of(task));
    RuntimeException cause = new RuntimeException("S3 down");

    resultWriter.fail(TASK_ID, cause);
    resultWriter.fail(TASK_ID, cause);
    resultWriter.fail(TASK_ID, cause);

    assertThat(task.getStatus()).isEqualTo(ReEncodeTaskStatus.FAILED);
    assertThat(task.getRetryCount()).isEqualTo(3);
  }

  // 작업이 없으면 아무 일도 하지 않는다(예외 없음).
  @Test
  void noOpWhenTaskNotFound() {
    given(repository.findById(TASK_ID)).willReturn(Optional.empty());

    assertThatNoException()
        .isThrownBy(
            () -> {
              resultWriter.complete(TASK_ID);
              resultWriter.fail(TASK_ID, new RuntimeException("x"));
            });
  }

  private ImageReEncodeTask pendingTask() {
    return ImageReEncodeTask.pending(10L, OBJECT_PATH, LocalDateTime.now());
  }
}
