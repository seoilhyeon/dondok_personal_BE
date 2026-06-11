package com.oit.dondok.infra.image.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.mockito.BDDMockito.given;

import com.oit.dondok.domain.image.entity.ImageReEncodeTask;
import com.oit.dondok.domain.image.entity.ReEncodeTaskStatus;
import com.oit.dondok.domain.image.repository.ImageReEncodeTaskRepository;
import com.oit.dondok.global.exception.CustomException;
import com.oit.dondok.infra.image.exception.ImageErrorCode;
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

  // complete: 자신의 claim version이면 DONE으로 전이하고 lastError를 비운다.
  @Test
  void completeMarksDone() {
    ImageReEncodeTask task = claimedTask();
    given(repository.findById(TASK_ID)).willReturn(Optional.of(task));

    resultWriter.complete(TASK_ID, task.getAttemptVersion());

    assertThat(task.getStatus()).isEqualTo(ReEncodeTaskStatus.DONE);
    assertThat(task.getLastError()).isNull();
  }

  // 실패 1회: retryCount 증가, PENDING 유지, lastError 기록.
  @Test
  void failBelowMaxStaysPending() {
    ImageReEncodeTask task = claimedTask();
    given(repository.findById(TASK_ID)).willReturn(Optional.of(task));

    resultWriter.fail(TASK_ID, task.getAttemptVersion(), new RuntimeException("S3 down"));

    assertThat(task.getStatus()).isEqualTo(ReEncodeTaskStatus.PENDING);
    assertThat(task.getRetryCount()).isEqualTo(1);
    assertThat(task.getLastError()).contains("S3 down");
  }

  // 3회 연속 실패하면 FAILED로 종료한다. (recordFailure는 version을 올리지 않으므로 같은 version으로 누적)
  @Test
  void failReachingMaxTransitionsToFailed() {
    ImageReEncodeTask task = claimedTask();
    long version = task.getAttemptVersion();
    given(repository.findById(TASK_ID)).willReturn(Optional.of(task));
    RuntimeException cause = new RuntimeException("S3 down");

    resultWriter.fail(TASK_ID, version, cause);
    resultWriter.fail(TASK_ID, version, cause);
    resultWriter.fail(TASK_ID, version, cause);

    assertThat(task.getStatus()).isEqualTo(ReEncodeTaskStatus.FAILED);
    assertThat(task.getRetryCount()).isEqualTo(3);
  }

  // 영구 실패(content-level 거절)는 재시도 없이 한 번에 FAILED로 종결한다.
  @Test
  void permanentFailureTransitionsToFailedWithoutRetry() {
    ImageReEncodeTask task = claimedTask();
    given(repository.findById(TASK_ID)).willReturn(Optional.of(task));
    CustomException cause = new CustomException(ImageErrorCode.IMAGE_DIMENSIONS_TOO_LARGE);

    resultWriter.fail(TASK_ID, task.getAttemptVersion(), cause);

    assertThat(task.getStatus()).isEqualTo(ReEncodeTaskStatus.FAILED);
    assertThat(task.getRetryCount()).isEqualTo(1);
    assertThat(task.getLastError()).isNotNull();
  }

  // transient 실패(content-level 거절이 아닌 CustomException, 예: IMAGE_NOT_FOUND)는 재시도 대상으로 PENDING 유지.
  @Test
  void transientCustomExceptionStaysPending() {
    ImageReEncodeTask task = claimedTask();
    given(repository.findById(TASK_ID)).willReturn(Optional.of(task));

    resultWriter.fail(
        TASK_ID, task.getAttemptVersion(), new CustomException(ImageErrorCode.IMAGE_NOT_FOUND));

    assertThat(task.getStatus()).isEqualTo(ReEncodeTaskStatus.PENDING);
    assertThat(task.getRetryCount()).isEqualTo(1);
  }

  // 스토리지 읽기 실패(IMAGE_STORAGE_READ_FAILED)는 일시적일 수 있으므로 영구 실패로 단정하지 않고 재시도 대상으로 둔다.
  @Test
  void storageReadFailureStaysPending() {
    ImageReEncodeTask task = claimedTask();
    given(repository.findById(TASK_ID)).willReturn(Optional.of(task));

    resultWriter.fail(
        TASK_ID,
        task.getAttemptVersion(),
        new CustomException(ImageErrorCode.IMAGE_STORAGE_READ_FAILED));

    assertThat(task.getStatus()).isEqualTo(ReEncodeTaskStatus.PENDING);
    assertThat(task.getRetryCount()).isEqualTo(1);
  }

  // stale: 이미 DONE인 작업에 늦게 도착한 fail은 상태를 변경하지 않는다.
  @Test
  void ignoresStaleFailAfterDone() {
    ImageReEncodeTask task = claimedTask();
    long version = task.getAttemptVersion();
    task.markDone(); // 다른 처리로 이미 종결
    given(repository.findById(TASK_ID)).willReturn(Optional.of(task));

    resultWriter.fail(TASK_ID, version, new RuntimeException("late failure"));

    assertThat(task.getStatus()).isEqualTo(ReEncodeTaskStatus.DONE);
    assertThat(task.getRetryCount()).isZero();
  }

  // stale: 다른 워커가 reclaim(version 상승)한 작업에 옛 version의 complete는 무시된다.
  @Test
  void ignoresStaleCompleteAfterReclaim() {
    ImageReEncodeTask task = claimedTask();
    long staleVersion = task.getAttemptVersion();
    task.claim(LocalDateTime.now().plusMinutes(5)); // reclaim → version 상승, 여전히 PENDING
    given(repository.findById(TASK_ID)).willReturn(Optional.of(task));

    resultWriter.complete(TASK_ID, staleVersion);

    assertThat(task.getStatus()).isEqualTo(ReEncodeTaskStatus.PENDING);
  }

  // 작업이 없으면 아무 일도 하지 않는다(예외 없음).
  @Test
  void noOpWhenTaskNotFound() {
    given(repository.findById(TASK_ID)).willReturn(Optional.empty());

    assertThatNoException()
        .isThrownBy(
            () -> {
              resultWriter.complete(TASK_ID, 1L);
              resultWriter.fail(TASK_ID, 1L, new RuntimeException("x"));
            });
  }

  // claim된(version이 발급된) PENDING 작업 픽스처.
  private ImageReEncodeTask claimedTask() {
    ImageReEncodeTask task = ImageReEncodeTask.pending(10L, OBJECT_PATH, LocalDateTime.now());
    task.claim(LocalDateTime.now().plusMinutes(5));
    return task;
  }
}
