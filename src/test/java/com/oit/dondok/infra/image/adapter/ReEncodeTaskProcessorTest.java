package com.oit.dondok.infra.image.adapter;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.oit.dondok.domain.image.entity.ImageReEncodeTask;
import com.oit.dondok.domain.mission.port.ImageProcessingPort;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class ReEncodeTaskProcessorTest {

  private static final Long TASK_ID = 1L;
  private static final String OBJECT_PATH = "mission/42/101/abc";

  @Mock private ImageProcessingPort imageProcessingPort;
  @Mock private ReEncodeTaskResultWriter resultWriter;

  @InjectMocks private ReEncodeTaskProcessor processor;

  // 성공: 락/tx 밖에서 reEncode 후 claim version과 함께 complete만 기록, fail 미호출.
  @Test
  void reEncodesThenRecordsComplete() {
    ImageReEncodeTask task = task();

    processor.process(task);

    verify(imageProcessingPort).reEncode(OBJECT_PATH);
    verify(resultWriter).complete(TASK_ID, task.getAttemptVersion());
    verify(resultWriter, never()).fail(anyLong(), anyLong(), any());
  }

  // reEncode 실패: claim version과 함께 fail만 기록, complete 미호출(잘못된 성공 기록 방지).
  @Test
  void recordsFailureWhenReEncodeThrows() {
    willThrow(new RuntimeException("S3 down")).given(imageProcessingPort).reEncode(OBJECT_PATH);
    ImageReEncodeTask task = task();

    processor.process(task);

    verify(resultWriter)
        .fail(eq(TASK_ID), eq(task.getAttemptVersion()), any(RuntimeException.class));
    verify(resultWriter, never()).complete(anyLong(), anyLong());
  }

  // claim된(version이 발급된) 작업 픽스처.
  private ImageReEncodeTask task() {
    ImageReEncodeTask task = ImageReEncodeTask.pending(10L, OBJECT_PATH, LocalDateTime.now());
    ReflectionTestUtils.setField(task, "id", TASK_ID);
    task.claim(LocalDateTime.now().plusMinutes(5));
    return task;
  }
}
