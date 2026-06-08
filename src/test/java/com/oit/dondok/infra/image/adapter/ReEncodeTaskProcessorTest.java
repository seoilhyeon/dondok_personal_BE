package com.oit.dondok.infra.image.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.oit.dondok.domain.image.entity.ImageReEncodeTask;
import com.oit.dondok.domain.image.entity.ReEncodeTaskStatus;
import com.oit.dondok.domain.image.repository.ImageReEncodeTaskRepository;
import com.oit.dondok.domain.mission.port.ImageProcessingPort;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ReEncodeTaskProcessorTest {

  private static final Long TASK_ID = 1L;
  private static final Long MISSION_LOG_ID = 10L;
  private static final String OBJECT_PATH = "mission/42/101/abc";

  @Mock private ImageReEncodeTaskRepository repository;
  @Mock private ImageProcessingPort imageProcessingPort;

  @InjectMocks private ReEncodeTaskProcessor processor;

  // 성공: reEncode 호출 후 DONE으로 전이하고 lastError를 비운다.
  @Test
  void marksDoneOnSuccess() {
    ImageReEncodeTask task = pendingTask();
    given(repository.findById(TASK_ID)).willReturn(Optional.of(task));

    processor.process(TASK_ID);

    verify(imageProcessingPort).reEncode(OBJECT_PATH);
    assertThat(task.getStatus()).isEqualTo(ReEncodeTaskStatus.DONE);
    assertThat(task.getLastError()).isNull();
  }

  // 작업이 없으면 reEncode를 호출하지 않는다.
  @Test
  void skipsWhenTaskNotFound() {
    given(repository.findById(TASK_ID)).willReturn(Optional.empty());

    processor.process(TASK_ID);

    verify(imageProcessingPort, never()).reEncode(anyString());
  }

  // 이미 PENDING이 아닌 작업(DONE/FAILED)은 멱등 guard로 건너뛴다(중복 reEncode 방지).
  @Test
  void skipsWhenTaskNotPending() {
    ImageReEncodeTask task = pendingTask();
    task.markDone();
    given(repository.findById(TASK_ID)).willReturn(Optional.of(task));

    processor.process(TASK_ID);

    verify(imageProcessingPort, never()).reEncode(anyString());
    assertThat(task.getStatus()).isEqualTo(ReEncodeTaskStatus.DONE);
  }

  // 실패 1회: retryCount 증가, PENDING 유지, lastError 기록.
  @Test
  void recordsFailureAndStaysPendingBelowMaxRetry() {
    ImageReEncodeTask task = pendingTask();
    given(repository.findById(TASK_ID)).willReturn(Optional.of(task));
    willThrow(new RuntimeException("S3 down")).given(imageProcessingPort).reEncode(OBJECT_PATH);

    processor.process(TASK_ID);

    assertThat(task.getStatus()).isEqualTo(ReEncodeTaskStatus.PENDING);
    assertThat(task.getRetryCount()).isEqualTo(1);
    assertThat(task.getLastError()).contains("S3 down");
  }

  // 3회 연속 실패하면 FAILED로 종료한다.
  @Test
  void transitionsToFailedAfterMaxRetries() {
    ImageReEncodeTask task = pendingTask();
    given(repository.findById(TASK_ID)).willReturn(Optional.of(task));
    willThrow(new RuntimeException("S3 down")).given(imageProcessingPort).reEncode(OBJECT_PATH);

    processor.process(TASK_ID); // retry 1 -> PENDING
    processor.process(TASK_ID); // retry 2 -> PENDING
    processor.process(TASK_ID); // retry 3 -> FAILED

    assertThat(task.getStatus()).isEqualTo(ReEncodeTaskStatus.FAILED);
    assertThat(task.getRetryCount()).isEqualTo(3);
    verify(imageProcessingPort, times(3)).reEncode(OBJECT_PATH);
  }

  private ImageReEncodeTask pendingTask() {
    return ImageReEncodeTask.pending(MISSION_LOG_ID, OBJECT_PATH, LocalDateTime.now());
  }
}
